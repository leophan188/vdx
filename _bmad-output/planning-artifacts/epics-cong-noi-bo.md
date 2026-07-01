---
stepsCompleted: [step-01, step-02, step-03]
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-cong-noi-bo-2026-06-27/prd.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-bpm-platform-2026-06-24/ARCHITECTURE-SPINE.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-bpm-platform-2026-06-24/DESIGN.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-bpm-platform-2026-06-24/EXPERIENCE.md'
---

# Cổng nội bộ ONEConnect (GĐ2) - Epic Breakdown

## Overview

Phân rã yêu cầu PRD GĐ2 ("Cổng nội bộ ONEConnect") thành epic/story khả thi, **mở rộng codebase GĐ1** (brownfield) — tái dùng auth/RBAC/tổ chức/Flowable/notification/audit/design-system sẵn có.

## Requirements Inventory

### Functional Requirements

**Nhóm A — Đồng bộ nhân sự từ Google Sheet**
- **FR-A01**: Cấu hình nguồn đồng bộ (Google Sheet qua **tài khoản dịch vụ, chỉ-đọc, không công khai**) + ánh xạ cột → trường nhân sự.
- **FR-A02**: Bấm "Đồng bộ" → đọc Sheet → đối chiếu tài khoản hiện có theo khoá định danh (mã NV / email).
- **FR-A03**: Bảng **xem trước thay đổi** (Thêm mới / Cập nhật / Sẽ khoá / Cần bàn giao) trước khi áp.
- **FR-A04**: Admin xác nhận → áp dụng: tạo tài khoản (mật khẩu theo chính sách GĐ1), cập nhật, **tự động khoá** người vắng mặt.
- **FR-A05**: Kiểm tra & báo lỗi dữ liệu (thiếu khoá định danh, trùng email, sai định dạng); không chặn dòng hợp lệ.
- **FR-A06**: Nhật ký đồng bộ (ai/khi nào/số thêm-sửa-khoá) + ghi audit.
- **FR-A07**: Đồng bộ **một chiều** (không ghi ngược lên Sheet).
- **FR-A08**: **Chặn khoá** người đang giữ vị trí có việc đang xử lý / là người duyệt-được giao trong quy trình BPM → đánh dấu "cần bàn giao" + cảnh báo admin (tránh task mồ côi).

**Nhóm B — Mạng xã hội nội bộ (Bảng tin)**
- **FR-B01**: Admin đăng bài (rich text + đính kèm **ảnh ≤10MB / video ≤100MB**) + chọn **phạm vi** (toàn công ty / một phòng) + **danh mục chủ đề**.
- **FR-B02**: Ghim bài / đánh dấu nổi bật.
- **FR-B03**: Bảng tin (feed) theo thiết kế `/home`, dữ liệu thật, mới nhất + bài ghim lên đầu, **tải-thêm theo lô**.
- **FR-B04**: Phạm vi xem = **bài toàn công ty + bài phòng mình**; lọc theo phòng ban / chủ đề.
- **FR-B05**: Like / Bỏ like + hiển thị số lượt.
- **FR-B06**: Bình luận bài (1 cấp); tác giả sửa/xoá bình luận của mình khi còn hạn.
- **FR-B07**: Thông báo in-app (tái dùng notification GĐ1): bài mới theo phòng + bình luận mới trên bài liên quan.
- **FR-B08**: Kiểm duyệt nhẹ: admin ẩn/xoá bài/bình luận + ghi audit.

**Nhóm C — Công cụ: Đăng ký OT**
- **FR-C01**: Nhân viên đăng ký OT (ngày, giờ bắt đầu/kết thúc hoặc số giờ, lý do/dự án, ghi chú).
- **FR-C02**: Nhân viên xem/sửa/xoá đăng ký OT của mình khi **kỳ chưa chốt**.
- **FR-C03**: Tổng hợp OT theo nhân viên / phòng ban / kỳ.
- **FR-C04**: Xuất báo cáo OT ra **.xlsx**.
- **FR-C05**: Admin **chốt kỳ** OT → khoá chỉnh sửa trong kỳ.

