package com.bpm;

import com.bpm.domain.erp.WorkdayReconciliation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Quy ước đối soát công: quy đổi giờ→công, khoá ghép tên, và cách tổng hợp một kỳ. */
class WorkdayReconciliationTest {

    @Test
    @DisplayName("8 giờ = 1 công, làm tròn 2 chữ số")
    void quyDoiGioSangCong() {
        assertThat(WorkdayReconciliation.toDays(8)).isEqualTo(1d);
        assertThat(WorkdayReconciliation.toDays(4)).isEqualTo(0.5d);
        assertThat(WorkdayReconciliation.toDays(167.5)).isEqualTo(20.94d);
    }

    @Test
    @DisplayName("Khoá ghép bỏ dấu, gộp khoảng trắng, không phân biệt hoa thường")
    void khoaGhepTen() {
        String a = WorkdayReconciliation.matchKey("Nguyễn  Đăng Mạnh");
        String b = WorkdayReconciliation.matchKey("nguyen dang manh");
        assertThat(a).isEqualTo(b);
        // Chữ đ hoa/thường đều về d — file khách hàng hay viết hoa toàn bộ tên.
        assertThat(WorkdayReconciliation.matchKey("ĐẶNG THỊ HÀ"))
                .isEqualTo(WorkdayReconciliation.matchKey("Đặng Thị Hà"));
    }

    @Test
    @DisplayName("Tổng hợp đếm đúng từng nhóm và cộng đúng chênh lệch")
    void tongHopKy() {
        List<WorkdayReconciliation.Row> rows = List.of(
                row("Khớp", 160, 20, 20, WorkdayReconciliation.Status.MATCHED),
                row("Lệch", 168, 21, 20, WorkdayReconciliation.Status.DIFF),
                row("Chỉ ERP", 80, 10, 0, WorkdayReconciliation.Status.ERP_ONLY),
                row("Chỉ KH", 0, 0, 18, WorkdayReconciliation.Status.CUSTOMER_ONLY));

        WorkdayReconciliation.Summary s = WorkdayReconciliation.summarize(rows);
        assertThat(s.total()).isEqualTo(4);
        assertThat(s.matched()).isEqualTo(1);
        assertThat(s.diff()).isEqualTo(1);
        assertThat(s.erpOnly()).isEqualTo(1);
        assertThat(s.customerOnly()).isEqualTo(1);
        assertThat(s.erpDays()).isEqualTo(51d);
        assertThat(s.customerDays()).isEqualTo(58d);
        assertThat(s.diffDays()).isEqualTo(-7d);
    }

    private static WorkdayReconciliation.Row row(String name, double hours, double erpDays,
                                                 double custDays, WorkdayReconciliation.Status st) {
        return new WorkdayReconciliation.Row(WorkdayReconciliation.matchKey(name), name, null,
                hours, erpDays, 0, custDays, WorkdayReconciliation.round2(erpDays - custDays), st);
    }
}
