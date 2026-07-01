---
name: BPM Platform — Experience
status: final
created: 2026-06-24
updated: 2026-06-24
design_ref: ./DESIGN.md
sources:
  - '../../prds/prd-bpm-platform-2026-06-24/prd.md'
  - '../../architecture/architecture-bpm-platform-2026-06-24/ARCHITECTURE-SPINE.md'
---

# EXPERIENCE.md — BPM Platform

> Nhận diện thị giác (màu, font, token) ở [DESIGN.md](./DESIGN.md); tài liệu này sở hữu *cách vận hành*. Hai spine thắng khi xung đột với mọi mock/import.

## Foundation

- **Form-factor:** Web desktop-first (Angular 21 SPA), responsive xuống tablet; không ưu tiên mobile GĐ1.
- **UI system:** PrimeNG (data-dense enterprise) — [ASSUMPTION], xác nhận khi dựng. Toàn bộ giao diện **tiếng Việt**.
- **Bản chất:** công cụ *năng suất nội bộ* — người dùng quay lại nhiều lần/ngày; tối ưu **tốc độ thao tác, phím tắt, mật độ**, không phải onboarding hào nhoáng.

## Information Architecture

Shell = **sidebar trái (theo vai trò)** + **topbar** (tìm kiếm toàn cục · chuông thông báo · hồ sơ). Khu chính:

```
Việc của tôi (mặc định)   — hộp thư việc, nhóm Chờ xử lý/Đang làm/Đã xong
Thiết kế quy trình         — danh sách quy trình + canvas designer  [Admin IT]
Form builder               — danh sách form + canvas builder         [Admin IT]
Dashboard & Báo cáo        — điều hành, "ai đang làm gì", 4 lát cắt   [Quản lý]
Quản trị tổ chức           — cây đơn vị/vị trí, tài khoản, phân quyền [Admin]
Hồ sơ & Lưu trữ            — tra cứu, hồ sơ đã đóng
```

Sidebar **lọc theo vai trò**: chuyên viên thấy Việc của tôi + Hồ sơ; Admin IT thêm Thiết kế quy trình/Form; Quản lý thêm Dashboard; Quản trị thêm Tổ chức. **Surface closure:** mỗi nhu cầu PRD (FR A–I) có một khu; mỗi khu có luồng dẫn tới.

## Voice and Tone

Microcopy **trang trọng, ngắn, hành động rõ**. Nút động từ ("Trình duyệt", "Phê duyệt", "Trả lại", "Xin gia hạn"). Thông báo nêu *việc gì + ai + hạn nào*. Tránh tiếng lóng, emoji. Trạng thái rỗng hướng dẫn ("Chưa có việc nào chờ xử lý").

## Component Patterns (hành vi)

- **Hộp thư việc** — bảng nhóm theo trạng thái, lọc/sắp xếp theo hạn/loại/đơn vị; click mở màn xử lý. Cờ **Quá hạn** nổi đỏ ưu tiên đầu danh sách.
- **Canvas designer (kéo-thả BPMN, bpmn-js)** — palette node trái, thuộc tính phải; mọi loại luồng (tuần tự/rẽ nhánh/song song/join/lặp) là phần tử kéo vào; lưu = publish phiên bản mới (AD-3).
- **Form builder (kéo-thả)** — palette loại trường trái, preview giữa, thuộc tính trường phải (bắt buộc/ẩn-hiện điều kiện/validation/quyền theo bước).
- **Màn xử lý nhiệm vụ** — trái: tiến độ theo bước (timeline) + lịch sử bước trước (thu gọn); giữa: form bước hiện tại + **OnlyOffice nhúng** soạn thảo; phải: **bảng tổng hợp ý kiến** + **comment kiểu Jira** (threaded, @mention).
- **Cây tổ chức** — expand/collapse nhiều cấp, kéo-thả, badge vị trí trống.
- **Dashboard/Báo cáo** — thẻ đếm (đang xử lý/hoàn thành/quá hạn/sắp hạn) + bảng 4 lát cắt (đơn vị/người/loại/đúng-trễ) + nút xuất Excel/PDF.

