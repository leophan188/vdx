# Runbook 5.5 — Vòng đời & Partition bảng audit vận hành

Bối cảnh: `audit_event` (và `ACT_HI_*` của Flowable) là bảng **append-only**, tăng tuyến tính theo vận hành.
Mục tiêu: giữ truy vấn nhanh + chi phí lưu trữ kiểm soát được, KHÔNG mất tính bất biến/toàn vẹn.

## Nguyên tắc
- **KHÔNG xóa** audit trong thời hạn pháp lý/nghiệp vụ. Vòng đời = **partition theo thời gian → archive partition cũ → drop sau khi đã archive**.
- App đã chặn UPDATE/DELETE audit ở tầng JPA (`@PreUpdate/@PreRemove` ném lỗi). Thao tác vòng đời làm ở tầng DBA bằng partition (DDL), không qua app.

## Partition theo RANGE tháng (MariaDB)
Áp dụng khi khởi tạo schema prod (hoặc ALTER khi bảng còn nhỏ):
```sql
ALTER TABLE audit_event
  PARTITION BY RANGE (TO_DAYS(created_at)) (
    PARTITION p2026_06 VALUES LESS THAN (TO_DAYS('2026-07-01')),
    PARTITION p2026_07 VALUES LESS THAN (TO_DAYS('2026-08-01')),
    PARTITION pmax     VALUES LESS THAN MAXVALUE
  );
```
> `created_at` phải nằm trong PRIMARY KEY/UNIQUE để partition (ràng buộc MariaDB). Cân nhắc PK kép `(id, created_at)`.

### Tạo partition tháng mới (cron hàng tháng, trước khi sang tháng)
```sql
ALTER TABLE audit_event REORGANIZE PARTITION pmax INTO (
  PARTITION p2026_08 VALUES LESS THAN (TO_DAYS('2026-09-01')),
  PARTITION pmax     VALUES LESS THAN MAXVALUE
);
```

### Archive + drop partition quá hạn lưu (vd giữ 24 tháng)
```sql
-- 1) Xuất partition ra file lưu trữ lạnh trước
SELECT * FROM audit_event PARTITION (p2024_06) INTO OUTFILE '/archive/audit_2024_06.csv';
-- 2) Sau khi xác nhận đã archive an toàn:
ALTER TABLE audit_event DROP PARTITION p2024_06;
```

## Flowable history (`ACT_HI_*`)
- Giảm tải bằng cấu hình mức history (đang `audit` mặc định). Nếu chỉ cần ít hơn: đặt `flowable.history-level=activity` (mất chi tiết biến) — cân nhắc theo yêu cầu kiểm toán.
- Dọn instance lịch sử đã kết thúc lâu: dùng API/job dọn history của Flowable theo retention, HOẶC partition `ACT_HI_PROCINST`/`ACT_HI_TASKINST` theo `END_TIME_` tương tự trên.

## Giám sát
- Cảnh báo khi `audit_event` vượt ngưỡng dòng/dung lượng.
- Theo dõi thời gian truy vấn trang Kiểm toán; nếu chậm → thêm index `(object_type, object_id)` + đảm bảo partition pruning theo `created_at`.
