---
stepsCompleted: [step-01, step-02, step-03]
inputDocuments:
  - '_bmad-output/planning-artifacts/prds/prd-bpm-platform-2026-06-24/prd.md'
  - '_bmad-output/planning-artifacts/prds/prd-bpm-platform-2026-06-24/addendum.md'
  - '_bmad-output/planning-artifacts/architecture/architecture-bpm-platform-2026-06-24/ARCHITECTURE-SPINE.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-bpm-platform-2026-06-24/DESIGN.md'
  - '_bmad-output/planning-artifacts/ux-designs/ux-bpm-platform-2026-06-24/EXPERIENCE.md'
---

# Nền tảng BPM - Epic Breakdown

## Overview

Phân rã PRD (65 FR nhóm A–I + 13 NFR), Architecture spine (15 AD) và UX spines thành các epic & story khả thi. **Quyết định C1 (lát cắt dọc):** nền generic từ ngày 1 (Flowable + form metadata) nhưng **giao hàng ưu tiên chạy trọn quy trình mẫu #1 ('Phối hợp nghiên cứu, tham mưu') end-to-end trước**, rồi mở rộng độ phủ generic. Thứ tự epic phản ánh trình tự này.

## Requirements Inventory

### Functional Requirements

- **A. Cấu hình quy trình:** FR-A01 designer kéo-thả · FR-A02 đầy đủ loại luồng · FR-A03 metadata bước · FR-A04 tập hành động · FR-A05 điều kiện chuyển bước · FR-A06 versioning + snapshot · FR-A07 publish/retire · FR-A08 SLA bước.
- **B. Form động:** FR-B01 form builder kéo-thả · FR-B02 bộ loại trường · FR-B03 org-tree picker · FR-B04 ẩn/hiện điều kiện · FR-B05 validation · FR-B06 gắn form vào bước · FR-B07 dữ liệu xuyên suốt · FR-B08 quyền trường theo bước · FR-B09 versioning form + snapshot.
- **C. Org & phân công:** FR-C01 cây org động · FR-C02 gán người vào vị trí · FR-C03 phân công vị trí/người · FR-C04 uỷ quyền/chuyển tiếp/thay thế · FR-C05 snapshot khi đổi cơ cấu · FR-C06 RBAC · FR-C07 đăng nhập nội bộ · FR-C08 fallback vị trí trống.
- **D. Thực thi nhiệm vụ:** FR-D01 khởi tạo · FR-D02 hộp thư việc · FR-D03 trạng thái + tiến độ · FR-D04 hành động theo bước · FR-D05 tự chuyển việc · FR-D06 quy tắc sửa/hủy · FR-D07 quá hạn (cờ) · FR-D08 instance lưu lịch sử · FR-D09 xin gia hạn · FR-D10 cascade khi Hủy.
- **E. Soạn thảo & ý kiến:** FR-E01 soạn thảo OnlyOffice · FR-E02 thu ý kiến phối hợp · FR-E03 bảng tổng hợp · FR-E04 tiếp thu/không tiếp thu · FR-E05 phê duyệt dự thảo · FR-E06 comment kiểu Jira · FR-E07 sửa ý kiến trong hạn · FR-E08 version dự thảo · FR-E09 ghi nhận ban hành.
- **F. Phối hợp:** FR-F01 phối hợp đơn vị · FR-F02 luồng trong đơn vị phối hợp · FR-F03 cá nhân phối hợp · FR-F04 nội dung+hạn từng nhánh · FR-F05 gộp/đồng bộ + chống treo · FR-F06 nhánh không phối hợp.
- **G. Minh bạch & thống kê:** FR-G01 dashboard · FR-G02 ai đang làm gì · FR-G03 theo dõi cá nhân · FR-G04 tra cứu · FR-G05 báo cáo 4 lát cắt · FR-G06 gần realtime ≤5s · FR-G07 xuất Excel/PDF.
- **H. Thông báo:** FR-H01 in-app · FR-H02 email bật/tắt · FR-H03 nhắc hạn · FR-H04 trung tâm thông báo.
- **I. Audit & lưu trữ:** FR-I01 audit append-only · FR-I02 vết phê duyệt · FR-I03 đóng hồ sơ & lưu trữ · FR-I04 metadata chuẩn-sẵn.

### NonFunctional Requirements

NFR-01 stack (Angular21/SpringBoot3.5/Flowable7/MariaDB11.8/OnlyOffice9.4) · NFR-02 quy mô 100–500 (giới hạn OnlyOffice đã gỡ ở 9.4) · NFR-03 on-prem · NFR-04 bảo mật/RBAC/TLS · NFR-05 khả dụng & sao lưu · NFR-06 hiệu năng (<2s, dashboard ≤5s) · NFR-07 tiếng Việt + sẵn sàng đa ngôn ngữ · NFR-08 tương thích trình duyệt · NFR-09 cấu hình không-build-lại (cốt lõi) · NFR-10 lưu form JSON-column + projection · NFR-11 backup nhất quán đa kho · NFR-12 hiệu năng báo cáo trên projection · NFR-13 vòng đời/partition bảng audit.

### Additional Requirements

(Từ Architecture spine — 15 AD)

