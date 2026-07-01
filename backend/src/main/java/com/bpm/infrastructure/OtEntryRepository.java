package com.bpm.infrastructure;

import com.bpm.domain.ot.OtEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface OtEntryRepository extends JpaRepository<OtEntry, String> {

    /** Đăng ký của một user, mới nhất trước (tab "OT của tôi"). */
    List<OtEntry> findByUserIdOrderByWorkDateDescCreatedAtDesc(String userId);

    /** Đăng ký có workDate trong [from,to] (tổng hợp/xuất theo khoảng ngày). */
    List<OtEntry> findByWorkDateBetween(LocalDate from, LocalDate to);

    /** Toàn bộ đăng ký trong một kỳ (tổng hợp / xuất). */
    List<OtEntry> findByPeriodKeyOrderByOrgUnitIdAscUserNameAscWorkDateAsc(String periodKey);

    /** Các kỳ đang có dữ liệu (để FE liệt kê chọn kỳ). */
    List<OtEntry> findAllByOrderByPeriodKeyDesc();
}
