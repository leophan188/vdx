package com.bpm;

import com.bpm.application.ErpTimesheetService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Thứ tự mã nhân viên trong màn Kiểm soát giờ công. */
class EmpCodeOrderTest {

    @SuppressWarnings("unchecked")
    private static Comparator<String> order() throws Exception {
        Field f = ErpTimesheetService.class.getDeclaredField("CODE_ORDER");
        f.setAccessible(true);
        return (Comparator<String>) f.get(null);
    }

    @Test
    @DisplayName("Mã số so theo SỐ, không phải theo chuỗi")
    void maSoSoTheoSo() throws Exception {
        List<String> codes = new ArrayList<>(Arrays.asList("4021", "985", "2328", "10", "9"));
        codes.sort(order());
        // So thuần chuỗi thì "10" đứng trước "9" và "985" đứng trước "2328".
        assertThat(codes).containsExactly("9", "10", "985", "2328", "4021");
    }

    @Test
    @DisplayName("Mã có đuôi chữ/số phụ vẫn xếp theo phần số đầu; chưa có mã xuống cuối")
    void maDacBietVaThieuMa() throws Exception {
        List<String> codes = new ArrayList<>(Arrays.asList(null, "3982.1", "4021", "", "3982"));
        codes.sort(order());
        assertThat(codes.subList(0, 3)).containsExactly("3982", "3982.1", "4021");
        // Hai dòng chưa có mã (null và chuỗi rỗng) nằm cuối, thứ tự giữa chúng không quan trọng.
        assertThat(codes.subList(3, 5)).allSatisfy(c -> assertThat(c == null || c.isBlank()).isTrue());
    }
}
