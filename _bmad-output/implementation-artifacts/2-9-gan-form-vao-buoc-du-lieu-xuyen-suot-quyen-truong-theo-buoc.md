# Story 2.9: Gắn form vào bước + dữ liệu xuyên suốt + quyền trường theo bước

Status: review

## Story

As a **hệ thống**,
I want **gắn form cho từng bước, mang dữ liệu xuyên suốt, và áp quyền trường theo bước**,
so that **mỗi bước dùng đúng form và chỉ thấy/sửa được trường được phép (FR-B06, FR-B07, FR-B08)**.

## Acceptance Criteria

1. **Given** một bước (userTask), **When** cấu hình, **Then** **gắn một biểu mẫu** (FormDefinition) cho bước. _(FR-B06)_
2. **Given** form gắn ở nhiều bước, **When** chạy, **Then** **dữ liệu mang xuyên suốt** (cùng mã trường) giữa các bước. _(FR-B07 — runtime ở Epic 3)_
3. **Given** một bước có form, **When** cấu hình quyền trường, **Then** mỗi trường có **quyền theo bước** (Sửa được / Chỉ xem / Ẩn). _(FR-B08)_
4. **Given** lưu thiết kế, **Then** `formId` + `fieldPerms` lưu trong `stepsMeta` (JSON) cùng định nghĩa.

## Tasks / Subtasks

- [x] **FE designer**: thêm tab **"Biểu mẫu"** vào modal cấu hình bước — chọn form (từ `FormService.list`); nạp các trường của form; ma trận **quyền trường** (EDIT/READONLY/HIDDEN) mỗi trường. Lưu `formId`/`fieldPerms` vào `stepsMeta`.
- [x] **DemoSeeder**: gắn form "Phiếu trình hồ sơ" vào cả 2 bước — Task_Tao = EDIT toàn bộ; Task_Duyet = READONLY toàn bộ (demo dữ liệu xuyên suốt + quyền theo bước).

## Dev Notes

- **Không đổi schema BE:** `formId` + `fieldPerms{fieldKey: EDIT|READONLY|HIDDEN}` chỉ là thuộc tính trong `stepsMeta` JSON của bước (đã lưu qua `PUT /processes/{id}/design`).
- **Dữ liệu xuyên suốt (FR-B07) runtime = Epic 3:** instance mang một đối tượng dữ liệu (JSON) tích lũy qua các bước; bước sau đọc/ghi theo `fieldPerms`. 2.9 lo phần **cấu hình** (gắn form + quyền).
- **Tab "Khai báo metadata"** cũ đổi nhãn → **"Trường thêm"** (trường ad-hoc ngoài biểu mẫu). Org-tree picker (2.7) + ẩn/hiện điều kiện & validation (2.8) + versioning form snapshot (2.10) là story riêng.
- **Tái dụng:** `FormService`, `Tabs`, `data-grid`/select của design-system. Verify live: demo Task_Tao(EDIT)/Task_Duyet(READONLY) cùng form `phieu-trinh-ho-so`.

### References
- [Source: epics.md#Story-2.9] · [Source: ARCHITECTURE-SPINE.md#AD-4]

## Dev Agent Record

### Completion Notes List
- ✅ FE **23/23**, build clean. Designer modal cấu hình bước nay 4 tab: Người thực hiện · **Biểu mẫu** · Trường thêm · Thông báo. Verify live qua DemoSeeder.
- ⚠️ Sửa kèm: cập nhật `app.spec` brand assertion `Nền tảng BPM`→`ONEConnect` (do rebrand song song).

### File List
Frontend: sửa `processes/designer/designer.ts` (+FormService, StepMeta formId/fieldPerms, tab form, loadFormFields/fieldPerm/setFieldPerm), `designer.html` (+tab Biểu mẫu); `app.spec.ts` (brand).
Backend: sửa `infrastructure/DemoSeeder.java` (tạo form trước, gắn formId + fieldPerms vào 2 bước).
