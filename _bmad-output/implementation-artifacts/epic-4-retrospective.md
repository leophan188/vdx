# Epic 4 — Retrospective (HOÀN THÀNH, 2026-06-26)

Trạng thái: **10/10 story `done`** · epic-4 `done`. BE 55/55, FE 23/23. Verify live trên dữ liệu thật.

## Mục tiêu epic
Minh bạch, thống kê & thông báo cho nền tảng vận hành quy trình (FR nhóm G + H).

## Đã giao (10 story)
| Story | Kết quả |
|---|---|
| 4.1 Projection trường reportable | `searchText` gom mọi giá trị field → tra cứu theo dữ liệu nghiệp vụ |
| 4.2 Dashboard điều hành | stat-card hồ-sơ/việc/quá-hạn + "hồ sơ theo quy trình" |
| 4.3 Ai đang làm gì | tải việc theo từng người (mở + quá hạn) |
| 4.4 Theo dõi nhiệm vụ cá nhân | màn "Hồ sơ của tôi" cho mọi user |
| 4.5 Tra cứu / tìm kiếm | search box + lọc trạng thái + tiêu đề hồ sơ |
| 4.6 Báo cáo 4 lát cắt + lọc thời gian | trạng thái/quy trình/người/thời gian |
| 4.7 Cập nhật gần realtime | auto-refresh inbox/tracking 15s · dashboard/chuông 30s |
| 4.8 Xuất Excel/PDF | CSV UTF-8 BOM (Excel) + In/PDF (@media print) |
| 4.9 Thông báo in-app | NotificationBell + emit "Việc mới" khi giao việc |
| 4.10 Email nhắc hạn | MailPort + scheduler quét quá hạn + cooldown chống spam |

## Điều làm tốt
- **Tái dùng triệt để**: dashboard/report/reminder đều dùng lại `effectiveDue`/`userDisplay`/`safeProcessName`/`taskOverdue` của WorkflowService — không trùng logic.
- **Lấp đúng nợ đã ghi từ epic trước**: 4.9 thay "cảnh báo cấp trên ghi audit" bằng notification center thật; 4.10 hiện thực "SLA phát thông báo" (nhóm H).
- **Cổng trừu tượng cho hạ tầng chưa có**: `MailPort` + `LoggingMailAdapter` cho phép hoàn thiện luồng email end-to-end mà chưa cần SMTP — chỉ thay adapter khi triển khai.
- **Verify trên dữ liệu thật**: nhờ DemoSeeder thực tế (~10 hồ sơ đa trạng thái), mọi số liệu/tra cứu/nhắc hạn kiểm chứng được ngay (vd tìm "Dell"/"Hỏa tốc", nhắc đúng việc quá hạn).
- **Phân quyền chuẩn hóa**: dashboard/report/reminder = ADMIN; inbox/my-requests/notifications/startable = mọi user — tách rạch ròi trong SecurityConfig.

## Bài học / cải thiện
- **Quên `mvn package` sau khi thêm controller** → endpoint 404 do chạy jar cũ. Quy trình verify cần luôn repackage trước restart.
- **Realtime mới ở mức poll** (15–30s), chưa phải push (SSE/WebSocket) — đủ cho GĐ1 nhưng 4.7 "5s đầy đủ" sẽ cần kênh push khi tải lớn.
- **N+1 query** ở dashboard/report/instances (mỗi instance/task truy vấn biến/lịch sử riêng) — chấp nhận ở quy mô 100–500 user; cần projection/cache nếu mở rộng.
- **Email tự phục vụ**: nhắc hạn chạy nền tốt, nhưng "bật/tắt theo người dùng" (preference) chưa làm — gộp vào quản lý hồ sơ cá nhân sau.

## Số liệu
BE 45→55 test (Epic 3+4) · FE 23 test · 0 fail. 10 story done.
