package com.bpm.application;

import com.bpm.api.dto.PostDto;
import com.bpm.domain.AccountStatus;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.position.Position;
import com.bpm.domain.social.Post;
import com.bpm.domain.social.PostComment;
import com.bpm.domain.social.PostLike;
import com.bpm.domain.social.PostMedia;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.PostCommentRepository;
import com.bpm.infrastructure.PostLikeRepository;
import com.bpm.infrastructure.PostMediaRepository;
import com.bpm.infrastructure.PostRepository;
import com.bpm.infrastructure.UserAccountRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bảng tin mạng xã hội nội bộ (Epic 2): tạo bài + media, feed phạm vi xem theo lô, ghim, like/unlike,
 * bình luận 1 cấp (sửa/xoá trong hạn), thông báo (FR-B07) qua NotificationService, kiểm duyệt ẩn/xoá + audit.
 * Hexagonal-lite: mọi mutation phát audit qua AuditPort.
 */
@Service
public class PostService {

    private static final int EDIT_WINDOW_MIN = 15;   // sửa/xoá bình luận trong 15 phút (FR-B06)
    private static final int DEFAULT_BATCH = 20;     // tải-thêm theo lô (NFR-04)

    private final PostRepository postRepo;
    private final PostCommentRepository commentRepo;
    private final PostLikeRepository likeRepo;
    private final PostMediaRepository mediaRepo;
    private final PositionRepository positionRepo;
    private final UserAccountRepository userRepo;
    private final NotificationService notify;
    private final AuditPort audit;
    private final ObjectMapper mapper = new ObjectMapper();

    public PostService(PostRepository postRepo, PostCommentRepository commentRepo, PostLikeRepository likeRepo,
                       PostMediaRepository mediaRepo, PositionRepository positionRepo, UserAccountRepository userRepo,
                       NotificationService notify, AuditPort audit) {
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.likeRepo = likeRepo;
        this.mediaRepo = mediaRepo;
        this.positionRepo = positionRepo;
        this.userRepo = userRepo;
        this.notify = notify;
        this.audit = audit;
    }

    // ===================== Tạo bài + media (Story 2.1) =====================

    @Transactional
    public Post create(String body, List<String> mediaIds, String category, String topic,
                       List<String> mentionUserIds, String actor) {
        if ((body == null || body.isBlank()) && (mediaIds == null || mediaIds.isEmpty())) {
            throw new IllegalArgumentException("Bài viết cần nội dung hoặc media");
        }
        // Bài đăng luôn cho TOÀN CÔNG TY (bỏ phạm vi phòng ban): scope cố định "ALL", orgUnitId null.
        String sc = "ALL";
        String cat = Post.normalizeCategory(category);
        UserAccount author = user(actor);
        String mediaJson = mediaJson(mediaIds == null ? List.of() : mediaIds);
        List<String> mentions = validMentionIds(mentionUserIds);
        Post p = postRepo.save(new Post(author.getId(), author.getFullName(), body == null ? "" : body,
                mediaJson, sc, null, topic, String.join(",", mentions), cat));
        // gắn post_id cho media đã lưu
        if (mediaIds != null) {
            for (String mid : mediaIds) {
                mediaRepo.findById(mid).ifPresent(m -> { m.setPostId(p.getId()); mediaRepo.save(m); });
            }
        }
        audit.record("POST_CREATED", "Post", p.getId(), actor, "scope=ALL,category=" + cat);
        notifyNewPost(p);
        notifyMentions(mentions, author, p.getId(), p.getAuthorId(), "POST_MENTION",
                author.getFullName() + " đã nhắc bạn trong một bài viết", snippet(p.getBody()),
                Set.of(p.getAuthorId()));
        return p;
    }

    /**
     * Tạo TIN HỆ THỐNG nổi bật (ghim) — dùng cho tin tự động (chúc mừng sinh nhật / onboard).
     * Idempotent theo {@code marker} ẩn cuối body: nếu đã có bài chứa marker → KHÔNG tạo lại (trả false).
     * Tác giả = tài khoản admin truyền vào (authorId/authorName); hiển thị tên hệ thống "VMO DX".
     * {@code subjectUserId} = nhân sự được chúc (để FE nhúng ảnh đại diện vào nội dung); null nếu không có.
     * KHÔNG phát thông báo hàng loạt (tránh spam khi endpoint highlights được gọi lặp).
     *
     * @return true nếu vừa tạo mới, false nếu đã tồn tại (bỏ qua).
     */
    @Transactional
    public boolean createSystemPinned(String authorId, String authorName, String body, String category,
                                      String subjectUserId, String marker) {
        if (body == null || marker == null) {
            return false;
        }
        if (postRepo.existsByBodyContaining(marker)) {
            return false; // đã có tin loại này cho người/ngày → bỏ qua
        }
        String cat = Post.normalizeCategory(category);
        String fullBody = body + marker;
        Post p = new Post(authorId, authorName == null ? "VMO DX" : authorName, fullBody,
                "[]", "ALL", null, null, "", cat);
        p.setPinned(true);
        p.setSubjectUserId(subjectUserId);
        postRepo.save(p);
        audit.record("POST_CREATED", "Post", p.getId(), authorName,
                "auto-celebrate,pinned=true,category=" + cat
                        + (subjectUserId != null ? ",subject=" + subjectUserId : ""));
        return true;
    }

