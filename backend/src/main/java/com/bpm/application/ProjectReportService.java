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
     * @param upcomingTo   KHÔNG CÒN DÙNG — "Sắp làm" nay xét theo HẠN 1–3 ngày tới và chỉ có ở
     *                     báo cáo NGÀY. Giữ tham số để không phải đổi chữ ký ở các nơi gọi.
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
        List<ProjectDto.ReportTaskItem> todo = new ArrayList<>(); // CẦN LÀM: hạn đã đến trong kỳ mà chưa khởi động
        List<ProjectDto.ReportTaskItem> overdue = new ArrayList<>();
        List<ProjectDto.ReportTaskItem> epicStory = new ArrayList<>(); // tiến độ % EPIC/Story (tổng quan)

        // Est/ngày của task CHA phải TỰ TỔNG HỢP từ LÁ con (khớp web & Backlog Excel).
        // Trước đây báo cáo lấy est/ngày RIÊNG của cha → cha hiện 06/07–06/07 thay vì khoảng thật
        // của các lá, và tổng est cộng trùng cả cha lẫn con.
        Map<String, List<ProjectTask>> childrenOf = new HashMap<>();
        for (ProjectTask t : tasks) {
            if (t.getParentId() != null) {
                childrenOf.computeIfAbsent(t.getParentId(), k -> new ArrayList<>()).add(t);
            }
        }
        Map<String, Double> rollEst = new HashMap<>();
        Map<String, LocalDate[]> rollRange = new HashMap<>();
        for (ProjectTask t : tasks) {
            computeRollup(t, childrenOf, rollEst, rollRange, new java.util.HashSet<>());
        }

        // Tổng số công việc = số VIỆC THỰC THI (lá, không tính Epic/Story kể cả Epic/Story rỗng)
        // — đồng bộ với danh sách bên dưới và bảng thống kê theo nhân sự.
        int totalTasks = 0, doneTasks = 0, overdueCount = 0, bugCount = 0;
        for (ProjectTask t : tasks) {
            if (!childrenOf.containsKey(t.getId())
                    && t.getType() != TaskType.EPIC && t.getType() != TaskType.STORY) {
                totalTasks++;
            }
        }
        double totalEstimate = 0, doneEstimate = 0;

        for (ProjectTask t : tasks) {
            boolean isDone = t.getStatus() == TaskStatus.DONE;
            boolean isCancelled = t.getStatus() == TaskStatus.CANCELLED; // Huỷ = ngoài phạm vi
            // Tổng est chỉ cộng LÁ (bỏ Huỷ) — cộng cả cha sẽ tính trùng phần của con.
            boolean isLeaf = !childrenOf.containsKey(t.getId());
            // "Việc làm được" = lá VÀ không phải Epic/Story. Epic/Story RỖNG (chưa có task con) vẫn
            // là lá về mặt cây nhưng không phải công việc thực thi → không đưa vào danh sách/đếm.
            boolean isWorkItem = isLeaf && t.getType() != TaskType.EPIC && t.getType() != TaskType.STORY;
            if (isLeaf && !isCancelled) {
                totalEstimate += t.getEstimateHours();
                if (isDone) {
                    doneEstimate += t.getEstimateHours();
                }
            }
            // Chỉ đếm việc LÁ để khớp với các DANH SÁCH bên dưới (cũng chỉ liệt kê lá) — nếu đếm cả
            // Epic/Story thì con số tổng quan lại không khớp số dòng, đúng kiểu lỗi đã gặp nhiều lần.
            if (isWorkItem) {
                if (isDone) {
                    doneTasks++;
                }
                if (t.getType() == TaskType.BUG) {
                    bugCount++;
                }
            }
            // Ngày HIỆU LỰC = ngày đã tổng hợp từ lá con (lá thì là ngày của chính nó).
            // PHẢI dùng đúng ngày đang HIỂN THỊ để xét trễ hạn/sắp tới; nếu xét bằng ngày riêng của
            // cha thì cha có hạn riêng 06/07 (đã qua) bị báo "trễ hạn" trong khi bảng hiện 31/07.
            LocalDate[] effRange = rollRange.getOrDefault(t.getId(),
                    new LocalDate[]{t.getStartDate(), t.getDueDate()});
            LocalDate effStart = effRange[0], effDue = effRange[1];

            boolean isOverdue = effDue != null && effDue.isBefore(today) && !isDone && !isCancelled;
            if (isOverdue && isWorkItem) {
                overdueCount++;
            }

            // DANH SÁCH CÔNG VIỆC CHỈ GỒM TASK NHỎ NHẤT (lá). Epic/Story không liệt kê thành dòng
            // riêng — ngữ cảnh cha đã nằm trong parentPath ("Epic: … › Story: …") của từng dòng lá.
            // Tiến độ Epic/Story vẫn có khối riêng (epicStory) ở báo cáo tuần.
            if (!isWorkItem) {
                continue;
            }

            ProjectDto.ReportTaskItem item = item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById, rollEst, rollRange);

            if (isDone) {
                Instant upd = t.getUpdatedAt();
                if (upd != null && !upd.isBefore(startOfPeriod) && upd.isBefore(endOfPeriod)) {
                    done.add(item);
                }
            } else if (t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.IN_REVIEW) {
                inProgress.add(item);
            } else if (!isCancelled) {
                // Việc CHƯA KHỞI ĐỘNG (Cần làm/Backlog) — chia 2 nhóm theo HẠN:
                //  · CẦN LÀM: hạn đã đến TRONG KỲ mà vẫn chưa ai bắt tay vào
                //    (ngày = đúng hôm nay; tuần = trong tuần đang xem). Trước đây nhóm này
                //    RƠI VÀO KHOẢNG TRỐNG — không nằm ở Trễ hạn (chưa quá hạn) cũng không
                //    ở Sắp làm (hạn không còn ở phía trước) nên biến mất khỏi báo cáo.
                //  · SẮP LÀM / TUẦN TIẾP THEO: hạn còn ở phía trước
                //    (ngày = 1–3 ngày tới; tuần = tuần kế tiếp).
                LocalDate todoFrom = isWeekly ? periodStart : today;
                LocalDate todoTo = isWeekly ? periodEnd : today;
                LocalDate nextFrom = isWeekly ? periodEnd.plusDays(1) : today.plusDays(1);
                LocalDate nextTo = isWeekly ? periodEnd.plusDays(7) : today.plusDays(3);
                if (effDue != null && !effDue.isBefore(todoFrom) && !effDue.isAfter(todoTo)) {
                    todo.add(item);
                } else if (effDue != null && !effDue.isBefore(nextFrom) && !effDue.isAfter(nextTo)) {
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
            // Cây CHỈ gồm Epic/Story (khác childrenOf ở trên vốn gồm mọi task để rollup est/ngày).
            java.util.Map<String, List<ProjectTask>> esChildrenOf = new java.util.LinkedHashMap<>();
            for (ProjectTask t : tasks) {
                if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) {
                    esChildrenOf.computeIfAbsent(t.getParentId() == null ? "" : t.getParentId(), k -> new ArrayList<>()).add(t);
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
                epicStory.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById, rollEst, rollRange));
                List<ProjectTask> kids = esChildrenOf.getOrDefault(t.getId(), List.of());
                for (int i = kids.size() - 1; i >= 0; i--) stack.push(kids.get(i));
            }
        }

        // ===== XỬ LÝ TRONG KỲ: dựa trên NHẬT KÝ HOẠT ĐỘNG, không dựa updated_at =====
        // Lúc TẠO task thì updated_at = created_at → việc vừa log (chưa ai đụng) cũng bị tính là
        // "đã xử lý" (vd 2 Issue log lúc 09:02/09:14 vẫn ở Cần làm mà vẫn đếm). Nên chỉ tính task
        // có hoạt động THỰC SỰ LÀM trong kỳ: mọi loại TRỪ CREATED (tạo mới không phải là xử lý).
        java.util.Set<String> workedInPeriod = new java.util.HashSet<>();
        // MỐC BÀN GIAO trong kỳ — nền cho thống kê đóng góp theo VAI:
        //  · handedToReview = dev đã bàn giao sang Kiểm thử (dev xong phần của mình)
        //  · movedToDone    = tester đã chuyển Hoàn thành (tester xong phần của mình)
        // Mỗi vai tính vào ĐÚNG kỳ xảy ra mốc của mình: dev bàn giao tuần trước, tester duyệt
        // tuần này → tuần này chỉ đếm cho tester.
        java.util.Set<String> handedToReview = new java.util.HashSet<>();
        java.util.Set<String> movedToDone = new java.util.HashSet<>();
        for (com.bpm.domain.project.TaskActivity act
                : activityRepo.findByProjectIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        projectId, startOfPeriod, endOfPeriod)) {
            if (!com.bpm.domain.project.TaskActivity.CREATED.equals(act.getAction())) {
                workedInPeriod.add(act.getTaskId());
            }
            if (com.bpm.domain.project.TaskActivity.STATUS.equals(act.getAction())) {
                TaskStatus to = toStatusOf(act);
                if (to == TaskStatus.IN_REVIEW) {
                    handedToReview.add(act.getTaskId());
                } else if (to == TaskStatus.DONE) {
                    movedToDone.add(act.getTaskId());
                }
            }
        }

        // Tỷ lệ hoàn thành THEO NHÂN SỰ (chỉ việc lá: bỏ EPIC/STORY & việc Huỷ).
        // [0..4] = TOÀN DỰ ÁN; [5],[6] = TRONG KỲ (việc có updatedAt rơi vào kỳ đang xem).
        // Gom theo CHỦ HIỆN TẠI (xem ownerUserId): việc ở Kiểm thử tính cho người kiểm thử /
        // người log chứ không nằm mãi ở dev đã bàn giao. Mỗi việc chỉ thuộc ĐÚNG MỘT người
        // tại một thời điểm nên cộng các dòng lại vẫn đúng bằng tổng công việc.
        java.util.Map<String, int[]> pAgg = new java.util.LinkedHashMap<>();   // key -> [total,done,doing,todo,overdue,inPeriod,donePeriod,devHandover,testerDone]
        java.util.Map<String, String> pName = new java.util.LinkedHashMap<>(); // key -> tên hiển thị
        for (ProjectTask t : tasks) {
            if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) continue;
            if (t.getStatus() == TaskStatus.CANCELLED) continue;
            String uid = ownerUserId(t);
            String key = uid == null ? "" : uid;
            String nm = uid == null ? "(chưa gán)"
                    : nameCache.computeIfAbsent(uid, x -> userRepo.findById(uid).map(ProjectService::displayName).orElse(uid));
            pName.putIfAbsent(key, nm);
            int[] a = pAgg.computeIfAbsent(key, x -> new int[9]);
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

        // ĐÓNG GÓP TRONG KỲ THEO VAI — tính cho ĐÚNG người giữ vai đó, không theo chủ hiện tại.
        // Một task bàn giao rồi duyệt xong trong cùng kỳ sẽ được đếm cho CẢ dev lẫn tester
        // (2 phần việc), trong khi tổng task của dự án vẫn là 1.
        java.util.function.BiConsumer<String, Integer> bump = (uid, slot) -> {
            if (uid == null) return;
            pName.putIfAbsent(uid, nameCache.computeIfAbsent(uid,
                    x -> userRepo.findById(uid).map(ProjectService::displayName).orElse(uid)));
            pAgg.computeIfAbsent(uid, x -> new int[9])[slot]++;
        };
        List<ProjectDto.ReportTaskItem> devHandoverItems = new ArrayList<>();
        List<ProjectDto.ReportTaskItem> testerDoneItems = new ArrayList<>();
        for (ProjectTask t : tasks) {
            if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) continue;
            if (handedToReview.contains(t.getId())) {
                bump.accept(t.getAssigneeUserId(), 7);
                devHandoverItems.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById, rollEst, rollRange));
            }
            if (movedToDone.contains(t.getId())) {
                bump.accept(testerUserIdOf(t), 8);
                testerDoneItems.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById, rollEst, rollRange));
            }
        }

        List<ProjectDto.PersonProgress> byPerson = new ArrayList<>();
        for (var e : pAgg.entrySet()) {
            int[] a = e.getValue();
            double pct = a[0] == 0 ? 0 : Math.round(a[1] * 1000.0 / a[0]) / 10.0;
            byPerson.add(new ProjectDto.PersonProgress(
                    e.getKey().isEmpty() ? null : e.getKey(), pName.get(e.getKey()),
                    a[0], a[1], a[2], a[3], a[4], pct, a[5], a[6], a[7], a[8]));
        }
        // Ai đóng góp nhiều TRONG KỲ lên trước, rồi mới đến tổng công việc. Phải cộng cả đóng góp
        // theo vai: tester duyệt xong nhiều nhưng không còn giữ việc nào thì inPeriod = 0,
        // chỉ xếp theo inPeriod sẽ đẩy họ xuống đáy bảng dù làm nhiều nhất kỳ đó.
        java.util.Comparator<ProjectDto.PersonProgress> byEffort =
                java.util.Comparator.comparingInt(
                        (ProjectDto.PersonProgress z) -> z.inPeriod() + z.devHandover() + z.testerDone()).reversed();
        byPerson.sort(byEffort.thenComparing(ProjectDto.PersonProgress::total, java.util.Comparator.reverseOrder()));

        // Danh sách việc XỬ LÝ TRONG KỲ — CÙNG điều kiện với bộ đếm inPeriod ở trên, để popup chi tiết
        // và con số trong bảng LUÔN khớp nhau (trước đây popup lấy từ 4 nhóm done/inProgress/upcoming/
        // overdue nên có người đếm ra 1 việc mà bấm vào lại trống).
        List<ProjectDto.ReportTaskItem> periodItems = new ArrayList<>();
        for (ProjectTask t : tasks) {
            if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) continue;
            if (t.getStatus() == TaskStatus.CANCELLED) continue;
            if (workedInPeriod.contains(t.getId())) {
                periodItems.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById, rollEst, rollRange));
            }
        }

        // Bug/Issue được LOG (tạo) TRONG KỲ — thống kê tester log / dev bị log theo ĐÚNG kỳ Ngày/Tuần.
        List<ProjectDto.ReportTaskItem> bugsLogged = new ArrayList<>();
        for (ProjectTask t : tasks) {
            if (t.getType() != TaskType.BUG && t.getType() != TaskType.ISSUE) continue;
            Instant c = t.getCreatedAt();
            if (c != null && !c.isBefore(startOfPeriod) && c.isBefore(endOfPeriod)) {
                bugsLogged.add(item(t, p.getCode(), nameCache, progress.getOrDefault(t.getId(), 0.0), taskById, rollEst, rollRange));
            }
        }

        return new ProjectDto.PeriodReportResponse(label, done, inProgress, upcoming, overdue, overview,
                epicStory, byPerson, bugsLogged, periodItems, devHandoverItems, testerDoneItems, todo);
    }

    /**
     * Est/khoảng ngày TỰ TỔNG HỢP cho task CHA: Σ est các LÁ con và [min bắt đầu, max kết thúc]
     * của lá (bỏ việc Huỷ). Lá thì lấy giá trị của chính nó. Ghi nhớ vào map để không tính lại.
     * {@code guard} chặn vòng cha-con lỗi dữ liệu (nếu có) khỏi đệ quy vô hạn.
     */
    private static void computeRollup(ProjectTask t, Map<String, List<ProjectTask>> childrenOf,
                                      Map<String, Double> est, Map<String, LocalDate[]> range,
                                      java.util.Set<String> guard) {
        if (est.containsKey(t.getId()) || !guard.add(t.getId())) {
            return;
        }
        List<ProjectTask> kids = childrenOf.get(t.getId());
        if (kids == null || kids.isEmpty()) {
            boolean cancelled = t.getStatus() == TaskStatus.CANCELLED;
            est.put(t.getId(), cancelled ? 0.0 : t.getEstimateHours());
            range.put(t.getId(), cancelled ? new LocalDate[]{null, null}
                    : new LocalDate[]{t.getStartDate(), t.getDueDate()});
            return;
        }
        double sum = 0;
        LocalDate min = null, max = null;
        for (ProjectTask k : kids) {
            computeRollup(k, childrenOf, est, range, guard);
            sum += est.getOrDefault(k.getId(), 0.0);
            LocalDate[] r = range.getOrDefault(k.getId(), new LocalDate[]{null, null});
            if (r[0] != null && (min == null || r[0].isBefore(min))) min = r[0];
            if (r[1] != null && (max == null || r[1].isAfter(max))) max = r[1];
        }
        est.put(t.getId(), Math.round(sum * 100) / 100.0);
        range.put(t.getId(), new LocalDate[]{min, max});
    }

    private ProjectDto.ReportTaskItem item(ProjectTask t, String projectCode,
                                           Map<String, String> nameCache, double progressPct,
                                           Map<String, ProjectTask> taskById,
                                           Map<String, Double> rollEst, Map<String, LocalDate[]> rollRange) {
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
        // Cha: est/ngày lấy từ rollup (lá con); lá: chính giá trị của nó.
        double est = rollEst.getOrDefault(t.getId(), t.getEstimateHours());
        LocalDate[] range = rollRange.getOrDefault(t.getId(),
                new LocalDate[]{t.getStartDate(), t.getDueDate()});
        String tester = null;
        if (t.getTesterUserId() != null) {
            tester = nameCache.computeIfAbsent(t.getTesterUserId(), uid ->
                    userRepo.findById(uid).map(ProjectService::displayName).orElse(uid));
        }
        String ownerId = ownerUserId(t);
        String owner = null;
        if (ownerId != null) {
            owner = nameCache.computeIfAbsent(ownerId, uid ->
                    userRepo.findById(uid).map(ProjectService::displayName).orElse(uid));
        }
        return new ProjectDto.ReportTaskItem(t.getId(), String.valueOf(t.getSeq()), t.getTitle(),
                t.getType().name(), t.getStatus().name(), assignee, est,
                range[0] == null ? null : range[0].format(DMY),
                range[1] == null ? null : range[1].format(DMY), progressPct,
                t.getPriority() == null ? null : t.getPriority().name(),
                t.getSeverity() == null ? null : t.getSeverity().name(),
                t.getAssigneeUserId(), parentPath(t, taskById),
                t.getReporterUserId(), reporter, t.getTesterUserId(), tester, ownerId, owner);
    }

    /**
     * CHỦ HIỆN TẠI của một công việc — ai đang thực sự giữ việc tại trạng thái này.
     * Hệ thống giữ 3 vai RIÊNG BIỆT và KHÔNG đổi assignee khi chuyển trạng thái
     * (người thực hiện / người kiểm thử / người log), nên phải suy chủ theo trạng thái:
     * <ul>
     *   <li>Kiểm thử + task thường → NGƯỜI KIỂM THỬ (bắt buộc chọn trước khi chuyển).</li>
     *   <li>Kiểm thử + bug/issue   → NGƯỜI LOG (hệ thống bàn giao ngầm để verify).</li>
     *   <li>Các trạng thái khác    → NGƯỜI THỰC HIỆN.</li>
     * </ul>
     * Thiếu vai tương ứng thì lùi về người thực hiện để không việc nào rơi ra ngoài thống kê.
     */
    /**
     * Trạng thái ĐÍCH của một lần chuyển. Ưu tiên cột {@code to_status} (bản ghi mới).
     * Bản ghi CŨ chỉ có {@code detail} dạng "Đang làm → Kiểm thử" nên phải đọc ngược từ nhãn,
     * nếu không mọi số liệu lịch sử sẽ bằng 0. Nhãn không khớp → null (bỏ qua, không đoán bừa).
     */
    private static TaskStatus toStatusOf(com.bpm.domain.project.TaskActivity act) {
        if (act.getToStatus() != null) {
            try {
                return TaskStatus.valueOf(act.getToStatus());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        String d = act.getDetail();
        if (d == null) {
            return null;
        }
        int arrow = d.lastIndexOf('→');
        if (arrow < 0) {
            return null;
        }
        String label = d.substring(arrow + 1).trim();
        int paren = label.indexOf('(');           // bỏ "(tự tổng hợp từ task con)"
        if (paren >= 0) {
            label = label.substring(0, paren).trim();
        }
        // Nhận CẢ nhãn tiếng Việt (bản ghi trước khi đổi giao diện sang tiếng Anh) lẫn nhãn mới.
        switch (label) {
            case "Backlog": return TaskStatus.BACKLOG;
            case "Cần làm": case "To Do": return TaskStatus.TODO;
            case "Đang làm": case "In Progress": return TaskStatus.IN_PROGRESS;
            case "Kiểm thử": case "Testing": return TaskStatus.IN_REVIEW;
            case "Hoàn thành": case "Done": return TaskStatus.DONE;
            case "Huỷ": case "Cancelled": return TaskStatus.CANCELLED;
            default: return null;
        }
    }

    /**
     * Người giữ vai KIỂM THỬ của một task. Dữ liệu cũ (bug/issue chuyển Kiểm thử trước khi có
     * quy tắc tự gán tester = người log) chưa có testerUserId → lùi về người log để không mất số liệu.
     */
    private static String testerUserIdOf(ProjectTask t) {
        if (t.getTesterUserId() != null) {
            return t.getTesterUserId();
        }
        return (t.getType() == TaskType.BUG || t.getType() == TaskType.ISSUE) ? t.getReporterUserId() : null;
    }

    static String ownerUserId(ProjectTask t) {
        if (t.getStatus() == TaskStatus.IN_REVIEW) {
            if (t.getType() == TaskType.BUG || t.getType() == TaskType.ISSUE) {
                if (t.getReporterUserId() != null) {
                    return t.getReporterUserId();
                }
            } else if (t.getTesterUserId() != null) {
                return t.getTesterUserId();
            }
        }
        return t.getAssigneeUserId();
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
    /** % hoàn thành — dùng chung quy tắc ở {@link TaskProgress} để không lệch với Tổng quan. */
    private double completionPct(List<ProjectTask> tasks, Set<String> parentIds) {
        return TaskProgress.completion(tasks, parentIds);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
