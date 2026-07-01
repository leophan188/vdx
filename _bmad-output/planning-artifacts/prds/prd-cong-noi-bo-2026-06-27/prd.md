---
title: "PRD — Cổng nội bộ ONEConnect (Mạng xã hội nội bộ + Công cụ nội bộ)"
status: final
created: 2026-06-27
updated: 2026-06-27
---

# PRD — Cổng nội bộ ONEConnect (GĐ2)

## 1. Tổng quan & Tầm nhìn

Nền tảng BPM/ONEConnect (GĐ1) đã hoàn thiện phần cấu hình & vận hành quy trình. **GĐ2** mở rộng nó thành một **Cổng nội bộ nhân viên** — nơi truyền thông nội bộ và các công cụ tác nghiệp hằng ngày hội tụ, trên cùng tài khoản/phân quyền/tổ chức sẵn có.

Mục tiêu: tăng **gắn kết nội bộ** (bảng tin công ty) và **giảm thao tác thủ công** (đồng bộ nhân sự, đăng ký OT, lập báo cáo từ Excel) cho toàn bộ nhân sự công ty.

Bốn năng lực cốt lõi:
1. **Đồng bộ danh sách nhân sự** từ một Google Sheet (nguồn chính, đồng bộ thủ công có kiểm soát).
2. **Mạng xã hội nội bộ** — admin đăng bài, nhân viên like + comment; UI kế thừa màn `/home` (ochome) đã dựng.
3. **Công cụ #1 — Đăng ký OT** (ghi nhận + tổng hợp báo cáo).
4. **Công cụ #2 — Import Excel → Báo cáo** (vài mẫu cố định, xuất .xlsx).

**Đối tượng:** sản phẩm dùng nội bộ thật cho **toàn công ty**.

## 2. Mục tiêu & Chỉ số thành công

| Mục tiêu | Chỉ số (Success Metric) | Counter-metric |
|---|---|---|
| Giảm nhập liệu nhân sự thủ công | ≥ 95% tài khoản tạo/cập nhật qua đồng bộ Sheet | Số lần sửa tay sau đồng bộ |
| Tăng gắn kết nội bộ | Tỷ lệ nhân viên tương tác (like/comment) ≥ 40%/tuần | Tỷ lệ bài 0 tương tác |
| Số hoá đăng ký OT | ≥ 80% OT ghi nhận qua hệ thống (thay file rời) | Sai lệch số giờ vs chấm công |
| Rút ngắn lập báo cáo | Thời gian tạo báo cáo từ Excel giảm ≥ 70% | Số báo cáo phải làm lại do lỗi định dạng |

## 3. Người dùng

- **Quản trị / Nhân sự (Admin)** — đồng bộ nhân sự, đăng & ghim bài, kiểm duyệt, chạy báo cáo Excel, xem tổng hợp OT.
- **Nhân viên** — xem bảng tin, like/comment, đăng ký OT của mình, xem danh bạ.
- **Trưởng phòng** _[GIẢ ĐỊNH]_ — như nhân viên (GĐ này chưa mở quyền đăng bài cho trưởng phòng; xem mục Ngoài phạm vi).

## 4. Yêu cầu chức năng

### Nhóm A — Đồng bộ nhân sự từ Google Sheet