**Nhóm D — Công cụ: Import Excel → Báo cáo**
- **FR-D01**: Chọn **loại báo cáo** (mẫu cố định) → tải lên file Excel đầu vào.
- **FR-D02**: Kiểm tra định dạng theo mẫu → liệt kê dòng/cột sai, cho sửa & tải lại.
- **FR-D03**: Tính toán theo **bộ công thức khai báo sẵn của mẫu** (lặp lại được + test mẫu kèm).
- **FR-D04**: Tải file báo cáo ra **.xlsx**.
- **FR-D05**: Lịch sử lần chạy (ai/khi nào/mẫu/file vào-ra).
- **FR-D06**: Mẫu khởi đầu = **Tổng hợp chấm công / OT**.

### NonFunctional Requirements

- **NFR-01**: Tái sử dụng nền tảng GĐ1 (Angular 21 + Spring Boot 3.5 + MariaDB; auth/RBAC/tổ chức/audit/notification/design-system) — không dựng song song.
- **NFR-02**: Phân quyền admin (đăng bài/đồng bộ/báo cáo/kiểm duyệt/chốt kỳ) vs nhân viên (tương tác/đăng ký OT).
- **NFR-03**: Lưu trữ media trên đĩa server; giới hạn dung lượng (ảnh ≤10MB, video ≤100MB) + loại file cho phép.
- **NFR-04**: Hiệu năng — feed tải-thêm theo lô; chịu quy mô toàn công ty (100–500+ nhân sự).
- **NFR-05**: Bảo mật tích hợp & PII — khoá service account qua secret; Sheet không công khai; PII chỉ admin xem.
- **NFR-06**: Kiểm toán — đồng bộ, kiểm duyệt, chốt kỳ OT, chạy báo cáo đều ghi audit.
- **NFR-07**: On-prem & vận hành — tương thích hồ sơ triển khai prod GĐ1 (actuator/backup/profile prod).
- **NFR-08**: Ngôn ngữ tiếng Việt (kế thừa GĐ1).
- **NFR-09**: An toàn file — chống formula/CSV injection khi xuất .xlsx; kiểm loại/dung lượng file + chống zip-bomb/OOM + từ chối macro/liên kết ngoài khi đọc Excel/nhận media.

### Additional Requirements

(Từ Architecture GĐ1 + tích hợp — ảnh hưởng triển khai)

- **Brownfield**: mở rộng codebase GĐ1, không starter template mới; theo kiến trúc **hexagonal-lite** (api→application→domain→infrastructure), mọi mutation qua **AuditPort**.
- **Tái dùng lõi**: RBAC + cơ cấu tổ chức + **lõi phân công & quy trình Flowable** (cần cho FR-A08 kiểm tra ràng buộc khoá) + **NotificationService** (FR-B07) + design-system + chính sách mật khẩu.
- **Tích hợp Google Sheets**: Google Sheets API + **tài khoản dịch vụ** (đọc-only); cấu hình endpoint/secret qua biến môi trường.
- **Thư viện Excel**: thêm dependency đọc/ghi `.xlsx` (vd Apache POI) cho Nhóm C/D.
- **Lưu trữ media**: cơ chế lưu file trên đĩa server (tương tự lưu tài liệu OnlyOffice GĐ1) + endpoint phục vụ media.

### UX Design Requirements

(Tái dùng design-system GĐ1 + nền màn `/home` (ochome) đã dựng)

- **UX-DR1**: Hiện thực **bảng tin `/home`** với **dữ liệu thật** (feed, post card, banner) thay dữ liệu mẫu hiện có — dùng tokens/component design-system GĐ1.
- **UX-DR2**: **Composer đăng bài (admin)** — rich text + upload ảnh/video + chọn phạm vi (toàn cty/phòng) + danh mục chủ đề + ghim.
- **UX-DR3**: **Tương tác bài** — nút Like/Bỏ like + khu **bình luận 1 cấp** (sửa/xoá trong hạn) trên post card.
- **UX-DR4**: **Bộ lọc bảng tin** (phòng ban/chủ đề) + nút **"Tải thêm"**.
- **UX-DR5**: **Màn đồng bộ nhân sự** — cấu hình nguồn + **bảng xem trước thay đổi** (tô màu Thêm/Sửa/Khoá/Bàn giao) + nhật ký, dùng data-grid GĐ1.
- **UX-DR6**: **Màn công cụ OT** (form đăng ký + lưới đăng ký của tôi + màn tổng hợp/chốt kỳ admin) theo form/data-grid GĐ1.
- **UX-DR7**: **Màn Import Excel→Báo cáo** — chọn mẫu, tải file, hiển thị lỗi định dạng, tải kết quả, lịch sử — theo design-system.