## State Patterns

- **Trạng thái nhiệm vụ** (badge, DESIGN.Components): Chờ phê duyệt · Đang xử lý · Đã hoàn thành · Hủy + **cờ Quá hạn trực giao** (đỏ chồng lên, không thay badge — AD-5).
- **Vị trí trống** (FR-C08): việc vào hàng đợi "Chưa có người nhận" của đơn vị, badge cảnh báo, không biến mất.
- **Loading/empty/error** mỗi bảng & canvas: skeleton khi tải; empty có hướng dẫn; lỗi có thông điệp + traceId (envelope AD).
- **Phối hợp song song**: mỗi nhánh có chip trạng thái riêng (Đang chờ/Đã trả/Quá hạn/Đã đóng); chủ trì thấy tiến độ gộp + nút "Đóng phối hợp" khi hết hạn (FR-F05).
- **Đồng-tồn-tại phiên bản**: instance hiển thị nhãn "Phiên bản quy trình vX" để người dùng hiểu vì sao giao diện bước có thể khác bản mới.

## Interaction Primitives

Kéo-thả (designer/form/cây org) · click-mở-chi-tiết · phím tắt cho hành động việc (phê duyệt/trả lại) · @mention popup · upload kéo-thả file · autosave dự thảo (OnlyOffice) · xác nhận trước hành động không hoàn tác (Hủy có cascade — FR-D10).

## Accessibility Floor

WCAG **2.1 AA**: tương phản ≥4.5:1 (token màu đã chọn theo hướng này), focus ring rõ (`{colors.focus-ring}`), điều hướng **bàn phím đầy đủ** (bảng, canvas có lối bàn phím thay thế kéo-thả), nhãn ARIA cho badge trạng thái (không chỉ dựa màu — kèm chữ), hỗ trợ phóng to 200%.

## Key Flows

**Luồng 1 — Chuyên viên Minh xử lý quy trình "Phối hợp nghiên cứu, tham mưu" (mẫu #1).**
Minh mở *Việc của tôi*, thấy việc mới "Soạn tham mưu" nổi đầu danh sách → mở màn xử lý → điền form bước 1, soạn dự thảo trong OnlyOffice nhúng → chọn 2 đơn vị phối hợp + hạn → Trình. Hệ thống fan-out nhánh phối hợp. Vài ngày sau, một đơn vị chưa trả khi sắp hết hạn: Minh thấy chip "Quá hạn" đỏ ở nhánh đó, bấm **"Đóng phối hợp"** lấy input một phần → **bảng tổng hợp ý kiến** tự gom → Minh đánh dấu tiếp thu/không tiếp thu, hoàn thiện dự thảo → Trình lãnh đạo. **(Climax:** lãnh đạo Phê duyệt, văn thư Ghi nhận ban hành (số VB + scan PDF) → hồ sơ tự đóng & lưu trữ.)

**Luồng 2 — Admin IT Lan tạo quy trình mới không cần Dev.**
Lan mở *Thiết kế quy trình* → kéo các bước & gateway lên canvas, gắn form (kéo từ Form builder), đặt vai trò/hạn mỗi bước → **Publish**. Quy trình vào vận hành ngay, instance đang chạy của bản cũ **không bị ảnh hưởng** (nhãn phiên bản). **(Climax:** một thay đổi quy trình đi từ ý tưởng tới vận hành trong vài giờ, không mở ticket Dev.)

**Luồng 3 — Quản lý Hà nắm toàn cảnh.**
Hà mở *Dashboard*: thẻ đếm quá hạn nhảy số đỏ → bấm vào, lọc theo đơn vị → thấy "ai đang làm gì", việc nào trễ → xuất báo cáo 4 lát cắt ra Excel để họp giao ban. **(Climax:** lần đầu Hà thấy bức tranh nhiệm vụ toàn cơ quan realtime mà trước đây mù.)
