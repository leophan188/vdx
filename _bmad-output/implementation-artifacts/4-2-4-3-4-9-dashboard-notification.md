# Story 4.2 + 4.3 + 4.9: Dashboard điều hành · Ai đang làm gì · Thông báo in-app

Status: review

## Stories
- **4.2 Dashboard điều hành**: tổng quan vận hành (hồ sơ theo trạng thái/quy trình, việc mở/quá hạn).
- **4.3 "Ai đang làm gì"**: tải việc đang mở + quá hạn theo từng người.
- **4.9 Thông báo in-app (notification center)**: chuông + badge chưa đọc + dropdown; bắn "việc mới" khi giao việc.

## Acceptance Criteria
1. **GET /dashboard/summary** (ADMIN): tổng hồ sơ + theo trạng thái (chạy/xong/hủy) + việc mở + **việc quá hạn** + phân rã theo quy trình. _(4.2)_
2. **GET /dashboard/workload** (ADMIN): mỗi người → số việc đang mở + số quá hạn, sắp xếp giảm dần. _(4.3)_
3. Khi việc được giao cho một người (POSITION resolve người giữ / USER) → tạo **Notification** "Việc mới: <bước>" link `/inbox`. _(4.9)_
4. **GET /notifications** (mọi user, của mình) · **/unread-count** · **POST /{id}/read** · **/read-all**. Chuông topbar hiện badge + dropdown; poll lại số chưa đọc mỗi 30s (chạm Story 4.7). _(4.9)_

## Tasks / Subtasks
- [x] BE `WorkflowService.dashboardSummary/workload` (+ `taskOverdue` dùng `effectiveDue`), `DashboardDto`, `DashboardController` (/summary, /workload), security `/dashboard/** = ADMIN`.
- [x] FE `DashboardService` + dashboard dựng lại: nhóm **Vận hành** (4 stat-card + "Hồ sơ theo quy trình" + "Ai đang làm gì" bar) trên nhóm Tổ chức cũ.
- [x] BE `Notification` entity + repo + `NotificationService` (notify/mine/unreadCount/markRead/markAllRead); emit trong `assignActiveTasks` (POSITION lấy `TaskAssignment.assigneeUserId`, USER lấy assigneeId).
- [x] BE `NotificationDto` + `NotificationController` (mọi user). FE `NotificationService` + **`NotificationBell`** (badge + dropdown + đánh dấu đã đọc + điều hướng link) gắn topbar.
- [x] Verify live trên dữ liệu thật: summary 8 chạy/2 xong/1 quá hạn; workload đúng người; seed phát notification (tgd 3, tp.hcns 4, cv.ns 5 chưa đọc); mark-read 3→2. **BE 54/54, FE 23/23.**

## Dev Notes
- Notification phát ngay trong luồng giao việc (start + complete + return loop) → demo có sẵn lịch sử thông báo cho từng người.
- Dashboard tái dùng `effectiveDue`/`userDisplay`/`safeProcessName` của WorkflowService — không trùng logic.
- **Còn Epic 4:** 4.1 projection reportable · 4.4 theo dõi nhiệm vụ cá nhân · 4.5 tra cứu/tìm kiếm · 4.6 báo cáo 4 lát cắt + lọc thời gian · 4.7 realtime đầy đủ · 4.8 xuất Excel/PDF · 4.10 email nhắc hạn.

### References
[Source: epics.md#Story-4.2,4.3,4.9] · lấp nợ "cảnh báo cấp trên đang ghi audit — chờ notification center".

## File List
BE (mới): `domain/notification/Notification.java`, `infrastructure/NotificationRepository.java`, `application/NotificationService.java`, `api/dto/NotificationDto.java`, `api/NotificationController.java`, `api/dto/DashboardDto.java`, `api/DashboardController.java`; sửa `application/WorkflowService.java` (dashboard + emit notify), `infrastructure/auth/SecurityConfig.java` (+/dashboard).
FE (mới): `core/dashboard.service.ts`, `core/notification.service.ts`, `shared/notification-bell/{ts,html}`; sửa `dashboard/{ts,html}`, `app.ts`+`app.html` (chuông), `styles/_components.scss` (.section-title, .noti*).