### FR Coverage Map

- **FR-A01–A08** → Epic 1 (Đồng bộ nhân sự)
- **FR-B01–B08** → Epic 2 (Mạng xã hội nội bộ)
- **FR-C01–C05** → Epic 3 (Đăng ký OT)
- **FR-D01–D06** → Epic 4 (Import Excel → Báo cáo)
- **NFR-05** (bảo mật/PII) → Epic 1 · **NFR-03** (media) → Epic 2 · **NFR-09** (an toàn file) → Epic 2 (media) + Epic 3 (xuất) + Epic 4 (import/xuất)
- **NFR-01, 02, 04, 06, 07, 08** (tái dùng nền, phân quyền, hiệu năng, audit, vận hành, ngôn ngữ) → **xuyên suốt mọi epic** (gài vào AC từng story)

## Epic List

### Epic 1: Đồng bộ nhân sự từ Google Sheet
Admin/Nhân sự giữ danh sách tài khoản nhân viên **luôn khớp Google Sheet nguồn** — đồng bộ thủ công có kiểm soát: xem trước thay đổi, tự tạo/cập nhật/khoá, **an toàn** (Sheet không công khai, không khoá nhầm người đang giữ việc).
**FRs covered:** FR-A01, FR-A02, FR-A03, FR-A04, FR-A05, FR-A06, FR-A07, FR-A08 · **NFR:** 05 (+ 01/02/06).

### Epic 2: Mạng xã hội nội bộ (Bảng tin)
Nhân viên **gắn kết với công ty** qua bảng tin: admin đăng bài (ảnh/video, ghim, theo phòng/chủ đề), nhân viên like/bình luận, nhận thông báo — trên nền màn `/home` sẵn có.
**FRs covered:** FR-B01, FR-B02, FR-B03, FR-B04, FR-B05, FR-B06, FR-B07, FR-B08 · **NFR:** 03, 09 (media) (+ 01/02/04/06).

### Epic 3: Công cụ Đăng ký OT
Nhân viên **tự đăng ký giờ OT**; admin **tổng hợp + xuất báo cáo** OT theo kỳ — số hoá ghi nhận OT (không duyệt).
**FRs covered:** FR-C01, FR-C02, FR-C03, FR-C04, FR-C05 · **NFR:** 09 (xuất) (+ 01/02/06).

### Epic 4: Công cụ Import Excel → Báo cáo
Admin **tạo báo cáo tự động từ file Excel** theo mẫu cố định (đầu = chấm công/OT), **an toàn** (chống file độc/injection), tải kết quả .xlsx + lịch sử.
**FRs covered:** FR-D01, FR-D02, FR-D03, FR-D04, FR-D05, FR-D06 · **NFR:** 09 (+ 01/02/06).

---

## Epic 1: Đồng bộ nhân sự từ Google Sheet

Admin/Nhân sự giữ tài khoản nhân viên khớp Google Sheet nguồn — đồng bộ thủ công có kiểm soát, an toàn (Sheet không công khai, không khoá nhầm người đang giữ việc). _(UX-DR5; tái dùng RBAC/tài khoản/tổ chức/audit GĐ1.)_

### Story 1.1: Kết nối & cấu hình nguồn Google Sheet

As an **Admin**,
I want **cấu hình nguồn đồng bộ là một Google Sheet (qua tài khoản dịch vụ) và ánh xạ cột → trường nhân sự**,
So that **hệ thống biết đọc dữ liệu nhân sự từ đâu mà không để lộ PII**.

**Acceptance Criteria:**

