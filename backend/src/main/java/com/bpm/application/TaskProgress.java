package com.bpm.application;

import com.bpm.domain.project.ProjectTask;
import com.bpm.domain.project.TaskStatus;
import com.bpm.domain.project.TaskType;

import java.util.List;
import java.util.Set;

/**
 * QUY TẮC TÍNH % HOÀN THÀNH — nguồn sự thật DUY NHẤT.
 *
 * <p>Trước đây bốn nơi tự tính lấy ({@code ProjectService.completionPct}, {@code ProjectService.report},
 * {@code ProjectReportService.completionPct}, {@code ProjectTaskService.computeProgress}) nên chỉ cần
 * sửa một chỗ là số liệu giữa các màn lệch nhau. Mọi thay đổi quy tắc từ nay chỉ sửa ở đây.
 *
 * <h3>Ba quy tắc</h3>
 * <ol>
 *   <li><b>Chỉ đếm VIỆC THỰC THI</b>: task LÁ và không phải Epic/Story. Epic/Story là cấp NHÓM —
 *       kể cả khi chưa có con (lá về mặt cây) cũng không phải việc làm được, để lọt vào là
 *       est của một Story rỗng sẽ chui thẳng vào mẫu số.</li>
 *   <li><b>Trọng số = giờ hiệu lực</b>: ưu tiên est đã nhập; chưa nhập thì suy từ số ngày công
 *       (T2–T6) trong [bắt đầu, kết thúc] × 8h. Nhờ vậy task chưa ước lượng vẫn có sức nặng
 *       thay vì tàng hình với %.</li>
 *   <li><b>Điểm theo trạng thái</b>: Hoàn thành = 1.0, Kiểm thử = 0.8 (dev đã xong phần mình,
 *       chỉ còn nghiệm thu), còn lại = 0. Huỷ bị loại khỏi CẢ tử số lẫn mẫu số.</li>
 * </ol>
 */
final class TaskProgress {

    /** Điểm của task ở trạng thái Kiểm thử — dev xong phần mình, còn chờ nghiệm thu. */
    static final double IN_REVIEW_FACTOR = 0.8;

    private TaskProgress() {
    }

    /** Task này có phải VIỆC THỰC THI để đưa vào % không (lá, không Epic/Story, không Huỷ)? */
    static boolean countable(ProjectTask t, Set<String> parentIds) {
        if (parentIds.contains(t.getId())) {
            return false; // task cha — số của nó là tổng hợp từ con
        }
        if (t.getType() == TaskType.EPIC || t.getType() == TaskType.STORY) {
            return false; // cấp nhóm, kể cả khi chưa có con
        }
        return t.getStatus() != TaskStatus.CANCELLED; // Huỷ = ngoài phạm vi
    }

    /** Trọng số giờ: est đã nhập, hoặc suy từ ngày công × 8h khi chưa nhập. */
    static double weight(ProjectTask t) {
        return t.effectiveHours();
    }

    /** Điểm hoàn thành 0..1 theo trạng thái. */
    static double factor(TaskStatus s) {
        if (s == TaskStatus.DONE) {
            return 1.0;
        }
        return s == TaskStatus.IN_REVIEW ? IN_REVIEW_FACTOR : 0.0;
    }

    static double factor(ProjectTask t) {
        return factor(t.getStatus());
    }

    /**
     * % hoàn thành (0..100) của một tập task.
     * Có trọng số giờ thì tính theo giờ; toàn bộ trọng số = 0 thì rơi về đếm đầu việc để
     * không trả về 0% một cách vô lý khi dự án chưa ai ước lượng gì.
     */
    static double completion(List<ProjectTask> tasks, Set<String> parentIds) {
        double totalW = 0, doneW = 0;
        int n = 0;
        double scored = 0;
        for (ProjectTask t : tasks) {
            if (!countable(t, parentIds)) {
                continue;
            }
            double w = weight(t);
            double f = factor(t);
            totalW += w;
            doneW += w * f;
            n++;
            scored += f;
        }
        if (n == 0) {
            return 0.0;
        }
        double pct = totalW > 0 ? (doneW / totalW) * 100.0 : (scored / n) * 100.0;
        return Math.round(pct * 100.0) / 100.0;
    }
}
