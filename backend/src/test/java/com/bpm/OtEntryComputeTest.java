package com.bpm;

import com.bpm.domain.ot.OtEntry;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Giờ OT: ngày thường = cuối − đầu; T7/CN trừ 1.5h nghỉ trưa. */
class OtEntryComputeTest {

    private static LocalDate nextWeekday() {
        LocalDate d = LocalDate.of(2026, 6, 29);
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) d = d.plusDays(1);
        return d;
    }
    private static LocalDate nextSaturday() {
        LocalDate d = LocalDate.of(2026, 6, 29);
        while (d.getDayOfWeek() != DayOfWeek.SATURDAY) d = d.plusDays(1);
        return d;
    }

    @Test
    void ngayThuong_khongTruNghiTrua() {
        double h = OtEntry.computeHours(nextWeekday(), LocalTime.of(17, 30), LocalTime.of(18, 30), 0);
        assertThat(h).isEqualTo(1.0); // 18:30 - 17:30
    }

    @Test
    void cuoiTuan_batDau830_tru1h() {
        double h = OtEntry.computeHours(nextSaturday(), LocalTime.of(8, 30), LocalTime.of(17, 30), 0);
        assertThat(h).isEqualTo(8.0); // 9h - 1h nghỉ trưa (bắt đầu 8:30)
    }

    @Test
    void cuoiTuan_batDau800_tru1Phay5h() {
        double h = OtEntry.computeHours(nextSaturday(), LocalTime.of(8, 0), LocalTime.of(17, 30), 0);
        assertThat(h).isEqualTo(8.0); // 9.5h - 1.5h nghỉ trưa (bắt đầu 8:00)
    }

    @Test
    void khongCoGio_dungSoGioNhapTay() {
        double h = OtEntry.computeHours(nextWeekday(), null, null, 3.0);
        assertThat(h).isEqualTo(3.0);
    }
}
