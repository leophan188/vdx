# Runbook 5.7 — Triển khai & NFR vận hành (hiệu năng / sẵn sàng / tương thích / đa ngôn ngữ)

## 1. Hồ sơ triển khai
- Backend: `--spring.profiles.active=prod` → dùng `application-prod.yml` (MariaDB, HikariCP, `ddl-auto=validate`, Flowable `database-schema-update=false`, log ra file).
- Bí mật qua **biến môi trường** (`BPM_DB_*`, `BPM_*`) — không hard-code trong ảnh.
- Đứng sau **Nginx** (TLS, gzip, reverse proxy). `server.forward-headers-strategy=framework` đã bật để lấy đúng scheme/host.
- Frontend: `ng build` → phục vụ tĩnh qua Nginx; proxy `/api` + `/actuator/health` về backend.

## 2. Sẵn sàng (readiness) — ĐÃ CÓ
- `GET /actuator/health/liveness` → tiến trình còn sống (restart nếu DOWN).
- `GET /actuator/health/readiness` → sẵn sàng nhận traffic (DB UP). Dùng cho probe của LB/K8s.
- `GET /actuator/health` công khai; chi tiết ẩn ở prod (`show-details: never`).
- **Tắt êm**: `server.shutdown=graceful` + `timeout-per-shutdown-phase=25s` — phục vụ nốt request khi deploy/rolling update.

## 3. Hiệu năng — ĐÃ CÓ + cần làm khi tải lớn
- **Index DB** đã thêm: `notification(recipient_user_id,is_read)`, `workflow_instance(flowable_instance_id, started_by)`. Bổ sung khi cần: `audit_event(object_type,object_id)`.
- HikariCP pool: `maximum-pool-size=20` (chỉnh theo `BPM_DB_POOL_MAX`); Tomcat threads 200.
- **Nợ kỹ thuật cần xử lý khi mở rộng**: các danh sách (inbox/tracking/report) hiện truy vấn N+1 (mỗi instance/task 1 query biến/lịch sử). Khi vượt ~vài nghìn hồ sơ đang chạy → thêm **phân trang** + **projection/cache** số liệu dashboard.
- Bật cache HTTP tĩnh + gzip ở Nginx cho bundle FE.

## 4. Tương thích
- On-prem MariaDB 11.x + JDK 21 + Nginx. Trình duyệt: Chromium/Firefox/Edge bản hiện hành (Angular 21 build target).
- Flowable 7.1 chạy cùng database `bpm` (bảng `ACT_*`).

## 5. Đa ngôn ngữ (i18n) — TRẠNG THÁI & KẾ HOẠCH
- Hiện tại: UI **một ngôn ngữ (vi)**, chuỗi nhúng trực tiếp; `index.html` đã đặt `lang="vi"`.
- Để bật đa ngôn ngữ: dùng **`@angular/localize`** — đánh dấu chuỗi bằng `i18n`/`$localize`, trích `messages.xlf`, build theo từng locale (`/vi/`, `/en/`) phục vụ qua Nginx. Đây là **một hạng mục refactor riêng** (toàn bộ template), nên lên kế hoạch tách biệt, không gộp vào GĐ1.
- Backend: thông điệp lỗi/email có thể tách `messages_*.properties` + `MessageSource` khi cần.

## 6. Checklist go-live
- [ ] `BPM_DB_*` + secrets đã đặt; DB `bpm` đã tạo + chạy DDL schema (app/Flowable) một lần với user có quyền, sau đó hạ quyền + `ddl-auto=validate`.
- [ ] Probe LB trỏ `/actuator/health/readiness`.
- [ ] Cron backup (Runbook 5.4) + đã restore thử vào staging.
- [ ] Partition `audit_event` (Runbook 5.5).
- [ ] TLS + security headers ở Nginx; phiên cookie `Secure`/`HttpOnly`.
- [ ] Theo dõi log `/var/log/bpm/backend.log` + cảnh báo health DOWN.
