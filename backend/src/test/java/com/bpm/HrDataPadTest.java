package com.bpm;

import com.bpm.application.EmployeeService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Giữ số 0 đầu: mã NV 1–3 chữ số → 4 chữ số; SĐT 9 chữ số → thêm "0". */
class HrDataPadTest {

    @Test
    void padCode() {
        assertThat(EmployeeService.padCode("25")).isEqualTo("0025");
        assertThat(EmployeeService.padCode("191")).isEqualTo("0191");
        assertThat(EmployeeService.padCode("1411")).isEqualTo("1411"); // đủ 4 số → giữ
        assertThat(EmployeeService.padCode("1353.1")).isEqualTo("1353.1"); // có dấu chấm → giữ
    }

    @Test
    void padPhone() {
        assertThat(EmployeeService.padPhone("979444692")).isEqualTo("0979444692");
        assertThat(EmployeeService.padPhone("0979444692")).isEqualTo("0979444692"); // đủ 10 số → giữ
        assertThat(EmployeeService.padPhone("0385 563 493")).isEqualTo("0385 563 493"); // có dấu cách → giữ
    }
}
