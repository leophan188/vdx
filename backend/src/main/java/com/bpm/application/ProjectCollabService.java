package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskAttachment;
import com.bpm.domain.project.TaskComment;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.TaskActivity;
import com.bpm.domain.social.PostMedia;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
import com.bpm.infrastructure.TaskActivityRepository;
import com.bpm.infrastructure.TaskAttachmentRepository;
import com.bpm.infrastructure.TaskCommentRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bình luận (kiểu Jira) + ảnh đính kèm cho công việc dự án (req 4).
 * Tái dùng {@link MediaStorageService} (Epic 2) để lưu/đọc file ảnh trên đĩa (allowlist + magic bytes + giới hạn 10MB).
 * Tác giả lấy từ Authentication (username → UserAccount.fullName) như {@link PostService}. Mọi mutation ghi audit.
 */
@Service
public class ProjectCollabService {

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp");

    private final ProjectRepository projectRepo;
    private final ProjectTaskRepository taskRepo;
    private final TaskCommentRepository commentRepo;
    private final TaskAttachmentRepository attachmentRepo;
    private final UserAccountRepository userRepo;
    private final TaskActivityRepository activityRepo;
    private final MediaStorageService mediaStorage;
    private final NotificationService notificationService;
    private final AuditPort audit;

    public ProjectCollabService(ProjectRepository projectRepo, ProjectTaskRepository taskRepo,
                                TaskCommentRepository commentRepo, TaskAttachmentRepository attachmentRepo,
                                UserAccountRepository userRepo, TaskActivityRepository activityRepo,
                                MediaStorageService mediaStorage, NotificationService notificationService,
                                AuditPort audit) {
        this.projectRepo = projectRepo;
        this.taskRepo = taskRepo;
        this.commentRepo = commentRepo;
        this.attachmentRepo = attachmentRepo;
        this.userRepo = userRepo;
        this.activityRepo = activityRepo;
        this.mediaStorage = mediaStorage;
        this.notificationService = notificationService;
        this.audit = audit;
    }

    // ===================== Bình luận =====================

    @Transactional(readOnly = true)
    public List<ProjectDto.CommentResponse> listComments(String projectId, String taskId, String actor) {
        requireTask(projectId, taskId);
        String myId = userId(actor);
        List<ProjectDto.CommentResponse> out = new ArrayList<>();
        for (TaskComment c : commentRepo.findByTaskIdOrderByCreatedAtAsc(taskId)) {
            out.add(ProjectDto.CommentResponse.of(c, c.getAuthorId().equals(myId)));
        }
        return out;
    }

