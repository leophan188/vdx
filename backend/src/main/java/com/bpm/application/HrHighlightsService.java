package com.bpm.application;

import com.bpm.api.dto.HrHighlightsDto;
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

        for (Employee e : all) {
            // ----- SINH NHẬT (chỉ người đang hoạt động) -----
            if (e.isActive() && e.getBirthDate() != null) {
                MonthDay bd = MonthDay.of(e.getBirthDate().getMonthValue(), e.getBirthDate().getDayOfMonth());
                int inDays = daysUntilNextAnniversary(today, bd);
                if (bd.equals(todayMd)) {
                    birthdaysToday.add(birthdayView(e, 0));
                }
                if (inDays >= 0 && inDays <= 6) {
                    birthdaysThisWeek.add(birthdayView(e, inDays));
                }
            }

            // ----- ONBOARDING (joinDate tương lai) -----
            if (e.getJoinDate() != null && e.getJoinDate().isAfter(today)) {
                int daysUntil = (int) ChronoUnit.DAYS.between(today, e.getJoinDate());
                if (daysUntil > 0) {
                    onboardingSoon.add(new OnboardingView(e.getEmpCode(), e.getFullName(), e.getDeptCode(),
                            e.getJobPosition(), e.getUserAccountId(), e.getJoinDate(), daysUntil));
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
        }

        birthdaysThisWeek.sort(Comparator.comparingInt(BirthdayView::inDays));
        onboardingSoon.sort(Comparator.comparingInt(OnboardingView::daysUntil));

        return new HrHighlightsDto(birthdaysToday, birthdaysThisWeek, onboardingSoon);
    }

    private static BirthdayView birthdayView(Employee e, int inDays) {
        return new BirthdayView(e.getEmpCode(), e.getFullName(), e.getDeptCode(), e.getJobPosition(),
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
     * Tác giả = HỆ THỐNG (admin, hiển thị "VMO DX"); nội dung NHÚNG ảnh nhân sự qua subjectUserId.
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
                + "Cả nhà VMO DX cùng gửi lời chúc tốt đẹp nhất tới bạn! 🎉🎈";
        try {
            // subjectUserId = nhân sự được chúc (null nếu không có tài khoản — vẫn tạo bài, chỉ không có ảnh)
            postService.createSystemPinned(admin.getId(), "VMO DX", body, "EVENT",
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
                + "Rất vui được đón thêm một thành viên mới vào đại gia đình VMO DX. "
                + "Chúc bạn nhanh chóng hoà nhập và gặt hái nhiều thành công cùng đội ngũ! 🤝✨";
        try {
            postService.createSystemPinned(admin.getId(), "VMO DX", body, "EVENT",
                    blankToNull(e.getUserAccountId()), marker);
        } catch (Exception ex) {
            log.warn("[HrHighlights] Không tạo được tin chào mừng cho {}: {}", e.getEmpCode(), ex.toString());
        }
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
