# Story 2.8: Ẩn/hiện điều kiện + validation

Status: review

## Story

As an **admin IT**,
I want **cấu hình trường ẩn/hiện theo điều kiện và quy tắc validation**,
so that **form phản ứng theo dữ liệu và đảm bảo nhập đúng (FR-B04, FR-B05)**.

## Acceptance Criteria

1. **Given** một form, **When** đặt **điều kiện hiển thị** (vd chọn "có phối hợp" mới hiện đơn vị phối hợp), **Then** runtime **ẩn/hiện đúng**. _(FR-B04)_
2. **Given** một trường, **When** đặt **validation** (bắt buộc / định dạng email-SĐT / min-max / độ dài tối đa), **Then** runtime **chặn submit khi vi phạm**. _(FR-B05)_
3. **Given** trường đang ẩn, **When** validate, **Then** **bỏ qua** (không bắt buộc trường ẩn).

## Tasks / Subtasks

- [x] **FE builder model**: `FormField` + `visibleWhen{field, op(eq/ne/truthy), value}` + `validation{min, max, maxLength, format(none/email/phone)}`. Lưu trong schema JSON.
- [x] **FE builder panel**: mục **Điều kiện hiển thị** (chọn trường + toán tử + giá trị) + **Validation** theo loại (number→min/max; text/richtext→maxLength, text→định dạng email/SĐT).
- [x] **FE xem trước tương tác**: bind `values`, **ẩn/hiện** theo `isVisible()`, nút **"Kiểm tra & gửi"** chạy validate → tô đỏ lỗi + toast; trường ẩn không validate.
- [x] **DemoSeeder**: form thêm trường điều kiện (`don_vi_phoi_hop` hiện khi `co_phoi_hop`) + validation (số lượng min/max, email format, độ dài).

## Dev Notes

- **Không đổi schema BE** — `visibleWhen`/`validation` là thuộc tính trong `schema_json` của FormDefinition.
- **Format presets** GĐ1: email, phone (regex). Pattern tuỳ chỉnh đầy đủ → Epic 5.
- **Runtime thực thi form** (instance điền form) = Epic 3; 2.8 lo **thiết kế + xem-trước** đã đủ chứng minh AC (ẩn/hiện + chặn submit).
- **Tái dụng:** form builder 2.6 + design-system. Verify live: demo form 11 trường có điều kiện + validation.

### References
- [Source: epics.md#Story-2.8] · [Source: ARCHITECTURE-SPINE.md#AD-4]

## Dev Agent Record

### Completion Notes List
- ✅ FE **23/23**, build clean (builder lazy 82 kB). Xem-trước nay tương tác: bật "có phối hợp" → hiện "đơn vị phối hợp"; bấm Kiểm tra → báo lỗi email/min-max/bắt buộc, trường ẩn bỏ qua. Verify live qua DemoSeeder.

### File List
Frontend: sửa `forms/builder/builder.ts` (+visibleWhen/validation, isVisible/errorFor/validateAll/openPreview/setVal, otherFields/toggleCondition/ensureValidation), `builder.html` (panel điều kiện+validation, preview tương tác).
Backend: sửa `infrastructure/DemoSeeder.java` (form +điều kiện +validation).