    @Transactional
    public ProjectDto.CommentResponse addComment(String projectId, String taskId, String body, String actor) {
        ProjectTask task = requireTask(projectId, taskId);
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Bình luận trống");
        }
        UserAccount u = user(actor);
        // Người đã bình luận TRƯỚC đó (để báo) — lấy trước khi lưu bình luận mới.
        Set<String> priorCommenters = new LinkedHashSet<>();
        for (TaskComment prev : commentRepo.findByTaskIdOrderByCreatedAtAsc(taskId)) {
            priorCommenters.add(prev.getAuthorId());
        }
        TaskComment c = commentRepo.save(new TaskComment(taskId, u.getId(), ProjectService.displayName(u), body.trim()));
        audit.record("PROJECT_TASK_COMMENTED", "ProjectTask", taskId, actor, "comment=" + c.getId());
        // Activity COMMENT.
        activityRepo.save(new TaskActivity(taskId, projectId, ProjectService.displayName(u),
                TaskActivity.COMMENT, snippet(body.trim())));
        // Thông báo: assignee của task + những người đã bình luận trước (trừ người vừa viết).
        notifyComment(projectId, task, u.getId(), priorCommenters, body.trim());
        return ProjectDto.CommentResponse.of(c, true);
    }

    /** Báo cho assignee + người đã bình luận trước đó (trừ chính người vừa viết); bọc try/catch. */
    private void notifyComment(String projectId, ProjectTask task, String authorId,
                               Set<String> priorCommenters, String body) {
        Set<String> recipients = new LinkedHashSet<>();
        if (task.getAssigneeUserId() != null) {
            recipients.add(task.getAssigneeUserId());
        }
        recipients.addAll(priorCommenters);
        recipients.remove(authorId); // không tự báo cho người vừa viết
        if (recipients.isEmpty()) {
            return;
        }
        Project p = projectRepo.findById(projectId).orElse(null);
        String projectName = p != null ? p.getName() : "";
        String link = "/projects/" + projectId;
        for (String uid : recipients) {
            try {
                notificationService.notify(uid, "PROJECT_TASK_COMMENT",
                        "Bình luận mới trên công việc",
                        task.getTitle() + " (" + projectName + "): " + snippet(body), link);
            } catch (Exception ignored) {
                // lỗi thông báo không chặn nghiệp vụ
            }
        }
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 120 ? s : s.substring(0, 120) + "…";
    }

    @Transactional
    public ProjectDto.CommentResponse editComment(String projectId, String taskId, String commentId,
                                                  String body, String actor) {
        requireTask(projectId, taskId);
        TaskComment c = requireComment(taskId, commentId);
        UserAccount u = user(actor);
        if (!c.getAuthorId().equals(u.getId())) {
            throw new IllegalArgumentException("Chỉ tác giả mới sửa được bình luận");
        }
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Bình luận trống");
        }
        c.edit(body.trim());
        TaskComment saved = commentRepo.save(c);
        audit.record("PROJECT_TASK_COMMENT_EDITED", "ProjectTask", taskId, actor, "comment=" + commentId);
        return ProjectDto.CommentResponse.of(saved, true);
    }

    @Transactional
    public void deleteComment(String projectId, String taskId, String commentId, String actor, boolean isAdmin) {
        requireTask(projectId, taskId);
        TaskComment c = requireComment(taskId, commentId);
        UserAccount u = user(actor);
        if (!isAdmin && !c.getAuthorId().equals(u.getId())) {
            throw new IllegalArgumentException("Chỉ tác giả hoặc quản trị viên mới xoá được bình luận");
        }
        commentRepo.delete(c);
        audit.record("PROJECT_TASK_COMMENT_DELETED", "ProjectTask", taskId, actor, "comment=" + commentId);
    }

    // ===================== Ảnh đính kèm =====================

    @Transactional(readOnly = true)
    public List<ProjectDto.AttachmentResponse> listAttachments(String projectId, String taskId) {
        requireTask(projectId, taskId);
        List<ProjectDto.AttachmentResponse> out = new ArrayList<>();
        for (TaskAttachment a : attachmentRepo.findByTaskIdOrderByUploadedAtDesc(taskId)) {
            out.add(ProjectDto.AttachmentResponse.of(a, contentUrl(projectId, taskId, a.getId())));
        }
        return out;
    }

    @Transactional
    public ProjectDto.AttachmentResponse uploadAttachment(String projectId, String taskId,
                                                          MultipartFile file, String actor) {
        requireTask(projectId, taskId);
        String ct = file == null || file.getContentType() == null
                ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(ct)) {
            throw new IllegalArgumentException("Chỉ cho phép đính kèm ẢNH (JPEG/PNG/GIF/WEBP)");
        }
        // Tái dùng MediaStorageService: validate loại/dung lượng (≤10MB) + magic bytes + lưu lên đĩa.
        PostMedia media = mediaStorage.store(file, actor);
        TaskAttachment a = attachmentRepo.save(new TaskAttachment(taskId, media.getOriginalName(),
                media.getContentType(), media.getSizeBytes(), media.getId(), actor));
        audit.record("PROJECT_TASK_ATTACHMENT_ADDED", "ProjectTask", taskId, actor,
                "attachment=" + a.getId() + ", media=" + media.getId());
        activityRepo.save(new TaskActivity(taskId, projectId, actorName(actor),
                TaskActivity.ATTACH, "Đính kèm " + a.getFileName()));
        return ProjectDto.AttachmentResponse.of(a, contentUrl(projectId, taskId, a.getId()));
    }

    /** Mô tả file media để controller stream bytes (đường dẫn do MediaStorageService quản lý). */
    @Transactional(readOnly = true)
    public PostMedia attachmentMedia(String projectId, String taskId, String attId) {
        requireTask(projectId, taskId);
        TaskAttachment a = requireAttachment(taskId, attId);
        return mediaStorage.get(a.getStorageKey());
    }

    @Transactional
    public void deleteAttachment(String projectId, String taskId, String attId, String actor) {
        requireTask(projectId, taskId);
        TaskAttachment a = requireAttachment(taskId, attId);
        attachmentRepo.delete(a);
        audit.record("PROJECT_TASK_ATTACHMENT_DELETED", "ProjectTask", taskId, actor, "attachment=" + attId);
    }

    // ===================== Dọn dẹp khi xoá task =====================

    /** Xoá toàn bộ bình luận + đính kèm của một task (gọi khi xoá task). */
    @Transactional
    public void purgeForTask(String taskId) {
        commentRepo.deleteByTaskId(taskId);
        attachmentRepo.deleteByTaskId(taskId);
        activityRepo.deleteByTaskId(taskId);
    }

    private String actorName(String username) {
        if (username == null) {
            return "anonymous";
        }
        return userRepo.findByUsername(username).map(ProjectService::displayName).orElse(username);
    }

    // ===================== helpers =====================

    private String contentUrl(String projectId, String taskId, String attId) {
        return "/api/v1/projects/" + projectId + "/tasks/" + taskId + "/attachments/" + attId + "/content";
    }

    private ProjectTask requireTask(String projectId, String taskId) {
        if (!projectRepo.existsById(projectId)) {
            throw new IllegalArgumentException("Không tìm thấy dự án");
        }
        ProjectTask t = taskRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy công việc"));
        if (!t.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Công việc không thuộc dự án này");
        }
        return t;
    }

    private TaskComment requireComment(String taskId, String commentId) {
        TaskComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận"));
        if (!c.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Bình luận không thuộc công việc này");
        }
        return c;
    }

    private TaskAttachment requireAttachment(String taskId, String attId) {
        TaskAttachment a = attachmentRepo.findById(attId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ảnh đính kèm"));
        if (!a.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Ảnh đính kèm không thuộc công việc này");
        }
        return a;
    }

    private UserAccount user(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + username));
    }

    private String userId(String username) {
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }
}
