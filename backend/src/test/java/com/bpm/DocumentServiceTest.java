package com.bpm;

import com.bpm.application.DocumentService;
import com.bpm.domain.document.Document;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DocumentServiceTest {

    @Autowired DocumentService svc;
    @Autowired ObjectMapper om;

    @Test
    void create_makesV1_fromBlankTemplate() {
        Document d = svc.create("Dự thảo công văn", null, "tester");
        assertThat(d.getVersion()).isEqualTo(1);
        assertThat(svc.content(d.getId())).isNotEmpty();         // có nội dung .docx
        assertThat(svc.versions(d.getId())).hasSize(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void editorConfig_signedToken_urlsUseCallbackHost() {
        Document d = svc.create("Doc", null, "tester");
        Map<String, Object> res = svc.editorConfig(d.getId(), "u1", "Người 1", true);
        assertThat(res).containsKeys("documentServerUrl", "config");

        Map<String, Object> config = (Map<String, Object>) res.get("config");
        assertThat(config).containsKey("token");
        Map<String, Object> doc = (Map<String, Object>) config.get("document");
        assertThat((String) doc.get("url")).contains("/api/v1/documents/" + d.getId() + "/content?token=");
        Map<String, Object> ec = (Map<String, Object>) config.get("editorConfig");
        assertThat((String) ec.get("callbackUrl")).endsWith("/api/v1/documents/" + d.getId() + "/callback");
    }

    @Test
    void contentToken_roundTrip_rejectsTampered() {
        Document d = svc.create("Doc", null, "tester");
        String tok = svc.contentToken(d.getId());
        svc.verifyContentToken(d.getId(), tok); // hợp lệ → không ném
        assertThatThrownBy(() -> svc.verifyContentToken(d.getId(), tok + "x"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void callback_nonSaveStatus_isNoop() throws Exception {
        Document d = svc.create("Doc", null, "tester");
        svc.handleCallback(d.getId(), om.readTree("{\"status\":1}"), "onlyoffice");
        assertThat(svc.get(d.getId()).getVersion()).isEqualTo(1); // status=1 (đang mở) → không lưu
    }

    @Test
    void sign_thenArchive_statusTransitions() {
        Document d = svc.create("Quyết định", null, "tester");
        assertThat(d.getStatus()).isEqualTo("DRAFT");

        // chưa ký → không đóng hồ sơ được (3.17 guard)
        assertThatThrownBy(() -> svc.archive(d.getId(), "LT-01", "Thường", 10, "tester"))
                .isInstanceOf(IllegalStateException.class);

        Document signed = svc.sign(d.getId(), "15/2026/QĐ-ONE", "lanhdao");
        assertThat(signed.getStatus()).isEqualTo("SIGNED");
        assertThat(signed.getSignedNumber()).isEqualTo("15/2026/QĐ-ONE");

        Document archived = svc.archive(d.getId(), "HS-2026-001", "Mật", 20, "vanthu");
        assertThat(archived.getStatus()).isEqualTo("ARCHIVED");
        assertThat(archived.getRetentionYears()).isEqualTo(20);
    }
}
