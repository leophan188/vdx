package com.bpm.application;

import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.document.Document;
import com.bpm.domain.document.DocumentVersion;
import com.bpm.infrastructure.DocumentRepository;
import com.bpm.infrastructure.DocumentVersionRepository;
import com.bpm.infrastructure.onlyoffice.OnlyOfficeJwt;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tài liệu dự thảo OnlyOffice (Story 3.10): tạo từ template trống, cấp config editor (ký JWT),
 * nhận callback lưu → tăng phiên bản. url/callback dùng host.docker.internal để OnlyOffice (trong VM) gọi ngược.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository repo;
    private final DocumentVersionRepository versionRepo;
    private final OnlyOfficeJwt jwt;
    private final AuditPort auditPort;
    private final String onlyOfficeUrl;
    private final String callbackHost;

    public DocumentService(DocumentRepository repo, DocumentVersionRepository versionRepo, OnlyOfficeJwt jwt,
                           AuditPort auditPort,
                           @Value("${bpm.onlyoffice.url}") String onlyOfficeUrl,
                           @Value("${bpm.onlyoffice.callback-host}") String callbackHost) {
        this.repo = repo;
        this.versionRepo = versionRepo;
        this.jwt = jwt;
        this.auditPort = auditPort;
        this.onlyOfficeUrl = stripSlash(onlyOfficeUrl);
        this.callbackHost = stripSlash(callbackHost);
    }

    @Transactional
    public Document create(String name, String instanceId, String actor) {
        byte[] blank = readTemplate();
        Document d = repo.save(new Document(name, instanceId, blank, actor));
        versionRepo.save(new DocumentVersion(d.getId(), d.getVersion(), blank, actor));
        auditPort.record("DOCUMENT_CREATED", "Document", d.getId(), actor, "name=" + name);
        return d;
    }

    /**
     * Lấy (hoặc tạo mới) tài liệu OnlyOffice gắn với 1 BƯỚC của phiên chạy — mỗi bước 1 file riêng.
     * templateId != null: sao chép NỘI DUNG của tài liệu mẫu đó làm điểm bắt đầu; ngược lại dùng trang trắng.
     */
    @Transactional
    public Document getOrCreateForStep(String instanceId, String stepKey, String name, String templateId, String actor) {
        return repo.findFirstByInstanceIdAndStepKey(instanceId, stepKey).orElseGet(() -> {
            byte[] content = (templateId != null && !templateId.isBlank())
                    ? repo.findById(templateId).map(Document::getContent).orElseGet(this::readTemplate)
                    : readTemplate();
            Document d = new Document(name, instanceId, content, actor);
            d.setStepKey(stepKey);
            d = repo.save(d);
            versionRepo.save(new DocumentVersion(d.getId(), d.getVersion(), content, actor));
            auditPort.record("DOCUMENT_CREATED", "Document", d.getId(), actor,
                    "step=" + stepKey + " instance=" + instanceId + " tpl=" + templateId);
            return d;
        });
    }

    @Transactional(readOnly = true)
    public List<Document> list() {
        return repo.findAllByOrderByUpdatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Document get(String id) {
        return repo.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài liệu"));
    }

    @Transactional(readOnly = true)
    public List<DocumentVersion> versions(String id) {
        return versionRepo.findByDocumentIdOrderByVersionDesc(id);
    }

    /** Ghi nhận kết quả ký ban hành (Story 3.16). */
    @Transactional
    public Document sign(String id, String number, String actor) {
        Document d = get(id);
        d.sign(number, actor);
        repo.save(d);
        auditPort.record("DOCUMENT_SIGNED", "Document", id, actor, "number=" + number);
        return d;
    }

    /** Đóng hồ sơ + metadata lưu trữ (Story 3.17). */
    @Transactional
    public Document archive(String id, String archiveNo, String classification, Integer retentionYears, String actor) {
        Document d = get(id);
        d.archive(archiveNo, classification, retentionYears);
        repo.save(d);
        auditPort.record("DOCUMENT_ARCHIVED", "Document", id, actor,
                "archiveNo=" + archiveNo + ", retention=" + retentionYears + "y");
        return d;
    }

    /** Bytes nội dung hiện tại (OnlyOffice tải qua endpoint /content). */
    @Transactional(readOnly = true)
    public byte[] content(String id) {
        return get(id).getContent();
    }

    /** Token tải nội dung (JWT ngắn) — endpoint /content công khai kiểm token này. */
    public String contentToken(String id) {
        return jwt.sign(Map.of("docId", id, "scope", "download"));
    }

    public void verifyContentToken(String id, String token) {
        JsonNode p = jwt.verify(token);
        if (!id.equals(p.path("docId").asText()) || !"download".equals(p.path("scope").asText())) {
            throw new SecurityException("Token tải tài liệu không hợp lệ");
        }
    }

    /** Config cho DocsAPI.DocEditor (đã ký JWT) + URL Document Server cho FE nhúng api.js. */
    @Transactional(readOnly = true)
    public Map<String, Object> editorConfig(String id, String userId, String displayName, boolean canEdit) {
        Document d = get(id);
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("fileType", "docx");
        document.put("key", d.getDocKey());
        document.put("title", d.getName().endsWith(".docx") ? d.getName() : d.getName() + ".docx");
        document.put("url", callbackHost + "/api/v1/documents/" + id + "/content?token=" + contentToken(id));
        Map<String, Object> perms = new LinkedHashMap<>();
        perms.put("edit", canEdit);
        document.put("permissions", perms);

        Map<String, Object> editorConfig = new LinkedHashMap<>();
        editorConfig.put("mode", canEdit ? "edit" : "view");
        editorConfig.put("lang", "vi");
        editorConfig.put("callbackUrl", callbackHost + "/api/v1/documents/" + id + "/callback");
        editorConfig.put("user", Map.of("id", userId == null ? "anonymous" : userId,
                "name", displayName == null ? "Người dùng" : displayName));
        // Bật nút Lưu + Ctrl+S ghi thẳng vào kho (status=6 ForceSave) — không chờ tới khi đóng editor.
        Map<String, Object> customization = new LinkedHashMap<>();
        customization.put("forcesave", true);
        customization.put("autosave", true);
        editorConfig.put("customization", customization);

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("document", document);
        config.put("documentType", "word");
        config.put("editorConfig", editorConfig);
        config.put("token", jwt.sign(config)); // OnlyOffice yêu cầu token = JWT của chính config

        return Map.of("documentServerUrl", onlyOfficeUrl, "config", config,
                "name", d.getName(), "version", d.getVersion());
    }

    /**
     * Xử lý callback từ OnlyOffice. status=2 (MustSave) hoặc 6 (ForceSave) → tải bản đã sửa về, lưu phiên bản mới.
     * Body có "token" (JWT của payload khi JWT bật) → kiểm chữ ký rồi dùng payload đã xác thực.
     */
    @Transactional
    public void handleCallback(String id, JsonNode body, String actor) {
        JsonNode payload = body;
        if (body.has("token")) {
            payload = jwt.verify(body.get("token").asText());
        }
        int status = payload.path("status").asInt(0);
        if (status != 2 && status != 6) {
            return; // 1=đang mở, 4=đóng không sửa, ... → không cần lưu
        }
        String url = payload.path("url").asText(null);
        if (url == null || url.isBlank()) {
            log.warn("[onlyoffice] callback status={} nhưng thiếu url", status);
            return;
        }
        byte[] updated = download(rewriteHost(url));
        Document d = get(id);
        d.saveNewContent(updated, actor);
        repo.save(d);
        versionRepo.save(new DocumentVersion(d.getId(), d.getVersion(), updated, actor));
        auditPort.record("DOCUMENT_SAVED", "Document", d.getId(), actor, "version=" + d.getVersion());
        log.info("[onlyoffice] đã lưu tài liệu {} → v{}", d.getName(), d.getVersion());
    }

    // ===== helpers =====

    private byte[] readTemplate() {
        try (InputStream in = new ClassPathResource("onlyoffice/blank.docx").getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Không đọc được template blank.docx", e);
        }
    }

    /** OnlyOffice trả url tải bản đã sửa theo địa chỉ NỘI BỘ của nó → đổi host về địa chỉ host truy cập được. */
    private String rewriteHost(String url) {
        try {
            URI u = URI.create(url);
            String pathQuery = u.getRawPath() + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
            return onlyOfficeUrl + pathQuery;
        } catch (Exception e) {
            return url;
        }
    }

    private byte[] download(String url) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<byte[]> resp = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new IllegalStateException("Tải tài liệu đã sửa lỗi HTTP " + resp.statusCode());
            }
            return resp.body();
        } catch (Exception e) {
            throw new IllegalStateException("Không tải được tài liệu đã sửa từ OnlyOffice: " + e.getMessage(), e);
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