**Given** tôi là admin ở màn "Đồng bộ nhân sự",
**When** tôi nhập ID/đường dẫn Google Sheet + chọn ánh xạ cột (mã NV, họ tên, email, phòng ban, chức danh, trạng thái) và lưu,
**Then** cấu hình được lưu, khoá tài khoản dịch vụ đọc qua secret (không lộ ra client),
**And** nếu Sheet để công khai hoặc service account không có quyền đọc → báo lỗi rõ ràng, không lưu cấu hình sai. _(FR-A01, NFR-05)_

### Story 1.2: Đọc Sheet + đối chiếu → bảng xem trước thay đổi

As an **Admin**,
I want **bấm "Đồng bộ" để hệ thống đọc Sheet, đối chiếu với nhân sự hiện có và hiển thị bảng xem trước**,
So that **tôi thấy chính xác sẽ thêm/cập nhật/khoá ai trước khi áp**.

**Acceptance Criteria:**

**Given** đã cấu hình nguồn,
**When** tôi bấm "Đồng bộ",
**Then** hệ thống đối chiếu theo **mã NV (fallback email)** và hiển thị bảng phân nhóm **Thêm mới / Cập nhật / Sẽ khoá** kèm chi tiết từng dòng,
**And** các dòng lỗi (thiếu khoá định danh, trùng email, sai định dạng) được liệt kê riêng và **không chặn** các dòng hợp lệ. _(FR-A02, FR-A03, FR-A05)_

### Story 1.3: Áp dụng đồng bộ + nhật ký

As an **Admin**,
I want **xác nhận áp dụng các thay đổi từ bảng xem trước và xem nhật ký mỗi lần đồng bộ**,
So that **danh sách tài khoản được cập nhật và có vết kiểm toán**.

**Acceptance Criteria:**

**Given** đang xem bảng xem trước,
**When** tôi xác nhận áp dụng,
**Then** hệ thống **tạo** tài khoản mới (mật khẩu khởi tạo theo chính sách GĐ1), **cập nhật** thông tin, **khoá** tài khoản vắng mặt; đồng bộ **một chiều** (không ghi ngược Sheet),
**And** ghi **nhật ký** (ai/khi nào/số thêm-sửa-khoá) + audit qua AuditPort. _(FR-A04, FR-A06, FR-A07, NFR-06)_

### Story 1.4: Chặn khoá người đang giữ việc (an toàn tích hợp)

As an **Admin**,
I want **hệ thống chặn việc khoá tài khoản đang giữ việc/đang là người duyệt trong quy trình BPM**,
So that **không gây task mồ côi hay treo quy trình**.

**Acceptance Criteria:**

**Given** một tài khoản trong nhóm "sẽ khoá" đang giữ vị trí có việc đang xử lý hoặc là người được giao/duyệt,
**When** áp dụng đồng bộ,
**Then** tài khoản đó **KHÔNG bị khoá**, được chuyển sang nhóm **"cần bàn giao"** kèm cảnh báo,
**And** admin chỉ khoá được sau khi đã bàn giao/đóng việc liên quan. _(FR-A08)_

---

## Epic 2: Mạng xã hội nội bộ (Bảng tin)

Nhân viên gắn kết qua bảng tin: admin đăng bài (ảnh/video, ghim, theo phòng/chủ đề), nhân viên like/bình luận, nhận thông báo — trên nền màn `/home` sẵn có. _(UX-DR1–4; tái dùng notification/audit/design-system GĐ1.)_

### Story 2.1: Admin đăng bài + lưu media an toàn

As an **Admin**,
I want **đăng bài (nội dung + ảnh/video) chọn phạm vi và danh mục chủ đề**,
So that **truyền thông nội dung tới đúng nhóm nhân viên**.

**Acceptance Criteria:**

**Given** tôi là admin,
**When** tôi tạo bài với nội dung rich text + đính kèm ảnh (≤10MB) và/hoặc video (≤100MB), chọn phạm vi (toàn công ty / một phòng) + danh mục chủ đề,
**Then** bài được lưu, media lưu trên đĩa server và phục vụ qua endpoint,
**And** file vượt dung lượng/sai loại/độc hại bị **từ chối** với thông báo rõ. _(FR-B01, NFR-03, NFR-09)_

### Story 2.2: Bảng tin dữ liệu thật + phạm vi xem + tải thêm

