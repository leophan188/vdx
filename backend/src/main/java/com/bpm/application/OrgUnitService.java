package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.org.OrgUnit;
import com.bpm.domain.org.OrgUnitClosure;
import com.bpm.domain.org.OrgUnitUsageGuard;
import com.bpm.infrastructure.OrgUnitClosureRepository;
import com.bpm.infrastructure.OrgUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Quản trị cây cơ cấu tổ chức bằng closure-table (AD-7). Mọi mutation ghi audit (AD-6).
 */
@Service
public class OrgUnitService {

    private final OrgUnitRepository unitRepo;
    private final OrgUnitClosureRepository closureRepo;
    private final AuditPort auditPort;
    private final List<OrgUnitUsageGuard> usageGuards;

    public OrgUnitService(OrgUnitRepository unitRepo, OrgUnitClosureRepository closureRepo,
                          AuditPort auditPort, List<OrgUnitUsageGuard> usageGuards) {
        this.unitRepo = unitRepo;
        this.closureRepo = closureRepo;
        this.auditPort = auditPort;
        this.usageGuards = usageGuards;
    }

    @Transactional
    public OrgUnit create(String name, String parentId, String actor) {
        if (parentId != null && !unitRepo.existsById(parentId)) {
            throw new IllegalArgumentException("Đơn vị cha không tồn tại");
        }
        OrgUnit unit = unitRepo.save(new OrgUnit(name, parentId));
        // self link
        closureRepo.save(new OrgUnitClosure(unit.getId(), unit.getId(), 0));
        // sao chép mọi tổ tiên của cha, +1 depth
        if (parentId != null) {
            for (OrgUnitClosure a : closureRepo.findByDescendantId(parentId)) {
                closureRepo.save(new OrgUnitClosure(a.getAncestorId(), unit.getId(), a.getDepth() + 1));
            }
        }
        auditPort.record("ORG_UNIT_CREATED", "OrgUnit", unit.getId(), actor, "name=" + name + ", parent=" + parentId);
        return unit;
    }

    @Transactional
    public void rename(String id, String newName, String actor) {
        OrgUnit unit = unitRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị"));
        unit.setName(newName);
        unitRepo.save(unit);
        auditPort.record("ORG_UNIT_RENAMED", "OrgUnit", id, actor, "name=" + newName);
    }

    @Transactional
    public void move(String id, String newParentId, String actor) {
        OrgUnit unit = unitRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị"));
        if (newParentId != null) {
            if (!unitRepo.existsById(newParentId)) {
                throw new IllegalArgumentException("Đơn vị cha mới không tồn tại");
            }
            // Không cho di chuyển vào chính cây con của nó (tạo vòng)
            Set<String> subtree = closureRepo.findByAncestorId(id).stream()
                    .map(OrgUnitClosure::getDescendantId).collect(Collectors.toSet());
            if (subtree.contains(newParentId)) {
                throw new IllegalArgumentException("Không thể di chuyển đơn vị vào trong cây con của chính nó");
            }
        }

        // subtree(id): (descendantId, depth từ id)
        List<OrgUnitClosure> subtreeRows = closureRepo.findByAncestorId(id);
        Set<String> subtreeIds = subtreeRows.stream().map(OrgUnitClosure::getDescendantId).collect(Collectors.toSet());

        // Xóa liên kết từ tổ tiên CŨ (ngoài subtree) tới các hậu duệ trong subtree
        List<OrgUnitClosure> toDelete = subtreeIds.stream()
                .flatMap(d -> closureRepo.findByDescendantId(d).stream())
                .filter(row -> !subtreeIds.contains(row.getAncestorId()))
                .toList();
        closureRepo.deleteAll(toDelete);
        closureRepo.flush();

        // Chèn liên kết tới tổ tiên MỚI
        if (newParentId != null) {
            List<OrgUnitClosure> superAncestors = closureRepo.findByDescendantId(newParentId); // (A, depth A->P')
            for (OrgUnitClosure sa : superAncestors) {
                for (OrgUnitClosure st : subtreeRows) { // st: (D, depth id->D)
                    closureRepo.save(new OrgUnitClosure(
                            sa.getAncestorId(), st.getDescendantId(), sa.getDepth() + 1 + st.getDepth()));
                }
            }
        }

        unit.setParentId(newParentId);
        unitRepo.save(unit);
        auditPort.record("ORG_UNIT_MOVED", "OrgUnit", id, actor, "newParent=" + newParentId);
    }

    @Transactional
    public void delete(String id, String actor) {
        OrgUnit unit = unitRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn vị"));
        if (unitRepo.existsByParentId(id)) {
            throw new IllegalArgumentException("Đơn vị còn đơn vị con — xóa/di chuyển con trước");
        }
        for (OrgUnitUsageGuard guard : usageGuards) {
            if (guard.isUnitInUse(id)) {
                throw new IllegalArgumentException(guard.reason());
            }
        }
        // Xóa hàng closure liên quan id (self + liên kết tới tổ tiên)
        closureRepo.deleteAll(closureRepo.findByDescendantId(id));
        unitRepo.delete(unit);
        auditPort.record("ORG_UNIT_DELETED", "OrgUnit", id, actor, "name=" + unit.getName());
    }

    @Transactional(readOnly = true)
    public List<OrgUnit> all() {
        return unitRepo.findAll();
    }

    @Transactional(readOnly = true)
    public List<OrgUnit> roots() {
        return unitRepo.findByParentIdIsNull();
    }

    @Transactional(readOnly = true)
    public List<OrgUnit> children(String id) {
        return unitRepo.findByParentId(id);
    }

    /** Tổ tiên (không gồm self), gần→xa. */
    @Transactional(readOnly = true)
    public List<OrgUnit> ancestors(String id) {
        return closureRepo.findByDescendantId(id).stream()
                .filter(r -> r.getDepth() > 0)
                .sorted((a, b) -> Integer.compare(a.getDepth(), b.getDepth()))
                .map(r -> unitRepo.findById(r.getAncestorId()).orElseThrow())
                .toList();
    }

    /** Hậu duệ (không gồm self). */
    @Transactional(readOnly = true)
    public List<OrgUnit> descendants(String id) {
        return closureRepo.findByAncestorId(id).stream()
                .filter(r -> r.getDepth() > 0)
                .map(r -> unitRepo.findById(r.getDescendantId()).orElseThrow())
                .toList();
    }
}
