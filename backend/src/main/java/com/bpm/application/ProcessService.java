package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.process.ProcessDefinition;
import com.bpm.domain.process.ProcessStatus;
import com.bpm.domain.process.ProcessVersion;
import com.bpm.infrastructure.ProcessDefinitionRepository;
import com.bpm.infrastructure.ProcessVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Quản trị định nghĩa quy trình (Story 2.1). Lưu định nghĩa BPMN + metadata bước, cấu hình động
 * (không deploy lại — NFR-09). Mọi mutation qua AuditPort (AD-6). Publish/retire ở Story 2.4.
 */
@Service
public class ProcessService {

    private final ProcessDefinitionRepository repo;
    private final ProcessVersionRepository versionRepo;
    private final AuditPort auditPort;

    public ProcessService(ProcessDefinitionRepository repo, ProcessVersionRepository versionRepo, AuditPort auditPort) {
        this.repo = repo;
        this.versionRepo = versionRepo;
        this.auditPort = auditPort;
    }

    @Transactional
    public ProcessDefinition create(String processKey, String name, String actor) {
        if (repo.existsByProcessKey(processKey)) {
            throw new IllegalArgumentException("Mã quy trình đã tồn tại");
        }
        ProcessDefinition p = repo.save(new ProcessDefinition(processKey, name));
        auditPort.record("PROCESS_CREATED", "ProcessDefinition", p.getId(), actor,
                "key=" + processKey + ", name=" + name);
        return p;
    }

    @Transactional(readOnly = true)
    public List<ProcessDefinition> list() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public ProcessDefinition get(String id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy quy trình"));
    }

    @Transactional
    public ProcessDefinition rename(String id, String name, String actor) {
        ProcessDefinition p = get(id);
        p.setName(name);
        p.touch();
        repo.save(p);
        auditPort.record("PROCESS_RENAMED", "ProcessDefinition", id, actor, "name=" + name);
        return p;
    }

    /** Lưu định nghĩa từ designer: XML BPMN + metadata bước (JSON). Cấu hình động, không deploy lại. */
    @Transactional
    public ProcessDefinition saveDesign(String id, String bpmnXml, String stepsMetaJson, String actor) {
        ProcessDefinition p = get(id);
        p.setBpmnXml(bpmnXml);
        p.setStepsMetaJson(stepsMetaJson);
        p.touch();
        repo.save(p);
        auditPort.record("PROCESS_DESIGN_SAVED", "ProcessDefinition", id, actor, "key=" + p.getProcessKey());
        return p;
    }

    /**
     * Ban hành: snapshot bản nháp hiện tại thành phiên bản BẤT BIẾN mới (publishedVersion+1),
     * đặt quy trình về PUBLISHED. Instance đang chạy giữ phiên bản cũ; bản mới nhất khởi tạo nhiệm vụ mới.
     */
    @Transactional
    public ProcessVersion publish(String id, String actor) {
        ProcessDefinition p = get(id);
        if (p.getBpmnXml() == null || p.getBpmnXml().isBlank()) {
            throw new IllegalStateException("Chưa có sơ đồ để ban hành");
        }
        int newVer = p.getPublishedVersion() + 1;
        ProcessVersion v = versionRepo.save(new ProcessVersion(p.getId(), newVer, p.getBpmnXml(), p.getStepsMetaJson(), actor));
        p.setPublishedVersion(newVer);
        p.setStatus(ProcessStatus.PUBLISHED);
        p.touch();
        repo.save(p);
        auditPort.record("PROCESS_PUBLISHED", "ProcessDefinition", id, actor, "version=v" + newVer);
        return v;
    }

    /** Ngừng dùng: phiên bản đang ban hành chuyển RETIRED; quy trình RETIRED (không khởi tạo nhiệm vụ mới). */
    @Transactional
    public void retire(String id, String actor) {
        ProcessDefinition p = get(id);
        versionRepo.findFirstByProcessIdAndStatusOrderByVersionDesc(id, ProcessStatus.PUBLISHED)
                .ifPresent(v -> {
                    v.setStatus(ProcessStatus.RETIRED);
                    versionRepo.save(v);
                });
        p.setStatus(ProcessStatus.RETIRED);
        p.touch();
        repo.save(p);
        auditPort.record("PROCESS_RETIRED", "ProcessDefinition", id, actor, "key=" + p.getProcessKey());
    }

    @Transactional(readOnly = true)
    public List<ProcessVersion> listVersions(String id) {
        return versionRepo.findByProcessIdOrderByVersionDesc(id);
    }

    @Transactional
    public void delete(String id, String actor) {
        ProcessDefinition p = get(id);
        versionRepo.deleteAll(versionRepo.findByProcessIdOrderByVersionDesc(id));
        repo.delete(p);
        auditPort.record("PROCESS_DELETED", "ProcessDefinition", id, actor, "key=" + p.getProcessKey());
    }
}
