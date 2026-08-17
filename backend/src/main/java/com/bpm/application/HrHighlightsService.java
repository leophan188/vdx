package com.bpm.application;

import com.bpm.api.dto.HrHighlightsDto;
import com.bpm.api.dto.HrHighlightsDto.AnniversaryView;
import com.bpm.api.dto.HrHighlightsDto.BirthdayView;
import com.bpm.api.dto.HrHighlightsDto.OnboardingView;
import com.bpm.domain.UserAccount;
import com.bpm.domain.hr.Employee;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Điểm nhấn nhân sự cho trang chủ (Việc B + C):
 *  - SINH NHẬT hôm nay: nhân sự đang hoạt động (isActive) có birthDate trùng NGÀY/THÁNG hôm nay.
 *  - ONBOARDING: nhân sự có joinDate trong TƯƠNG LAI (joinDate > hôm nay) → "còn X ngày Onboard".
 *  - TRI ÂN THÂM NIÊN: kỷ niệm ngày vào làm, chỉ tính từ TRÒN 1 NĂM trở lên. Người vào làm
 *    đúng hôm nay có 0 năm nên không lọt vào đây — đó là sự kiện onboard, không được lẫn.
 *
 * <p>Công thức:
 *  - Sinh nhật: so khớp {@link MonthDay} (bỏ qua năm) → đúng cả người sinh 29/02 (rơi vào 28/02 năm thường? —
 *    ở đây so MonthDay trực tiếp; 29/02 chỉ "trùng" vào năm nhuận, chấp nhận).
 *  - daysUntil onboarding = số ngày từ HÔM NAY tới joinDate (ChronoUnit.DAYS), chỉ lấy &gt; 0.
 *  - Thông báo trước onboarding: với người có daysUntil &le; 7 → gửi NotificationService.notifyOnce
 *    cho ADMIN/HR + chính nhân sự (nếu có tài khoản). Dedup theo link để không spam khi gọi lặp.
 *
 * <p>Hôm nay lấy từ {@link LocalDate#now()} (BE). Không cron — tính khi gọi endpoint.
 */
@Service
public class HrHighlightsService {

    private static final Logger log = LoggerFactory.getLogger(HrHighlightsService.class);

    /** Ngưỡng nhắc onboarding (ngày). */
    private static final int ONBOARD_NOTIFY_THRESHOLD = 7;
    /**
     * Cửa sổ nhìn trước CHUNG cho cả ba thẻ nhân sự: sinh nhật, tri ân thâm niên, onboard.
     *
     * Một hằng số duy nhất để ba thẻ cạnh nhau không nói ba khoảng thời gian khác nhau.
     * Đã thử 30 ngày cho thâm niên thì ra 7 người cùng lúc, chiếm quá nhiều chỗ cột phải.
     *
     * Đổi lại thẻ thâm niên sẽ RỖNG khá thường xuyên (51/111 người đủ điều kiện, trải trên
     * 365 ngày → trung bình một tuần chỉ ~1 người); vì vậy thẻ vẫn phải LUÔN hiện kèm dòng
     * trạng thái rỗng, đừng ẩn đi kẻo người dùng tưởng mất tính năng.
     */
    private static final int LOOKAHEAD_DAYS = 7;
    /** Vai trò nhận thông báo onboarding (admin/HR). */
    private static final String ROLE_ADMIN = "ADMIN";

    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final NotificationService notify;
    private final PostService postService;

    public HrHighlightsService(EmployeeRepository employeeRepo, UserAccountRepository userRepo,
                               NotificationService notify, PostService postService) {
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.notify = notify;
        this.postService = postService;
    }

    @Transactional
    public HrHighlightsDto highlights() {
        LocalDate today = LocalDate.now();
        MonthDay todayMd = MonthDay.from(today);
        List<Employee> all = employeeRepo.findAllByOrderByEmpCodeAsc();

        List<BirthdayView> birthdaysToday = new ArrayList<>();
        List<BirthdayView> birthdaysThisWeek = new ArrayList<>();
        List<OnboardingView> onboardingSoon = new ArrayList<>();
        List<AnniversaryView> anniversariesToday = new ArrayList<>();
        List<AnniversaryView> anniversariesUpcoming = new ArrayList<>();

        for (Employee e : all) {
            // ----- SINH NHẬT (chỉ người đang hoạt động) -----
            if (e.isActive() && e.getBirthDate() != null) {
                MonthDay bd = MonthDay.of(e.getBirthDate().getMonthValue(), e.getBirthDate().getDayOfMonth());
                int inDays = daysUntilNextAnniversary(today, bd);
                if (bd.equals(todayMd)) {
                    birthdaysToday.add(birthdayView(e, 0));
                }
                if (inDays > 0 && inDays <= LOOKAHEAD_DAYS) {
                    birthdaysThisWeek.add(birthdayView(e, inDays));
                }
            }

            // ----- ONBOARDING (joinDate tương lai, trong cửa sổ 7 ngày) -----
            // Trước đây KHÔNG chặn trên: người vào làm sau nửa năm cũng nằm trên thẻ, đẩy
            // người sắp vào thật xuống dưới. Nay cùng cửa sổ 7 ngày với hai thẻ còn lại.
            if (e.getJoinDate() != null && e.getJoinDate().isAfter(today)) {
                int daysUntil = (int) ChronoUnit.DAYS.between(today, e.getJoinDate());
                if (daysUntil > 0 && daysUntil <= LOOKAHEAD_DAYS) {
                    onboardingSoon.add(new OnboardingView(e.getEmpCode(), e.getFullName(), e.getDeptCode(),
                            e.getJobPosition(), e.getTitle(), e.getUserAccountId(), e.getJoinDate(), daysUntil));
                    maybeNotifyOnboarding(e, daysUntil);
                }
            }

            // ----- TIN CHÚC MỪNG tự động (đúng ngày) — idempotent theo marker ngày -----
            if (e.isActive() && e.getBirthDate() != null
                    && MonthDay.of(e.getBirthDate().getMonthValue(), e.getBirthDate().getDayOfMonth()).equals(todayMd)) {
                maybeCelebrateBirthday(e, today);
            }
            if (e.getJoinDate() != null && e.getJoinDate().isEqual(today)) {
                maybeCelebrateOnboard(e, today);
            }

            // ----- TRI ÂN THÂM NIÊN (kỷ niệm ngày vào làm, từ TRÒN 1 NĂM trở lên) -----
            if (e.isActive() && e.getJoinDate() != null) {
                MonthDay jd = MonthDay.of(e.getJoinDate().getMonthValue(), e.getJoinDate().getDayOfMonth());
                int inDays = daysUntilNextAnniversary(today, jd);
                // Số năm ĐẠT ĐƯỢC tại lần kỷ niệm sắp tới, không phải số năm tính tới hôm nay:
                // người vào 01/09/2023, hôm nay 30/08 thì ngày mai tròn 2 năm — phải hiện "2 năm".
                int years = yearsAtNextAnniversary(today, e.getJoinDate(), inDays);
                if (years >= 1) {
                    if (inDays == 0) {
                        anniversariesToday.add(anniversaryView(e, years, 0));
                        maybeCelebrateAnniversary(e, today, years);
                    }
                    if (inDays > 0 && inDays <= LOOKAHEAD_DAYS) {
                        anniversariesUpcoming.add(anniversaryView(e, years, inDays));
                    }
                }
            }
        }

        birthdaysThisWeek.sort(Comparator.comparingInt(BirthdayView::inDays));
        onboardingSoon.sort(Comparator.comparingInt(OnboardingView::daysUntil));
        anniversariesUpcoming.sort(Comparator.comparingInt(AnniversaryView::inDays));

        return new HrHighlightsDto(birthdaysToday, birthdaysThisWeek, onboardingSoon,
                anniversariesToday, anniversariesUpcoming);
    }

    private static AnniversaryView anniversaryView(Employee e, int years, int inDays) {
        return new AnniversaryView(e.getEmpCode(), e.getFullName(), e.getDeptCode(), e.getJobPosition(),
                e.getTitle(), e.getUserAccountId(), e.getJoinDate(), years, inDays);
    }

    /**
     * Số năm gắn bó ĐẠT ĐƯỢC tại lần kỷ niệm kế tiếp.
     *
     * Lấy năm của ngày kỷ niệm kế tiếp trừ năm vào làm. Nếu tính theo "hôm nay trừ ngày vào"
     * thì hôm trước ngày kỷ niệm sẽ ra thiếu một năm, hiển thị "sắp tròn 1 năm" cho người
     * ngày mai tròn 2 năm.
     */
    private static int yearsAtNextAnniversary(LocalDate today, LocalDate joinDate, int inDays) {
        if (inDays < 0) {
            return -1; // không xác định được ngày kỷ niệm (dữ liệu ngày lạ)
        }
        return today.plusDays(inDays).getYear() - joinDate.getYear();
    }

    private static BirthdayView birthdayView(Employee e, int inDays) {
        return new BirthdayView(e.getEmpCode(), e.getFullName(), e.getDeptCode(), e.getJobPosition(), e.getTitle(),
                e.getUserAccountId(), e.getBirthDate().getDayOfMonth(), e.getBirthDate().getMonthValue(), inDays);
    }

    /** Số ngày từ hôm nay tới lần sinh nhật kế tiếp (0 = hôm nay). -1 nếu không xác định. */
    private static int daysUntilNextAnniversary(LocalDate today, MonthDay bd) {
        try {
            LocalDate next = bd.atYear(today.getYear());
            if (next.isBefore(today)) {
                next = bd.atYear(today.getYear() + 1);
            }
            return (int) ChronoUnit.DAYS.between(today, next);
        } catch (Exception ex) {
            return -1;
        }
    }

    /**
     * Nhắc trước onboarding khi daysUntil &le; 7 (best-effort, dedup theo link để không spam).
     * Gửi cho: ADMIN/HR (theo roleCode ADMIN) + chính nhân sự nếu có tài khoản.
     */
    private void maybeNotifyOnboarding(Employee e, int daysUntil) {
        if (daysUntil > ONBOARD_NOTIFY_THRESHOLD) {
            return;
        }
        try {
            String link = "/employees?onboard=" + e.getEmpCode() + "&date=" + e.getJoinDate();
            String title = "Sắp onboard: " + e.getFullName();
            String body = e.getFullName() + " (" + e.getEmpCode() + ") sẽ vào làm sau " + daysUntil
                    + " ngày — ngày vào " + e.getJoinDate() + ". Vui lòng chuẩn bị đón nhân sự mới.";

            // ADMIN/HR
            for (UserAccount admin : userRepo.findByRoleCode(ROLE_ADMIN)) {
                notify.notifyOnce(admin.getId(), "HR_ONBOARDING", title, body, link);
            }
            // Chính nhân sự (nếu có tài khoản)
            if (e.getUserAccountId() != null && !e.getUserAccountId().isBlank()) {
                notify.notifyOnce(e.getUserAccountId(), "HR_ONBOARDING",
                        "Chào mừng bạn sắp gia nhập!",
                        "Còn " + daysUntil + " ngày tới ngày vào làm (" + e.getJoinDate()
                                + "). Hẹn gặp bạn tại công ty!", link);
            }
        } catch (Exception ex) {
            log.warn("[HrHighlights] Không gửi được nhắc onboarding cho {}: {}", e.getEmpCode(), ex.toString());
        }
    }

    // ===================== Tin chúc mừng tự động (đúng ngày) =====================

    /**
     * Tự tạo 1 tin NỔI BẬT (ghim, category EVENT) chúc mừng sinh nhật khi HÔM NAY đúng ngày.
     * Idempotent theo marker ngày: mỗi nhân sự/ngày chỉ 1 bài.
     * Tác giả = HỆ THỐNG (admin, hiển thị "Plan X"); nội dung NHÚNG ảnh nhân sự qua subjectUserId.
     * Không có admin → bỏ qua (không lỗi).
     */
    private void maybeCelebrateBirthday(Employee e, LocalDate today) {
        UserAccount admin = systemAuthor();
        if (admin == null) {
            return; // không có tác giả hệ thống → bỏ qua (không lỗi)
        }
        String marker = "​#celebrate-bd-" + e.getEmpCode() + "-" + today;
        String dept = e.getDeptCode() == null || e.getDeptCode().isBlank() ? "" : " — " + e.getDeptCode();
        String body = "🎂 Chúc mừng sinh nhật " + e.getFullName() + dept + "! "
                + "Chúc bạn một tuổi mới thật nhiều sức khoẻ, niềm vui và thành công. "
                + "Cả nhà Plan X cùng gửi lời chúc tốt đẹp nhất tới bạn! 🎉🎈";
        try {
            // subjectUserId = nhân sự được chúc (null nếu không có tài khoản — vẫn tạo bài, chỉ không có ảnh)
            postService.createSystemPinned(admin.getId(), "Plan X", body, "EVENT",
                    blankToNull(e.getUserAccountId()), marker);
        } catch (Exception ex) {
            log.warn("[HrHighlights] Không tạo được tin sinh nhật cho {}: {}", e.getEmpCode(), ex.toString());
        }
    }

    /**
     * Tự tạo 1 tin NỔI BẬT (ghim, category EVENT) chào mừng nhân sự gia nhập khi joinDate == hôm nay.
     * Idempotent theo marker ngày. Tác giả = HỆ THỐNG; nội dung nhúng ảnh nhân sự qua subjectUserId.
     */
    private void maybeCelebrateOnboard(Employee e, LocalDate today) {
        UserAccount admin = systemAuthor();
        if (admin == null) {
            return;
        }
        String marker = "​#celebrate-ob-" + e.getEmpCode() + "-" + today;
        String dept = e.getDeptCode() == null || e.getDeptCode().isBlank() ? "công ty" : e.getDeptCode();
        String body = "🎉 Chào mừng " + e.getFullName() + " gia nhập " + dept + "! "
                + "Rất vui được đón thêm một thành viên mới vào đại gia đình Plan X. "
                + "Chúc bạn nhanh chóng hoà nhập và gặt hái nhiều thành công cùng đội ngũ! 🤝✨";
        try {
            postService.createSystemPinned(admin.getId(), "Plan X", body, "EVENT",
                    blankToNull(e.getUserAccountId()), marker);
        } catch (Exception ex) {
            log.warn("[HrHighlights] Không tạo được tin chào mừng cho {}: {}", e.getEmpCode(), ex.toString());
        }
    }

    /**
     * Tự tạo 1 tin NỔI BẬT tri ân thâm niên khi hôm nay đúng ngày kỷ niệm vào làm (≥ 1 năm).
     * Idempotent theo marker ngày, cùng cơ chế với tin sinh nhật / chào mừng.
     */
    private void maybeCelebrateAnniversary(Employee e, LocalDate today, int years) {
        UserAccount admin = systemAuthor();
        if (admin == null) {
            return;
        }
        String marker = "​#celebrate-anniv-" + e.getEmpCode() + "-" + today;
        String dept = e.getDeptCode() == null || e.getDeptCode().isBlank() ? "" : " (" + e.getDeptCode() + ")";
        String body = milestoneIcon(years) + " Tri ân " + years + " năm gắn bó — " + e.getFullName() + dept + "! "
                + "Cảm ơn bạn đã đồng hành cùng Plan X suốt chặng đường vừa qua, "
                + "góp sức vào từng bước trưởng thành của cả đội ngũ. "
                + "Chúc bạn thật nhiều sức khoẻ và tiếp tục gặt hái thành công! 🙏✨";
        try {
            postService.createSystemPinned(admin.getId(), "Plan X", body, "EVENT",
                    blankToNull(e.getUserAccountId()), marker);
        } catch (Exception ex) {
            log.warn("[HrHighlights] Không tạo được tin tri ân thâm niên cho {}: {}", e.getEmpCode(), ex.toString());
        }
    }

    /** Mốc tròn 5/10 năm dùng icon đậm hơn để nổi bật giữa các mốc thường. */
    private static String milestoneIcon(int years) {
        if (years >= 10) {
            return "🏆";
        }
        return years >= 5 ? "🎖️" : "💐";
    }

    /** Chuỗi rỗng/blank → null (để subjectUserId chuẩn). */
    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Tài khoản admin làm tác giả tin hệ thống (ưu tiên roleCode ADMIN, fallback username "admin"). null nếu không có. */
    private UserAccount systemAuthor() {
        List<UserAccount> admins = userRepo.findByRoleCode(ROLE_ADMIN);
        if (!admins.isEmpty()) {
            return admins.get(0);
        }
        return userRepo.findByUsername("admin").orElse(null);
    }
}
