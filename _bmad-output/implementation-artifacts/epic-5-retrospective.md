# Epic 5 — Retrospective (HOÀN THÀNH, 2026-06-26)

Trạng thái: **7/7 story `done`** · epic-5 `done`. BE 55/55, FE 23/23.

## Mục tiêu epic
Mở rộng generic (đủ loại luồng/trường) + hoàn thiện vận hành (backup, partition audit, NFR, UX/a11y).

## Đã giao (7 story)
| Story | Kết quả |
|---|---|
| 5.1 Đầy đủ loại luồng | tuần tự + exclusive + **vòng lặp/quay-lại** + **parallel fork/join** (test) |
| 5.2 Đầy đủ loại trường | builder 14 loại (+textarea/date/radio/multiselect/section), render đồng bộ runtime |
| 5.3 Affordance designer | **"Kiểm tra cấu hình"**: cảnh báo bước chưa gán người / chưa hành động / gateway chưa điều kiện / thiếu start-end |
| 5.4 Backup & khôi phục | scripts mariadb-dump `--single-transaction` + restore + runbook (cron, 3-2-1, kiểm chứng) |
| 5.5 Partition audit | runbook partition RANGE theo tháng + archive→drop (giữ append-only ở tầng DBA) |
| 5.6 UX polish + a11y AA | skip-link, landmarks, aria-live toast, chuông aria, reduced-motion, focus-visible, lang=vi |
| 5.7 NFR vận hành | Actuator health/liveness/readiness, `application-prod.yml`, HikariCP, DB index, graceful shutdown, runbook + kế hoạch i18n |

## Điều làm tốt
- **Phân biệt rõ "code verify được" vs "ops cần môi trường"**: phần code (actuator, prod profile, index, validate designer) làm + verify ngay; phần phụ thuộc production (backup/partition) giao bằng **script + runbook** chạy được khi triển khai — không giả lập vô nghĩa trên H2.
- **Trung thực về i18n**: thay vì refactor vội toàn bộ template, lập kế hoạch `@angular/localize` thành hạng mục riêng — tránh nợ kỹ thuật ẩn.
- **5.1 phần lớn đã có sẵn** từ Epic 3 (gateway/điều kiện); chỉ cần test parallel để xác nhận "đầy đủ loại luồng" → tránh làm lại.
- **5.3 dùng đúng dữ liệu sẵn có** (elementRegistry + stepsMeta) để kiểm tra, không thêm hạ tầng.

## Bài học / lưu ý
- **YAML duplicate key**: thêm block `spring:` thứ hai trong application.yml suýt gây lỗi — đã gộp vào block hiện có. Cẩn thận khi chèn config.
- **i18n để muộn** tốn công hơn nếu làm sớm; nhưng với GĐ1 một ngôn ngữ, hoãn là hợp lý — ghi nhận để không bất ngờ.
- **Ops chưa nghiệm thu thực tế**: backup/partition mới ở mức script+runbook, **chưa chạy trên MariaDB production** — phải kiểm chứng ở môi trường thật trước go-live (đã ghi trong runbook).

## Số liệu
BE 55 test · FE 23 test · 0 fail. 7 story done. Artifact ops mới: `ops/scripts/`, `ops/runbooks/`.
