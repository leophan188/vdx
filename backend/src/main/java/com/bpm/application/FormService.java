package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.form.FormDefinition;
import com.bpm.domain.form.FormVersion;
import com.bpm.domain.process.ProcessStatus;
import com.bpm.infrastructure.FormDefinitionRepository;
import com.bpm.infrastructure.FormVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Quản trị biểu mẫu động (Story 2.6). Schema trường lưu JSON; cấu hình động, không deploy lại (NFR-09).
 * Mọi mutation qua AuditPort (AD-6). Versioning/snapshot ở Story 2.10.
 */
@Service
public class FormService {

    private final FormDefinitionRepository repo;
    private final FormVersionRepository versionRepo;
    private final AuditPort auditPort;

    public FormService(FormDefinitionRepository repo, FormVersionRepository versionRepo, AuditPort auditPort) {
        this.repo = repo;
        this.versionRepo = versionRepo;
        this.auditPort = auditPort;
    }

    @Transactional
    public FormDefinition create(String formKey, String name, String actor) {
        return create(formKey, name, null, actor);
    }

    /** Tạo biểu mẫu mới; nếu copyFromId có giá trị → sao chép schema trường từ biểu mẫu nguồn. */
    @Transactional
    public FormDefinition create(String formKey, String name, String copyFromId, String actor) {
        if (repo.existsByFormKey(formKey)) {
            throw new IllegalArgumentException("Mã biểu mẫu đã tồn tại");
        }
        FormDefinition f = new FormDefinition(formKey, name);
        String extra = "key=" + formKey + ", name=" + name;
        if (copyFromId != null && !copyFromId.isBlank()) {
            FormDefinition src = get(copyFromId);
            f.setSchemaJson(src.getSchemaJson());
            extra += ", copyFrom=" + src.getFormKey();
        }
        f = repo.save(f);
        auditPort.record("FORM_CREATED", "FormDefinition", f.getId(), actor, extra);
        return f;
    }

    @Transactional(readOnly = true)
    public List<FormDefinition> list() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public FormDefinition get(String id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy biểu mẫu"));
    }

    @Transactional
    public FormDefinition rename(String id, String name, String actor) {
        FormDefinition f = get(id);
        f.setName(name);
        f.touch();
        repo.save(f);
        auditPort.record("FORM_RENAMED", "FormDefinition", id, actor, "name=" + name);
        return f;
    }

    /** Lưu schema từ form builder (JSON các trường). */
    @Transactional
    public FormDefinition saveSchema(String id, String schemaJson, String actor) {
        FormDefinition f = get(id);
        f.setSchemaJson(schemaJson);
        f.touch();
        repo.save(f);
        auditPort.record("FORM_SCHEMA_SAVED", "FormDefinition", id, actor, "key=" + f.getFormKey());
        return f;
    }

    /** Ban hành: snapshot schema hiện tại thành phiên bản bất biến mới (AD-3). */
    @Transactional
    public FormVersion publish(String id, String actor) {
        FormDefinition f = get(id);
        if (f.getSchemaJson() == null || f.getSchemaJson().isBlank()) {
            throw new IllegalStateException("Chưa có trường nào để ban hành");
        }
        int newVer = f.getPublishedVersion() + 1;
        FormVersion v = versionRepo.save(new FormVersion(f.getId(), newVer, f.getSchemaJson(), actor));
        f.setPublishedVersion(newVer);
        f.setStatus(ProcessStatus.PUBLISHED);
        f.touch();
        repo.save(f);
        auditPort.record("FORM_PUBLISHED", "FormDefinition", id, actor, "version=v" + newVer);
        return v;
    }

    @Transactional
    public void retire(String id, String actor) {
        FormDefinition f = get(id);
        versionRepo.findFirstByFormIdAndStatusOrderByVersionDesc(id, ProcessStatus.PUBLISHED)
                .ifPresent(v -> {
                    v.setStatus(ProcessStatus.RETIRED);
                    versionRepo.save(v);
                });
        f.setStatus(ProcessStatus.RETIRED);
        f.touch();
        repo.save(f);
        auditPort.record("FORM_RETIRED", "FormDefinition", id, actor, "key=" + f.getFormKey());
    }

    @Transactional(readOnly = true)
    public List<FormVersion> listVersions(String id) {
        return versionRepo.findByFormIdOrderByVersionDesc(id);
    }

    @Transactional
    public void delete(String id, String actor) {
        FormDefinition f = get(id);
        versionRepo.deleteAll(versionRepo.findByFormIdOrderByVersionDesc(id));
        repo.delete(f);
        auditPort.record("FORM_DELETED", "FormDefinition", id, actor, "key=" + f.getFormKey());
    }
}
