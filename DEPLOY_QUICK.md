# DEPLOY_QUICK — Triển khai nhanh BPM Platform trên server Linux nội bộ

Hướng dẫn copy-paste để đưa hệ thống lên server bằng Docker Compose.
Server này đã có sẵn nhiều container khác (jenkins, nginx-proxy-manager, các dự án vts/vcb...),
nên frontend chạy ở cổng **8085** để không xung đột.

---

## 0. Yêu cầu server (kiểm tra 1 lần)

```bash
docker --version
docker compose version   # phải là plugin v2
```

- RAM trống tối thiểu ~2GB (build Maven + npm khá nặng).
- Cổng **8085** phải trống trên server.
- Chỉ frontend mở ra host (8085); backend (8081) và MariaDB (3306) chạy **nội bộ**
  trong mạng compose `vdx_bpm-net`, KHÔNG đụng các container/DB cũ.

---

## 1. Pull code

```bash
git clone https://github.com/anhpt18890-eng/vdx.git
cd vdx
# nếu đã clone trước đó:
# git pull
```

---

## 2. Tạo file .env (BẮT BUỘC — không có trong git)

File `.env` bị `.gitignore` nên KHÔNG đi theo code. Phải tạo trực tiếp trên server:

```bash
cp .env.example .env
nano .env
```

Sửa các giá trị sau trong `.env`:

| Biến | Việc cần làm |
|------|--------------|
| `MYSQL_ROOT_PASSWORD` | đổi thành mật khẩu mạnh |
| `BPM_DB_PASSWORD` | đổi thành mật khẩu mạnh |
| `BPM_ADMIN_PASSWORD` | mật khẩu tài khoản `admin` (đổi lại sau lần đăng nhập đầu) |
| `BPM_HR_DEFAULT_PASSWORD` | mật khẩu mặc định khi tạo nhân sự |
| `BPM_ONLYOFFICE_JWT_SECRET` | chuỗi bí mật bất kỳ (không dùng nhưng nên đổi) |
| `FRONTEND_PORT` | đặt `8085` (tránh xung đột cổng 80 của nginx-proxy-manager) |
| `BPM_DDL_AUTO` | giữ `update` cho lần go-live ĐẦU TIÊN |

---

## 3. Build & chạy

```bash
docker compose up -d --build
```

Lần đầu build lâu vài phút (Maven tải dependency + build Angular). Cứ để chạy.

Kiểm tra trạng thái (chờ cả 3 service `healthy`):

```bash
docker compose ps
docker compose logs -f backend    # Ctrl+C để thoát khi thấy app khởi động xong
```

---

## 4. Truy cập

- Trực tiếp: `http://<IP-server>:8085`
- Đăng nhập bằng tài khoản `admin` + `BPM_ADMIN_PASSWORD` đã đặt. **Đổi mật khẩu ngay** sau lần đăng nhập đầu.

(Tuỳ chọn) Dùng qua domain qua **nginx-proxy-manager**:
tạo Proxy Host trỏ domain nội bộ → `<IP-server>:8085`, bật SSL nếu cần.

---

## 5. Sau khi schema ổn định (khuyến nghị)

Khi hệ thống đã chạy ổn và đã **backup DB**, chuyển Hibernate sang chế độ an toàn:

```bash
nano .env         # đổi BPM_DDL_AUTO=validate
docker compose up -d backend
```

---

## Lệnh vận hành thường dùng

```bash
# Cập nhật code mới rồi build lại
git pull && docker compose up -d --build

# Bật lại (không build) sau khi restart server
docker compose up -d

# Xem log
docker compose logs -f backend
docker compose logs -f frontend

# Dừng (giữ nguyên dữ liệu volume)
docker compose down

# CẢNH BÁO: xoá cả dữ liệu DB (mất sạch) — chỉ dùng khi muốn làm lại từ đầu
# docker compose down -v
```

---

## Backup nhanh MariaDB

```bash
docker compose exec mariadb \
  sh -c 'mariadb-dump -uroot -p"$MARIADB_ROOT_PASSWORD" bpm' > backup_$(date +%F).sql
```

---

## Ghi chú xung đột cổng (server này)

Frontend `vdx` ở **8085**. Các cổng đã bận trên server: 80, 443, 4099, 4200, 5432,
6379, 8080, 8081, 8082, 8083, 8099, 8181, 9000, 9001, 3307, 3308, 3309, 50000.
Nếu 8085 sau này bị chiếm, đổi `FRONTEND_PORT` trong `.env` sang cổng trống khác
rồi `docker compose up -d frontend`.