- Modular monolith on-prem; Flowable **nhúng** là nguồn sự thật trạng thái thực thi (AD-1, AD-2). **Không starter template** ngoài khởi tạo Spring Boot + Angular chuẩn.
- Đồng-tồn-tại phiên bản: instance snapshot id định nghĩa process+form lúc khởi tạo/instantiate node (AD-3).
- Lưu form hybrid: JSON-column + **projection đồng bộ cùng giao dịch**, báo cáo chỉ đọc projection (AD-4).
- Trạng thái nghiệp vụ là **projection thuần** từ Flowable listener, một writer (AD-13); phân công **app là nguồn sự thật**, Flowable assignee mirror qua AssignmentPort (AD-14); cancel cascade **chỉ qua Flowable** (AD-15).
- "Quá hạn" là **cột cờ materialize indexed**, không phải trạng thái (AD-5).
- Audit append-only một **AuditPort**, phân vùng thời gian; **Flowable history KHÔNG phải audit** (AD-6).
- Org closure-table; vị trí là đơn vị phân công, snapshot người khi giao (AD-7).
- OnlyOffice sau **DocumentEditorPort** (JWT callback); app sở hữu tài liệu+version (AD-8).
- Auth nội bộ tách rời để cắm SSO sau (AD-9); NotificationPort fan-out kênh (AD-10); backup nhất quán DB+file-store (AD-11); feature→core, không feature↔feature (AD-12).

### UX Design Requirements

- **UX-DR1** — Hệ token thiết kế: bảng màu (primary + 5 màu trạng thái + đỏ overdue riêng), typography (Inter, base 14px), spacing lưới 4px (compact).
- **UX-DR2** — Component **badge trạng thái** + **cờ Quá hạn trực giao** (đỏ chồng lên, không thay badge — AD-5), kèm nhãn chữ (không chỉ màu).
- **UX-DR3** — App shell: **sidebar trái lọc theo vai trò** + topbar (tìm kiếm toàn cục, chuông thông báo, hồ sơ).
- **UX-DR4** — Component **bảng dữ liệu dense**: header surface-alt, zebra, sticky header, lọc/sắp xếp trên cột, cột hành động phải.
- **UX-DR5** — Component **canvas kéo-thả** dùng chung cho process designer (bpmn-js) và form builder: palette trái, vùng canvas, panel thuộc tính phải.
- **UX-DR6** — **Màn xử lý nhiệm vụ 3 cột**: trái timeline tiến độ bước + lịch sử; giữa form bước + OnlyOffice nhúng; phải bảng tổng hợp ý kiến + comment threaded @mention.
- **UX-DR7** — Component **cây tổ chức** nhiều cấp: expand/collapse, kéo-thả, badge vị trí trống.
- **UX-DR8** — **Dashboard**: thẻ đếm (đang xử lý/hoàn thành/quá hạn/sắp hạn) + bảng 4 lát cắt + nút xuất Excel/PDF.
- **UX-DR9** — **Accessibility WCAG 2.1 AA**: tương phản ≥4.5:1, focus ring, điều hướng bàn phím đầy đủ (kể cả lối thay thế kéo-thả trên canvas), ARIA cho badge, zoom 200%.
- **UX-DR10** — **State patterns** mỗi bảng/canvas: skeleton/empty-có-hướng-dẫn/error-có-traceId; nhãn "Phiên bản quy trình vX" trên instance; chip trạng thái từng nhánh phối hợp.
- **UX-DR11** — Microcopy **tiếng Việt trang trọng**: nút động từ, thông báo nêu việc+người+hạn, không emoji.

### FR Coverage Map

