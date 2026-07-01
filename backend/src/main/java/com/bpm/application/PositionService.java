package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.position.Position;
import com.bpm.domain.position.PositionAssignment;
import com.bpm.infrastructure.OrgUnitRepository;
import com.bpm.infrastructure.PositionAssignmentRepository;
import com.bpm.infrastructure.PositionRepository;
import com.bpm.infrastructure.PositionRoleRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Quản trị vị trí/chức danh & nhiệm kỳ giữ vị trí (FR-C02, AD-14).
 * Mỗi vị trí tối đa một người giữ hiện hành; gán người mới đóng nhiệm kỳ cũ. Audit mọi mutation (AD-6).
 */
@Service
public class PositionService {

    private final PositionRepository positionRepo;
    private final PositionAssignmentRepository assignmentRepo;
    private final PositionRoleRepository positionRoleRepo;
    private final OrgUnitRepository orgUnitRepo;
    private final UserAccountRepository userRepo;
    private final AuditPort auditPort;

    public PositionService(PositionRepository positionRepo, PositionAssignmentRepository assignmentRepo,
                           PositionRoleRepository positionRoleRepo, OrgUnitRepository orgUnitRepo,
                           UserAccountRepository userRepo, AuditPort auditPort) {
        this.positionRepo = positionRepo;
        this.assignmentRepo = assignmentRepo;
        this.positionRoleRepo = positionRoleRepo;
        this.orgUnitRepo = orgUnitRepo;
        this.userRepo = userRepo;
        this.auditPort = auditPort;
    }

    @Transactional
    public Position create(String title, String orgUnitId, String actor) {
        if (!orgUnitRepo.existsById(orgUnitId)) {
            throw new IllegalArgumentException("Đơn vị không tồn tại");
        }
        Position p = positionRepo.save(new Position(title, orgUnitId));
        auditPort.record("POSITION_CREATED", "Position", p.getId(), actor, "title=" + title + ", unit=" + orgUnitId);
        return p;
    }

    /** Đổi tên vị trí. */
    @Transactional
    public Position updateTitle(String positionId, String title, String actor) {
        Position p = positionRepo.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí"));
        p.setTitle(title);
        positionRepo.save(p);
        auditPort.record("POSITION_UPDATED", "Position", positionId, actor, "title=" + title);
        return p;
    }

    /** Xóa vị trí — chặn nếu đang có người giữ; dọn các gán vai trò của vị trí. */
    @Transactional
    public void delete(String positionId, String actor) {
        Position p = positionRepo.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí"));
        if (p.getCurrentHolderUserId() != null) {
            throw new IllegalStateException("Vị trí đang có người giữ — gỡ người giữ trước khi xóa");
        }
        positionRoleRepo.deleteAll(positionRoleRepo.findByPositionId(positionId));
        positionRepo.delete(p);
        auditPort.record("POSITION_DELETED", "Position", positionId, actor, "title=" + p.getTitle());
    }

    @Transactional(readOnly = true)
    public List<Position> byOrgUnit(String orgUnitId) {
        return positionRepo.findByOrgUnitId(orgUnitId);
    }

    @Transactional(readOnly = true)
    public List<Position> all() {
        return positionRepo.findAll();
    }

    /** Gán/đổi người giữ: đóng nhiệm kỳ hiện hành (nếu có), mở nhiệm kỳ mới, cập nhật người hiện hành. */
    @Transactional
    public void assignHolder(String positionId, String userId, String actor) {
        Position p = positionRepo.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí"));
        if (!userRepo.existsById(userId)) {
            throw new IllegalArgumentException("Tài khoản không tồn tại");
        }
        assignmentRepo.findByPositionIdAndEndedAtIsNull(positionId).ifPresent(current -> {
            current.end();
            assignmentRepo.save(current);
        });
        assignmentRepo.save(new PositionAssignment(positionId, userId));
        p.setCurrentHolderUserId(userId);
        positionRepo.save(p);
        auditPort.record("POSITION_ASSIGNED", "Position", positionId, actor, "holder=" + userId);
    }

    @Transactional(readOnly = true)
    public String currentHolder(String positionId) {
        return positionRepo.findById(positionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vị trí"))
                .getCurrentHolderUserId();
    }
}
