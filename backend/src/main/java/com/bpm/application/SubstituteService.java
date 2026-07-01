package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.position.Substitute;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.SubstituteRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Người thay thế cho vị trí khi giữ vắng (FR-C04). Đặt người thay thế làm việc MỚI giao theo vị trí
 * resolve về người thay thế; gỡ thì quay lại người giữ. Audit mọi thay đổi (AD-6).
 */
@Service
public class SubstituteService {

    private final SubstituteRepository substituteRepo;
    private final PositionRepository positionRepo;
    private final UserAccountRepository userRepo;
    private final AuditPort auditPort;

    public SubstituteService(SubstituteRepository substituteRepo, PositionRepository positionRepo,
                             UserAccountRepository userRepo, AuditPort auditPort) {
        this.substituteRepo = substituteRepo;
        this.positionRepo = positionRepo;
        this.userRepo = userRepo;
        this.auditPort = auditPort;
    }

    /** Đặt/đổi người thay thế đang bật cho vị trí — đóng bản active cũ (nếu có), mở bản mới. */
    @Transactional
    public Substitute setSubstitute(String positionId, String substituteUserId, String actor) {
        var position = positionRepo.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí"));
        if (!userRepo.existsById(substituteUserId)) {
            throw new IllegalArgumentException("Tài khoản không tồn tại");
        }
        if (substituteUserId.equals(position.getCurrentHolderUserId())) {
            throw new IllegalArgumentException("Người thay thế không thể trùng người đang giữ vị trí");
        }
        substituteRepo.findByPositionIdAndActiveTrue(positionId).ifPresent(current -> {
            current.deactivate();
            substituteRepo.save(current);
        });
        Substitute s = substituteRepo.save(new Substitute(positionId, substituteUserId));
        auditPort.record("SUBSTITUTE_SET", "Position", positionId, actor, "substitute=" + substituteUserId);
        return s;
    }

    /** Gỡ người thay thế đang bật (nếu có). */
    @Transactional
    public void clearSubstitute(String positionId, String actor) {
        substituteRepo.findByPositionIdAndActiveTrue(positionId).ifPresent(current -> {
            current.deactivate();
            substituteRepo.save(current);
            auditPort.record("SUBSTITUTE_CLEARED", "Position", positionId, actor,
                    "substitute=" + current.getSubstituteUserId());
        });
    }

    @Transactional(readOnly = true)
    public Optional<Substitute> activeFor(String positionId) {
        return substituteRepo.findByPositionIdAndActiveTrue(positionId);
    }
}
