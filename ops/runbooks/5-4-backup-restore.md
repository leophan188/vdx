# Runbook 5.4 — Backup nhất quán & Khôi phục (đã kiểm chứng)

Mục tiêu: backup định kỳ, nhất quán, không gián đoạn; khôi phục được trong thời gian RTO mục tiêu.

## Thành phần
- `ops/scripts/backup-db.sh` — `mariadb-dump --single-transaction` (snapshot InnoDB nhất quán, không khóa bảng) → gzip, tự dọn backup cũ.
- `ops/scripts/restore-db.sh` — khôi phục từ file `.sql.gz` (có xác nhận thủ công).

## Cấu hình (biến môi trường)
| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `BPM_DB_HOST/PORT/NAME/USER/PASSWORD` | localhost/3306/bpm/bpm/— | Kết nối DB |
| `BPM_BACKUP_DIR` | /var/backups/bpm | Thư mục lưu backup |
| `BPM_BACKUP_RETENTION_DAYS` | 14 | Giữ N ngày |

## Lịch backup (cron)
```
0 1 * * *  BPM_DB_PASSWORD=*** /opt/bpm/ops/scripts/backup-db.sh >> /var/log/bpm/backup.log 2>&1
```
> Khuyến nghị **3-2-1**: 3 bản sao, 2 phương tiện, 1 ngoài site (đồng bộ `BPM_BACKUP_DIR` ra lưu trữ ngoài: S3/MinIO/NAS).

## Khôi phục (DR)
1. Dừng backend (systemd/k8s) để tránh ghi đồng thời.
2. `restore-db.sh /var/backups/bpm/bpm_YYYYMMDD_HHMMSS.sql.gz` → gõ `YES`.
3. Khởi động backend với `--spring.profiles.active=prod` (`ddl-auto=validate` sẽ xác nhận schema khớp).
4. Kiểm chứng: `GET /actuator/health/readiness` = UP; đăng nhập + mở "Theo dõi quy trình".

## Kiểm chứng backup định kỳ (BẮT BUỘC)
Backup chưa khôi phục thử = chưa có backup. Hàng tháng: restore vào DB staging + smoke test (đăng nhập, tạo yêu cầu, xử lý việc).

## Lưu ý về Flowable
Bảng `ACT_*` của Flowable nằm CÙNG database `bpm` → backup/restore ở trên đã bao trùm cả engine (instance/task/history). Không cần backup riêng.