- **FR-A01** — Admin cấu hình nguồn đồng bộ: **Google Sheet truy cập qua tài khoản dịch vụ Google (chỉ-đọc), KHÔNG để sheet công khai** (tránh lộ PII nhân sự và tránh sheet bị sửa tuỳ tiện) + ánh xạ cột Sheet → trường nhân sự (mã NV, họ tên, email, phòng ban, chức danh, trạng thái…).
- **FR-A02** — Admin bấm **"Đồng bộ"**: hệ thống đọc Sheet, đối chiếu với tài khoản/nhân sự hiện có theo khoá định danh (mã NV hoặc email).
- **FR-A03** — Hiển thị **bảng xem trước thay đổi** trước khi áp: số dòng **Thêm mới / Cập nhật / Sẽ khoá** (người không còn trong Sheet) + chi tiết từng dòng.
- **FR-A04** — Admin **xác nhận** → áp dụng: tạo tài khoản mới (mật khẩu khởi tạo **theo chính sách mật khẩu GĐ1**), cập nhật thông tin, **tự động khoá** tài khoản không còn trong Sheet. _(Bảng xem trước FR-A03 luôn hiển thị danh sách "sẽ khoá" để admin thấy trước khi áp.)_
- **FR-A08 — Chặn khoá người đang giữ việc (an toàn tích hợp)** — Trước khi khoá, hệ thống **kiểm tra ràng buộc**: nếu tài khoản đang **giữ vị trí có việc đang xử lý** hoặc **là người duyệt/được giao** trong quy trình BPM (GĐ1) → **KHÔNG khoá**, đánh dấu trong bảng xem trước là **"cần bàn giao"** + cảnh báo admin xử lý bàn giao trước. Tránh task mồ côi / quy trình treo.
- **FR-A05** — Kiểm tra & báo lỗi dữ liệu: dòng thiếu khoá định danh, email trùng, sai định dạng → liệt kê, không chặn các dòng hợp lệ.
- **FR-A06** — **Nhật ký đồng bộ**: mỗi lần lưu ai chạy, thời điểm, số thêm/sửa/khoá; ghi audit (tái dùng audit trail GĐ1).
- **FR-A07** — Đồng bộ là **một chiều** (Sheet → hệ thống); hệ thống không ghi ngược lên Sheet.

### Nhóm B — Mạng xã hội nội bộ (Bảng tin)

- **FR-B01** — **Admin đăng bài**: tiêu đề/nội dung (rich text) + **đính kèm ảnh và/hoặc video** _(ảnh ≤ 10MB, video ≤ 100MB)_; chọn **phạm vi**: **toàn công ty** hoặc **một phòng ban**; gắn **danh mục chủ đề** (Thông báo, Sự kiện, Vinh danh, Truyền thông…).
- **FR-B02** — **Ghim bài / Bài nổi bật**: admin ghim bài quan trọng lên đầu bảng tin.
- **FR-B03** — **Bảng tin (feed)** theo thiết kế màn `/home` hiện có, hiển thị **dữ liệu thật**; sắp xếp mới nhất + bài ghim lên trên; **tải theo trang kiểu "Tải thêm"** (load-more theo lô, vd 20 bài/lô) để chịu tải tốt.
- **FR-B04** — **Phạm vi xem**: nhân viên thấy **bài toàn công ty + bài phòng ban của mình**. Có thể **lọc** bảng tin theo phòng ban (trong phạm vi được xem) và/hoặc danh mục chủ đề.
- **FR-B05** — **Like / Bỏ like** bài viết; hiển thị số lượt.
- **FR-B06** — **Bình luận** bài viết (1 cấp _[GIẢ ĐỊNH]_); hiển thị số bình luận; tác giả sửa/xoá bình luận của mình _(trong hạn — kế thừa cơ chế 3.14 GĐ1)_.
- **FR-B07** — **Thông báo in-app** (tái dùng notification center GĐ1): khi có **bài mới** (theo phòng ban của nhân viên) và khi có **bình luận mới** trên bài mình theo dõi/đăng.
- **FR-B08** — **Kiểm duyệt nhẹ**: admin **ẩn/xoá** bài hoặc bình luận vi phạm; ghi audit.

### Nhóm C — Công cụ: Đăng ký OT

- **FR-C01** — Nhân viên **đăng ký OT**: ngày, giờ bắt đầu/kết thúc (hoặc số giờ), lý do/dự án, ghi chú.
- **FR-C02** — Nhân viên **xem / sửa / xoá** đăng ký OT của mình khi **kỳ chưa chốt** _(kỳ = tháng, [GIẢ ĐỊNH])_.
- **FR-C03** — **Tổng hợp OT** theo nhân viên / phòng ban / kỳ (admin xem).
- **FR-C04** — **Xuất báo cáo OT** ra **Excel (.xlsx)**.
- **FR-C05** — Admin **chốt kỳ** OT → khoá chỉnh sửa các đăng ký trong kỳ.
- _(GĐ này **không có bước duyệt** OT — chỉ ghi nhận + tổng hợp.)_ **[NOTE FOR PM — rủi ro đã chấp nhận]** Không duyệt + không đối chiếu chấm công trong hệ thống → **rủi ro gian lận giờ OT** được **kiểm soát ngoài phần mềm** (quy trình/nhân sự). Ghi nhận để rà lại nếu OT phục vụ tính lương trực tiếp.

