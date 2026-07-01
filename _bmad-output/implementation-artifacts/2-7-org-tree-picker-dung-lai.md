# Story 2.7: Org-tree picker dùng lại

Status: review

## Story
As an **admin IT**, I want **thành phần chọn nhân sự/đơn vị theo cây tổ chức**,
so that **dùng chung cho cả form lẫn quy tắc phân công (FR-B03)**.

## Acceptance Criteria
1. **Given** một trường kiểu chọn-người, **When** mở picker, **Then** hiển thị **cây tổ chức** (đơn vị → chức danh → người giữ) cho chọn. _(UX-DR7)_
2. **Given** cùng component, **When** dùng ở cấu hình phân công, **Then** **tái dụng** đúng. _(FR-B03)_

## Dev Notes
- **Component dùng chung** `app/shared/org-tree-picker/` — `<org-tree-picker mode="user|position|unit" [(value)]="id">`: nạp org-units + positions + users, dựng **cây gập/mở**, chọn node → emit id. Highlight + mở-rộng-tất-cả.
- **Tái dụng 2 nơi (đúng AC):**
  - **Phân công:** màn Vị trí → modal "Gán người giữ" dùng `mode="user"` (thay select phẳng).
  - **Form:** form builder thêm **loại trường "Chọn theo cây tổ chức"** (`orgtree` + `pickMode`) → preview render picker.
- Build clean, FE **23/23**.

## File List
FE (mới): `shared/org-tree-picker/{org-tree-picker.ts,org-tree-picker.html}`; sửa `positions/{positions.ts,positions.html}` (assign dùng picker), `forms/builder/{builder.ts,builder.html}` (+type orgtree), `styles/_components.scss` (org-picker CSS), `positions.spec.ts` (mock +all).
