---
name: BPM Platform — Design System
status: final
version: 2.0.0
baseline: "Chuẩn ĐÃ LƯU 2026-06-24 — mọi màn hình sau BẮT BUỘC tuân theo. Sửa chuẩn = cập nhật file này + /styleguide."
created: 2026-06-24
updated: 2026-06-24
extends: ./DESIGN.md
implements:
  - frontend/src/styles/_tokens.scss
  - frontend/src/styles/_base.scss
  - frontend/src/styles/_components.scss
  - frontend/src/styles/_shell.scss
  - frontend/src/app/shared/status-badge/status-badge.ts
gallery: frontend → /styleguide
---

# DESIGN-SYSTEM.md — Hệ thống thiết kế dùng lại

> Tài liệu này **hiện thực hóa** bản sắc thị giác thành **token + component dùng lại** để mọi epic sau (process designer, form builder, hộp thư việc, dashboard, báo cáo) **ráp lại** chứ không vẽ lại. Quy tắc vàng: **không hardcode màu/kích thước trong component — luôn dùng token**.

> **CẬP NHẬT BRAND (2026-06-24):** theo yêu cầu, brand chuyển sang **xanh Nicotex** (`--color-primary #16a34a`, sidebar xanh-đậm `#14532d`) — tham khảo giao diện DMS, **ghi đè** màu xanh-hành-chính trong DESIGN.md. Thêm **dark mode** (`<html data-theme>`), **Dashboard KPI**, **lưới dữ liệu nâng cao** (lọc + hàng mở rộng + chọn số dòng/trang + phân trang số) và **popup CRUD**.

## 1. Nguồn sự thật

| Lớp | Vị trí | Vai trò |
|---|---|---|
| Token | `styles/_tokens.scss` (`:root` CSS vars) | Toàn bộ giá trị màu/chữ/khoảng/bo/bóng — **một nguồn duy nhất** |
| Base | `styles/_base.scss` | reset + phần tử thô (`button/input/select/label/table`) đã bám token |
| Component | `styles/_components.scss` | `.btn .badge .card .data-table .form .alert .field` |
| Shell | `styles/_shell.scss` | khung sidebar-trái + topbar mảnh + vùng nội dung |
| Component Angular | `app/shared/**` | component tái dụng có logic (vd `status-badge`) |
| Gallery sống | route `/styleguide` | nơi tra cứu & copy mẫu — cập nhật khi thêm component |

## 2. Token (trích — đầy đủ ở `_tokens.scss`)

- **Màu:** `--color-primary #1E50A0` (+hover), `--color-surface/-alt`, `--color-border`, `--color-text/-muted`.
- **Trạng thái (bất biến nghĩa):** `--status-pending #B7791F` · `--status-active #1E66C7` · `--status-done #2F855A` · `--status-cancel #718096`. Mỗi màu kèm nền nhạt `--status-*-bg` cho badge.
- **Quá hạn:** `--overdue #C53030` — **trực giao** (AD-5): là chấm/viền đỏ chồng lên badge, **không** thay màu badge gốc; **không** tái dụng cho nút xóa/lỗi.
- **Chữ:** `--font-sans` = **Be Vietnam Pro** (self-host `@fontsource/be-vietnam-pro`, weight 400/500/600/700 — phủ đủ dấu tiếng Việt, offline-capable on-prem) → Segoe UI → Roboto; `--font-mono`; thang `--text-xs..2xl` (base 14px).
- **Khoảng (lưới 4px):** `--space-1..8` = 4·8·12·16·20·24·32·40.
- **Bo:** `--radius-sm 4 / md 6 / lg 10 / full`. **Bóng:** `--shadow-sm` (lớp phẳng), `--shadow-pop` (dropdown/dialog). **Kích thước:** `--control-h 36`, `--row-h 40`, `--sidebar-w 220`, `--topbar-h 52`, `--focus-ring`.

## 3. Component chuẩn

