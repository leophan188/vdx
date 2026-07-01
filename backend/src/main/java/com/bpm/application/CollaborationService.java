package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.collab.Comment;
import com.bpm.domain.collab.CoordinationRound;
import com.bpm.domain.collab.Opinion;
import com.bpm.infrastructure.CommentRepository;
import com.bpm.infrastructure.CoordinationRoundRepository;
import com.bpm.infrastructure.OpinionRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Tầng cộng tác trên tài liệu/hồ sơ (Story 3.11–3.15): bình luận + @mention, ý kiến phối hợp song song,
 * tiếp thu/không tiếp thu, sửa khi còn hạn, join chống treo.
 */
@Service
public class CollaborationService {

    private static final int EDIT_WINDOW_MIN = 60;  // 3.14: sửa ý kiến/bình luận trong 60 phút
    private static final Pattern MENTION = Pattern.compile("@([a-zA-Z0-9._]{2,50})");

    private final CommentRepository commentRepo;
    private final OpinionRepository opinionRepo;
    private final CoordinationRoundRepository roundRepo;
    private final NotificationService notify;
    private final UserAccountRepository userRepo;
    private final AuditPort audit;

    public CollaborationService(CommentRepository commentRepo, OpinionRepository opinionRepo,
                                CoordinationRoundRepository roundRepo, NotificationService notify,
                                UserAccountRepository userRepo, AuditPort audit) {
        this.commentRepo = commentRepo;
        this.opinionRepo = opinionRepo;
        this.roundRepo = roundRepo;
        this.notify = notify;
        this.userRepo = userRepo;
        this.audit = audit;
    }

    // ===================== Bình luận (3.13/3.14) =====================

    @Transactional
    public Comment addComment(String targetType, String targetId, String body, String actor) {
        Comment c = commentRepo.save(new Comment(targetType, targetId, actor, displayName(actor), body, EDIT_WINDOW_MIN));
        notifyMentions(body, actor, targetType, targetId);
        audit.record("COMMENT_ADDED", targetType, targetId, actor, "comment=" + c.getId());
        return c;
    }

