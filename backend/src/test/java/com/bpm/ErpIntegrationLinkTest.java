package com.bpm;

import com.bpm.domain.erp.ErpIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tách tên model khỏi link Odoo người dùng dán vào. Model nằm SAU dấu #, tức phần trình duyệt không
 * gửi lên máy chủ — chỉ lấy được bằng cách đọc chính chuỗi đó.
 */
class ErpIntegrationLinkTest {

    @Test
    @DisplayName("Lấy được model từ link thật của ERP công ty")
    void tachModelTuLink() {
        assertThat(ErpIntegration.modelFromLink(
                "https://erp.vmo.dev/web#action=148&cids=1&menu_id=117&model=hr.attendance&view_type=list"))
                .isEqualTo("hr.attendance");
        // model đứng ngay sau dấu # cũng phải nhận
        assertThat(ErpIntegration.modelFromLink("https://erp.vmo.dev/web#model=project.project&view_type=list"))
                .isEqualTo("project.project");
    }

    @Test
    @DisplayName("Link không có model thì trả null để màn hình bắt người dùng gõ tay")
    void linkKhongCoModel() {
        assertThat(ErpIntegration.modelFromLink("https://erp.vmo.dev/web#action=148&view_type=list")).isNull();
        assertThat(ErpIntegration.modelFromLink("")).isNull();
        assertThat(ErpIntegration.modelFromLink(null)).isNull();
    }

    @Test
    @DisplayName("Gõ tay model thì giữ nguyên, không bị link ghi đè")
    void goTayModel() {
        ErpIntegration it = new ErpIntegration("PROJECTS");
        it.update("https://erp.vmo.dev/web#action=9&model=project.project", "project.task", true, "tester");
        assertThat(it.getModelName()).isEqualTo("project.task");
    }

    @Test
    @DisplayName("Bỏ trống model thì lấy từ link")
    void modelTrongThiLayTuLink() {
        ErpIntegration it = new ErpIntegration("ORG_EMPLOYEE");
        it.update("https://erp.vmo.dev/web#action=9&model=hr.employee&view_type=list", "  ", true, "tester");
        assertThat(it.getModelName()).isEqualTo("hr.employee");
        assertThat(it.isEnabled()).isTrue();
    }
}
