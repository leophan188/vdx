package com.bpm.api.dto;

import java.util.List;

/** Số liệu bảng điều khiển vận hành (Epic 4 — Story 4.2/4.3). */
public class DashboardDto {

    /** Một dòng thống kê theo quy trình. */
    public record ProcessStat(String processName, long running, long completed) {
    }

    /** Tổng quan vận hành. */
    public record Summary(long totalInstances, long running, long completed, long cancelled,
                          long openTasks, long overdueTasks, List<ProcessStat> byProcess) {
    }

    /** Tải công việc của một người (Story 4.3 "Ai đang làm gì"). */
    public record WorkloadItem(String user, long openTasks, long overdueTasks) {
    }
}