    @Transactional
    public Comment editComment(String id, String body, String actor) {
        Comment c = commentRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));
        if (!c.getAuthor().equals(actor)) {
            throw new IllegalStateException("Chỉ tác giả mới sửa được bình luận");
        }
        if (!c.isEditable()) {
            throw new IllegalStateException("Đã quá hạn sửa bình luận");
        }
        c.edit(body);
        notifyMentions(body, actor, c.getTargetType(), c.getTargetId());
        return commentRepo.save(c);
    }

    @Transactional(readOnly = true)
    public List<Comment> comments(String targetType, String targetId) {
        return commentRepo.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
    }

    // ===================== Ý kiến phối hợp (3.11/3.12/3.14/3.15) =====================

    /** Chủ trì mở đợt phối hợp song song: mời nhiều người cho ý kiến (3.15). */
    @Transactional
    public CoordinationRound requestCoordination(String targetType, String targetId, List<String> participants,
                                                 int deadlineHours, String actor) {
        Set<String> ps = new LinkedHashSet<>(participants);
        ps.removeIf(s -> s == null || s.isBlank());
        if (ps.isEmpty()) {
            throw new IllegalArgumentException("Cần ít nhất một người phối hợp");
        }
        CoordinationRound r = roundRepo.save(new CoordinationRound(targetType, targetId, actor,
                String.join(",", ps), Instant.now().plus(Duration.ofHours(Math.max(1, deadlineHours)))));
        for (String u : ps) {
            userRepo.findByUsername(u).ifPresent(ua -> notify.notify(ua.getId(), "COORDINATION_REQUEST",
                    "Đề nghị cho ý kiến phối hợp", "Bạn được mời cho ý kiến (hạn " + deadlineHours + "h).", link(targetType, targetId)));
        }
        audit.record("COORDINATION_REQUESTED", targetType, targetId, actor, "round=" + r.getId() + ", n=" + ps.size());
        return r;
    }

    /** Gửi ý kiến (có thể thuộc một đợt phối hợp). 3.11. */
    @Transactional
    public Opinion giveOpinion(String targetType, String targetId, String roundId, String stance, String body, String actor) {
        Opinion o = opinionRepo.save(new Opinion(targetType, targetId, roundId, actor, displayName(actor),
                stance == null ? "CO_Y_KIEN" : stance, body, EDIT_WINDOW_MIN));
        audit.record("OPINION_GIVEN", targetType, targetId, actor, "stance=" + o.getStance());
        return o;
    }

    /** Sửa ý kiến của chính mình khi còn hạn và chưa bị tiếp thu/không (3.14). */
    @Transactional
    public Opinion editOpinion(String id, String stance, String body, String actor) {
        Opinion o = opinionRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ý kiến"));
        if (!o.getAuthor().equals(actor)) {
            throw new IllegalStateException("Chỉ tác giả mới sửa được ý kiến");
        }
        if (!o.isEditable()) {
            throw new IllegalStateException("Quá hạn sửa hoặc ý kiến đã được xử lý");
        }
        o.edit(stance, body);
        return opinionRepo.save(o);
    }

    /** Bảng tổng hợp ý kiến của tài liệu/hồ sơ (3.11). */
    @Transactional(readOnly = true)
    public List<Opinion> opinions(String targetType, String targetId) {
        return opinionRepo.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId);
    }

    /** Chủ trì tiếp thu / không tiếp thu một ý kiến (3.12). */
    @Transactional
    public Opinion resolveOpinion(String id, String resolution, String note, String actor) {
        Opinion o = opinionRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ý kiến"));
        if (!"TIEP_THU".equals(resolution) && !"KHONG_TIEP_THU".equals(resolution)) {
            throw new IllegalArgumentException("resolution phải là TIEP_THU hoặc KHONG_TIEP_THU");
        }
        o.resolve(resolution, note);
        notify.notify(userIdOrNull(o.getAuthor()), "OPINION_RESOLVED",
                ("TIEP_THU".equals(resolution) ? "Ý kiến được tiếp thu" : "Ý kiến không được tiếp thu"),
                note == null ? "" : note, link(o.getTargetType(), o.getTargetId()));
        audit.record("OPINION_RESOLVED", o.getTargetType(), o.getTargetId(), actor, "id=" + id + ", " + resolution);
        return opinionRepo.save(o);
    }

    /** Trạng thái đợt phối hợp: ai đã/chưa phản hồi, join khi đủ hoặc quá hạn (chống treo — 3.15). */
    @Transactional(readOnly = true)
    public Map<String, Object> roundStatus(String roundId) {
        CoordinationRound r = roundRepo.findById(roundId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đợt phối hợp"));
        List<String> participants = Arrays.stream(r.getParticipants().split(",")).filter(s -> !s.isBlank()).toList();
        Set<String> responded = opinionRepo.findByRoundId(roundId).stream().map(Opinion::getAuthor).collect(Collectors.toSet());
        List<String> pending = participants.stream().filter(p -> !responded.contains(p)).toList();
        boolean overdue = r.isOverdue();
        boolean complete = pending.isEmpty() || overdue; // join: đủ người HOẶC quá hạn (không treo chờ người vắng)
        return Map.of(
                "roundId", roundId, "status", r.getStatus(),
                "participants", participants, "respondedCount", responded.size(),
                "pending", pending, "overdue", overdue, "complete", complete,
                "deadline", r.getDeadline().toString());
    }

    @Transactional
    public void closeRound(String roundId, String actor) {
        CoordinationRound r = roundRepo.findById(roundId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đợt phối hợp"));
        r.close();
        roundRepo.save(r);
        audit.record("COORDINATION_CLOSED", r.getTargetType(), r.getTargetId(), actor, "round=" + roundId);
    }

    @Transactional(readOnly = true)
    public List<CoordinationRound> rounds(String targetType, String targetId) {
        return roundRepo.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(targetType, targetId);
    }

    // ===================== helpers =====================

    private void notifyMentions(String body, String actor, String targetType, String targetId) {
        Matcher m = MENTION.matcher(body == null ? "" : body);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find()) {
            String uname = m.group(1);
            if (uname.equals(actor) || !seen.add(uname)) {
                continue;
            }
            userRepo.findByUsername(uname).ifPresent(ua -> notify.notify(ua.getId(), "MENTION",
                    displayName(actor) + " đã nhắc đến bạn", snippet(body), link(targetType, targetId)));
        }
    }

    private String displayName(String username) {
        return userRepo.findByUsername(username)
                .map(u -> u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername())
                .orElse(username);
    }

    private String userIdOrNull(String username) {
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }

    private static String link(String targetType, String targetId) {
        return "DOCUMENT".equals(targetType) ? "/documents/" + targetId : "/tracking";
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 120 ? s.substring(0, 117) + "…" : s;
    }
}
