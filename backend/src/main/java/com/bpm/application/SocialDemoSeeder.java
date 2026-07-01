package com.bpm.application;

import com.bpm.domain.UserAccount;
import com.bpm.domain.audit.AuditPort;
import com.bpm.domain.hr.Employee;
import com.bpm.domain.social.Post;
import com.bpm.infrastructure.EmployeeRepository;
import com.bpm.infrastructure.PostRepository;
import com.bpm.infrastructure.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Seed dữ liệu DEMO cho BẢNG TIN MẠNG XÃ HỘI nội bộ (Epic 2) trên nhân sự THẬT.
 * Kích hoạt thủ công bởi ADMIN (POST /api/v1/system/seed-social-demo).
 *
 * <p>Nguyên tắc giống HrDemoSeeder:
 * <ul>
 *   <li>CHỈ THÊM — không xoá/đụng bài thật.</li>
 *   <li>Idempotent: nếu đã có bài demo (nhận diện theo dấu {@link #GUARD_MARKER} trong nội dung) thì BỎ QUA.</li>
 *   <li>Tác giả là NHÂN SỰ THẬT đang làm việc + có tài khoản (để feed/notify có người thật).</li>
 *   <li>Gọi đúng {@link PostService} (tạo bài + like + comment qua service, KHÔNG ghi thẳng repo cho bài/like/cmt).</li>
 * </ul>
 *
 * <p>Lưu ý: PostService.create() phát notify cho mọi người trong phạm vi — đây là demo do admin chủ động bấm,
 * chấp nhận thông báo bài mới (đúng hành vi thật).
 */
@Service
public class SocialDemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(SocialDemoSeeder.class);

    /** Dấu ẩn cuối nội dung để nhận diện bài demo (idempotent) — vô hình với người đọc thông thường. */
    private static final String GUARD_MARKER = "​#demo-mxh";

    private final PostService postService;
    private final PostRepository postRepo;
    private final EmployeeRepository employeeRepo;
    private final UserAccountRepository userRepo;
    private final AuditPort audit;

    public SocialDemoSeeder(PostService postService, PostRepository postRepo, EmployeeRepository employeeRepo,
                            UserAccountRepository userRepo, AuditPort audit) {
        this.postService = postService;
        this.postRepo = postRepo;
        this.employeeRepo = employeeRepo;
        this.userRepo = userRepo;
        this.audit = audit;
    }

    /** Tóm tắt kết quả seed (trả cho FE). */
    public record SeedResult(boolean seeded, int posts, int comments, int likes, String message) {
    }

    @Transactional
    public SeedResult seed(String actor) {
        // ---- Guard idempotent: đã có bài demo? ----
        boolean already = postRepo.findAll().stream()
                .anyMatch(p -> p.getBody() != null && p.getBody().contains(GUARD_MARKER));
        if (already) {
            log.info("[SocialDemoSeeder] Đã có bài MXH demo — bỏ qua.");
            audit.record("SOCIAL_DEMO_SEED_SKIPPED", "System", null, actor, "Đã tồn tại — không seed lại.");
            return new SeedResult(false, 0, 0, 0,
                    "Đã có bài đăng MXH demo từ trước — bỏ qua (không nhân đôi).");
        }

        // ---- Chọn nhân sự thật (đang làm việc + có tài khoản) làm tác giả/người tương tác ----
        List<Employee> active = employeeRepo.findAllByOrderByEmpCodeAsc().stream()
                .filter(Employee::isActive)
                .filter(e -> e.getUserAccountId() != null && !e.getUserAccountId().isBlank())
                .toList();
        List<String> authors = new ArrayList<>();
        for (Employee e : active) {
            userRepo.findById(e.getUserAccountId())
                    .map(UserAccount::getUsername)
                    .ifPresent(authors::add);
        }
        if (authors.isEmpty()) {
            return new SeedResult(false, 0, 0, 0,
                    "Chưa có nhân sự (đang làm việc + có tài khoản) để làm tác giả bài. Hãy import nhân sự trước.");
        }

        // ---- Nội dung bài đăng công sở thật ----  {tiêu đề, nội dung, phân loại NEWS|EVENT|ANNOUNCEMENT} ----
        String[][] contents = {
                {"📢 Thông báo lịch nghỉ lễ", "Công ty trân trọng thông báo lịch nghỉ lễ sắp tới. Các phòng ban vui lòng sắp xếp công việc và bàn giao trước kỳ nghỉ. Chúc cả nhà có kỳ nghỉ vui vẻ bên gia đình! 🎉", "ANNOUNCEMENT"},
                {"🎉 Chào mừng thành viên mới", "Xin chào và chào mừng các đồng nghiệp mới gia nhập đại gia đình công ty trong tháng này. Mong mọi người nhanh chóng hoà nhập và cùng nhau tạo nên nhiều thành tích! 🤝", "NEWS"},
                {"🏆 Chúc mừng team Kinh doanh", "Xin chúc mừng team Kinh doanh đã vượt 150% chỉ tiêu doanh số quý vừa qua. Một tinh thần làm việc đáng ngưỡng mộ — cả công ty tự hào về các bạn! 💪", "NEWS"},
                {"📚 Chia sẻ: Mẹo quản lý thời gian", "Một vài mẹo nhỏ giúp mình làm việc hiệu quả hơn: (1) ưu tiên việc quan trọng vào buổi sáng, (2) gộp các việc nhỏ lại làm một lúc, (3) tắt thông báo khi cần tập trung sâu. Mọi người có mẹo nào hay thì chia sẻ thêm nhé! ✍️", "NEWS"},
                {"☕ Mời cả nhà tham gia Coffee Talk", "Thứ Sáu tuần này phòng HCNS tổ chức buổi Coffee Talk giao lưu giữa các phòng ban tại khu pantry tầng 3, từ 15h30. Có trà bánh miễn phí — mời mọi người ghé góp vui! ☕🍰", "EVENT"},
                {"💡 Phát động cuộc thi sáng kiến cải tiến", "Công ty phát động cuộc thi 'Sáng kiến cải tiến quy trình' năm nay. Mọi ý tưởng giúp công việc nhanh - gọn - hiệu quả hơn đều được hoan nghênh và có thưởng. Gửi ý tưởng về HCNS trước cuối tháng nhé! 🚀", "EVENT"},
                {"🤝 Tổng kết hoạt động thiện nguyện", "Cảm ơn toàn thể anh chị em đã chung tay trong chương trình thiện nguyện vừa qua. Tinh thần sẻ chia của mọi người thật sự ấm áp. Hẹn gặp lại ở những hoạt động ý nghĩa tiếp theo! ❤️", "EVENT"},
                {"🖥️ Bảo trì hệ thống cuối tuần", "Phòng CNTT thông báo: hệ thống nội bộ sẽ được bảo trì nâng cấp vào tối thứ Bảy này. Trong thời gian bảo trì một số dịch vụ có thể gián đoạn. Mong mọi người thông cảm và sắp xếp công việc phù hợp. 🛠️", "ANNOUNCEMENT"}
        };

        // ---- Bình luận & like mẫu ----
        String[] sampleComments = {
                "Cảm ơn thông báo, rất hữu ích! 👍",
                "Tuyệt vời, mong chờ quá!",
                "Chúc mừng cả team nhé 🎉",
                "Mình tham gia với ạ!",
                "Thông tin rất rõ ràng, cảm ơn anh/chị.",
                "Hay quá, để mình áp dụng thử 😄"
        };

        int authorIdx = 0;
        int createdPosts = 0;
        int createdComments = 0;
        int createdLikes = 0;

        for (int i = 0; i < contents.length; i++) {
            String author = authors.get(authorIdx++ % authors.size());
            // body = nội dung + dấu guard ẩn cuối (idempotent); topic giữ tiêu đề ngắn.
            String body = contents[i][1] + GUARD_MARKER;
            Post p;
            try {
                p = postService.create(body, List.of(), contents[i][2],
                        topicFrom(contents[i][0]), List.of(), author);
            } catch (Exception ex) {
                log.warn("[SocialDemoSeeder] Bỏ qua bài lỗi ({}): {}", author, ex.toString());
                continue;
            }
            createdPosts++;

            // Like mẫu: vài người khác like bài (qua service, toggle = thêm like)
            int likers = 2 + (i % 4); // 2..5 like
            for (int k = 1; k <= likers && k < authors.size(); k++) {
                String liker = authors.get((authorIdx + k) % authors.size());
                if (liker.equals(author)) {
                    continue;
                }
                try {
                    postService.toggleLike(p.getId(), liker);
                    createdLikes++;
                } catch (Exception ignored) {
                    // best-effort
                }
            }

            // Bình luận mẫu: 1..2 bình luận từ người khác
            int nComments = 1 + (i % 2);
            for (int c = 0; c < nComments && c + 1 < authors.size(); c++) {
                String commenter = authors.get((authorIdx + c + 1) % authors.size());
                if (commenter.equals(author)) {
                    continue;
                }
                try {
                    postService.addComment(p.getId(), sampleComments[(i + c) % sampleComments.length],
                            null, List.of(), commenter);
                    createdComments++;
                } catch (Exception ignored) {
                    // best-effort
                }
            }
        }

        audit.record("SOCIAL_DEMO_SEEDED", "System", null, actor,
                "posts=" + createdPosts + ", comments=" + createdComments + ", likes=" + createdLikes);
        log.info("[SocialDemoSeeder] Seed MXH: {} bài, {} bình luận, {} like.",
                createdPosts, createdComments, createdLikes);
        return new SeedResult(true, createdPosts, createdComments, createdLikes,
                "Đã tạo " + createdPosts + " bài đăng MXH demo, " + createdComments + " bình luận và "
                        + createdLikes + " lượt thích (tác giả là nhân sự thật).");
    }

    /** Chủ đề ngắn từ tiêu đề (bỏ emoji đầu dòng). */
    private static String topicFrom(String title) {
        String t = title.replaceAll("^[^\\p{L}\\p{N}]+", "").trim();
        return t.length() > 60 ? t.substring(0, 60) : t;
    }
}
