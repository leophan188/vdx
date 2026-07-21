package com.bpm.application;

import com.bpm.api.dto.ProjectDto;
import com.bpm.domain.UserAccount;
import com.bpm.domain.project.Project;
import com.bpm.domain.project.ProjectMember;
import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;
import com.bpm.infrastructure.ProjectMemberRepository;
import com.bpm.infrastructure.ProjectRepository;
import com.bpm.infrastructure.ProjectTaskRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Báo cáo NGÀY / TUẦN cho một dự án (req 5). 4 nhóm: done (DONE cập nhật trong kỳ), inProgress
 * (IN_PROGRESS + IN_REVIEW), upcoming (TODO/BACKLOG có startDate trong cửa sổ tới hoặc chưa lên lịch),
 * overdue (dueDate < hôm nay & chưa DONE) + overview. progressPct mỗi dòng tính như ProjectTaskService.
 */
@Service
public class ProjectReportService {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final ProjectRepository projectRepo;
    private final ProjectTaskRepository taskRepo;
    private final UserAccountRepository userRepo;
    private final ProjectMemberRepository memberRepo;
    private final com.bpm.infrastructure.TaskActivityRepository activityRepo;

    public ProjectReportService(ProjectRepository projectRepo, ProjectTaskRepository taskRepo,
                                UserAccountRepository userRepo, ProjectMemberRepository memberRepo,
                                com.bpm.infrastructure.TaskActivityRepository activityRepo) {
        this.projectRepo = projectRepo;
        this.taskRepo = taskRepo;
        this.userRepo = userRepo;
        this.memberRepo = memberRepo;
        this.activityRepo = activityRepo;
    }

    @Transactional(readOnly = true)
    public ProjectDto.PeriodReportResponse daily(String projectId) {
        return daily(projectId, LocalDate.now());
    }

    /** Báo cáo NGÀY cho ngày chỉ định (mặc định hôm nay). */
    @Transactional(readOnly = true)
    public ProjectDto.PeriodReportResponse daily(String projectId, LocalDate day) {
        LocalDate d = day != null ? day : LocalDate.now();
        boolean isToday = d.equals(LocalDate.now());
        String label = (isToday ? "Hôm nay " : "Ngày ") + d.format(DMY);
        return build(projectId, d, d, d.plusDays(1), label, d, false);
    }

    @Transactional(readOnly = true)
    public ProjectDto.PeriodReportResponse weekly(String projectId) {
        return weekly(projectId, LocalDate.now());
    }