- **Button** — `.btn` (+`.btn--primary` đặc xanh / `.btn--secondary` viền / `.btn--ghost` phụ / `.btn--danger` chỉ cho hành động phá hủy). Cao `--control-h`. *Phần tử `<button>` thô mặc định = primary để màn cũ vẫn đẹp.*
- **Input/Select** — cao `--control-h`, viền `--border`, focus hiện `--focus-ring`. Bọc trong `.field` (label muted + control + `.field__error`).
- **Form** — `.form` (lưới dọc, gap `--space-2`, tối đa 420px); alias `.create-form` giữ tương thích màn 1.1–1.8.
- **Badge trạng thái** — `<status-badge [status] [overdue] [label]>` → pill `--radius-full`, nền `--status-*-bg` + chữ `--status-*`; `overdue` thêm chấm đỏ. **Đây là component bắt buộc dùng** cho mọi nơi hiện trạng thái nhiệm vụ (Epic 3/4) để đảm bảo nhất quán.
- **Bảng** — `.data-table`: header `--surface-alt`, zebra, hàng `--row-h`, cột hành động phải. Sticky header khi cuộn.
- **Card** — `.card` (viền + `--radius-lg` + `--shadow-sm`).
- **Alert/Hint** — `.alert--error/--success/--info`, `.hint` (chú thích muted).
- **PageHeader + Breadcrumb** — `<app-page-header title subtitle [breadcrumb]>` (slot `[actions]`): tiêu đề + mô tả (trái) · breadcrumb 🏠|Nhóm/Màn + nút (phải). Đầu MỌI màn.
- **Avatar** — `<app-avatar [name] [size] [bg]>`: vòng tròn chữ-cái (topbar + ô bảng người dùng).
- **StatCard (KPI)** — `<stat-card [icon] [label] [value] [trend] [tint]>` cho dashboard: icon nền pastel + số lớn + xu hướng.
- **DataGrid** — `<data-grid [columns] [rows] [expandable] [pageSize] [pageSizes]>`: toolbar (slot `[gridFilters]` cho dropdown lọc, `[gridActions]` cho nút), tìm kiếm, sort theo cột, **hàng mở rộng** (`<ng-template gridDetail let-row>`), custom cell (`<ng-template gridCell="key" let-row>`), chân lưới "Hiển thị x–y / tổng z" + chọn số dòng/trang + phân trang số.
- **Modal / ConfirmDialog** — `<app-modal [open] title [wide] (closed)>` (slot `[modalFooter]`) + `<app-confirm-dialog [danger]>` cho thêm/sửa/xóa; đóng bằng ×/ESC/click nền.
- **Toast** — `inject(ToastService).success|info|warning|error(title, text?)`; host `<app-toast-host/>` đặt một lần ở shell. Tự ẩn ~4s. **Mọi phản hồi thao tác dùng toast** (không alert trong trang).
- **Stepper** — `<app-stepper [steps] [current]>` cho wizard nhiều bước.
- **Tabs** — `<app-tabs [tabs] [(active)]>`; cha tự render panel theo `active()`.
- **Theme** — `ThemeService.toggle()` đổi `<html data-theme="light|dark">`, lưu localStorage; nút ☀️/🌙 ở topbar. Dark mode tự đổi vì component chỉ đọc semantic token.
- **Canvas (Epic 2)** — `.canvas` nền chấm lưới + `.palette` trái + `.props` phải (khung đã đặt sẵn token, dựng chi tiết khi vào process/form builder).

## 4. App shell (sidebar-trái — UX-DR3)

```
┌────────┬───────────────────────────────┐
│ BPM    │ topbar mảnh: tìm kiếm 🔔 admin▾ │
│ ─────  ├───────────────────────────────┤
│ nav    │                               │
│ dọc    │   .content (.page bên trong)  │
│ (lọc   │                               │
│ vai    │                               │
│ trò)   │                               │
│ ─────  │                               │
│ ⚙ HTK  │                               │
└────────┴───────────────────────────────┘
```

- Sidebar **thu gọn được** (nút ☰ → chỉ icon). Điều hướng **lọc theo vai trò** (chỉ ADMIN thấy khu quản trị — AC-4 Story 1.4).
- Topbar mảnh: chừa chỗ **tìm kiếm toàn cục** + **chuông thông báo** (Epic 4) + hồ sơ/đăng xuất.
- **Chưa đăng nhập → không render shell**, chỉ trang đăng nhập căn giữa.

## 4b. Công thức màn hình CRUD (COPY khi dựng màn mới)

Mọi màn quản trị dựng theo đúng khuôn này (đã áp cho accounts/org/positions/roles/audit — soi `accounts.*` làm mẫu chuẩn):

