# BPM Platform — Hướng dẫn GO-LIVE (Docker Compose, 1 server)

Triển khai 3 service trên một máy chủ: **MariaDB + Backend (Spring Boot) + Frontend (Angular qua nginx)**.
Bản này **KHÔNG** bao gồm OnlyOffice / module BPM-doc.

Kiến trúc cổng:

```
Internet ──(HTTPS, khuyến nghị qua reverse proxy)──► nginx (frontend, cổng 80)
                                                       ├─ /            → Angular SPA
                                                       ├─ /api/...     → backend:8081
                                                       └─ /actuator/.. → backend:8081
                                                                          backend ──► mariadb:3306
```

- MariaDB và Backend **không** mở cổng ra ngoài — chỉ truy cập trong mạng nội bộ compose.
- Dữ liệu bền: volume `mariadb-data` (DB) và `backend-data` (media bài viết + log).

---

## 1. Yêu cầu máy chủ

- Docker Engine ≥ 24 và Docker Compose v2 (`docker compose version`).
- RAM tối thiểu ~3–4 GB (MariaDB + JVM + build). Build FE/BE cần thêm RAM tạm thời.
- Cổng 80 (hoặc cổng bạn chọn ở `FRONTEND_PORT`) trống.
- Đồng hồ hệ thống đúng (ảnh hưởng SLA/nhắc hạn).

---

## 2. Chuẩn bị cấu hình

```bash
cd /đường-dẫn/BPMN
cp .env.example .env
# Mở .env và điền TẤT CẢ mật khẩu (xem chú thích trong file):
#   MYSQL_ROOT_PASSWORD, BPM_DB_PASSWORD, BPM_ADMIN_PASSWORD, BPM_HR_DEFAULT_PASSWORD ...
nano .env
```

Bắt buộc đổi khỏi giá trị `CHANGE_ME_*`:
`MYSQL_ROOT_PASSWORD`, `BPM_DB_PASSWORD`, `BPM_ADMIN_PASSWORD`, `BPM_HR_DEFAULT_PASSWORD`.

> `.env` đã nằm trong `.gitignore` — không commit.

---

## 3. Build & khởi chạy

```bash
docker compose build          # build image backend + frontend (bỏ qua test)
docker compose up -d          # chạy nền 3 service
docker compose ps             # kiểm tra trạng thái
```

Lần đầu, `BPM_DDL_AUTO=update` → Hibernate tự tạo bảng. Backend khởi động xong sau khi MariaDB
báo `healthy` (compose chờ tự động).

---

## 4. Kiểm tra sức khỏe

```bash
# Backend (qua nginx):
curl -fsS http://localhost/actuator/health/readiness    # mong đợi {"status":"UP"}
curl -fsS http://localhost/actuator/health/liveness

# Frontend:
curl -I http://localhost/                                # mong đợi 200 + index.html

# Log backend (boot):
docker compose logs -f backend
```

Mở trình duyệt: `http://<IP-server>/` → đăng nhập bằng `admin` / mật khẩu `BPM_ADMIN_PASSWORD`.

---

## 5. Sau khi đăng nhập lần đầu (BẮT BUỘC)

1. **Đổi mật khẩu admin** ngay trong giao diện (mật khẩu seed chỉ để đăng nhập lần đầu).
2. Tạo các tài khoản/vai trò thật, gán quyền chức năng (FEAT_*).
3. Khi schema đã ổn định và **đã backup**: chuyển sang chế độ an toàn hơn (mục 6).

---

## 6. Rà soát bảo mật trước khi mở ra Internet

- [ ] Đã đổi MỌI mật khẩu `CHANGE_ME_*` trong `.env`.
- [ ] Đã đổi mật khẩu `admin` qua giao diện sau lần đăng nhập đầu.
- [ ] `BPM_SEED_DEMO=false` (đã mặc định; profile `prod` cũng không seed demo).
- [ ] **Chuyển `BPM_DDL_AUTO=validate`** sau lần go-live đầu để Hibernate KHÔNG tự đổi schema:
      sửa `.env` → `docker compose up -d backend`. (Mọi thay đổi schema sau đó làm thủ công + backup trước.)
