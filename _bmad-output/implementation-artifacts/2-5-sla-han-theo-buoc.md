# Story 2.5: SLA/hạn theo bước

Status: review

## Story
As an **admin IT**, I want **gán hạn xử lý (SLA) cho bước**,
so that **hệ thống theo dõi và cảnh báo quá hạn (FR-A08, liên kết FR-D07)**.

## Acceptance Criteria
1. **Given** một bước, **When** cấu hình, **Then** lưu **SLA (giờ)** trong định nghĩa bước. _(FR-A08)_
2. **Given** việc tới bước có SLA, **When** runtime, **Then** tính hạn + lưu trên task; đến hạn bật **cờ quá hạn** + phát thông báo. _(runtime → Epic 3)_

## Dev Notes
- **Phần CẤU HÌNH đã hoàn tất ở Story 2.1**: tab "Người thực hiện" có trường **"Hạn xử lý (SLA, giờ)"** → lưu `slaHours` trong `stepsMeta`. DemoSeeder: Task_Tao=8h, Task_Duyet=24h.
- **Còn lại = runtime (Epic 3, Story 3.7):** khi tạo task tính `deadline = now + slaHours`, lưu cột; scheduler bật cờ `overdue` (materialized, AD-5) + thông báo (Nhóm H). Gia hạn (FR-D07) gỡ cờ.
- **[ASSUMPTION]** SLA toàn-quy-trình (ngoài SLA bước) có thể thêm field cấp process sau nếu cần; GĐ1 dùng SLA theo bước.

### References
- [Source: epics.md#Story-2.5] · liên quan [[2-1-process-designer-keo-tha-metadata-buoc]]