```html
<section class="page">
  <app-page-header title="…" subtitle="…"
                   [breadcrumb]="[{ label: 'Nhóm' }, { label: 'Màn' }]" />

  @if (error()) { <p class="alert alert--error" role="alert">{{ error() }}</p> }
  @if (message()) { <p class="alert alert--success" role="status">{{ message() }}</p> }

  <data-grid [columns]="cols" [rows]="rows()" title="…" emptyText="…">
    <button gridFilters class="btn btn--secondary">🔽 Bộ lọc</button>     <!-- lọc -->
    <button gridActions class="btn btn--primary" (click)="openCreate()">＋ Thêm</button>
    <ng-template gridCell="user" let-r>
      <span class="cell-user"><app-avatar [name]="r.name" [size]="28" /> {{ r.name }}</span>
    </ng-template>
    <ng-template gridCell="status" let-r><status-badge [status]="r.kind" [label]="r.label" /></ng-template>
    <ng-template gridCell="actions" let-r>
      <span class="cell-actions"><button class="btn btn--secondary btn--sm">…</button></span>
    </ng-template>
  </data-grid>
</section>

<app-modal [open]="createOpen()" title="Thêm…" (closed)="createOpen.set(false)"> …form… </app-modal>
<app-confirm-dialog [open]="delOpen()" [danger]="true" (confirm)="…" (cancel)="…" />
```

**State chuẩn trong component:** `rows = signal([])`, `createOpen/editOpen/delOpen/detailOpen = signal(false)`, `cols: GridColumn[]`. CRUD đi qua modal, xóa qua confirm-dialog, **phản hồi bằng toast**, mọi thao tác `reload()` lại grid.

### Flow đầy đủ mẫu — module Tài khoản (12 trạng thái, soi `accounts.*`)

Module Tài khoản là **bản mẫu hoàn chỉnh** cho mọi module CRUD sau (hồ sơ, quy trình, biểu mẫu…):
1. **Danh sách** — grid + avatar + dot-badge + page-header/breadcrumb.
2–4. **Thêm = wizard 3 bước** trong modal `[wide]` + `<app-stepper>`: Thông tin chung → Vai trò & quyền → Xác nhận.
5. **Sửa** — modal form (`PATCH /users/{id}`).
6. **Khóa/Mở** — `<app-confirm-dialog>` (cảnh báo không đăng nhập được).
7. **Xóa** — confirm-dialog `[danger]` (`DELETE /users/{id}`).
8. **Xuất Excel** — modal chọn phạm vi → CSV (BOM UTF-8) tải về.
9. **Bộ lọc nâng cao** — panel `.filter-panel` (vai trò/trạng thái…) Áp dụng/Đặt lại, lọc client-side.
10–11. **Chi tiết + Lịch sử** — modal `[wide]` + `<app-tabs>`: tab Thông tin (`.desc-grid`) + tab Lịch sử (audit trail của đối tượng).
12. **Thông báo** — toast success/info/warning/error cho mọi thao tác.

## 5. Quy tắc tái dụng (bắt buộc với mọi epic sau)

1. **Token-only:** màu/space/bo/chữ phải đọc từ `var(--…)`. PR hardcode hex/px sẽ bị trả.
2. **Trạng thái nhiệm vụ:** luôn qua `<status-badge>`; không tự bịa pill.
3. **Đỏ = quá hạn**, không dùng đỏ cho mục đích khác (dùng `--color-text-muted`/`.btn--danger` cho xóa).
4. **Một bộ icon line** nhất quán; không trộn họ icon, không gradient/bóng đậm.
5. **Thêm pattern mới → thêm vào `/styleguide`** ngay, để cả nhóm thấy và tái dụng.
6. Tương phản **AA**; mọi control có trạng thái `:focus-visible` rõ.

## 6. Accessibility (NFR — chuẩn AA)

Focus-ring nhìn thấy mọi control; nhãn `<label for>` gắn input; bảng có `<th scope>`; vùng động dùng `role="alert"`/`role="status"`; cỡ chữ tối thiểu `--text-sm`. Tiếng Việt phủ đủ dấu (font stack có Segoe UI/Roboto dự phòng on-prem, không phụ thuộc CDN).