### Nhóm D — Công cụ: Import Excel → Báo cáo

- **FR-D01** — Admin chọn **loại báo cáo** (danh sách **mẫu cố định** cấu hình sẵn) → **tải lên file Excel** đầu vào.
- **FR-D02** — Hệ thống **kiểm tra định dạng** theo mẫu (cột bắt buộc, kiểu dữ liệu) → liệt kê dòng/cột sai, cho phép sửa & tải lại.
- **FR-D03** — **Tính toán** theo **bộ công thức khai báo sẵn của từng mẫu** (định nghĩa trong cấu hình mẫu: cột đầu vào bắt buộc, phép tổng hợp/tính toán, cột đầu ra). Kết quả phải **lặp lại được** (cùng input → cùng output) và có **bộ test mẫu** kèm theo mỗi mẫu để kiểm chứng đúng số.
- **FR-D04** — **Tải file báo cáo** ra **Excel (.xlsx)**.
- **FR-D05** — **Lịch sử lần chạy**: ai, khi nào, mẫu nào, file vào/ra (lưu lại để tải lại).
- **FR-D06** — **Mẫu khởi đầu = "Tổng hợp chấm công / OT"**: input bảng giờ công/giờ OT theo nhân viên → output tổng hợp theo **nhân viên / phòng ban / kỳ**. _(Cột đầu vào & công thức tổng hợp cụ thể chốt ở bước thiết kế — xem Câu hỏi mở.)_ Các mẫu khác bổ sung dần.

## 5. Yêu cầu phi chức năng (NFR)

- **NFR-01 — Tái sử dụng nền tảng**: kế thừa Angular 21 + Spring Boot 3.5 + MariaDB; **tài khoản, RBAC, cơ cấu tổ chức, audit, notification, design-system** của GĐ1. Không dựng song song.
- **NFR-02 — Phân quyền**: admin (đăng bài/đồng bộ/báo cáo/kiểm duyệt/chốt kỳ) vs nhân viên (tương tác/đăng ký OT). Bảo vệ chặt các thao tác admin.
- **NFR-03 — Lưu trữ media**: ảnh/video bài viết lưu trên **đĩa server** _[GIẢ ĐỊNH: như cách lưu tài liệu GĐ1]_; **giới hạn: ảnh ≤ 10MB, video ≤ 100MB** (cấu hình được); chỉ chấp nhận định dạng ảnh/video phổ biến.
- **NFR-04 — Hiệu năng**: bảng tin phân trang; tải media tối ưu; tổng hợp OT/báo cáo chịu được quy mô **toàn công ty (100–500+ nhân sự)**.
- **NFR-05 — Bảo mật tích hợp & PII**: **khoá tài khoản dịch vụ Google** lưu an toàn qua biến môi trường/secret, không lộ ra client; **Sheet không công khai**. Dữ liệu nhân sự (PII) chỉ admin có quyền xem.
- **NFR-09 — An toàn file (import/export & media)**: khi **xuất .xlsx** phải **trung hoà formula/CSV injection** (escape ô bắt đầu bằng `= + - @`); khi **đọc Excel/nhận media** phải **kiểm loại file thật + giới hạn dung lượng** (ảnh ≤10MB, video ≤100MB, Excel có trần dòng/kích thước), **chống zip-bomb/OOM** (đọc theo luồng, hạn mức bộ nhớ), từ chối macro/liên kết ngoài. Áp cho Nhóm B (media), C, D.
- **NFR-06 — Kiểm toán**: đồng bộ nhân sự, kiểm duyệt nội dung, chốt kỳ OT, chạy báo cáo đều ghi audit.
- **NFR-07 — On-prem & Vận hành**: chạy on-prem; tương thích hồ sơ triển khai prod GĐ1 (actuator, backup, profile prod).
- **NFR-08 — Ngôn ngữ**: tiếng Việt (kế thừa GĐ1; i18n vẫn nằm ngoài phạm vi).

## 6. Ngoài phạm vi (GĐ này)