- [ ] **HTTPS**: đặt reverse proxy phía trước (Caddy / nginx-proxy + acme / Cloudflare Tunnel)
      để chấm dứt TLS. Backend đã bật `forward-headers-strategy: framework` nên nhận đúng scheme/host.
      Gợi ý: đặt `FRONTEND_PORT=8080` rồi cho proxy nghe 443 chuyển tiếp về `127.0.0.1:8080`.
- [ ] **Cookie/phiên**: app dùng phiên `HttpSession`. Khi chạy sau HTTPS, đảm bảo proxy chuyển
      header `X-Forwarded-Proto=https` (nginx FE đã set sẵn) để cookie hoạt động đúng.
- [ ] Không publish cổng 3306 / 8081 ra ngoài (compose mặc định đã ẩn).
- [ ] Tường lửa server: chỉ mở 80/443 (hoặc cổng proxy).
- [ ] Lên lịch **backup volume MariaDB** (mục 8).

---

## 7. Xem log & vận hành

```bash
docker compose logs -f backend          # log ứng dụng (stdout)
docker compose logs -f frontend         # log nginx
docker compose logs -f mariadb

# Log file backend (trong volume backend-data):
docker compose exec backend sh -c 'tail -f /app/data/logs/backend.log'

docker compose restart backend          # restart 1 service
docker compose down                     # dừng (giữ volume/dữ liệu)
docker compose up -d --build            # cập nhật khi có code mới
```

> Nâng cấp code: `git pull` → `docker compose build` → `docker compose up -d`.
> Khuyến nghị **backup DB trước mỗi lần nâng cấp**.

---

## 8. Backup & Restore CSDL

DB nằm trong volume `mariadb-data`. Hai cách:

### 8a. Dump logic (khuyến nghị — nhất quán, không khóa bảng)

```bash
# Backup ra file .sql.gz (chạy từ host, gọi vào container):
docker compose exec mariadb sh -c \
  'mariadb-dump --single-transaction --quick --routines --triggers --events \
   -u root -p"$MARIADB_ROOT_PASSWORD" bpm' | gzip -9 > bpm_$(date +%Y%m%d_%H%M%S).sql.gz

# Restore (GHI ĐÈ dữ liệu hiện tại — dừng backend trước):
docker compose stop backend
gunzip -c bpm_YYYYMMDD_HHMMSS.sql.gz | \
  docker compose exec -T mariadb sh -c 'mariadb -u root -p"$MARIADB_ROOT_PASSWORD" bpm'
docker compose start backend
```

> Scripts tham khảo có sẵn: `ops/scripts/backup-db.sh`, `ops/scripts/restore-db.sh`
> (dùng khi DB expose ra host; có thể chỉnh `BPM_DB_HOST` cho phù hợp).
> Đặt cron 1h sáng: `0 1 * * * /đường-dẫn/backup.sh`.

### 8b. Snapshot volume (toàn bộ data dir)

```bash
docker run --rm -v bpmn_mariadb-data:/data -v "$PWD":/backup alpine \
  tar czf /backup/mariadb-data_$(date +%F).tar.gz -C /data .
```
(Tên volume thực tế thường là `<thư-mục-dự-án>_mariadb-data`; kiểm bằng `docker volume ls`.)

Backup luôn nên gồm cả volume `backend-data` (chứa media bài viết).

---

## 9. Khắc phục nhanh

| Triệu chứng | Kiểm tra |
|---|---|
| Backend không lên | `docker compose logs backend` — sai mật khẩu DB? `BPM_DDL_AUTO=validate` nhưng bảng chưa tồn tại? |
| 502 khi gọi `/api` | Backend chưa healthy; chờ `start_period`, xem readiness probe |
| Đăng nhập trả 401 liên tục sau HTTPS | Proxy chưa chuyển `X-Forwarded-Proto`; kiểm cookie phiên |
| Upload ảnh/video lỗi 413 | Tăng `client_max_body_size` (nginx FE) và proxy phía trước nếu có |
