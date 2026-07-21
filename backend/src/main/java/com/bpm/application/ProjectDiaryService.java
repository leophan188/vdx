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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    /** Chỉ dùng cho cột next_actions (JSON) — nội bộ service, không phụ thuộc cấu hình web. */
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /** Một bản ghi nhật ký (dùng cho xuất biên bản họp). */
    @Transactional(readOnly = true)
    public ProjectDto.DiaryEntry get(String projectId, String entryId, String actor, boolean isAdmin) {
        requireProject(projectId);
        ProjectDiary d = requireEntry(projectId, entryId);
        String actorUserId = userIdOf(actor);
        return toDto(d, actorUserId, canManage(projectId, actorUserId, isAdmin), new HashMap<>());
    }

    @Transactional
    public ProjectDto.DiaryEntry create(String projectId, ProjectDto.DiaryRequest req, String actor) {
        requireProject(projectId);
        UserAccount u = user(actor);
        ProjectDiary d = new ProjectDiary(projectId, parseDate(req.workDate()),
                blankToNull(req.category()), joinIds(req.teamUserIds()),
                blankToNull(req.clientContacts()), blankToNull(req.content()), blankToNull(req.conclusion()),
                u.getId(), displayNameOf(u.getId()));
        applyMeetingFields(d, req);
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
        applyMeetingFields(d, req);
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
        List<ProjectDto.DiaryPerson> team = new ArrayList<>();
        for (String uid : ids) {
            String name = nameCache.computeIfAbsent(uid, this::displayNameOf);
            names.add(name);
            team.add(new ProjectDto.DiaryPerson(name, roleOf(d.getProjectId(), uid)));
        }
        boolean canEdit = canManage
                || (actorUserId != null && actorUserId.equals(d.getCreatedBy()));
        return new ProjectDto.DiaryEntry(d.getId(),
                d.getWorkDate() == null ? null : d.getWorkDate().format(DMY),
                d.getCategory(), ids, names, team,
                d.getClientContacts(), d.getContent(), d.getConclusion(),
                d.getLocation(), d.getStartTime(), d.getEndTime(), readActions(d.getNextActions()),
                d.getCreatedBy(), d.getCreatedByName(),
                d.getCreatedAt() == null ? null : d.getCreatedAt().toString(), canEdit);
    }

    /** Ghi các trường phục vụ BIÊN BẢN HỌP (địa điểm, giờ, next action) — dùng chung cho tạo & sửa. */
    private void applyMeetingFields(ProjectDiary d, ProjectDto.DiaryRequest req) {
        d.setLocation(blankToNull(req.location()));
        d.setStartTime(parseTime(req.startTime()));
        d.setEndTime(parseTime(req.endTime()));
        d.setNextActions(writeActions(req.nextActions()));
    }

    /** "HH:mm" hợp lệ → giữ nguyên; trống/sai định dạng → null (giờ họp không bắt buộc). */
    private static String parseTime(String v) {
        String s = blankToNull(v);
        if (s == null) {
            return null;
        }
        return s.matches("^([01]\\d|2[0-3]):[0-5]\\d$") ? s : null;
    }

    /** Danh sách next action → JSON. Bỏ dòng trống nội dung; chuẩn hoá hạn & trạng thái. Rỗng → null. */
    private String writeActions(List<ProjectDto.DiaryAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        List<ProjectDto.DiaryAction> clean = new ArrayList<>();
        for (ProjectDto.DiaryAction a : actions) {
            if (a == null || blankToNull(a.content()) == null) {
                continue; // dòng trống — người dùng thêm rồi bỏ
            }
            LocalDate due = parseDateSafe(a.dueDate());
            clean.add(new ProjectDto.DiaryAction(blankToNull(a.content()), blankToNull(a.owner()),
                    due == null ? null : due.format(DMY), normalizeStatus(a.status())));
        }
        if (clean.isEmpty()) {
            return null;
        }
        try {
            return JSON.writeValueAsString(clean);
        } catch (Exception e) {
            throw new IllegalArgumentException("Không lưu được danh sách next action");
        }
    }

    /** JSON → danh sách next action. Dữ liệu cũ/null/hỏng → danh sách rỗng (không làm vỡ màn hình). */
    private List<ProjectDto.DiaryAction> readActions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<ProjectDto.DiaryAction>>() { });
        } catch (Exception e) {
            return List.of();
        }
    }

    /** NEW | DOING | DONE — giá trị lạ/trống → NEW. */
    private static String normalizeStatus(String s) {
        if (s == null) {
            return "NEW";
        }
        String v = s.trim().toUpperCase();
        return ("DOING".equals(v) || "DONE".equals(v)) ? v : "NEW";
    }

    /** Như {@link #parseDate} nhưng KHÔNG ném lỗi — hạn next action sai định dạng thì bỏ trống. */
    private static LocalDate parseDateSafe(String v) {
        try {
            return parseDate(v);
        } catch (Exception e) {
            return null;
        }
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

    /**
     * VAI TRÒ lấy TỪ HỆ THỐNG cho người phía đơn vị thực hiện (in vào biên bản họp):
     * ưu tiên vai trò trong dự án, không có thì lấy chức danh nhân sự, cuối cùng là chức vụ.
     */
    private String roleOf(String projectId, String userId) {
        ProjectMember m = memberRepo.findByProjectIdAndUserId(projectId, userId).orElse(null);
        if (m != null && m.getRoleInProject() != null) {
            return roleLabel(m.getRoleInProject());
        }
        Employee emp = employeeRepo.findByUserAccountId(userId).orElse(null);
        if (emp == null) {
            return null;
        }
        return blankToNull(emp.getJobPosition()) != null ? emp.getJobPosition() : blankToNull(emp.getTitle());
    }

    /** Nhãn tiếng Việt của vai trò trong dự án (đồng bộ màn Thành viên). */
    private static String roleLabel(ProjectRoleInProject r) {
        switch (r) {
            case PM: return "Quản lý dự án";
            case LEAD: return "Trưởng nhóm";
            default: return "Thành viên";
        }
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
