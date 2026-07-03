package com.bpm.api;

import com.bpm.api.dto.ProjectDto;
import com.bpm.application.ProjectCollabService;
import com.bpm.application.ProjectReportService;
import com.bpm.application.ProjectService;
import com.bpm.application.ProjectTaskService;
import com.bpm.application.MediaStorageService;
import com.bpm.domain.social.PostMedia;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;

/**
 * Module Quản lý dự án (mini-Jira). Prefix {@code /api/v1/projects}.
 * Validate ném IllegalArgumentException → 400 (theo handler chung của app, như EmployeeController).
 * Security: thêm matcher "/api/v1/projects/** = authenticated" (SecurityConfig tự thêm).
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectTaskService taskService;
    private final ProjectCollabService collabService;
    private final ProjectReportService reportService;
    private final MediaStorageService mediaStorage;

    public ProjectController(ProjectService projectService, ProjectTaskService taskService,
                             ProjectCollabService collabService, ProjectReportService reportService,
                             MediaStorageService mediaStorage) {
        this.projectService = projectService;
        this.taskService = taskService;
        this.collabService = collabService;
        this.reportService = reportService;
        this.mediaStorage = mediaStorage;
    }

    private static String actor(Authentication a) {
        return a != null ? a.getName() : "anonymous";
    }

    private static boolean isAdmin(Authentication a) {
        if (a == null) {
            return false;
        }
        for (GrantedAuthority ga : a.getAuthorities()) {
            if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    // ===== Project =====

    @GetMapping
    public List<ProjectDto.ProjectResponse> list(Authentication auth) {
        return projectService.list(actor(auth), isAdmin(auth));
    }

    /** Việc của tôi xuyên dự án (màn cá nhân). */
    @GetMapping("/my-tasks")
    public List<ProjectDto.MyTaskResponse> myTasks(Authentication auth) {
        return projectService.myTasks(actor(auth));
    }

    @PostMapping
    public ProjectDto.ProjectResponse create(@RequestBody ProjectDto.ProjectRequest req, Authentication auth) {
        return projectService.create(req, actor(auth));
    }

    @GetMapping("/{id}")
    public ProjectDto.ProjectResponse get(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return projectService.detail(id);
    }

    @PutMapping("/{id}")
    public ProjectDto.ProjectResponse update(@PathVariable String id,
                                             @RequestBody ProjectDto.ProjectRequest req, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        return projectService.update(id, req, actor(auth));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        projectService.delete(id, actor(auth));
    }

    // ===== Members =====

    @GetMapping("/{id}/members")
    public List<ProjectDto.MemberResponse> members(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return projectService.listMembers(id);
    }

    @PostMapping("/{id}/members")
    public ProjectDto.MemberResponse addMember(@PathVariable String id,
                                               @RequestBody ProjectDto.AddMemberRequest req, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        return projectService.addMember(id, req.userId(), req.role(), req.startDate(), req.endDate(),
                req.effortPct(), actor(auth));
    }

    @PutMapping("/{id}/members/{memberId}")
    public ProjectDto.MemberResponse updateMember(@PathVariable String id, @PathVariable String memberId,
                                                  @RequestBody ProjectDto.UpdateMemberRequest req, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        return projectService.updateMember(id, memberId, req.role(), req.startDate(), req.endDate(),
                req.effortPct(), actor(auth));
    }

    @DeleteMapping("/{id}/members/{memberId}")
    public void removeMember(@PathVariable String id, @PathVariable String memberId, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        projectService.removeMember(id, memberId, actor(auth));
    }

    // ===== Tasks (phẳng — FE tự dựng cây) =====

    @GetMapping("/{id}/tasks")
    public List<ProjectDto.TaskResponse> tasks(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return taskService.list(id);
    }

    @PostMapping("/{id}/tasks")
    public ProjectDto.TaskResponse createTask(@PathVariable String id,
                                              @RequestBody ProjectDto.TaskRequest req, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        return taskService.create(id, req, actor(auth));
    }

    @PutMapping("/{id}/tasks/{taskId}")
    public ProjectDto.TaskResponse updateTask(@PathVariable String id, @PathVariable String taskId,
                                              @RequestBody ProjectDto.TaskRequest req, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        return taskService.update(id, taskId, req, actor(auth));
    }

    @PatchMapping("/{id}/tasks/{taskId}/status")
    public ProjectDto.TaskResponse updateTaskStatus(@PathVariable String id, @PathVariable String taskId,
                                                    @RequestBody ProjectDto.StatusRequest req, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return taskService.updateStatus(id, taskId, req.status(), actor(auth));
    }

    @PatchMapping("/{id}/tasks/{taskId}/assignee")
    public ProjectDto.TaskResponse assignTask(@PathVariable String id, @PathVariable String taskId,
                                              @RequestBody ProjectDto.AssigneeRequest req, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return taskService.assign(id, taskId, req.assigneeUserId(), actor(auth));
    }

    @PostMapping("/{id}/tasks/reorder")
    public void reorderTasks(@PathVariable String id,
                             @RequestBody ProjectDto.ReorderRequest req, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        taskService.reorder(id, req, actor(auth));
    }

    @DeleteMapping("/{id}/tasks/{taskId}")
    public void deleteTask(@PathVariable String id, @PathVariable String taskId, Authentication auth) {
        projectService.requireCanManage(id, actor(auth), isAdmin(auth));
        taskService.delete(id, taskId, actor(auth));
    }

    /** Lịch sử / hoạt động của một công việc (mới → cũ). */
    @GetMapping("/{id}/tasks/{taskId}/activity")
    public List<ProjectDto.TaskActivityResponse> taskActivity(@PathVariable String id, @PathVariable String taskId,
                                                              Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return taskService.listActivity(id, taskId);
    }

    /** Nhật ký hoạt động toàn dự án (mới → cũ): tạo/đổi trạng thái/gán/sửa… của mọi task. */
    @GetMapping("/{id}/activity")
    public List<ProjectDto.ProjectActivityItem> projectActivity(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return taskService.listProjectActivity(id);
    }

    /** Log work: cộng thêm giờ thực tế vào công việc. */
    @PostMapping("/{id}/tasks/{taskId}/log-work")
    public ProjectDto.TaskResponse logWork(@PathVariable String id, @PathVariable String taskId,
                                           @RequestBody ProjectDto.LogWorkRequest req, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return taskService.logWork(id, taskId, req.hours(), actor(auth));
    }

    // ===== Bình luận task (kiểu Jira) =====

    @GetMapping("/{id}/tasks/{taskId}/comments")
    public List<ProjectDto.CommentResponse> listComments(@PathVariable String id, @PathVariable String taskId,
                                                         Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return collabService.listComments(id, taskId, actor(auth));
    }

    @PostMapping("/{id}/tasks/{taskId}/comments")
    public ProjectDto.CommentResponse addComment(@PathVariable String id, @PathVariable String taskId,
                                                 @RequestBody ProjectDto.CommentRequest req, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return collabService.addComment(id, taskId, req.body(), actor(auth));
    }

    @PutMapping("/{id}/tasks/{taskId}/comments/{commentId}")
    public ProjectDto.CommentResponse editComment(@PathVariable String id, @PathVariable String taskId,
                                                  @PathVariable String commentId,
                                                  @RequestBody ProjectDto.CommentRequest req, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return collabService.editComment(id, taskId, commentId, req.body(), actor(auth));
    }

    @DeleteMapping("/{id}/tasks/{taskId}/comments/{commentId}")
    public void deleteComment(@PathVariable String id, @PathVariable String taskId,
                              @PathVariable String commentId, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        collabService.deleteComment(id, taskId, commentId, actor(auth), isAdmin(auth));
    }

    // ===== Ảnh đính kèm task (kiểu Jira) =====

    @GetMapping("/{id}/tasks/{taskId}/attachments")
    public List<ProjectDto.AttachmentResponse> listAttachments(@PathVariable String id, @PathVariable String taskId,
                                                               Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return collabService.listAttachments(id, taskId);
    }

    @PostMapping("/{id}/tasks/{taskId}/attachments")
    public ProjectDto.AttachmentResponse uploadAttachment(@PathVariable String id, @PathVariable String taskId,
                                                          @RequestParam("file") MultipartFile file,
                                                          Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return collabService.uploadAttachment(id, taskId, file, actor(auth));
    }

    @GetMapping("/{id}/tasks/{taskId}/attachments/{attId}/content")
    public ResponseEntity<Resource> attachmentContent(@PathVariable String id, @PathVariable String taskId,
                                                      @PathVariable String attId, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        PostMedia m = collabService.attachmentMedia(id, taskId, attId);
        Path path = mediaStorage.pathOf(m);
        Resource body = new PathResource(path);
        if (!body.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + m.getId() + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                .contentType(MediaType.parseMediaType(m.getContentType()))
                .body(body);
    }

    @DeleteMapping("/{id}/tasks/{taskId}/attachments/{attId}")
    public void deleteAttachment(@PathVariable String id, @PathVariable String taskId,
                                 @PathVariable String attId, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        collabService.deleteAttachment(id, taskId, attId, actor(auth));
    }

    // ===== Report =====

    @GetMapping("/{id}/report")
    public ProjectDto.ReportResponse report(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return projectService.report(id);
    }

    @GetMapping("/{id}/report/daily")
    public ProjectDto.PeriodReportResponse reportDaily(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return reportService.daily(id);
    }

    @GetMapping("/{id}/report/weekly")
    public ProjectDto.PeriodReportResponse reportWeekly(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return reportService.weekly(id);
    }

    /** Biểu đồ burndown (giờ ước lượng còn lại theo thời gian: ideal vs actual). */
    @GetMapping("/{id}/burndown")
    public ProjectDto.BurndownResponse burndown(@PathVariable String id, Authentication auth) {
        projectService.requireMember(id, actor(auth), isAdmin(auth));
        return reportService.burndown(id);
    }

    // ===== People (chọn thành viên / assignee) =====

    @GetMapping("/people")
    public List<ProjectDto.PersonResponse> people() {
        return projectService.people();
    }
}
