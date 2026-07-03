package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectDiary;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectRoleInProject;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectDiaryRepository;
import com.bpm.infrastructure.ProjectMemberRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nhật ký dự án — ghi TAY các buổi làm việc với KHÁCH HÀNG (khác tab "Log" tự động).
 * Mọi thành viên dự án được GHI; SỬA/XOÁ chỉ bởi người tạo hoặc admin/PM/owner (kiểm ở đây).
 * Conventions: validate ném {@link IllegalArgumentException} (→ 400 tầng API); mọi mutation ghi {@link AuditPort}.
 */
@Service
public class ProjectDiaryService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    private final ProjectDiaryRepository diaryRepo;
    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final UserAccountRepository userRepo;
    private final EmployeeRepository employeeRepo;
    private final AuditPort auditPort;

    public ProjectDiaryService(ProjectDiaryRepository diaryRepo, ProjectRepository projectRepo,
                               ProjectMemberRepository memberRepo, UserAccountRepository userRepo,
                               EmployeeRepository employeeRepo, AuditPort auditPort) {
        this.diaryRepo = diaryRepo;
        this.projectRepo = projectRepo;
        this.memberRepo = memberRepo;
        this.userRepo = userRepo;
        this.employeeRepo = employeeRepo;
        this.auditPort = auditPort;
    }

    @Transactional(readOnly = true)
    public List<ProjectDto.DiaryEntry> list(String projectId, String actor, boolean isAdmin) {
        requireProject(projectId);
        String actorUserId = userIdOf(actor);
        boolean canManage = canManage(projectId, actorUserId, isAdmin);
        Map<String, String> nameCache = new HashMap<>();
        List<ProjectDto.DiaryEntry> out = new ArrayList<>();
        for (ProjectDiary d : diaryRepo.findByProjectIdOrderByWorkDateDescCreatedAtDesc(projectId)) {
            out.add(toDto(d, actorUserId, canManage, nameCache));
        }
        return out;
    }

    @Transactional
    public ProjectDto.DiaryEntry create(String projectId, ProjectDto.DiaryRequest req, String actor) {
        requireProject(projectId);
        UserAccount u = user(actor);
        ProjectDiary d = new ProjectDiary(projectId, parseDate(req.workDate()),
                blankToNull(req.category()), joinIds(req.teamUserIds()),
                blankToNull(req.clientContacts()), blankToNull(req.content()), blankToNull(req.conclusion()),
                u.getId(), displayNameOf(u.getId()));
        ProjectDiary saved = diaryRepo.save(d);
        auditPort.record("PROJECT_DIARY_CREATED", "ProjectDiary", saved.getId(), actor,
                "projectId=" + projectId + ", category=" + saved.getCategory());
        return toDto(saved, u.getId(), true, new HashMap<>());
    }

    @Transactional
    public ProjectDto.DiaryEntry update(String projectId, String entryId, ProjectDto.DiaryRequest req,
                                        String actor, boolean isAdmin) {
        requireProject(projectId);
        ProjectDiary d = requireEntry(projectId, entryId);
        String actorUserId = userIdOf(actor);
        requireCanEdit(projectId, d, actorUserId, isAdmin);
        d.setWorkDate(parseDate(req.workDate()));
        d.setCategory(blankToNull(req.category()));
        d.setTeamUserIds(joinIds(req.teamUserIds()));
        d.setClientContacts(blankToNull(req.clientContacts()));
        d.setContent(blankToNull(req.content()));
        d.setConclusion(blankToNull(req.conclusion()));
        d.touch();
        ProjectDiary saved = diaryRepo.save(d);
        auditPort.record("PROJECT_DIARY_UPDATED", "ProjectDiary", saved.getId(), actor,
                "projectId=" + projectId);
        return toDto(saved, actorUserId, true, new HashMap<>());
    }

    @Transactional
    public void delete(String projectId, String entryId, String actor, boolean isAdmin) {
        requireProject(projectId);
        ProjectDiary d = requireEntry(projectId, entryId);
        String actorUserId = userIdOf(actor);
        requireCanEdit(projectId, d, actorUserId, isAdmin);
        diaryRepo.delete(d);
        auditPort.record("PROJECT_DIARY_DELETED", "ProjectDiary", entryId, actor, "projectId=" + projectId);
    }

    // ===== helpers =====

    private ProjectDto.DiaryEntry toDto(ProjectDiary d, String actorUserId, boolean canManage,
                                        Map<String, String> nameCache) {
        List<String> ids = splitIds(d.getTeamUserIds());
        List<String> names = new ArrayList<>();
        for (String uid : ids) {
            names.add(nameCache.computeIfAbsent(uid, this::displayNameOf));
        }
        boolean canEdit = canManage
                || (actorUserId != null && actorUserId.equals(d.getCreatedBy()));
        return new ProjectDto.DiaryEntry(d.getId(),
                d.getWorkDate() == null ? null : d.getWorkDate().format(DMY),
                d.getCategory(), ids, names, d.getClientContacts(), d.getContent(), d.getConclusion(),
                d.getCreatedBy(), d.getCreatedByName(),
                d.getCreatedAt() == null ? null : d.getCreatedAt().toString(), canEdit);
    }

    /** Chỉ người tạo hoặc admin/PM/owner được sửa/xoá. */
    private void requireCanEdit(String projectId, ProjectDiary d, String actorUserId, boolean isAdmin) {
        if (actorUserId != null && actorUserId.equals(d.getCreatedBy())) {
            return;
        }
        if (canManage(projectId, actorUserId, isAdmin)) {
            return;
        }
        throw new IllegalArgumentException("Chỉ người tạo hoặc quản lý dự án mới sửa/xoá được nhật ký này");
    }

    /** Có quyền quản lý dự án (admin / owner / PM). */
    private boolean canManage(String projectId, String actorUserId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }
        if (actorUserId == null) {
            return false;
        }
        Project p = projectRepo.findById(projectId).orElse(null);
        if (p != null && actorUserId.equals(p.getOwnerUserId())) {
            return true;
        }
        ProjectMember m = memberRepo.findByProjectIdAndUserId(projectId, actorUserId).orElse(null);
        return m != null && m.getRoleInProject() == ProjectRoleInProject.PM;
    }

    private void requireProject(String projectId) {
        if (!projectRepo.existsById(projectId)) {
            throw new IllegalArgumentException("Không tìm thấy dự án");
        }
    }

    private ProjectDiary requireEntry(String projectId, String entryId) {
        ProjectDiary d = diaryRepo.findById(entryId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bản ghi nhật ký"));
        if (!d.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("Bản ghi nhật ký không thuộc dự án này");
        }
        return d;
    }

    /** Tên hiển thị của một userId (ưu tiên hồ sơ nhân sự, rồi tài khoản). */
    private String displayNameOf(String userId) {
        if (userId == null) {
            return null;
        }
        UserAccount acc = userRepo.findById(userId).orElse(null);
        Employee emp = employeeRepo.findByUserAccountId(userId).orElse(null);
        return ProjectService.personName(emp, acc, userId);
    }

    private UserAccount user(String username) {
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản: " + username));
    }

    private String userIdOf(String username) {
        if (username == null) {
            return null;
        }
        return userRepo.findByUsername(username).map(UserAccount::getId).orElse(null);
    }

    /** Chấp nhận dd/MM/yyyy HOẶC yyyy-MM-dd; trống → null. */
    private static LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        String s = v.trim();
        try {
            return LocalDate.parse(s, DMY);
        } catch (Exception ignored) {
            // thử ISO
        }
        try {
            return LocalDate.parse(s, ISO);
        } catch (Exception e) {
            throw new IllegalArgumentException("Ngày làm việc sai định dạng dd/MM/yyyy hoặc yyyy-MM-dd (\"" + v + "\")");
        }
    }

    /** userId list → chuỗi nối bằng dấu phẩy (bỏ trống/trùng, giữ thứ tự). Rỗng → null. */
    private static String joinIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                seen.add(id.trim());
            }
        }
        for (String id : seen) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static List<String> splitIds(String joined) {
        List<String> out = new ArrayList<>();
        if (joined == null || joined.isBlank()) {
            return out;
        }
        for (String part : joined.split(",")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        return out;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