    // ===================== Feed (Story 2.2/2.3) =====================

    /**
     * Feed của user, ghim trước, mới nhất, phân trang theo lô.
     * Bài mới đều scope=ALL (toàn công ty); bài cũ scope=ORG_UNIT vẫn hiển thị cho thành viên phòng đó.
     * Lọc nhanh tuỳ chọn theo phân loại (category ∈ NEWS/EVENT/ANNOUNCEMENT) và chủ đề (topic).
     */
    @Transactional(readOnly = true)
    public List<Post> feed(String actor, int page, Integer size, String filterCategory, String topic) {
        List<String> unitIds = unitsOf(actor);
        int batch = size == null || size <= 0 || size > 100 ? DEFAULT_BATCH : size;
        Pageable pageable = PageRequest.of(Math.max(0, page), batch);
        String cat = blankToNull(filterCategory);
        return postRepo.feed(unitIds.isEmpty() ? List.of("__none__") : unitIds,
                cat == null ? null : Post.normalizeCategory(cat), blankToNull(topic), pageable);
    }

    @Transactional(readOnly = true)
    public List<Post> adminList(int page, Integer size) {
        int batch = size == null || size <= 0 || size > 100 ? DEFAULT_BATCH : size;
        return postRepo.findAllByOrderByPinnedDescCreatedAtDesc(PageRequest.of(Math.max(0, page), batch));
    }

    @Transactional(readOnly = true)
    public Post get(String id) {
        return postRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết"));
    }

    /** userId của username (null nếu không có) — FE đánh dấu bình luận của chính mình. */
    @Transactional(readOnly = true)
    public String myUserId(String username) {
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }

    public long likeCount(String postId) { return likeRepo.countByPostId(postId); }
    public long commentCount(String postId) { return commentRepo.countByPostIdAndHiddenFalse(postId); }

    public boolean likedBy(String postId, String actor) {
        UserAccount u = userRepo.findByUsername(actor).orElse(null);
        return u != null && likeRepo.existsByPostIdAndUserId(postId, u.getId());
    }

    /** Liked map cho một tập bài của user hiện tại (tránh N+1 ở feed). */
    @Transactional(readOnly = true)
    public Set<String> likedPostIds(String actor, List<String> postIds) {
        UserAccount u = userRepo.findByUsername(actor).orElse(null);
        if (u == null || postIds.isEmpty()) {
            return Set.of();
        }
        return likeRepo.findByUserIdAndPostIdIn(u.getId(), postIds).stream()
                .map(PostLike::getPostId).collect(Collectors.toSet());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> likeCounts(List<String> postIds) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (PostLike l : likeRepo.findByPostIdIn(postIds)) {
            m.merge(l.getPostId(), 1L, Long::sum);
        }
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> commentCounts(List<String> postIds) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (PostComment c : commentRepo.findByPostIdInAndHiddenFalse(postIds)) {
            m.merge(c.getPostId(), 1L, Long::sum);
        }
        return m;
    }

    @Transactional(readOnly = true)
    public List<PostMedia> media(Post p) {
        List<String> ids = mediaIdsOf(p);
        if (ids.isEmpty()) {
            return List.of();
        }
        return mediaRepo.findAllById(ids);
    }

    // ===================== Ghim (Story 2.3) =====================

    @Transactional
    public Post setPinned(String id, boolean pinned, String actor) {
        Post p = get(id);
        p.setPinned(pinned);
        postRepo.save(p);
        audit.record(pinned ? "POST_PINNED" : "POST_UNPINNED", "Post", id, actor, "");
        return p;
    }

    // ===================== Like / Unlike (Story 2.4) =====================

    /** Toggle like. Trả số lượt like sau khi thao tác. */
    @Transactional
    public long toggleLike(String id, String actor) {
        get(id); // tồn tại
        UserAccount u = user(actor);
        var existing = likeRepo.findByPostIdAndUserId(id, u.getId());
        if (existing.isPresent()) {
            likeRepo.delete(existing.get());
        } else {
            likeRepo.save(new PostLike(id, u.getId()));
        }
        return likeRepo.countByPostId(id);
    }

    // ===================== Bình luận (Story 2.5/2.6) =====================

