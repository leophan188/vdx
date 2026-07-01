package com.bpm.api;

import com.bpm.api.dto.PostDto;
import com.bpm.application.MediaStorageService;
import com.bpm.application.PostService;
import com.bpm.domain.social.Post;
import com.bpm.domain.social.PostComment;
import com.bpm.domain.social.PostMedia;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bảng tin mạng xã hội nội bộ (Epic 2). /api/v1/posts.
 * GET (feed/đọc) = mọi user đăng nhập; tạo bài / ghim / kiểm duyệt = ADMIN (chặn ở SecurityConfig theo method).
 * Upload media = ADMIN (đi kèm tạo bài). Like/bình luận = mọi user.
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final PostService service;
    private final MediaStorageService mediaService;

    public PostController(PostService service, MediaStorageService mediaService) {
        this.service = service;
        this.mediaService = mediaService;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    // ===== Records request =====
    // Bài đăng luôn cho toàn công ty: KHÔNG còn scope/orgUnitId. category ∈ {NEWS,EVENT,ANNOUNCEMENT}.
    public record CreateRequest(String body, List<String> mediaIds, String category, String topic,
                                List<String> mentionUserIds) {
    }

    public record CommentRequest(String body, String parentId, List<String> mentionUserIds) {
    }

    public record PinRequest(Boolean pinned) {
    }

    public record HiddenRequest(Boolean hidden) {
    }

    // ===== Tạo bài (ADMIN) =====
    @PostMapping
    public PostDto.PostView create(@RequestBody CreateRequest req, Authentication auth) {
        Post p = service.create(req.body(), req.mediaIds(), req.category(), req.topic(),
                req.mentionUserIds(), actor(auth));
        return toView(p, actor(auth));
    }

    /** Gợi ý @mention (mọi user đăng nhập). Tối đa 8 người đang hoạt động khớp q. */
    @GetMapping("/mention-suggest")
    public List<PostDto.MentionView> mentionSuggest(@RequestParam(name = "q", required = false) String q) {
        return service.mentionSuggest(q);
    }

    /** Upload một media (ADMIN) trước/khi soạn bài — trả id để gắn vào CreateRequest.mediaIds. */
    @PostMapping("/media")
    public PostDto.MediaView uploadMedia(@RequestParam("file") MultipartFile file, Authentication auth) {
        PostMedia m = mediaService.store(file, actor(auth));
        return PostDto.MediaView.of(m);
    }

    // ===== Feed (mọi user) — lọc nhanh theo phân loại (category), bỏ lọc theo phòng ban =====
    @GetMapping
    public List<PostDto.PostView> feed(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String topic,
                                       Authentication auth) {
        List<Post> posts = service.feed(actor(auth), page, size, category, topic);
        return toViews(posts, actor(auth));
    }

    /** Danh sách quản lý cho ADMIN (gồm cả bài ẩn). */
    @GetMapping("/admin")
    public List<PostDto.PostView> adminList(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(required = false) Integer size,
                                            Authentication auth) {
        return toViews(service.adminList(page, size), actor(auth));
    }

    @GetMapping("/{id}")
    public PostDto.PostView get(@PathVariable String id, Authentication auth) {
        return toView(service.get(id), actor(auth));
    }

    // ===== Ghim (ADMIN) =====
    @PatchMapping("/{id}/pin")
    public PostDto.PostView pin(@PathVariable String id, @RequestBody PinRequest req, Authentication auth) {
        return toView(service.setPinned(id, Boolean.TRUE.equals(req.pinned()), actor(auth)), actor(auth));
    }

    // ===== Like / Unlike (mọi user) =====
    @PostMapping("/{id}/like")
    public Map<String, Object> toggleLike(@PathVariable String id, Authentication auth) {
        long count = service.toggleLike(id, actor(auth));
        return Map.of("likeCount", count, "liked", service.likedBy(id, actor(auth)));
    }

    // ===== Bình luận (mọi user) =====
    @GetMapping("/{id}/comments")
    public List<PostDto.CommentView> comments(@PathVariable String id, Authentication auth) {
        return commentViews(service.comments(id), actor(auth));
    }

    @PostMapping("/{id}/comments")
    public PostDto.CommentView addComment(@PathVariable String id, @RequestBody CommentRequest req, Authentication auth) {
        PostComment c = service.addComment(id, req.body(), req.parentId(), req.mentionUserIds(), actor(auth));
        return PostDto.CommentView.of(c, true, service.resolveMentions(c.getMentions()));
    }

    @PutMapping("/comments/{commentId}")
    public PostDto.CommentView editComment(@PathVariable String commentId, @RequestBody CommentRequest req, Authentication auth) {
        PostComment c = service.editComment(commentId, req.body(), actor(auth));
        return PostDto.CommentView.of(c, true, service.resolveMentions(c.getMentions()));
    }

    /** Tác giả xoá bình luận của mình khi còn hạn. (ADMIN xoá qua endpoint kiểm duyệt riêng.) */
    @DeleteMapping("/comments/{commentId}")
    public Map<String, Object> deleteOwnComment(@PathVariable String commentId, Authentication auth) {
        service.deleteOwnComment(commentId, actor(auth));
        return Map.of("ok", true);
    }

    // ===== Kiểm duyệt (ADMIN) =====
    @PatchMapping("/{id}/hidden")
    public Map<String, Object> hidePost(@PathVariable String id, @RequestBody HiddenRequest req, Authentication auth) {
        service.setPostHidden(id, Boolean.TRUE.equals(req.hidden()), actor(auth));
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> deletePost(@PathVariable String id, Authentication auth) {
        service.deletePost(id, actor(auth));
        return Map.of("ok", true);
    }

    @PatchMapping("/comments/{commentId}/hidden")
    public Map<String, Object> hideComment(@PathVariable String commentId, @RequestBody HiddenRequest req, Authentication auth) {
        service.setCommentHidden(commentId, Boolean.TRUE.equals(req.hidden()), actor(auth));
        return Map.of("ok", true);
    }

    @DeleteMapping("/comments/{commentId}/admin")
    public Map<String, Object> deleteCommentAdmin(@PathVariable String commentId, Authentication auth) {
        service.deleteCommentByAdmin(commentId, actor(auth));
        return Map.of("ok", true);
    }

    // ===== mapping helpers =====

    private List<PostDto.PostView> toViews(List<Post> posts, String actor) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<String> ids = posts.stream().map(Post::getId).toList();
        Map<String, Long> likeCounts = service.likeCounts(ids);
        Map<String, Long> commentCounts = service.commentCounts(ids);
        Set<String> liked = service.likedPostIds(actor, ids);
        return posts.stream().map(p -> PostDto.PostView.of(p,
                likeCounts.getOrDefault(p.getId(), 0L),
                liked.contains(p.getId()),
                commentCounts.getOrDefault(p.getId(), 0L),
                service.media(p).stream().map(PostDto.MediaView::of).toList(),
                List.of(),
                service.resolveMentions(p.getMentions()))).toList();
    }

    private PostDto.PostView toView(Post p, String actor) {
        List<PostDto.MediaView> media = service.media(p).stream().map(PostDto.MediaView::of).toList();
        List<PostDto.CommentView> comments = commentViews(service.comments(p.getId()), actor);
        return PostDto.PostView.of(p, service.likeCount(p.getId()), service.likedBy(p.getId(), actor),
                service.commentCount(p.getId()), media, comments, service.resolveMentions(p.getMentions()));
    }

    private List<PostDto.CommentView> commentViews(List<PostComment> comments, String actor) {
        Set<String> mineIds = mineCommentIds(comments, actor);
        return comments.stream()
                .map(c -> PostDto.CommentView.of(c, mineIds.contains(c.getId()), service.resolveMentions(c.getMentions())))
                .collect(Collectors.toList());
    }

    /** Đánh dấu bình luận của chính người gọi (so theo authorId resolve từ username). */
    private Set<String> mineCommentIds(List<PostComment> comments, String actor) {
        String myUserId = service.myUserId(actor);
        if (myUserId == null) {
            return Set.of();
        }
        return comments.stream().filter(c -> myUserId.equals(c.getAuthorId()))
                .map(PostComment::getId).collect(Collectors.toSet());
    }
}
