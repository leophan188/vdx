package com.bpm.api;

import com.bpm.api.dto.DashboardDto;
import com.bpm.api.dto.PmHrDashboardDto.PmHrDashboard;
import com.bpm.application.PmHrDashboardService;
import com.bpm.application.WorkflowService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Bảng điều khiển — KHỐI cũ (vận hành quy trình, Epic 4) GIỮ NGUYÊN để không vỡ;
 * THÊM endpoint TỔNG HỢP mới {@code /pm-hr} tập trung DỰ ÁN + NHÂN SỰ (mini-Jira + HR).
 * Toàn bộ {@code /api/v1/dashboard/**} đã bảo vệ bằng FEAT_REPORTS (SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final WorkflowService workflow;
    private final PmHrDashboardService pmHr;

    public DashboardController(WorkflowService workflow, PmHrDashboardService pmHr) {
        this.workflow = workflow;
        this.pmHr = pmHr;
    }

    /** Số liệu tổng hợp DỰ ÁN + NHÂN SỰ (cho Bảng điều khiển + Báo cáo). */
    @GetMapping("/pm-hr")
    public PmHrDashboard pmHr() {
        return pmHr.build();
    }

    @GetMapping("/summary")
    public DashboardDto.Summary summary() {
        return workflow.dashboardSummary();
    }

    @GetMapping("/workload")
    public List<DashboardDto.WorkloadItem> workload() {
        return workflow.workload();
    }
}