- **Chat / nhắn tin trực tiếp** giữa nhân viên.
- **Nhân viên / trưởng phòng tự đăng bài** (GĐ này chỉ admin đăng).
- **Duyệt OT** (quy trình phê duyệt) — GĐ này chỉ ghi nhận + báo cáo.
- **Cấu hình mẫu báo cáo Excel linh hoạt** (admin tự định nghĩa cột/công thức) — làm sau; GĐ này dùng mẫu cố định.
- **Bình luận đa cấp / mention** trong bảng tin (GĐ này comment 1 cấp; mention có thể bổ sung sau, tái dùng 3.13).
- **Ghi ngược lên Google Sheet** / đồng bộ 2 chiều.
- **Chữ ký số, xuất PDF báo cáo** (chỉ .xlsx).

## 7. Phụ thuộc

- **Google Sheet**: truy cập qua **tài khoản dịch vụ Google (Sheets API, chỉ-đọc)**; sheet **không công khai**.
- Hạ tầng **lưu media** (ảnh/video) trên đĩa server.
- Nền tảng GĐ1 (auth/RBAC/tổ chức/**lõi phân công & quy trình BPM** để kiểm tra ràng buộc khoá/notification/audit/design-system/chính sách mật khẩu).
- Thư viện đọc/ghi **Excel** (vd Apache POI) cho Nhóm C/D.

## 8. Quyết định đã chốt & Giả định còn lại

**Đã chốt (qua Discovery + Reviewer Gate):** mức độ = nội bộ toàn công ty · **Google Sheet qua tài khoản dịch vụ (không công khai)**, đồng bộ **bấm tay** · **tự động khoá** người vắng mặt **nhưng CHẶN nếu đang giữ việc/là người duyệt → bàn giao trước** · mạng XH **chỉ admin đăng** · OT **chỉ ghi nhận + báo cáo** (không duyệt; **rủi ro gian lận chấp nhận, kiểm soát ngoài hệ thống**) · import Excel **mẫu cố định**, xuất **.xlsx** (có chống injection) · media **ảnh + video** (≤10MB / ≤100MB, có kiểm an toàn) · bài xem theo **toàn cty + phòng mình**, feed **tải-thêm theo lô** · mẫu báo cáo đầu = **chấm công/OT**.

**Giả định còn lại _[GIẢ ĐỊNH]_:** tái dùng nguyên auth/RBAC/tổ chức/notification/audit GĐ1 · khoá định danh đồng bộ = **mã nhân viên** (fallback email) · media lưu **đĩa server** như GĐ1 · **comment 1 cấp** · **kỳ OT = tháng**.

## 9. Câu hỏi mở (chi tiết — chốt ở bước thiết kế, không chặn bóc epic)

1. **Ánh xạ cột Google Sheet** thực tế → trường nhân sự nào (tên cột cụ thể trong sheet).
2. **Cột đầu vào & công thức** của mẫu báo cáo "Tổng hợp chấm công/OT".
3. **Bộ danh mục chủ đề** bài viết chuẩn (Thông báo/Sự kiện/Vinh danh/…): chốt danh sách.

## 10. Thuật ngữ (Glossary)

- **Đồng bộ nhân sự**: đọc Google Sheet → đối chiếu → tạo/cập nhật/khoá tài khoản (một chiều).
- **Khoá tài khoản**: vô hiệu hoá đăng nhập của một nhân sự (Nhóm A). *Khác với* **Chốt kỳ OT** (FR-C05) = đóng băng chỉnh sửa đăng ký OT trong một kỳ.
- **Bài / Bảng tin (feed)**: nội dung admin đăng + dòng hiển thị cho nhân viên (Nhóm B).
- **Kỳ OT**: khoảng thời gian tổng hợp OT (mặc định = tháng).
- **Mẫu báo cáo**: cấu hình cố định gồm cột đầu vào + công thức + cột đầu ra cho Nhóm D.
- **Tài khoản dịch vụ (service account)**: định danh máy của Google dùng để đọc Sheet mà không cần công khai.

---

> **Trạng thái:** đã qua Reviewer Gate (rubric + adversarial); 3 vấn đề nghiêm trọng đã xử lý (Sheet→service account, chặn-khoá-người-giữ-việc, rủi ro OT ghi nhận chấp nhận) + vá kỹ thuật (chống injection/file độc, làm rõ công thức, feed tải-thêm, Glossary). Câu hỏi mở còn lại là chi tiết thiết kế. Bước tiếp: `bmad-create-epics-and-stories`.
