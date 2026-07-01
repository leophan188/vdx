package com.bpm.api;

import com.bpm.api.dto.HrHighlightsDto;
import com.bpm.application.HrHighlightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Điểm nhấn nhân sự cho trang chủ (Việc B + C). /api/v1/hr/highlights — mọi user đăng nhập
 * (matcher SecurityConfig: '/api/v1/hr/** = authenticated').
 * Trả sinh nhật hôm nay + sắp onboard; đồng thời nhắc onboarding trước 7 ngày (best-effort, dedup).
 */
@RestController
@RequestMapping("/api/v1/hr")
public class HrHighlightsController {

    private final HrHighlightsService service;

    public HrHighlightsController(HrHighlightsService service) {
        this.service = service;
    }

    @GetMapping("/highlights")
    public HrHighlightsDto highlights() {
        return service.highlights();
    }
}
