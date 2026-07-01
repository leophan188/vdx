package com.bpm.api;

import com.bpm.api.dto.SearchDto;
import com.bpm.application.SearchService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tìm kiếm toàn cục (quick-jump topbar) — GET /api/v1/search?q=...
 * Chặn = authenticated ở SecurityConfig; phân quyền theo nhóm xử lý trong SearchService (đọc authorities).
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public SearchDto search(@RequestParam(name = "q", required = false, defaultValue = "") String q,
                            Authentication auth) {
        return service.search(q, auth);
    }
}