    /** Báo cáo TUẦN chứa ngày chỉ định (mặc định tuần này). */
    @Transactional(readOnly = true)
    public ProjectDto.PeriodReportResponse weekly(String projectId, LocalDate anchor) {
        LocalDate a = anchor != null ? anchor : LocalDate.now();
        LocalDate monday = a.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);
        String label = "Tuần " + monday.format(DMY) + "–" + sunday.format(DMY);
        return build(projectId, monday, sunday, a.plusDays(7), label, a, true);
    }

    // ===================== BURNDOWN =====================

    /**
     * Biểu đồ burndown của dự án (giờ ước lượng còn lại theo thời gian).
     *
     * <ul>
     *   <li>Trục thời gian: [{@code project.startDate}, {@code project.dueDate}]. Thiếu thì suy từ
     *       min(startDate task) → max(dueDate task); vẫn thiếu → 30 ngày quanh hôm nay.</li>
     *   <li>Lấy mẫu theo NGÀY; nếu khoảng > 60 ngày thì theo TUẦN (mỗi 7 ngày) cho gọn.
     *       Luôn có mốc cuối = dueDate.</li>
     *   <li>{@code totalEstimate} = Σ estimateHours task LÁ.</li>
     *   <li><b>Ideal</b>: giảm tuyến tính totalEstimate (mốc đầu) → 0 (mốc cuối).</li>
     *   <li><b>Actual</b> tại mốc D = totalEstimate − Σ(est lá DONE có "ngày hoàn thành" ≤ D),
     *       với ngày hoàn thành = {@code dueDate} nếu có, else ngày suy từ {@code updatedAt}.</li>
     * </ul>
     */
    @Transactional(readOnly = true)
    public ProjectDto.BurndownResponse burndown(String projectId) {
        Project p = projectRepo.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án"));
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(projectId);

        Set<String> parentIds = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                parentIds.add(t.getParentId());
            }
        }

        // Tổng est của task LÁ + danh sách (ngày hoàn thành, est) của lá DONE; tổng spent lá.
        double totalEstimate = 0, totalSpent = 0;
        List<double[]/* {epochDay, est} */> doneLeaves = new ArrayList<>();
        LocalDate minTaskStart = null, maxTaskDue = null;
        for (ProjectTask t : tasks) {
            if (t.getStartDate() != null && (minTaskStart == null || t.getStartDate().isBefore(minTaskStart))) {
                minTaskStart = t.getStartDate();
            }
            if (t.getDueDate() != null && (maxTaskDue == null || t.getDueDate().isAfter(maxTaskDue))) {
                maxTaskDue = t.getDueDate();
            }
            boolean leaf = !parentIds.contains(t.getId());
            if (!leaf) {
                continue;
            }
            if (t.getStatus() == TaskStatus.CANCELLED) {
                continue; // Huỷ = ngoài phạm vi, không tính vào burndown
            }
            totalEstimate += t.getEstimateHours();
            totalSpent += t.getSpentHours();
            if (t.getStatus() == TaskStatus.DONE) {
                LocalDate completed = completionDate(t);
                doneLeaves.add(new double[]{completed.toEpochDay(), t.getEstimateHours()});
            }
        }

        // Xác định trục thời gian.
        LocalDate start = p.getStartDate();
        LocalDate end = p.getDueDate();
        if (start == null) {
            start = minTaskStart;
        }
        if (end == null) {
            end = maxTaskDue;
        }
        if (start == null || end == null) {
            LocalDate today = LocalDate.now();
            start = today.minusDays(15);
            end = today.plusDays(15);
        }
        if (end.isBefore(start)) {
            end = start;
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        boolean weekly = days > 60;
        int step = weekly ? 7 : 1;
        String unit = weekly ? "week" : "day";

        // Sinh các mốc: từ start, bước step, đảm bảo có mốc cuối = end.
        List<LocalDate> marks = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(step)) {
            marks.add(d);
        }
        if (marks.isEmpty() || !marks.get(marks.size() - 1).equals(end)) {
            marks.add(end);
        }

        int n = marks.size();
        List<ProjectDto.BurndownPoint> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LocalDate d = marks.get(i);
            double ideal = n <= 1 ? 0.0 : totalEstimate * (1.0 - (double) i / (n - 1));
            long dEpoch = d.toEpochDay();
            double doneByD = 0;
            for (double[] dl : doneLeaves) {
                if (dl[0] <= dEpoch) {
                    doneByD += dl[1];
                }
            }
            double actual = totalEstimate - doneByD;
            if (actual < 0) {
                actual = 0;
            }
            points.add(new ProjectDto.BurndownPoint(d.format(DMY), round2(ideal), round2(actual)));
        }

        int teamManday = 0;
        for (ProjectMember m : memberRepo.findByProjectIdOrderByJoinedAtAsc(projectId)) {
            teamManday += m.manday();
        }

        return new ProjectDto.BurndownResponse(start.format(DMY), end.format(DMY),
                round2(totalEstimate), round2(totalSpent), teamManday, unit, points);
    }

    /** Ngày hoàn thành của task DONE: dueDate nếu có, else ngày (local) suy từ updatedAt; cuối cùng hôm nay. */
    private LocalDate completionDate(ProjectTask t) {
        if (t.getDueDate() != null) {
            return t.getDueDate();
        }
        if (t.getUpdatedAt() != null) {
            return t.getUpdatedAt().atZone(ZONE).toLocalDate();
        }
        return LocalDate.now();
    }

    /**
     * @param periodStart  đầu kỳ (bao gồm) — dùng cho "done trong kỳ" (updatedAt)
     * @param periodEnd    cuối kỳ (bao gồm) — dùng cho "done trong kỳ"
     * @param upcomingTo   biên trên cửa sổ "sắp tới" (loại trừ): daily=+1 ngày, weekly=+7 ngày
     */
    private ProjectDto.PeriodReportResponse build(String projectId, LocalDate periodStart, LocalDate periodEnd,
                                                  LocalDate upcomingTo, String label, LocalDate refToday, boolean isWeekly) {
        Project p = projectRepo.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy dự án"));
        List<ProjectTask> tasks = taskRepo.findByProjectIdOrderByOrderIndexAscSeqAsc(projectId);
        Set<String> parentIds = new HashSet<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                parentIds.add(t.getParentId());
            }
        }
        Map<String, Double> progress = computeProgress(tasks, parentIds);
        Map<String, String> nameCache = new HashMap<>();
        // Map id→task để dựng chuỗi cha (Epic › Story › …) cho từng công việc.
        Map<String, ProjectTask> taskById = new HashMap<>();
        for (ProjectTask t : tasks) {
            taskById.put(t.getId(), t);
        }

        LocalDate today = refToday != null ? refToday : LocalDate.now();
        Instant startOfPeriod = periodStart.atStartOfDay(ZONE).toInstant();
        Instant endOfPeriod = periodEnd.plusDays(1).atStartOfDay(ZONE).toInstant(); // [start, end+1)

        List<ProjectDto.ReportTaskItem> done = new ArrayList<>();
        List<ProjectDto.ReportTaskItem> inProgress = new ArrayList<>();
        List<ProjectDto.ReportTaskItem> upcoming = new ArrayList<>();
        List<ProjectDto.ReportTaskItem> overdue = new ArrayList<>();
        List<ProjectDto.ReportTaskItem> epicStory = new ArrayList<>(); // tiến độ % EPIC/Story (tổng quan)

        int totalTasks = tasks.size(), doneTasks = 0, overdueCount = 0, bugCount = 0;
        double totalEstimate = 0, doneEstimate = 0;

        for (ProjectTask t : tasks) {
            boolean isDone = t.getStatus() == TaskStatus.DONE;
            boolean isCancelled = t.getStatus() == TaskStatus.CANCELLED; // Huỷ = ngoài phạm vi
            totalEstimate += t.getEstimateHours();
            if (isDone) {
                doneTasks++;
                doneEstimate += t.getEstimateHours();
            }
            if (t.getType() == TaskType.BUG) {
                bugCount++;
            }
            boolean isOverdue = t.getDueDate() != null && t.getDueDate().isBefore(today) && !isDone && !isCancelled;
            if (isOverdue) {
                overdueCount++;
            }

            ProjectDto.ReportTaskItem item = item(t, p.getCode(), nameCache,
                    progress.getOrDefault(t.getId(), 0.0), taskById);

            if (isDone) {
                Instant upd = t.getUpdatedAt();
                if (upd != null && !upd.isBefore(startOfPeriod) && upd.isBefore(endOfPeriod)) {
                    done.add(item);
                }
            } else if (t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.IN_REVIEW) {
                inProgress.add(item);
            } else if (!isCancelled) { // TODO / BACKLOG (bỏ qua việc đã Huỷ)
                LocalDate start = t.getStartDate();
                boolean within = start == null || (!start.isBefore(today) && start.isBefore(upcomingTo));
                if (within) {
                    upcoming.add(item);
                }
            }

            if (isOverdue) {
                overdue.add(item);
            }
        }

        ProjectDto.ReportOverview overview = new ProjectDto.ReportOverview(
                completionPct(tasks, parentIds), totalTasks, doneTasks,
                round2(totalEstimate), round2(doneEstimate), overdueCount, bugCount);

        // Báo cáo TUẦN: tiến độ % EPIC/Story theo THỨ TỰ CÂY (Story nằm dưới Epic cha). Ngày: bỏ.
        if (isWeekly) {
            java.util.Map<String, List<ProjectTask>> childrenOf = new java.util.LinkedHashMap<>();
            for (ProjectTask t : tasks) {
                if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) {
                    childrenOf.computeIfAbsent(t.getParentId() == null ? "" : t.getParentId(), k -> new ArrayList<>()).add(t);
                }
            }
            java.util.Set<String> esIds = new java.util.HashSet<>();
            for (ProjectTask t : tasks) {
                if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) esIds.add(t.getId());
            }
            java.util.Deque<ProjectTask> stack = new java.util.ArrayDeque<>();
            // Gốc = Epic/Story không có cha Epic/Story (giữ thứ tự backlog).
            List<ProjectTask> roots = new ArrayList<>();
            for (ProjectTask t : tasks) {
                if ((t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY)
                        && (t.getParentId() == null || !esIds.contains(t.getParentId()))) {
                    roots.add(t);
                }
            }
            for (int i = roots.size() - 1; i >= 0; i--) stack.push(roots.get(i));
            java.util.Set<String> seen = new java.util.HashSet<>();
            while (!stack.isEmpty()) {
                ProjectTask t = stack.pop();
                if (!seen.add(t.getId())) continue;
                epicStory.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById));
                List<ProjectTask> kids = childrenOf.getOrDefault(t.getId(), List.of());
                for (int i = kids.size() - 1; i >= 0; i--) stack.push(kids.get(i));
            }
        }

        // ===== XỬ LÝ TRONG KỲ: dựa trên NHẬT KÝ HOẠT ĐỘNG, không dựa updated_at =====
        // Lúc TẠO task thì updated_at = created_at → việc vừa log (chưa ai đụng) cũng bị tính là
        // "đã xử lý" (vd 2 Issue log lúc 09:02/09:14 vẫn ở Cần làm mà vẫn đếm). Nên chỉ tính task
        // có hoạt động THỰC SỰ LÀM trong kỳ: mọi loại TRỪ CREATED (tạo mới không phải là xử lý).
        java.util.Set<String> workedInPeriod = new java.util.HashSet<>();
        for (com.bpm.domain.project.TaskActivity act
                : activityRepo.findByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        projectId, startOfPeriod, endOfPeriod)) {
            if (!com.bpm.domain.project.TaskActivity.CREATED.equals(act.getAction())) {
                workedInPeriod.add(act.getTaskId());
            }
        }

        // Tỷ lệ hoàn thành THEO NHÂN SỰ (chỉ việc lá: bỏ EPIC/STORY & việc Huỷ).
        // [0..4] = TOÀN DỰ ÁN; [5],[6] = TRONG KỲ (việc có updatedAt rơi vào kỳ đang xem).
        java.util.Map<String, int[]> pAgg = new java.util.LinkedHashMap<>();   // key -> [total,done,doing,todo,overdue,inPeriod,donePeriod]
        java.util.Map<String, String> pName = new java.util.LinkedHashMap<>(); // key -> tên hiển thị
        for (ProjectTask t : tasks) {
            if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) continue;
            if (t.getStatus() == TaskStatus.CANCELLED) continue;
            String uid = t.getAssigneeUserId();
            String key = uid == null ? "" : uid;
            String nm = uid == null ? "(chưa gán)"
                    : nameCache.computeIfAbsent(uid, x -> userRepo.findById(uid).map(ProjectService::displayName).orElse(uid));
            pName.putIfAbsent(key, nm);
            int[] a = pAgg.computeIfAbsent(key, x -> new int[7]);
            a[0]++;
            boolean d = t.getStatus() == TaskStatus.DONE;
            if (d) a[1]++;
            else if (t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.IN_REVIEW) a[2]++;
            else a[3]++; // TODO / BACKLOG
            if (t.getDueDate() != null && t.getDueDate().isBefore(today) && !d) a[4]++;
            if (workedInPeriod.contains(t.getId())) {
                a[5]++;
                if (d) a[6]++;
            }
        }
        List<ProjectDto.PersonProgress> byPerson = new ArrayList<>();
        for (var e : pAgg.entrySet()) {
            int[] a = e.getValue();
            double pct = a[0] == 0 ? 0 : Math.round(a[1] * 1000.0 / a[0]) / 10.0;
            byPerson.add(new ProjectDto.PersonProgress(
                    e.getKey().isEmpty() ? null : e.getKey(), pName.get(e.getKey()),
                    a[0], a[1], a[2], a[3], a[4], pct, a[5], a[6]));
        }
        // Ai làm nhiều TRONG KỲ lên trước (báo cáo kỳ), rồi mới đến tổng công việc.
        byPerson.sort((x, y) -> y.inPeriod() != x.inPeriod()
                ? Integer.compare(y.inPeriod(), x.inPeriod())
                : Integer.compare(y.total(), x.total()));

        // Danh sách việc XỬ LÝ TRONG KỲ — CÙNG điều kiện với bộ đếm inPeriod ở trên, để popup chi tiết
        // và con số trong bảng LUÔN khớp nhau (trước đây popup lấy từ 4 nhóm done/inProgress/upcoming/
        // overdue nên có người đếm ra 1 việc mà bấm vào lại trống).
        List<ProjectDto.ReportTaskItem> periodItems = new ArrayList<>();
        for (ProjectTask t : tasks) {
            if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) continue;
            if (t.getStatus() == TaskStatus.CANCELLED) continue;
            if (workedInPeriod.contains(t.getId())) {
                periodItems.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById));
            }
        }

        // Bug/Issue được LOG (tạo) TRONG KỲ — thống kê tester log / dev bị log theo ĐÚNG kỳ Ngày/Tuần.
        List<ProjectDto.ReportTaskItem> bugsLogged = new ArrayList<>();
        for (ProjectTask t : tasks) {
            if (t.getType() != TaskType.BUG && t.getType() != TaskType.ISSUE) continue;
            Instant c = t.getCreatedAt();
            if (c != null && !c.isBefore(startOfPeriod) && c.isBefore(endOfPeriod)) {
                bugsLogged.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById));
            }
        }

        return new ProjectDto.PeriodReportResponse(label, done, inProgress, upcoming, overdue, overview,
                epicStory, byPerson, bugsLogged, periodItems);
    }

    private ProjectDto.ReportTaskItem item(ProjectTask t, String projectCode,
                                           Map<String, String> nameCache, double progressPct,
                                           Map<String, ProjectTask> taskById) {
        String assignee = null;
        if (t.getAssigneeUserId() != null) {
            assignee = nameCache.computeIfAbsent(t.getAssigneeUserId(), uid ->
                    userRepo.findById(uid).map(ProjectService::displayName).orElse(uid));
        }
        String reporter = null;
        if (t.getReporterUserId() != null) {
            reporter = nameCache.computeIfAbsent(t.getReporterUserId(), uid ->
                    userRepo.findById(uid).map(ProjectService::displayName).orElse(uid));
        }
        return new ProjectDto.ReportTaskItem(t.getId(), String.valueOf(t.getSeq()), t.getTitle(),
                t.getType().name(), t.getStatus().name(), assignee, t.getEstimateHours(),
                t.getStartDate() == null ? null : t.getStartDate().format(DMY),
                t.getDueDate() == null ? null : t.getDueDate().format(DMY), progressPct,
                t.getPriority() == null ? null : t.getPriority().name(),
                t.getSeverity() == null ? null : t.getSeverity().name(),
                t.getAssigneeUserId(), parentPath(t, taskById),
                t.getReporterUserId(), reporter);
    }

    /** Chuỗi cha "Epic: … › Story: …" (gốc→gần); null nếu không có cha. */
    private static String parentPath(ProjectTask t, Map<String, ProjectTask> taskById) {
        java.util.Deque<String> chain = new java.util.ArrayDeque<>();
        ProjectTask cur = t.getParentId() == null ? null : taskById.get(t.getParentId());
        int guard = 0;
        while (cur != null && guard++ < 12) {
            chain.addFirst(typeShort(cur.getType()) + ": " + cur.getTitle());
            cur = cur.getParentId() == null ? null : taskById.get(cur.getParentId());
        }
        return chain.isEmpty() ? null : String.join(" › ", chain);
    }

    private static String typeShort(TaskType t) {
        switch (t) {
            case EPIC: return "Epic";
            case STORY: return "Story";
            case TASK: return "Task";
            case SUBTASK: return "Sub-task";
            case BUG: return "Bug";
            case ISSUE: return "Issue";
            default: return t.name();
        }
    }

    // ===== progress rollup (cùng quy tắc ProjectTaskService) =====

    private Map<String, Double> computeProgress(List<ProjectTask> tasks, Set<String> parentIds) {
        Map<String, List<ProjectTask>> children = new HashMap<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                children.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t);
            }
        }
        Map<String, Double> pct = new HashMap<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() == null) {
                rollup(t, children, pct);
            }
        }
        return pct;
    }

    private double[] rollup(ProjectTask t, Map<String, List<ProjectTask>> children, Map<String, Double> pct) {
        List<ProjectTask> kids = children.get(t.getId());
        if (kids == null || kids.isEmpty()) {
            if (t.getStatus() == TaskStatus.CANCELLED) {
                pct.put(t.getId(), 0.0);
                return new double[]{0, 0, 0, 0}; // Huỷ = ngoài phạm vi, không góp vào rollup cha
            }
            boolean done = t.getStatus() == TaskStatus.DONE;
            double est = t.getEstimateHours();
            pct.put(t.getId(), done ? 100.0 : 0.0);
            return new double[]{est, done ? est : 0.0, 1, done ? 1 : 0};
        }
        double leafEst = 0, leafDoneEst = 0, leaf = 0, leafDone = 0;
        for (ProjectTask k : kids) {
            double[] r = rollup(k, children, pct);
            leafEst += r[0];
            leafDoneEst += r[1];
            leaf += r[2];
            leafDone += r[3];
        }
        double p = leaf == 0 ? 0.0 : (leafEst > 0 ? (leafDoneEst / leafEst) * 100.0 : (leafDone / leaf) * 100.0);
        pct.put(t.getId(), round2(p));
        return new double[]{leafEst, leafDoneEst, leaf, leafDone};
    }

    /** completionPct toàn dự án = rollup tại "rễ ảo" (gộp mọi lá). Nhất quán ProjectService.completionPct. */
    private double completionPct(List<ProjectTask> tasks, Set<String> parentIds) {
        double leafEst = 0, leafDoneEst = 0;
        int leaf = 0, leafDone = 0;
        for (ProjectTask t : tasks) {
            if (parentIds.contains(t.getId())) {
                continue;
            }
            if (t.getStatus() == TaskStatus.CANCELLED) {
                continue; // Huỷ = ngoài phạm vi, không tính vào % hoàn thành
            }
            leaf++;
            leafEst += t.getEstimateHours();
            if (t.getStatus() == TaskStatus.DONE) {
                leafDone++;
                leafDoneEst += t.getEstimateHours();
            }
        }
        if (leaf == 0) {
            return 0.0;
        }
        return round2(leafEst > 0 ? (leafDoneEst / leafEst) * 100.0 : ((double) leafDone / leaf) * 100.0);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
