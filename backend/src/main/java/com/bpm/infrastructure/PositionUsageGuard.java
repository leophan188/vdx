package com.bpm.infrastructure;

import com.bpm.domain.org.OrgUnitUsageGuard;
import org.springframework.stereotype.Component;

/** Chặn xóa đơn vị còn vị trí (AC-5 Story 1.2 + 1.3). Implement port của org (AD-12). */
@Component
public class PositionUsageGuard implements OrgUnitUsageGuard {

    private final PositionRepository positionRepository;

    public PositionUsageGuard(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    @Override
    public boolean isUnitInUse(String orgUnitId) {
        return positionRepository.existsByOrgUnitId(orgUnitId);
    }

    @Override
    public String reason() {
        return "Đơn vị còn vị trí/chức danh — xóa/di chuyển vị trí trước";
    }
}