As a **Nhân viên**,
I want **xem bảng tin theo thiết kế /home với bài liên quan tới tôi**,
So that **tôi nắm thông tin công ty và phòng mình**.

**Acceptance Criteria:**

**Given** tôi đã đăng nhập,
**When** tôi mở Trang chủ/Bảng tin,
**Then** thấy **bài toàn công ty + bài phòng ban của tôi**, sắp xếp mới nhất, có thể **lọc** theo phòng/chủ đề,
**And** danh sách **tải thêm theo lô** (vd 20 bài) thay vì tải hết một lần. _(FR-B03, FR-B04, NFR-04)_

### Story 2.3: Ghim bài / Bài nổi bật

As an **Admin**,
I want **ghim một bài quan trọng**,
So that **nó luôn hiển thị đầu bảng tin**.

**Acceptance Criteria:**

**Given** một bài đã đăng,
**When** tôi ghim bài,
**Then** bài đó hiển thị **trên đầu** bảng tin (trên cả bài mới hơn) với dấu hiệu "ghim",
**And** tôi có thể bỏ ghim để trả về thứ tự thường. _(FR-B02)_

### Story 2.4: Like / Bỏ like

As a **Nhân viên**,
I want **thả/bỏ like một bài**,
So that **tôi bày tỏ phản hồi nhanh**.

**Acceptance Criteria:**

**Given** một bài trên bảng tin,
**When** tôi bấm Like rồi bấm lại,
**Then** số lượt like tăng rồi giảm tương ứng, trạng thái của tôi được lưu,
**And** mỗi người chỉ tính **một** like cho một bài. _(FR-B05)_

### Story 2.5: Bình luận + sửa/xoá trong hạn

As a **Nhân viên**,
I want **bình luận một bài và sửa/xoá bình luận của mình khi còn hạn**,
So that **tôi trao đổi và sửa sai sót kịp thời**.

**Acceptance Criteria:**

**Given** một bài trên bảng tin,
**When** tôi gửi bình luận (1 cấp),
**Then** bình luận hiển thị kèm tên + thời gian, số bình luận tăng,
**And** tôi **sửa/xoá được bình luận của chính mình khi còn trong hạn**; quá hạn hoặc của người khác thì không. _(FR-B06)_

### Story 2.6: Thông báo bài mới + bình luận mới

As a **Nhân viên**,
I want **nhận thông báo khi có bài mới liên quan và khi có bình luận trên bài tôi quan tâm**,
So that **tôi không bỏ lỡ thông tin**.

**Acceptance Criteria:**

**Given** notification center GĐ1,
**When** admin đăng bài nhắm phòng tôi (hoặc toàn cty) / có bình luận mới trên bài tôi đăng/đã bình luận,
**Then** tôi nhận **thông báo in-app** với liên kết tới bài,
**And** thông báo tuân theo cơ chế đã đọc/đếm chưa đọc sẵn có. _(FR-B07)_

### Story 2.7: Kiểm duyệt nội dung

As an **Admin**,
I want **ẩn/xoá bài hoặc bình luận vi phạm**,
So that **giữ bảng tin lành mạnh**.

**Acceptance Criteria:**

**Given** một bài/bình luận vi phạm,
**When** tôi ẩn hoặc xoá nó,
**Then** nội dung không còn hiển thị với nhân viên,
**And** thao tác được **ghi audit** (ai/khi nào/đối tượng). _(FR-B08, NFR-06)_

---

## Epic 3: Công cụ Đăng ký OT

Nhân viên tự đăng ký giờ OT; admin tổng hợp + xuất báo cáo theo kỳ (không duyệt). _(UX-DR6.)_

### Story 3.1: Nhân viên đăng ký + quản lý OT của mình

As a **Nhân viên**,
I want **đăng ký OT và xem/sửa/xoá đăng ký của mình khi kỳ chưa chốt**,
So that **ghi nhận chính xác giờ làm thêm của tôi**.

**Acceptance Criteria:**

**Given** tôi đã đăng nhập,
**When** tôi tạo đăng ký OT (ngày, giờ bắt đầu/kết thúc hoặc số giờ, lý do/dự án, ghi chú),
**Then** đăng ký được lưu và hiện trong "OT của tôi",
**And** tôi **sửa/xoá được khi kỳ chưa chốt**; kỳ đã chốt thì chỉ xem. _(FR-C01, FR-C02)_

