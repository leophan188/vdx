# Story 2.6: Form builder kéo-thả + loại trường cốt lõi

Status: review

## Story

As an **admin IT**,
I want **thiết kế form bằng kéo-thả với bộ loại trường cốt lõi**,
so that **mỗi bước có form phù hợp mà không cần code (FR-B01, FR-B02, AD-4)**.

## Acceptance Criteria

1. **Given** form builder, **When** kéo trường (văn bản, số, ngày-giờ, có/không, danh sách chọn, tải file, rich-text, bảng nhiều dòng) và cấu hình thuộc tính, **Then** form sinh tự động từ metadata. _(FR-B01, FR-B02)_
2. **Given** schema biểu mẫu, **When** lưu, **Then** lưu **JSON-column** (`schema_json`). _(NFR-10, AD-4)_
3. **Given** danh sách biểu mẫu, **When** quản trị, **Then** CRUD (tạo/đổi tên/xóa). Audit (AD-6).

## Tasks / Subtasks

- [x] **BE**: `FormDefinition` (formKey, name, status DRAFT, `schemaJson` TEXT, version) + repo + `FormService` (create/list/get/rename/saveSchema/delete, audit) + `FormController` `/api/v1/forms` + DTO + security. Test (43/43).
- [x] **FE danh sách**: `form.service` + màn `forms/` (grid CRUD) + nav nhóm "Cấu hình quy trình → Biểu mẫu" + route.
- [x] **FE builder** (lazy `/forms/:id`): palette **8 loại trường cốt lõi** (bấm để thêm) · canvas **kéo-thả sắp xếp** (Angular CDK `cdkDropList`/`cdkDrag`) · panel thuộc tính (nhãn/mã/loại/bắt buộc/placeholder; dropdown: options STATIC/CATALOG; table: cấu hình cột) · **Xem trước** (modal render form từ metadata).

## Dev Notes

- **8 loại trường:** text · number · datetime · boolean · dropdown · file · richtext · table (bảng nhiều dòng có cột tuỳ chỉnh). Bộ đầy đủ hơn → Epic 5.
- **Schema JSON** `{fields:[{key,label,type,required,placeholder,optionSource,options|catalog,columns}]}` — lưu `schema_json` (AD-4). Gắn form vào bước + dữ liệu xuyên suốt + quyền trường = Story 2.9; ẩn/hiện điều kiện + validation = 2.8; versioning/snapshot = 2.10.
- **Dep mới:** `@angular/cdk@21` (drag-drop). Builder lazy-load (chunk ~75 kB).
- **Tái dụng:** design-system v2 (data-grid, modal, confirm, toast, page-header). BE theo [[bpm-dev-conventions]].
- **DemoSeeder**: +1 biểu mẫu "Phiếu trình hồ sơ" với đủ 8 loại trường — verify live.

### References
- [Source: epics.md#Story-2.6] · [Source: ARCHITECTURE-SPINE.md#AD-4]

## Dev Agent Record

### Completion Notes List
- ✅ BE **43/43** (+3 test FormService: saveSchema lưu JSON, chặn trùng key, rename/delete). FE **23/23**, build clean (builder lazy 75 kB). Verify live: demo form "Phiếu trình hồ sơ" 8 trường.
- ⚠️ Sửa kèm: lỗi build từ thay đổi song song (login ONEConnect) — `@use '@fontsource/sora/400.css'` numeric-namespace lỗi → chuyển nạp font Sora/Inter qua `angular.json styles`; thêm `login.scss` stub để build chạy (login redesign do phiên khác làm).

### File List
Backend (mới): `domain/form/FormDefinition.java`, `infrastructure/FormDefinitionRepository.java`, `application/FormService.java`, `api/FormController.java`, `api/dto/FormDto.java`, `test/FormServiceTest.java`; sửa `SecurityConfig` (+/forms), `DemoSeeder` (+biểu mẫu demo, +FormService).
Frontend (mới): `core/form.service.ts`, `forms/{forms.ts,forms.html}`, `forms/builder/{builder.ts,builder.html}`; sửa `app.routes.ts` (+forms, +lazy builder), `app.html` (nav Biểu mẫu), `app.spec.ts` (nav=8), `angular.json` (+cdk drag, +Sora/Inter), `styles.scss`, `_components.scss` (builder CSS). Dep `@angular/cdk@21`.