    @Transactional
    public PostComment addComment(String postId, String body, String parentId,
                                  List<String> mentionUserIds, String actor) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Bình luận trống");
        }
        Post p = get(postId);
        UserAccount u = user(actor);
        PostComment parent = null;
        if (parentId != null && !parentId.isBlank()) {
            parent = commentRepo.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận cha"));
            if (!parent.getPostId().equals(postId)) {
                throw new IllegalArgumentException("Bình luận cha không thuộc bài viết này");
            }
        }
        List<String> mentions = validMentionIds(mentionUserIds);
        PostComment c = commentRepo.save(new PostComment(postId, u.getId(), u.getFullName(), body.trim(),
                parent == null ? null : parentId, String.join(",", mentions), EDIT_WINDOW_MIN));
        audit.record("POST_COMMENTED", "Post", postId, actor, "comment=" + c.getId());
        // người đã được thông báo bởi sự kiện bình luận này (tránh nhắc trùng)
        Set<String> alreadyNotified = notifyNewComment(p, c, u.getId());
        // reply → báo thêm tác giả bình luận cha (trừ tự reply chính mình)
        if (parent != null && !parent.getAuthorId().equals(u.getId())) {
            notify.notify(parent.getAuthorId(), "COMMENT_REPLY",
                    u.getFullName() + " đã trả lời bình luận của bạn", snippet(c.getBody()),
                    "/home?post=" + p.getId());
            alreadyNotified.add(parent.getAuthorId());
        }
        notifyMentions(mentions, u, p.getId(), u.getId(), "COMMENT_MENTION",
                u.getFullName() + " đã nhắc bạn trong một bình luận", snippet(c.getBody()), alreadyNotified);
        return c;
    }

    @Transactional
    public PostComment editComment(String id, String body, String actor) {
        PostComment c = comment(id);
        UserAccount u = user(actor);
        if (!c.getAuthorId().equals(u.getId())) {
            throw new IllegalArgumentException("Chỉ tác giả mới sửa được bình luận");
        }
        if (!c.isEditable()) {
            throw new IllegalArgumentException("Đã quá hạn sửa bình luận");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Bình luận trống");
        }
        c.edit(body.trim());
        return commentRepo.save(c);
    }

    /** Tác giả xoá bình luận của mình khi còn hạn. */
    @Transactional
    public void deleteOwnComment(String id, String actor) {
        PostComment c = comment(id);
        UserAccount u = user(actor);
        if (!c.getAuthorId().equals(u.getId())) {
            throw new IllegalArgumentException("Chỉ tác giả mới xoá được bình luận");
        }
        if (!c.isEditable()) {
            throw new IllegalArgumentException("Đã quá hạn xoá bình luận");
        }
        commentRepo.delete(c);
    }

    @Transactional(readOnly = true)
    public List<PostComment> comments(String postId) {
        return commentRepo.findByPostIdAndHiddenFalseOrderByCreatedAtAsc(postId);
    }

    // ===================== Kiểm duyệt (Story 2.7) =====================

    @Transactional
    public void setPostHidden(String id, boolean hidden, String actor) {
        Post p = get(id);
        p.setHidden(hidden);
        postRepo.save(p);
        audit.record(hidden ? "POST_HIDDEN" : "POST_UNHIDDEN", "Post", id, actor, "");
    }

    @Transactional
    public void deletePost(String id, String actor) {
        Post p = get(id);
        postRepo.delete(p);
        audit.record("POST_DELETED", "Post", id, actor, "");
    }

    @Transactional
    public void setCommentHidden(String id, boolean hidden, String actor) {
        PostComment c = comment(id);
        c.setHidden(hidden);
        commentRepo.save(c);
        audit.record(hidden ? "COMMENT_HIDDEN" : "COMMENT_UNHIDDEN", "PostComment", id, actor, "post=" + c.getPostId());
    }

    @Transactional
    public void deleteCommentByAdmin(String id, String actor) {
        PostComment c = comment(id);
        commentRepo.delete(c);
        audit.record("COMMENT_DELETED", "PostComment", id, actor, "post=" + c.getPostId());
    }

    // ===================== Thông báo (FR-B07) =====================

    /** Bài mới → thông báo người trong phạm vi (phòng đích, hoặc toàn cty), trừ tác giả. */
    private void notifyNewPost(Post p) {
        Set<String> recipients = new HashSet<>();
        if ("ORG_UNIT".equals(p.getScope()) && p.getOrgUnitId() != null) {
            for (Position pos : positionRepo.findByOrgUnitId(p.getOrgUnitId())) {
                if (pos.getCurrentHolderUserId() != null) {
                    recipients.add(pos.getCurrentHolderUserId());
                }
            }
        } else {
            for (UserAccount u : userRepo.findAll()) {
                recipients.add(u.getId());
            }
        }
        recipients.remove(p.getAuthorId());
        String link = "/home?post=" + p.getId();
        for (String uid : recipients) {
            notify.notify(uid, "POST_NEW", "Bài viết mới trên bảng tin", snippet(p.getBody()), link);
        }
    }

    /**
     * Bình luận mới → thông báo tác giả bài + những người đã bình luận (trừ người vừa bình luận).
     * Trả về tập userId đã nhận thông báo (để tránh nhắc trùng @mention cho cùng sự kiện).
     */
    private Set<String> notifyNewComment(Post p, PostComment c, String commenterUserId) {
        Set<String> recipients = new HashSet<>();
        recipients.add(p.getAuthorId());
        for (PostComment other : commentRepo.findByPostIdAndHiddenFalse(p.getId())) {
            recipients.add(other.getAuthorId());
        }
        recipients.remove(commenterUserId);
        String link = "/home?post=" + p.getId();
        for (String uid : recipients) {
            notify.notify(uid, "POST_COMMENT", "Bình luận mới: " + c.getAuthorName(), snippet(c.getBody()), link);
        }
        return recipients;
    }

    // ===================== @mention (gợi ý / lưu / thông báo / resolve) =====================

    /** Gợi ý tối đa 8 người (account đang hoạt động) theo tên/username chứa q. */
    @Transactional(readOnly = true)
    public List<PostDto.MentionView> mentionSuggest(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return userRepo.searchActive(q.trim(), AccountStatus.ACTIVE, Limit.of(8)).stream()
                .map(u -> new PostDto.MentionView(u.getId(), u.getFullName()))
                .collect(Collectors.toList());
    }

    /** Lọc userId hợp lệ (tồn tại, đang hoạt động), khử trùng, giữ thứ tự. */
    private List<String> validMentionIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids) {
            if (id == null || id.isBlank() || !seen.add(id)) {
                continue;
            }
            userRepo.findById(id)
                    .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                    .ifPresent(u -> out.add(u.getId()));
        }
        return out;
    }

    /** Resolve userId → MentionView (tên) từ chuỗi "id1,id2" lưu trên entity. */
    @Transactional(readOnly = true)
    public List<PostDto.MentionView> resolveMentions(String mentionsCsv) {
        if (mentionsCsv == null || mentionsCsv.isBlank()) {
            return List.of();
        }
        List<PostDto.MentionView> out = new ArrayList<>();
        for (String id : mentionsCsv.split(",")) {
            String trimmed = id.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            userRepo.findById(trimmed)
                    .ifPresent(u -> out.add(new PostDto.MentionView(u.getId(), u.getFullName())));
        }
        return out;
    }

    /** Gửi thông báo @mention cho từng người (trừ chính tác giả và những người đã nhận thông báo khác). */
    private void notifyMentions(List<String> mentionIds, UserAccount actor, String postId,
                                String selfId, String type, String title, String body, Set<String> skip) {
        if (mentionIds.isEmpty()) {
            return;
        }
        String link = "/home?post=" + postId;
        for (String uid : mentionIds) {
            if (uid.equals(selfId) || (skip != null && skip.contains(uid))) {
                continue;
            }
            notify.notify(uid, type, title, body, link);
        }
    }

    // ===================== helpers =====================

    private UserAccount user(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + username));
    }

    private PostComment comment(String id) {
        return commentRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));
    }

    /** Danh sách phòng (orgUnitId) mà user đang giữ vị trí — phạm vi xem bài phòng. */
    private List<String> unitsOf(String username) {
        UserAccount u = userRepo.findByUsername(username).orElse(null);
        if (u == null) {
            return List.of();
        }
        Set<String> units = new HashSet<>();
        for (Position pos : positionRepo.findByCurrentHolderUserId(u.getId())) {
            if (pos.getOrgUnitId() != null) {
                units.add(pos.getOrgUnitId());
            }
        }
        return new ArrayList<>(units);
    }

    private String mediaJson(List<String> ids) {
        try {
            List<Map<String, String>> arr = new ArrayList<>();
            for (String id : ids) {
                arr.add(Map.of("id", id));
            }
            return mapper.writeValueAsString(arr);
        } catch (Exception e) {
            return "[]";
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> mediaIdsOf(Post p) {
        try {
            List<Map<String, Object>> arr = mapper.readValue(
                    p.getMediaPaths() == null ? "[]" : p.getMediaPaths(), List.class);
            List<String> ids = new ArrayList<>();
            for (Map<String, Object> m : arr) {
                Object id = m.get("id");
                if (id != null) {
                    ids.add(id.toString());
                }
            }
            return ids;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() > 120 ? t.substring(0, 117) + "…" : t;
    }
}