### Story 3.2: Admin tổng hợp OT

As an **Admin**,
I want **xem tổng hợp OT theo nhân viên / phòng ban / kỳ**,
So that **nắm được khối lượng OT toàn công ty**.

**Acceptance Criteria:**

**Given** có các đăng ký OT,
**When** tôi mở màn tổng hợp và chọn kỳ/phòng,
**Then** thấy tổng số giờ OT theo **nhân viên / phòng ban / kỳ**,
**And** số liệu khớp với các đăng ký trong phạm vi đã chọn. _(FR-C03)_

### Story 3.3: Chốt kỳ OT

As an **Admin**,
I want **chốt một kỳ OT**,
So that **khoá chỉnh sửa để số liệu ổn định cho báo cáo**.

**Acceptance Criteria:**

**Given** một kỳ OT đang mở,
**When** tôi chốt kỳ,
**Then** mọi đăng ký trong kỳ bị **khoá chỉnh sửa** (nhân viên chỉ xem),
**And** thao tác chốt kỳ được ghi audit. _(FR-C05, NFR-06)_

### Story 3.4: Xuất báo cáo OT (.xlsx)

As an **Admin**,
I want **xuất báo cáo OT ra Excel**,
So that **chia sẻ/lưu trữ số liệu OT**.

**Acceptance Criteria:**

**Given** màn tổng hợp OT theo kỳ,
**When** tôi bấm "Xuất Excel",
**Then** tải về file **.xlsx** đúng số liệu tổng hợp,
**And** giá trị ô được **trung hoà formula/CSV injection** (escape ô bắt đầu bằng `= + - @`). _(FR-C04, NFR-09)_

---

## Epic 4: Công cụ Import Excel → Báo cáo

Admin tạo báo cáo tự động từ file Excel theo mẫu cố định (đầu = chấm công/OT), an toàn, tải .xlsx + lịch sử. _(UX-DR7.)_

### Story 4.1: Khung import + an toàn file

As an **Admin**,
I want **chọn loại báo cáo, tải file Excel đầu vào và được kiểm tra định dạng an toàn**,
So that **chỉ dữ liệu hợp lệ & an toàn mới được xử lý**.

**Acceptance Criteria:**

**Given** màn "Import Excel → Báo cáo",
**When** tôi chọn mẫu báo cáo + tải file .xlsx,
**Then** hệ thống kiểm tra **cột bắt buộc + kiểu dữ liệu**, liệt kê dòng/cột sai để sửa & tải lại,
**And** file sai loại/quá lớn/độc hại (macro, liên kết ngoài, zip-bomb) bị **từ chối**, đọc theo luồng tránh OOM. _(FR-D01, FR-D02, NFR-09)_

### Story 4.2: Mẫu "Tổng hợp chấm công/OT" — tính toán

As an **Admin**,
I want **hệ thống tính toán báo cáo theo công thức của mẫu chấm công/OT**,
So that **có kết quả tổng hợp chính xác, lặp lại được**.

**Acceptance Criteria:**

**Given** file đầu vào hợp lệ cho mẫu "Tổng hợp chấm công/OT",
**When** tôi chạy tính toán,
**Then** hệ thống sinh báo cáo tổng hợp theo **nhân viên / phòng ban / kỳ** đúng công thức khai báo của mẫu,
**And** cùng input cho **cùng output** (có bộ test mẫu kiểm chứng đúng số). _(FR-D03, FR-D06)_

### Story 4.3: Tải kết quả + lịch sử lần chạy

As an **Admin**,
I want **tải file báo cáo .xlsx và xem lại lịch sử các lần chạy**,
So that **lấy lại kết quả và truy vết**.

**Acceptance Criteria:**

**Given** đã chạy một báo cáo,
**When** tôi tải kết quả,
**Then** nhận file **.xlsx** (đã trung hoà injection),
**And** lần chạy được lưu **lịch sử** (ai/khi nào/mẫu/file vào-ra) để tải lại sau. _(FR-D04, FR-D05, NFR-09)_
