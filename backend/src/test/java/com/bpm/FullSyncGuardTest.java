package com.bpm;

import com.bpm.api.dto.EmployeeDto;
import com.bpm.application.EmployeeService;
import com.bpm.domain.hr.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đồng bộ TOÀN PHẦN chỉ được xử lý người vắng mặt khi file đủ đầy đặn — một lần tải thiếu dòng
 * không được phép khoá cả công ty.
 */
@SpringBootTest
@ActiveProfiles("test")
class FullSyncGuardTest {

    @Autowired EmployeeService employeeService;

    /** Header đúng như file HR thật (reader nhận diện theo tên cột tiếng Việt). */
    private static final String HEADER =
            "ID,Trạng thái,Họ và tên,Vị trí công việc,Chức danh,Mã bộ phận,Đơn vị,"
            + "Ngày tham gia,Ngày sinh,Số điện thoại,Loại hợp đồng,Số tài khoản,Ngân hàng,Level\n";

    private static byte[] csv(String... rows) {
        return (HEADER + String.join("\n", rows)).getBytes(StandardCharsets.UTF_8);
    }

    private static String row(String code, String name) {
        return code + ",Đang làm việc," + name + ",DEV,Nhân Viên,PDX.1,VMO,01/01/2024,01/01/1995,,,,,Middle";
    }

    @Test
    void skinnyFileDoesNotMarkEveryoneAsLeft() {
        // dựng 5 nhân sự từ file đầy đủ
        employeeService.apply(csv(row("GUARD1", "Người 1"), row("GUARD2", "Người 2"), row("GUARD3", "Người 3"),
                row("GUARD4", "Người 4"), row("GUARD5", "Người 5")), "day-du.csv", "tester", false);
        long before = employeeService.list(EmployeeService.STATUS_GROUP_ACTIVE, null, null, "GUARD").size();
        assertThat(before).isEqualTo(5);

        // file "hỏng" chỉ còn 1 dòng + bật đồng bộ toàn phần → KHÔNG được đánh dấu 4 người kia đã nghỉ
        var res = employeeService.apply(csv(row("GUARD1", "Người 1")), "thieu-dong.csv", "tester", true);

        assertThat(res.locked()).isZero();
        assertThat(employeeService.list(EmployeeService.STATUS_GROUP_ACTIVE, null, null, "GUARD"))
                .hasSize(5)
                .allSatisfy(e -> assertThat(e.isActive()).isTrue());
    }

    @Test
    void fullFileStillMarksTheOneWhoLeft() {
        employeeService.apply(csv(row("KEEP1", "Giữ 1"), row("KEEP2", "Giữ 2"), row("KEEP3", "Giữ 3"),
                row("KEEP4", "Giữ 4"), row("LEAVE1", "Đã nghỉ")), "day-du.csv", "tester", false);

        // file mới thiếu đúng 1 người (4/5 = 80%, đạt ngưỡng) → người đó bị đánh dấu nghỉ
        employeeService.apply(csv(row("KEEP1", "Giữ 1"), row("KEEP2", "Giữ 2"), row("KEEP3", "Giữ 3"),
                row("KEEP4", "Giữ 4")), "thieu-mot-nguoi.csv", "tester", true);

        var left = employeeService.list(null, null, null, "LEAVE1");
        assertThat(left).singleElement()
                .satisfies(e -> assertThat(e.isActive()).isFalse());
    }
}
