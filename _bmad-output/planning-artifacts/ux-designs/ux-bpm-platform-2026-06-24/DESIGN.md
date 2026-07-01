---
name: BPM Platform — Visual Identity
status: final
created: 2026-06-24
updated: 2026-06-24
sources:
  - '../../prds/prd-bpm-platform-2026-06-24/prd.md'
  - '../../architecture/architecture-bpm-platform-2026-06-24/ARCHITECTURE-SPINE.md'
colors:
  primary: '#1E50A0'        # xanh hành chính, điềm tĩnh
  primary-hover: '#17407F'
  surface: '#FFFFFF'
  surface-alt: '#F4F6F9'    # nền vùng, hàng xen kẽ
  border: '#D9DEE6'
  text: '#1B2430'
  text-muted: '#5B6675'
  status-pending: '#B7791F' # Chờ phê duyệt — amber
  status-active: '#1E66C7'  # Đang xử lý — blue
  status-done: '#2F855A'    # Đã hoàn thành — green
  status-cancel: '#718096'  # Hủy — gray
  overdue: '#C53030'        # cờ Quá hạn — đỏ (trực giao)
  focus-ring: '#3B82F6'
typography:
  font-sans: "'Be Vietnam Pro', 'Segoe UI', 'Roboto', sans-serif"   # self-host @fontsource, phủ đủ dấu tiếng Việt
  font-mono: "'JetBrains Mono', monospace"
  scale: { xs: 12px, sm: 13px, base: 14px, lg: 16px, xl: 20px, '2xl': 26px }
  weight: { regular: 400, medium: 500, semibold: 600 }
rounded: { sm: 4px, md: 6px, lg: 10px, full: 9999px }
spacing: { base: 4px, density: compact }   # enterprise data-dense: bước 4px
components:
  table: { row-height: 40px, header: surface-alt, zebra: true }
  badge: { radius: full, padding: '2px 10px', weight: medium }
  button: { radius: md, height: 36px }
  input: { radius: md, height: 36px, border: border }
  card: { radius: lg, border: border, shadow: sm }
---

# DESIGN.md — BPM Platform

## Brand & Style

Điềm tĩnh, chuyên nghiệp, đáng tin — ngôn ngữ thị giác của một **công cụ điều hành cơ quan nhà nước**, không phải sản phẩm tiêu dùng. Ưu tiên **rõ ràng trạng thái và phân cấp** hơn trang trí. Mật độ thông tin **vừa–cao** (nhiều bảng, cây, biểu mẫu) nhưng có khoảng thở. Không gradient lòe loẹt, không màu thương hiệu rực; màu chỉ dùng để **truyền nghĩa** (trạng thái, cảnh báo, hành động chính).

## Colors

Bảng token ở frontmatter. Nguyên tắc: nền trắng/`surface-alt` xen kẽ; **một màu primary** xanh hành chính cho hành động chính & liên kết; **năm màu trạng thái** dùng *nhất quán tuyệt đối* cho badge nhiệm vụ; **đỏ `overdue` chỉ dành riêng** cho cờ quá hạn — không tái dụng đỏ cho việc khác.

## Typography

Sans (`{typography.font-sans}`) toàn hệ; mono cho mã hồ sơ/số văn bản. Thang nhỏ gọn (base 14px) hợp mật độ cao. Tiêu đề vùng `xl/2xl` weight semibold; nhãn bảng `sm` muted uppercase nhẹ. Tiếng Việt: đảm bảo font phủ đủ dấu.

## Layout & Spacing

Bước lưới **4px** (compact). Shell: **sidebar trái** (điều hướng theo vai trò, thu gọn được) + **topbar** (tìm kiếm toàn cục, chuông thông báo, hồ sơ) + vùng nội dung. Bảng dày 40px/hàng, zebra. Khoảng đệm vùng 16–24px.

## Elevation & Depth

Phẳng là mặc định; **shadow rất nhẹ** chỉ cho lớp nổi (dropdown, dialog, popover, thẻ thả kéo). Không đổ bóng trang trí. Phân lớp bằng `border` + `surface-alt`, không bằng bóng đậm.

## Shapes

Bo góc nhỏ (`{rounded.sm}`–`{rounded.md}`) cho input/nút/thẻ; `{rounded.full}` cho badge & avatar. Đường nét mảnh 1px `border`.

## Components

- **Badge trạng thái** — pill bo tròn, nền nhạt + chữ đậm theo 5 màu trạng thái; **cờ Quá hạn** là chấm/viền đỏ chồng lên, không thay màu badge gốc (phản ánh AD-5 trực giao).
- **Bảng dữ liệu** — header `surface-alt`, zebra, sticky header, cột hành động phải; lọc/sắp xếp trên đầu cột.
- **Canvas thiết kế** (process designer, form builder) — nền chấm lưới, palette thả kéo bên trái, panel thuộc tính bên phải.
- **Nút** — primary đặc (xanh), secondary viền, ghost cho hành động phụ; cao 36px.
- **Cây tổ chức** — node có icon đơn vị/vị trí, expand/collapse, kéo-thả sắp xếp.

## Do's and Don'ts

- ✅ Dùng màu trạng thái **nhất quán** ở mọi nơi (badge, dashboard, báo cáo).
- ✅ Giữ mật độ cao nhưng căn lề lưới 4px, đủ tương phản AA.
- ❌ Không tái dụng đỏ `overdue` cho nút xóa/lỗi chung — đỏ thuộc về quá hạn.
- ❌ Không trang trí bằng gradient/bóng đậm/màu thương hiệu rực.
- ❌ Không trộn nhiều họ icon; một bộ icon line nhất quán.
