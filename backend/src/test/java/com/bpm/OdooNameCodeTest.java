package com.bpm;

import com.bpm.infrastructure.erp.OdooAttendanceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tên hiển thị nhân sự bên Odoo ghi kèm mã ("Đoàn Đình Đức - 4021"). Tách được mã thì đối soát ghép
 * theo mã thay vì theo tên — tên viết hoa/thường/thiếu dấu mỗi nơi một kiểu, và trùng tên là có thật.
 */
class OdooNameCodeTest {

    @Test
    @DisplayName("Tách tên và mã từ tên hiển thị của Odoo")
    void tachTenVaMa() {
        assertThat(OdooAttendanceClient.nameOf("Đoàn Đình Đức - 4021")).isEqualTo("Đoàn Đình Đức");
        assertThat(OdooAttendanceClient.codeOf("Đoàn Đình Đức - 4021")).isEqualTo("4021");
        // Tên có dấu gạch ngang bên trong vẫn phải giữ nguyên, chỉ đuôi cùng mới là mã.
        assertThat(OdooAttendanceClient.nameOf("Nguyễn Thị Mai - Anh - 4426")).isEqualTo("Nguyễn Thị Mai - Anh");
        assertThat(OdooAttendanceClient.codeOf("Nguyễn Thị Mai - Anh - 4426")).isEqualTo("4426");
    }

    @Test
    @DisplayName("Không có mã thì giữ nguyên tên, mã trả null")
    void khongCoMa() {
        assertThat(OdooAttendanceClient.nameOf("Trần Văn B")).isEqualTo("Trần Văn B");
        assertThat(OdooAttendanceClient.codeOf("Trần Văn B")).isNull();
        assertThat(OdooAttendanceClient.codeOf(null)).isNull();
    }
}