- FR-C01..C08, FR-I01, FR-I02 → **Epic 1** (nền tảng & tổ chức)
- FR-A01..A08 (A02 tập cốt lõi), FR-B01..B09 (B02 tập cốt lõi) → **Epic 2** (cấu hình đủ chạy mẫu #1)
- FR-D01..D10, FR-E01..E09, FR-F01..F06, FR-I03, FR-I04 → **Epic 3** (thực thi & chạy mẫu #1 end-to-end)
- FR-G01..G07, FR-H01..H04 → **Epic 4** (minh bạch, báo cáo, thông báo)
- FR-A02 (đầy đủ loại luồng), FR-B02 (đầy đủ loại trường) + NFR vận hành + UX polish → **Epic 5** (mở rộng generic & hoàn thiện)

## Epic List

### Epic 1: Nền tảng & Quản trị tổ chức
Người dùng đăng nhập, quản trị cây cơ cấu tổ chức/vị trí/tài khoản, phân quyền; hệ thống có lõi phân công (snapshot) và audit — bệ phóng cho mọi epic sau.
**FRs covered:** FR-C01..C08, FR-I01, FR-I02 · **NFR:** 04, 09, 13 · **AD:** 1,2,6,7,9,12,13,14

### Epic 2: Cấu hình quy trình & form (đủ chạy mẫu #1)
Admin IT tự thiết kế quy trình kéo-thả và form động (đủ loại luồng/loại trường cho quy trình mẫu #1), publish có phiên bản — hiện thực giá trị "ít Dev".
**FRs covered:** FR-A01..A08 (A02 tập cốt lõi), FR-B01..B09 (B02 tập cốt lõi) · **NFR:** 09, 10 · **AD:** 3, 4

### Epic 3: Thực thi nhiệm vụ & chạy trọn quy trình mẫu #1
Chuyên viên & lãnh đạo xử lý nhiệm vụ end-to-end: hộp thư việc, soạn thảo OnlyOffice, phối hợp nội bộ song song, phê duyệt, ghi nhận ban hành, đóng hồ sơ.
**FRs covered:** FR-D01..D10, FR-E01..E09, FR-F01..F06, FR-I03, FR-I04 · **AD:** 3,5,8,13,14,15

### Epic 4: Minh bạch, thống kê & thông báo
Quản lý thấy toàn cảnh nhiệm vụ realtime, báo cáo 4 lát cắt xuất Excel/PDF; mọi vai trò nhận thông báo in-app + email.
**FRs covered:** FR-G01..G07, FR-H01..H04 · **NFR:** 06, 12 · **AD:** 4, 10

### Epic 5: Mở rộng generic & hoàn thiện vận hành
Mở độ phủ generic đầy đủ (mọi loại luồng/loại trường, designer nâng cao), hoàn thiện NFR vận hành on-prem và UX polish/accessibility.
**FRs covered:** FR-A02 (đầy đủ), FR-B02 (đầy đủ) · **NFR:** 05, 06, 07, 08, 11, 13(ops) · **UX-DR:** 1–11 · **AD:** 11

---

## Epic 1: Nền tảng & Quản trị tổ chức

Đăng nhập nội bộ, quản trị tổ chức, phân quyền, lõi phân công snapshot và audit append-only.

### Story 1.1: Đăng nhập & quản lý tài khoản nội bộ

As a quản trị hệ thống,
I want quản lý tài khoản nội bộ (tạo, đặt mật khẩu, khóa) và người dùng đăng nhập bằng tài khoản hệ thống,
So that hệ thống có cổng xác thực riêng mà không phụ thuộc SSO/AD ở GĐ1.

**Acceptance Criteria:**

**Given** một quản trị đã đăng nhập, **When** tạo tài khoản mới với tên đăng nhập + mật khẩu, **Then** tài khoản được lưu (mật khẩu băm) **And** người dùng đó đăng nhập được qua HTTPS, sai mật khẩu bị từ chối, phiên hết hạn theo cấu hình (FR-C07, NFR-04).
**Given** kiến trúc auth tách rời (AD-9), **When** triển khai, **Then** lớp xác thực là module riêng có thể thay bằng SSO sau mà không sửa lõi nghiệp vụ.

### Story 1.2: Cây cơ cấu tổ chức nhiều cấp

As a quản trị,
I want CRUD cây đơn vị độ sâu tùy ý (closure-table),
So that mô hình tổ chức thực tế (Tập đoàn → đơn vị con → …) được biểu diễn động.

**Acceptance Criteria:**

**Given** màn Quản trị tổ chức, **When** thêm/sửa/xóa/di chuyển nút đơn vị ở bất kỳ cấp, **Then** cây cập nhật, truy vấn tổ tiên/hậu duệ đúng (closure-table, AD-7) **And** không cho xóa đơn vị còn chứa vị trí đang gán việc.

### Story 1.3: Vị trí/chức danh & gán người giữ

As a quản trị,
I want tạo vị trí trong đơn vị và gán đúng một người giữ mỗi vị trí tại một thời điểm,
So that việc giao theo vị trí luôn giải về đúng một người.

**Acceptance Criteria:**

**Given** một đơn vị, **When** tạo vị trí và gán người, **Then** vị trí có tối đa một người giữ hiện hành **And** gán người mới sẽ kết thúc nhiệm kỳ người cũ (lưu lịch sử) **And** đổi người giữ ghi audit.

### Story 1.4: Phân quyền RBAC theo vị trí → vai trò

As a quản trị,
I want gán vai trò cho vị trí và kiểm soát ai thấy/làm gì,
So that quyền được resolve qua vị trí thay vì gán trực tiếp cho người.

**Acceptance Criteria:**

**Given** vai trò định nghĩa quyền, **When** gán vai trò cho một vị trí, **Then** người đang giữ vị trí nhận đúng quyền **And** đổi người giữ vị trí tự kế thừa quyền **And** sidebar/khu chức năng lọc theo vai trò (FR-C06, UX-DR3).

### Story 1.5: Phân công theo vị trí/người + snapshot khi giao

As a hệ thống,
I want khi giao việc thì snapshot người được resolve vào task và coi bảng app là nguồn sự thật phân công,
So that đổi cơ cấu sau đó không cướp việc đang chạy và không lệch với Flowable assignee.

**Acceptance Criteria:**

**Given** một bước phân công theo vị trí, **When** việc được giao, **Then** `TASK_ASSIGNMENT` lưu người+vị trí tại thời điểm giao, Flowable assignee mirror qua AssignmentPort **trong cùng giao dịch** (AD-14) **And** sau đó đổi người giữ vị trí thì việc đang chạy vẫn ở người cũ (FR-C05), chỉ việc mới giải về người mới.

### Story 1.6: Uỷ quyền, chuyển tiếp & người thay thế

As a người giữ vị trí,
I want uỷ quyền/chuyển tiếp việc hoặc đặt người thay thế khi vắng,
So that công việc không bị tắc.

**Acceptance Criteria:**

**Given** một việc đang ở người A, **When** A uỷ quyền/chuyển tiếp cho B (hoặc cấu hình người thay thế), **Then** việc chuyển sang B qua AssignmentPort (app+Flowable một giao dịch), ghi audit **And** chuỗi uỷ quyền có guard chống lặp/self-approval (tách quyền duyệt).

### Story 1.7: Hàng đợi "chưa có người nhận" cho vị trí trống

As a hệ thống,
I want khi việc mới route tới vị trí đang trống thì giữ ở hàng đợi đơn vị và cảnh báo,
So that việc không biến mất âm thầm và đơn vị không tắc ở khâu duyệt.

**Acceptance Criteria:**

**Given** một vị trí chưa có người giữ, **When** việc mới route tới, **Then** việc vào hàng đợi "chưa có người nhận" của đơn vị + cờ `UNASSIGNED` + candidate-group = đơn vị (AD-14), cảnh báo cấp trên (FR-C08) **And** người có quyền gán tạm/định tuyến lên cấp trên, ghi audit.

### Story 1.8: Audit trail append-only + vết phê duyệt

As a kiểm toán viên,
I want mọi thay đổi trạng thái/dữ liệu được ghi append-only qua một cổng audit,
So that có vết đầy đủ, không sửa được, truy vết được các cấp phê duyệt.

**Acceptance Criteria:**

**Given** một hành động làm thay đổi trạng thái/dữ liệu, **When** thực thi, **Then** một bản ghi audit (ai·làm gì·đối tượng·thời điểm·trước/sau) được ghi qua **AuditPort** vào bảng append-only phân vùng thời gian (AD-6, NFR-13), không UPDATE/DELETE **And** Flowable history không được dùng làm audit nghiệp vụ **And** mỗi nhiệm vụ truy được lịch sử các cấp duyệt + nhận xét (FR-I02).

## Epic 2: Cấu hình quy trình & form (đủ chạy mẫu #1)

Process designer + form builder kéo-thả, versioning với snapshot.

### Story 2.1: Process designer kéo-thả + metadata bước

As a admin IT,
I want thiết kế quy trình trên canvas kéo-thả (bpmn-js) và khai báo metadata mỗi bước,
So that tạo quy trình mới mà không cần viết code.

**Acceptance Criteria:**

**Given** canvas designer (UX-DR5), **When** kéo bước/gateway/luồng nối và cấu hình metadata bước (vai trò/vị trí, form gắn, dữ liệu vào/ra, hành động, hạn, thông báo), **Then** quy trình lưu được dưới dạng định nghĩa BPMN **And** không thao tác nào yêu cầu build lại/deploy (FR-A01, FR-A03, NFR-09).

### Story 2.2: Loại luồng cốt lõi cho mẫu #1 + điều kiện chuyển bước

As a admin IT,
I want cấu hình tuần tự, rẽ nhánh điều kiện, chạy song song, gộp/đồng bộ (join),
So that mô hình hóa được quy trình mẫu #1 "Phối hợp nghiên cứu, tham mưu".

**Acceptance Criteria:**

**Given** designer, **When** thêm gateway rẽ nhánh với điều kiện dựa trên dữ liệu form (vd "có phối hợp"), nhánh song song và join, **Then** Flowable thực thi đúng định tuyến (FR-A02 tập cốt lõi, FR-A05) **And** quy trình mẫu #1 mô hình hóa trọn vẹn. _(Loại luồng còn lại: lặp nhiều vòng/quay lại bước → Epic 5.)_

### Story 2.3: Tập hành động cho phép trên bước

As a admin IT,
I want cấu hình tập hành động mỗi bước (Ghi lại/Sửa/Hủy/Trình duyệt/Phê duyệt/Trả lại/Từ chối/Uỷ quyền),
So that hành vi cho phép của từng bước khớp nghiệp vụ.

**Acceptance Criteria:**

**Given** một bước, **When** chọn tập hành động cho phép, **Then** khi thực thi chỉ các hành động đã cấu hình hiển thị cho đúng vai trò (FR-A04) **And** mỗi hành động map tới hành vi Flowable tương ứng.

### Story 2.4: Versioning quy trình + publish/retire + snapshot

As a admin IT,
I want lưu quy trình có phiên bản, publish/retire, và instance snapshot phiên bản lúc khởi tạo,
So that sửa quy trình không làm vỡ nhiệm vụ đang chạy.

**Acceptance Criteria:**

**Given** một quy trình đã publish đang có instance chạy, **When** publish phiên bản mới, **Then** instance đang chạy giữ nguyên phiên bản đã snapshot (AD-3), instance mới dùng bản mới nhất **And** chỉ bản publish mới khởi tạo được nhiệm vụ (FR-A06, FR-A07) **And** UI hiển thị nhãn "Phiên bản vX" (UX-DR10).

### Story 2.5: SLA/hạn theo bước

As a admin IT,
I want gán hạn xử lý (SLA) cho bước và/hoặc toàn quy trình,
So that hệ thống theo dõi và cảnh báo quá hạn.

**Acceptance Criteria:**

**Given** một bước có SLA, **When** việc tới bước đó, **Then** hạn được tính và lưu trên task **And** đến hạn hệ thống bật cờ quá hạn (liên kết Epic 3 FR-D07) và phát thông báo (Nhóm H).

### Story 2.6: Form builder kéo-thả + loại trường cốt lõi

As a admin IT,
I want thiết kế form bằng kéo-thả với bộ loại trường cốt lõi,
So that mỗi bước có form phù hợp mà không cần code.

**Acceptance Criteria:**

**Given** form builder (UX-DR5), **When** kéo trường (văn bản, số, ngày-giờ, có/không, danh sách chọn, tải file, rich-text, bảng nhiều dòng) và cấu hình thuộc tính, **Then** form sinh tự động từ metadata (FR-B01, FR-B02 tập cốt lõi) **And** dữ liệu form lưu JSON-column (NFR-10, AD-4). _(Bộ loại trường đầy đủ → Epic 5.)_

### Story 2.7: Org-tree picker dùng lại

As a admin IT,
I want thành phần chọn nhân sự/đơn vị theo cây tổ chức,
So that dùng chung cho cả form lẫn quy tắc phân công.

**Acceptance Criteria:**

**Given** một trường kiểu chọn-người, **When** mở picker, **Then** hiển thị cây tổ chức (UX-DR7) cho chọn vị trí/người **And** cùng component được tái dụng ở cấu hình phân công bước (FR-B03).

### Story 2.8: Ẩn/hiện điều kiện + validation

As a admin IT,
I want cấu hình trường ẩn/hiện theo điều kiện và quy tắc validation,
So that form phản ứng theo dữ liệu và đảm bảo nhập đúng.

**Acceptance Criteria:**

**Given** một form, **When** đặt điều kiện hiển thị (vd chọn "có phối hợp" mới hiện phần đơn vị phối hợp) và validation (bắt buộc/định dạng/min-max), **Then** runtime ẩn/hiện đúng và chặn submit khi vi phạm validation (FR-B04, FR-B05).

### Story 2.9: Gắn form vào bước + dữ liệu xuyên suốt + quyền trường theo bước

As a hệ thống,
I want gắn form cho từng bước, mang dữ liệu xuyên suốt, và áp quyền trường theo bước,
So that cùng một trường có thể chỉ-đọc ở bước này, cho-sửa ở bước khác.

**Acceptance Criteria:**

**Given** một nhiệm vụ qua nhiều bước, **When** chuyển bước, **Then** dữ liệu bước trước mang theo (mặc định thu gọn, mở lịch sử xem lại — FR-B07) **And** quyền trường theo cấu hình bước (chỉ-đọc/cho-sửa/ẩn — FR-B08) **And** form đúng bước hiển thị (FR-B06).

### Story 2.10: Versioning form + snapshot mọi node

As a hệ thống,
I want form có phiên bản và mọi node tạo form-binding snapshot phiên bản form,
So that sửa form không làm vỡ instance/sub-task đang chạy.

**Acceptance Criteria:**

**Given** một form đang dùng bởi instance/sub-task chạy, **When** publish phiên bản form mới, **Then** instance/sub-task giữ snapshot `formDefinitionVersionId` tại thời điểm node được instantiate (AD-3, FR-B09), dữ liệu đã nhập vẫn hợp lệ **And** node mới dùng bản form mới nhất.

## Epic 3: Thực thi nhiệm vụ & chạy trọn quy trình mẫu #1

Xử lý nhiệm vụ end-to-end, soạn thảo, phối hợp, phê duyệt, ban hành, lưu trữ.

### Story 3.1: Khởi tạo nhiệm vụ từ quy trình publish

As a chuyên viên chủ trì,
I want khởi tạo nhiệm vụ từ một quy trình đã publish và điền form bước đầu,
So that bắt đầu một việc theo quy trình chuẩn.

**Acceptance Criteria:**

**Given** danh sách quy trình publish, **When** chọn một quy trình và điền form bước 1, **Then** một instance Flowable được tạo, snapshot phiên bản process+form (AD-3), lưu lịch sử (FR-D01, FR-D08) **And** việc xuất hiện đúng người thực hiện bước kế.

### Story 3.2: Hộp thư việc "Việc của tôi"

As a người dùng,
I want hộp thư việc nhóm theo trạng thái với lọc/sắp xếp,
So that biết việc nào cần xử lý trước.

**Acceptance Criteria:**

**Given** tôi có việc, **When** mở "Việc của tôi", **Then** việc đến theo vai trò/vị trí, nhóm Chờ xử lý/Đang làm/Đã xong, lọc theo hạn/trạng thái/loại (FR-D02, UX-DR4) **And** việc quá hạn nổi đỏ đầu danh sách (UX-DR2).

### Story 3.3: Trạng thái nhiệm vụ (projection) + tiến độ bước

As a người dùng,
I want thấy trạng thái nhiệm vụ và tiến độ theo bước,
So that nắm việc đang ở đâu.

**Acceptance Criteria:**

**Given** một nhiệm vụ, **When** xem chi tiết, **Then** trạng thái hiển thị từ tập cố định {Chờ phê duyệt, Đang xử lý, Đã hoàn thành, Hủy} được suy ra bởi **một StatusProjectionWriter** từ Flowable listener (AD-13), không service set trực tiếp **And** timeline tiến độ bước hiển thị (FR-D03, UX-DR6).

### Story 3.4: Hành động trên việc theo bước & vai trò

As a người thực hiện,
I want thực hiện đúng các hành động cho phép của bước,
So that thao tác khớp quy trình.

**Acceptance Criteria:**

**Given** một việc ở một bước, **When** mở, **Then** chỉ hiển thị hành động đã cấu hình (FR-A04) cho đúng vai trò (FR-D04) **And** mỗi hành động sinh audit (AD-6).

### Story 3.5: Tự chuyển việc sang bước kế

As a hệ thống,
I want khi hoàn thành một bước thì tự chuyển việc theo định nghĩa & điều kiện,
So that luồng chạy tự động.

**Acceptance Criteria:**

**Given** một bước hoàn thành, **When** điều kiện chuyển thỏa, **Then** Flowable định tuyến sang người thực hiện bước kế (FR-D05), snapshot phân công (AD-14) **And** thông báo người nhận (Nhóm H).

### Story 3.6: Quy tắc sửa/hủy theo trạng thái

As a người thực hiện,
I want sửa/hủy tự do trước khi trình, và hủy sau khi trình phải được duyệt,
So that dữ liệu được kiểm soát theo trạng thái.

**Acceptance Criteria:**

**Given** một việc, **When** chưa trình duyệt, **Then** cho sửa/hủy tự do; **When** đã trình, **Then** hủy cần phê duyệt (FR-D06) **And** mọi sửa/hủy ghi audit.

### Story 3.7: Cờ Quá hạn trực giao + theo dõi hạn

As a hệ thống,
I want bật cờ quá hạn độc lập với trạng thái và gỡ cờ khi gia hạn được duyệt,
So that metric trễ hạn phản ánh đúng thực tế.

**Acceptance Criteria:**

**Given** một việc qua hạn, **When** scheduler chạy, **Then** cột cờ `overdue` (materialize indexed, AD-5) bật, trạng thái nghiệp vụ giữ nguyên **And** khi gia hạn được duyệt với hạn tương lai, cờ được gỡ (FR-D07), lịch sử từng-quá-hạn lưu audit.

### Story 3.8: Xin gia hạn

As a người thực hiện,
I want đề xuất gia hạn (kể cả khi đã quá hạn) và cấp có thẩm quyền duyệt,
So that hạn mới được cập nhật minh bạch.

**Acceptance Criteria:**

**Given** một việc, **When** đề xuất gia hạn, **Then** yêu cầu chuyển cấp có thẩm quyền; **When** duyệt, **Then** hạn mới cập nhật, gỡ cờ quá hạn nếu hạn ở tương lai (FR-D09, FR-D07), ghi audit **And** yêu cầu gia hạn có hạn duyệt riêng để không treo.

### Story 3.9: Cascade khi Hủy nhiệm vụ

As a hệ thống,
I want khi hủy task cha có nhánh phối hợp con thì thu hồi các nhánh con,
So that không để lại sub-task mồ côi.

**Acceptance Criteria:**

**Given** task cha có nhánh phối hợp đang chờ, **When** task cha bị Hủy, **Then** việc thực thi **chỉ qua Flowable** (delete execution → execution-listener) đóng các COLLAB_REQUEST con, gỡ khỏi hộp thư người phối hợp, thông báo, ghi audit (FR-D10, AD-15) **And** app không tự xóa nhánh ngoài engine.

### Story 3.10: Soạn thảo OnlyOffice nhúng + import + version dự thảo

As a chuyên viên chủ trì,
I want soạn thảo văn bản trong hệ thống bằng OnlyOffice nhúng, import .docx và lưu phiên bản,
So that soạn dự thảo ngay trong luồng việc.

**Acceptance Criteria:**

**Given** một bước soạn thảo, **When** mở trình soạn thảo, **Then** OnlyOffice Docs 9.4 nhúng qua **DocumentEditorPort** (JWT callback, AD-8) cho soạn/sửa **And** import .docx + đính kèm file được (FR-E01) **And** mỗi lần lưu sinh phiên bản dự thảo (ai/khi nào) do **app sở hữu** (FR-E08), xem lại được.

### Story 3.11: Thu thập ý kiến phối hợp + bảng tổng hợp

As a chuyên viên chủ trì,
I want hệ thống gom ý kiến phối hợp vào một bảng tổng hợp,
So that xem & tiếp thu tập trung.

**Acceptance Criteria:**

**Given** các đơn vị/cá nhân phối hợp đã gửi ý kiến (có cấu trúc + đính kèm, theo hạn — FR-E02), **When** mở bảng tổng hợp, **Then** mọi ý kiến tự gom thành bảng (thủ công, không AI — FR-E03, UX-DR6).

### Story 3.12: Tiếp thu/không tiếp thu + phê duyệt dự thảo

As a chủ trì & lãnh đạo,
I want đánh dấu tiếp thu/không tiếp thu (kèm giải trình) và lãnh đạo phê duyệt/trả lại,
So that dự thảo được hoàn thiện và kiểm soát chất lượng.

**Acceptance Criteria:**

**Given** bảng tổng hợp ý kiến, **When** chủ trì đánh dấu tiếp thu/không tiếp thu (giải trình) và hoàn thiện dự thảo (FR-E04), **Then** trình lãnh đạo; **When** lãnh đạo phê duyệt hoặc trả lại kèm nhận xét (FR-E05), **Then** việc chuyển bước tương ứng, ghi vết duyệt (FR-I02).

### Story 3.13: Comment kiểu Jira + @mention

As a người tham gia,
I want bình luận threaded có @mention trên dự thảo/nhiệm vụ,
So that trao đổi nhanh bên cạnh ý kiến chính thức.

**Acceptance Criteria:**

**Given** một dự thảo/nhiệm vụ, **When** thêm comment threaded và @mention người, **Then** hiển thị theo thời gian, người được mention nhận thông báo (FR-E06, AD-10) **And** comment tách biệt với văn bản ý kiến chính thức.

### Story 3.14: Sửa ý kiến đã gửi khi còn hạn

As a đơn vị phối hợp,
I want sửa ý kiến đã gửi khi còn trong thời hạn phối hợp,
So that điều chỉnh kịp thời.

**Acceptance Criteria:**

**Given** một ý kiến đã gửi, **When** còn trong hạn phối hợp, **Then** cho sửa và cập nhật bảng tổng hợp (FR-E07); **When** hết hạn, **Then** khóa sửa.

### Story 3.15: Phối hợp đơn vị/cá nhân song song + join + chống treo

As a chủ trì,
I want gửi phối hợp tới ≥1 đơn vị/cá nhân song song, theo dõi từng nhánh, và gộp khi đủ/hết hạn,
So that chạy trọn khâu phối hợp của quy trình mẫu #1 mà không treo.

**Acceptance Criteria:**

**Given** một bước phối hợp, **When** gửi tới nhiều đơn vị/cá nhân với nội dung+hạn riêng (FR-F01, FR-F03, FR-F04), **Then** mỗi nhánh có chip trạng thái riêng (UX-DR10); luồng đơn vị phối hợp: vụ trưởng phân công chuyên viên → soạn → phê duyệt → trả về (FR-F02) **And** gộp chờ đủ hoặc theo hạn; nhánh không trả tới hạn áp chính sách đóng cấu hình được (input một phần/leo thang — FR-F05), ghi audit **And** nhánh "không phối hợp" bỏ qua bước (FR-F06).

### Story 3.16: Ghi nhận kết quả ký/ban hành

As a văn thư,
I want nhập số văn bản, ngày ban hành và upload bản scan đã ký,
So that hệ thống theo dõi trạng thái khâu ký (làm ngoài hệ thống GĐ1).

**Acceptance Criteria:**

**Given** bước ký/ban hành, **When** văn thư nhập số VB + ngày ban hành + upload PDF đã ký, **Then** bước đánh dấu Hoàn thành, hệ thống lưu kết quả (không thực hiện ký số — FR-E09), ghi audit.

### Story 3.17: Đóng hồ sơ & lưu trữ + metadata chuẩn-sẵn

As a chuyên viên chủ trì,
I want đóng hồ sơ khi kết thúc quy trình với bộ metadata chuẩn-sẵn,
So that hồ sơ tra cứu lại được và GĐ sau nâng cấp không phải migrate lớn.

**Acceptance Criteria:**

**Given** một nhiệm vụ kết thúc, **When** đóng hồ sơ, **Then** gom văn bản+dữ liệu+metadata tối thiểu (mã hồ sơ, tiêu đề, loại, đơn vị/người lập, thời gian mở/đóng, danh mục văn bản với số/tên/loại/ngày ký/người ký, thời hạn bảo quản, trạng thái — FR-I04) thành hồ sơ tra cứu được (FR-I03).

## Epic 4: Minh bạch, thống kê & thông báo

Dashboard, báo cáo 4 lát cắt, projection báo cáo, thông báo đa kênh.

### Story 4.1: Projection trường reportable đồng bộ

As a hệ thống,
I want chiếu các trường reportable sang bảng quan hệ có chỉ mục trong cùng giao dịch ghi,
So that báo cáo nhanh và không lệch dữ liệu.

**Acceptance Criteria:**

**Given** một form có trường gắn cờ reportable, **When** dữ liệu được ghi, **Then** một **ProjectionWriter** chiếu sang bảng projection có chỉ mục **trong cùng giao dịch** với JSON payload (AD-4, NFR-12), cấm async **And** có job reconcile phát hiện lệch JSON↔projection.

### Story 4.2: Dashboard điều hành

As a quản lý,
I want dashboard tổng quan nhiệm vụ theo đơn vị/người/trạng thái,
So that nắm bức tranh điều hành.

**Acceptance Criteria:**

**Given** dữ liệu projection, **When** mở dashboard, **Then** hiển thị thẻ đếm đang xử lý/hoàn thành/quá hạn/sắp đến hạn (FR-G01, UX-DR8) đọc từ projection (không query JSON).

### Story 4.3: "Ai đang làm gì"

As a quản lý,
I want danh sách nhân sự + nhiệm vụ đang đảm nhận + tiến độ,
So that biết tải công việc thực tế.

**Acceptance Criteria:**

**Given** projection, **When** mở "Ai đang làm gì", **Then** liệt kê người + nhiệm vụ + trạng thái/tiến độ (FR-G02), lọc theo đơn vị.

### Story 4.4: Theo dõi nhiệm vụ cá nhân

As a nhân viên,
I want xem thống kê nhiệm vụ của mình (đang làm/đã xong/trễ hạn),
So that tự chủ theo dõi công việc.

**Acceptance Criteria:**

**Given** tôi đăng nhập, **When** mở trang cá nhân, **Then** thống kê nhiệm vụ của tôi theo trạng thái + cờ quá hạn (FR-G03).

### Story 4.5: Tra cứu & tìm kiếm

As a người dùng,
I want tìm kiếm nhiệm vụ & hồ sơ theo nhiều tiêu chí,
So that tìm nhanh thông tin cần.

**Acceptance Criteria:**

**Given** dữ liệu, **When** tìm theo loại/trạng thái/đơn vị/người/thời gian/từ khóa, **Then** trả kết quả đúng (FR-G04) đọc từ projection/chỉ mục.

### Story 4.6: Báo cáo 4 lát cắt + lọc thời gian

As a quản lý,
I want báo cáo thống kê theo đơn vị/người/loại/đúng-trễ hạn, lọc theo khoảng thời gian,
So that phục vụ họp giao ban.

**Acceptance Criteria:**

**Given** projection (gồm cờ overdue indexed), **When** chọn lát cắt + khoảng thời gian, **Then** báo cáo tổng hợp đúng theo 4 lát cắt (FR-G05) trong thời gian chấp nhận được (NFR-12).

### Story 4.7: Cập nhật gần realtime ≤5s

As a quản lý,
I want dashboard/hộp thư phản ánh thay đổi trong ≤5s,
So that thông tin đủ tươi để điều hành.

**Acceptance Criteria:**

**Given** một trạng thái nhiệm vụ thay đổi, **When** ≤5 giây, **Then** dashboard/hộp thư cập nhật (FR-G06, NFR-06) qua polling/refresh.

### Story 4.8: Xuất báo cáo Excel & PDF

As a quản lý,
I want xuất báo cáo ra Excel và PDF,
So that xử lý tiếp hoặc in/trình.

**Acceptance Criteria:**

**Given** một báo cáo, **When** bấm xuất, **Then** tải về file Excel (xử lý tiếp) và PDF (in/trình) đúng nội dung đang xem (FR-G07).

### Story 4.9: Thông báo in-app + trung tâm thông báo

As a người dùng,
I want nhận thông báo in-app cho các sự kiện và xem trung tâm thông báo,
So that không bỏ lỡ việc.

**Acceptance Criteria:**

**Given** một sự kiện (việc mới/duyệt/trả lại/từ chối/ý kiến về/comment-mention/sắp hạn/quá hạn), **When** xảy ra, **Then** thông báo in-app qua **NotificationPort** (AD-10, FR-H01) **And** trung tâm thông báo liệt kê, đánh dấu đã đọc (FR-H04).

### Story 4.10: Thông báo email bật/tắt + nhắc hạn

As a người dùng,
I want nhận email theo loại sự kiện (bật/tắt) và được nhắc trước hạn,
So that nắm việc cả khi không mở hệ thống.

**Acceptance Criteria:**

**Given** cấu hình kênh, **When** một sự kiện xảy ra và email bật cho loại đó, **Then** gửi email qua NotificationPort (FR-H02) **And** nhắc hạn trước X (cấu hình) + cảnh báo quá hạn (FR-H03).

## Epic 5: Mở rộng generic & hoàn thiện vận hành

Đầy đủ độ phủ generic, NFR vận hành on-prem, UX polish/accessibility.

### Story 5.1: Đầy đủ loại luồng (lặp, quay lại bước, đa nhánh phức)

As a admin IT,
I want cấu hình vòng lặp nhiều vòng, quay lại bước trước và định tuyến phức,
So that mọi dạng quy trình đều là tùy chọn cấu hình.

**Acceptance Criteria:**

**Given** designer, **When** thêm vòng lặp/quay-lại-bước/đa nhánh, **Then** Flowable thực thi đúng (FR-A02 đầy đủ) **And** versioning ý kiến khi re-collect ở vòng lặp được xử lý (không vỡ snapshot).

### Story 5.2: Đầy đủ bộ loại trường form

As a admin IT,
I want bộ loại trường mở rộng ngoài tập cốt lõi,
So that form phủ mọi nhu cầu nghiệp vụ.

**Acceptance Criteria:**

**Given** form builder, **When** thêm các loại trường mở rộng, **Then** sinh/lưu/validate đúng (FR-B02 đầy đủ) **And** tương thích projection reportable (AD-4).

### Story 5.3: Affordance designer nâng cao

As a admin IT,
I want validation phức, xem khác biệt phiên bản và thao tác designer nâng cao,
So that cấu hình quy trình/form hiệu quả ở quy mô lớn.

**Acceptance Criteria:**

**Given** designer/form builder, **When** dùng tính năng nâng cao (so sánh phiên bản, nhân bản, validation phức), **Then** hoạt động đúng, không phá vỡ AD-3.

### Story 5.4: Backup nhất quán đa kho + phục hồi

As a quản trị hệ thống,
I want backup phối hợp cùng điểm thời gian giữa MariaDB và file-store với RPO/RTO rõ,
So that phục hồi không làm lệch tài liệu và metadata.

**Acceptance Criteria:**

**Given** dữ liệu ở DB + file-store, **When** chạy backup, **Then** ảnh chụp nhất quán cùng điểm thời gian (AD-11, NFR-11), phát hiện tham chiếu/file mồ côi **And** quy trình phục hồi kiểm thử đạt RPO ≤24h/RTO ≤4h [ASSUMPTION].

### Story 5.5: Vòng đời & partition bảng audit (vận hành)

As a quản trị hệ thống,
I want bảng audit phân vùng theo thời gian + chính sách lưu trữ dữ liệu cũ,
So that truy vấn audit/báo cáo không suy giảm theo thời gian.

**Acceptance Criteria:**

**Given** bảng audit tăng trưởng, **When** vận hành dài hạn, **Then** phân vùng/chỉ mục theo thời gian + đóng băng dữ liệu cũ hoạt động (NFR-13) **And** truy vấn audit trong khoảng thời gian vẫn đạt hiệu năng mục tiêu.

### Story 5.6: UX polish — design system & accessibility AA

As a người dùng,
I want hệ thống dùng token thiết kế nhất quán và đạt WCAG 2.1 AA,
So that giao diện rõ ràng, tiếp cận được.

**Acceptance Criteria:**

**Given** toàn bộ giao diện, **When** kiểm thử, **Then** token màu/typography/spacing áp nhất quán (UX-DR1), badge trạng thái + cờ overdue trực giao có nhãn chữ (UX-DR2) **And** đạt tương phản ≥4.5:1, điều hướng bàn phím đầy đủ (kể cả lối thay thế kéo-thả trên canvas), ARIA, zoom 200% (UX-DR9) **And** microcopy tiếng Việt trang trọng nhất quán (UX-DR11).

### Story 5.7: NFR vận hành — hiệu năng, tương thích, sẵn sàng đa ngôn ngữ

As a quản trị,
I want hệ thống đạt mục tiêu hiệu năng, chạy trên trình duyệt phổ biến và kiến trúc sẵn sàng đa ngôn ngữ,
So that vận hành ổn định cho 100–500 người dùng.

**Acceptance Criteria:**

**Given** tải thực tế, **When** thao tác thông thường, **Then** phản hồi <2s, dashboard ≤5s (NFR-06) **And** chạy đúng trên Chrome/Edge/Firefox bản hiện hành (NFR-08) **And** giao diện tiếng Việt với kiến trúc i18n-ready (NFR-07).
