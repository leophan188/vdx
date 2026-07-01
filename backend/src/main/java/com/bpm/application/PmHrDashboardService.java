package com.bpm.application;

import com.bpm.api.dto.PmHrDashboardDto.EffortByPerson;
import com.bpm.api.dto.PmHrDashboardDto.HrStats;
import com.bpm.api.dto.PmHrDashboardDto.Overloaded;
import com.bpm.api.dto.PmHrDashboardDto.PersonRef;
import com.bpm.api.dto.PmHrDashboardDto.PmHrDashboard;
import com.bpm.api.dto.PmHrDashboardDto.ProjectRow;
import com.bpm.api.dto.PmHrDashboardDto.ProjectStats;
import com.bpm.domain.UserAccount;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectStatus;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.ProjectMemberRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tổng hợp số liệu DỰ ÁN + NHÂN SỰ cho Bảng điều khiển & Báo cáo (mini-Jira + HR).
 *
 * <p>Hiệu năng: load TẤT CẢ project/member/task/employee/userAccount đúng MỘT lần, rồi dựng:
 * <ul>
 *   <li>map userAccountId → Employee (từ {@code employee.userAccountId});</li>
 *   <li>gom members theo projectId & theo userId;</li>
 *   <li>gom tasks theo projectId.</li>
 * </ul>
 *
 * <p>Công thức:
 * <ul>
 *   <li>completionPct/dự án: tái dùng {@link ProjectService#completionPct(List)} (theo task lá / estimateHours).</li>
 *   <li>actualMM/dự án = Σ(member.manday) / 22 (man-day → man-month).</li>
 *   <li>overdue = task có dueDate &lt; hôm nay &amp; status ≠ DONE.</li>
 *   <li>bugOpen = task type BUG/ISSUE &amp; status ≠ DONE.</li>
 *   <li>quá tải = Σ effortPct của nhân sự qua mọi dự án &gt; 100%.</li>
 * </ul>
 */
@Service
public class PmHrDashboardService {

    private static final double WORKDAYS_PER_MONTH = 22.0;
    private static final int OVERLOAD_THRESHOLD = 100;
    private static final int AVAILABLE_SAMPLE_LIMIT = 8;
    private static final int TOP_DEPT_LIMIT = 12;

    private final ProjectRepository projectRepo;
    private final ProjectMemberRepository memberRepo;
    private final ProjectTaskRepository taskRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final ProjectService projectService;

    public PmHrDashboardService(ProjectRepository projectRepo, ProjectMemberRepository memberRepo,
                                ProjectTaskRepository taskRepo, EmployeeRepository employeeRepo,
                                UserAccountRepository userRepo, ProjectService projectService) {
        this.projectRepo = projectRepo;
        this.memberRepo = memberRepo;
        this.taskRepo = taskRepo;
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public PmHrDashboard build() {
        LocalDate today = LocalDate.now();

        // ===== Load 1 lần =====
        List<Project> projects = projectRepo.findAll();
        List<ProjectMember> members = memberRepo.findAll();
        List<ProjectTask> tasks = taskRepo.findAll();
        List<Employee> employees = employeeRepo.findAll();
        List<UserAccount> users = userRepo.findAll();

        // map userAccountId -> Employee
        Map<String, Employee> empByUser = new HashMap<>();
        for (Employee e : employees) {
            if (e.getUserAccountId() != null) {
                empByUser.put(e.getUserAccountId(), e);
            }
        }
        // map userId -> tên hiển thị (ưu tiên Employee.fullName, rồi UserAccount)
        Map<String, UserAccount> userById = new HashMap<>();
        for (UserAccount u : users) {
            userById.put(u.getId(), u);
        }

        // gom members theo project & theo user
        Map<String, List<ProjectMember>> membersByProject = new HashMap<>();
        Map<String, List<ProjectMember>> membersByUser = new HashMap<>();
        for (ProjectMember m : members) {
            membersByProject.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>()).add(m);
            membersByUser.computeIfAbsent(m.getUserId(), k -> new ArrayList<>()).add(m);
        }
        // gom tasks theo project
        Map<String, List<ProjectTask>> tasksByProject = new HashMap<>();
        for (ProjectTask t : tasks) {
            tasksByProject.computeIfAbsent(t.getProjectId(), k -> new ArrayList<>()).add(t);
        }
        Map<String, Project> projectById = new HashMap<>();
        for (Project p : projects) {
            projectById.put(p.getId(), p);
        }

        // ===== Khối DỰ ÁN =====
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ProjectStatus s : ProjectStatus.values()) {
            byStatus.put(s.name(), 0);
        }
        int totalTasks = 0, doneTasks = 0, overdueTasks = 0, openBugs = 0;
        long totalBudget = 0;
        double totalPlannedMM = 0, totalActualMM = 0, sumCompletion = 0;

        List<ProjectRow> rows = new ArrayList<>();
        for (Project p : projects) {
            byStatus.merge(p.getStatus().name(), 1, Integer::sum);

            List<ProjectTask> ptasks = tasksByProject.getOrDefault(p.getId(), List.of());
            List<ProjectMember> pmembers = membersByProject.getOrDefault(p.getId(), List.of());

            double completion = projectService.completionPct(ptasks);
            sumCompletion += completion;

            int pOverdue = 0, pBugOpen = 0;
            for (ProjectTask t : ptasks) {
                boolean done = t.getStatus() == TaskStatus.DONE;
                totalTasks++;
                if (done) {
                    doneTasks++;
                }
                if (t.getDueDate() != null && t.getDueDate().isBefore(today) && !done) {
                    overdueTasks++;
                    pOverdue++;
                }
                if ((t.getType() == TaskType.BUG || t.getType() == TaskType.ISSUE) && !done) {
                    openBugs++;
                    pBugOpen++;
                }
            }

            double actualMm = totalEffortMM(pmembers);
            double plannedMm = p.getPlannedEffortMm() == null ? 0.0 : p.getPlannedEffortMm();
            totalPlannedMM += plannedMm;
            totalActualMM += actualMm;
            if (p.getBudget() != null) {
                totalBudget += p.getBudget();
            }

            rows.add(new ProjectRow(p.getId(), p.getCode(), p.getName(), p.getStatus().name(),
                    completion, pmembers.size(), round2(plannedMm), actualMm, p.getBudget(),
                    pOverdue, pBugOpen));
        }
        rows.sort(Comparator.comparing(ProjectRow::status).thenComparing(ProjectRow::code));

        double avgCompletion = projects.isEmpty() ? 0.0 : round2(sumCompletion / projects.size());
        ProjectStats projectStats = new ProjectStats(projects.size(), byStatus, totalTasks, doneTasks,
                avgCompletion, overdueTasks, openBugs, totalBudget,
                round2(totalPlannedMM), round2(totalActualMM));

        // ===== Khối NHÂN SỰ =====
        int active = 0, inactive = 0, external = 0;
        Map<String, Integer> byDeptRaw = new HashMap<>();
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        for (Employee e : employees) {
            if (e.isExternal()) {
                external++;
            }
            if (e.isActive()) {
                active++;
            } else {
                inactive++;
            }
            String dept = blankOr(e.getDeptCode(), "(Chưa rõ)");
            byDeptRaw.merge(dept, 1, Integer::sum);
            String level = blankOr(e.getLevel(), "(Chưa rõ)");
            byLevel.merge(level, 1, Integer::sum);
        }
        // byDept: top theo count giảm dần
        Map<String, Integer> byDept = byDeptRaw.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_DEPT_LIMIT)
                .collect(LinkedHashMap::new, (mp, en) -> mp.put(en.getKey(), en.getValue()), LinkedHashMap::putAll);

        // userId -> tổng effort & danh sách tên dự án (cho overload + effortByPerson)
        // unassigned: nhân sự active KHÔNG join dự án nào
        int unassigned = 0;
        List<PersonRef> availableSample = new ArrayList<>();
        for (Employee e : employees) {
            if (!e.isActive()) {
                continue;
            }
            String uid = e.getUserAccountId();
            boolean joined = uid != null && membersByUser.containsKey(uid) && !membersByUser.get(uid).isEmpty();
            if (!joined) {
                unassigned++;
                if (availableSample.size() < AVAILABLE_SAMPLE_LIMIT) {
                    availableSample.add(new PersonRef(e.getEmpCode(), e.getFullName(),
                            e.getDeptCode(), e.getJobPosition()));
                }
            }
        }

        // overloaded + effortByPerson — duyệt theo từng user có membership
        List<Overloaded> overloaded = new ArrayList<>();
        List<EffortByPerson> effortByPerson = new ArrayList<>();
        for (Map.Entry<String, List<ProjectMember>> en : membersByUser.entrySet()) {
            String uid = en.getKey();
            List<ProjectMember> ms = en.getValue();
            Employee emp = empByUser.get(uid);
            String empCode = emp != null ? emp.getEmpCode() : null;
            String name = personName(emp, userById.get(uid), uid);
            String deptCode = emp != null ? emp.getDeptCode() : null;

            int totalEffort = 0, totalManday = 0;
            List<String> projNames = new ArrayList<>();
            for (ProjectMember m : ms) {
                totalEffort += m.getEffortPct();
                totalManday += m.manday();
                Project p = projectById.get(m.getProjectId());
                projNames.add(p != null ? p.getName() : m.getProjectId());
            }
            double totalMM = round2(totalManday / WORKDAYS_PER_MONTH);

            effortByPerson.add(new EffortByPerson(empCode, name, deptCode,
                    ms.size(), totalEffort, totalManday, totalMM));

            if (totalEffort > OVERLOAD_THRESHOLD) {
                overloaded.add(new Overloaded(empCode, name, totalEffort, projNames));
            }
        }
        effortByPerson.sort(Comparator.comparingInt(EffortByPerson::totalEffort).reversed());
        overloaded.sort(Comparator.comparingInt(Overloaded::totalEffort).reversed());

        HrStats hrStats = new HrStats(employees.size(), active, inactive, external,
                byDept, byLevel, overloaded, unassigned, availableSample);

        return new PmHrDashboard(projectStats, rows, hrStats, effortByPerson);
    }

    /** Tổng nỗ lực man-month = Σ(member.manday) / 22 (làm tròn 2 chữ số). */
    private static double totalEffortMM(List<ProjectMember> members) {
        int totalManday = members.stream().mapToInt(ProjectMember::manday).sum();
        return round2(totalManday / WORKDAYS_PER_MONTH);
    }

    /** Tên hiển thị ưu tiên Employee.fullName, rồi UserAccount, cuối cùng fallback id. */
    private static String personName(Employee emp, UserAccount acc, String fallback) {
        if (emp != null && emp.getFullName() != null && !emp.getFullName().isBlank()) {
            return emp.getFullName();
        }
        if (acc != null) {
            return (acc.getFullName() != null && !acc.getFullName().isBlank())
                    ? acc.getFullName() : acc.getUsername();
        }
        return fallback;
    }

    private static String blankOr(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s.trim();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
