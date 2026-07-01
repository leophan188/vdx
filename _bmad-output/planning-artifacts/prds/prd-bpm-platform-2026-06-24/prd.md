---
title: "PRD — Nền tảng Quản lý & Thực thi Quy trình Nghiệp vụ (BPM) cho Tập đoàn"
status: final
created: 2026-06-24
updated: 2026-06-24
stack: [Angular, Java Spring, MariaDB]
---

# PRD — Nền tảng Quản lý & Thực thi Quy trình Nghiệp vụ (BPM)

## Tổng quan

Nền tảng quản lý & thực thi quy trình nghiệp vụ (BPM) cho một tập đoàn/cơ quan đa đơn vị. Khác biệt cốt lõi: hệ thống **động, cấu hình được** để phục vụ rất nhiều quy trình thay đổi liên tục mà **gần như không cần phát triển phần mềm (Dev) mới** cho mỗi thay đổi — kèm năng lực **minh bạch điều hành và thống kê nhiệm vụ** mà tổ chức hiện chưa có.

## Vấn đề & Bối cảnh

- **Quá nhiều quy trình, biến động liên tục.** Mỗi điều chỉnh quy trình hiện nay tốn công Dev và thời gian; tổ chức cần một hệ thống động để cấu hình nhanh, gọn, ít phụ thuộc lập trình viên.
- **Mù thông tin điều hành.** Quản lý không nắm được nhân viên đang làm gì và các nhiệm vụ đã giao đang ở trạng thái/tiến độ nào.
- **Nhân viên không tự theo dõi được.** Mỗi người không có nơi thống kê danh sách và trạng thái nhiệm vụ của mình.
- **Thiếu thống kê/báo cáo** cho công tác quản lý nhiệm vụ.
- **Bối cảnh:** cơ quan/văn phòng kiểu VPTW; có hệ thống điều hành tác nghiệp ngoài (**ĐHTN**) ở khâu ký/ban hành; lưu trữ điện tử phải tuân thủ quy chế 06-QC/VPTW, QĐ 4063-QĐ/VPTW, NĐ 45/2020/NĐ-CP.

## Mục tiêu (Goals)

1. **Cấu hình quy trình động (low-code).** Admin nghiệp vụ tự tạo/chỉnh quy trình, form, phân công — không cần release code cho phần lớn thay đổi.
2. **Minh bạch điều hành.** Quản lý thấy được (gần) realtime: ai đang làm gì, mỗi nhiệm vụ ở trạng thái/tiến độ nào, việc nào sắp/đang trễ.
3. **Tự chủ cho nhân viên.** Mỗi người có nơi xem & theo dõi toàn bộ nhiệm vụ của mình.
4. **Thống kê & báo cáo nhiệm vụ.**
5. **Thực thi đúng quy trình phối hợp** (nội bộ & liên đơn vị) và **lưu trữ tuân thủ**.

## Thước đo thành công (Success Metrics)

> Mục tiêu khởi điểm — sẽ hiệu chỉnh sau pilot. Số đã chốt với người dùng (2026-06-24).

- % thay đổi quy trình thực hiện được **bằng cấu hình, không cần phát hành code**: **≥ 80%**.
- **Thời gian đưa một quy trình mới vào vận hành**: **≤ 1 ngày làm việc** (thay vì hàng tuần Dev).
- Quản lý xem được **trạng thái/tiến độ realtime** với độ phủ **≥ 95%** nhiệm vụ.
- **% nhiệm vụ trễ hạn**: mục tiêu **≤ 10%** sau 6 tháng vận hành. _[DEFER] baseline hiện trạng cần đo thực tế — người phụ trách: PM/đơn vị nghiệp vụ; revisit sau pilot 1 tháng._
- Tỷ lệ nhân viên chủ động dùng hệ thống để theo dõi nhiệm vụ cá nhân: **≥ 70%** dùng hàng tuần.
- _Counter-metric:_ thời gian/độ phức tạp cấu hình một quy trình không được tăng tới mức admin nghiệp vụ bỏ cuộc quay lại nhờ Dev.

## Mô hình tổ chức & Vai trò người dùng

### Mô hình tổ chức (cây động, nhiều cấp)

Cơ cấu tổ chức là **cây động, độ sâu tùy ý** (Tập đoàn → các cấp đơn vị con → … → vị trí/chức danh) — không cố định số cấp. Đây là xương sống để phân quyền và phân công: việc được gán theo *vị trí/chức danh* (giải quyết muộn — ai đang giữ chức tại thời điểm tạo việc) hoặc theo *người đích danh*. **Mỗi vị trí chỉ có một người giữ tại một thời điểm nhất định.**

### Vai trò (Actors)

| Vai trò | Trách nhiệm chính |
|---|---|
| **Chuyên viên chủ trì** | Tạo nhiệm vụ, tổng hợp ý kiến phối hợp, soạn dự thảo, trình ký, đóng hồ sơ & lưu trữ |
| **Lãnh đạo đơn vị chủ trì** (Vụ trưởng) | Phê duyệt nhiệm vụ; kiểm tra & phê duyệt dự thảo |
| **Vụ trưởng đơn vị phối hợp** | Phân công chuyên viên phối hợp; phê duyệt ý kiến trước khi trả về đơn vị chủ trì |
| **Chuyên viên đơn vị phối hợp** | Soạn ý kiến/góp ý theo phân công |
| **Cá nhân phối hợp** | Cho ý kiến/góp ý trực tiếp (không qua đơn vị) |
| **Lãnh đạo Ban / Văn thư** | Ký, ban hành — *GĐ1 thực hiện ngoài hệ thống, chỉ theo dõi trạng thái* |
| **Quản lý / Lãnh đạo (giám sát)** | Xem dashboard, tiến độ, thống kê nhiệm vụ |
| **Admin nghiệp vụ (IT)** | Cấu hình quy trình, form động, quy tắc phân công — *không viết code/deploy cho mỗi thay đổi* |
| **Quản trị hệ thống** | Quản lý người dùng, cơ cấu tổ chức, phân quyền |

> Vai trò gắn với **vị trí trong cơ cấu tổ chức**, không hard-code theo người. Một người có thể giữ nhiều vai trò; một vai trò áp cho nhiều người.

### Phân công & xử lý tình huống (cấu hình được)

Việc gán người thực hiện phải **cấu hình được** để bao cả bài toán chung lẫn các tình huống:
- Gán theo **vị trí/chức danh** (giải quyết muộn) hoặc **người đích danh**.
- **Uỷ quyền** (delegation) khi lãnh đạo phân cho cấp dưới.
- **Chuyển tiếp / phân công lại** (reassign) khi cần đổi người thực hiện.
- **Người thay thế khi vắng** (substitute) — đảm bảo việc không bị tắc khi người giữ vị trí đi vắng.
- Khi cơ cấu tổ chức thay đổi giữa chừng: giữ snapshot lịch sử cho audit; định nghĩa quy tắc xử lý việc đang chạy. _(chi tiết kỹ thuật ở addendum)_

## Bản đồ năng lực & Lộ trình

| # | Nhóm năng lực | Giai đoạn |
|---|---|---|
| A | Cấu hình quy trình (Process Designer) | GĐ1 — trụ cột |
| B | Form động (metadata-driven) | GĐ1 — trụ cột |
| C | Mô hình tổ chức & phân công | GĐ1 |
| D | Thực thi nhiệm vụ (task execution) | GĐ1 |
| E | Soạn thảo & cho ý kiến | GĐ1 |
| F | Phối hợp nội bộ + **liên đơn vị cơ bản** (GĐ1); phối hợp nâng cao (GĐ2) | GĐ1 / GĐ2 |
| G | Minh bạch & Thống kê | GĐ1 — trụ cột |
| H | Thông báo & Giao việc | GĐ1 |
| I | Audit & Lưu trữ tuân thủ | GĐ1 |

### Ngoài phạm vi (Out of scope)

- **Mọi tính năng AI** (trợ lý ảo hỗ trợ soạn thảo, tóm tắt dự thảo bằng AI) — **loại bỏ hoàn toàn**, mọi giai đoạn. _(Lưu ý: "bảng tổng hợp ý kiến" là tính năng thủ công, không phải AI — vẫn giữ.)_
- **Tích hợp ĐHTN** — ngoài phạm vi GĐ1 (để ngỏ cho giai đoạn sau).

---

## Yêu cầu chức năng (FR)

> FR đánh số toàn cục, ổn định.
>
> **Quy ước tiêu chí chấp nhận (acceptance):** mỗi FR được coi là "xong" khi (a) đường đi thành công thực hiện được qua UI bởi đúng vai trò; (b) các nhánh lỗi/biên đã nêu trong FR được xử lý có thông báo rõ ràng; (c) hành động sinh ra **audit** (FR-I01) khi làm thay đổi trạng thái/dữ liệu. Tiêu chí chi tiết theo từng FR sẽ được cụ thể hoá ở bước Epics/Stories.

### A. Cấu hình quy trình (Process Designer)

- **FR-A01** — Cung cấp trình thiết kế quy trình **kéo-thả trực quan** (canvas kiểu BPMN): Admin IT định nghĩa quy trình bằng các phần tử bước, cổng rẽ nhánh (gateway), luồng nối — **không cần viết code**.
- **FR-A02** — Hỗ trợ đầy đủ các dạng luồng, **bật/cấu hình tùy chọn** (bài toán tổng thể): tuần tự · rẽ nhánh điều kiện · chạy song song · gộp/đồng bộ (chờ tất cả nhánh) · quay lại bước trước · vòng lặp nhiều vòng.
- **FR-A03** — Mỗi bước khai báo metadata cấu hình được: tên & mô tả · vai trò/vị trí thực hiện · sự kiện kích hoạt · **form gắn vào bước** (→ Nhóm B) · dữ liệu vào/ra · mẫu file văn bản · hành động cho phép · điều kiện chuyển bước · người/cấp phê duyệt · **hạn xử lý (SLA)** · cấu hình thông báo (→ Nhóm H).
- **FR-A04** — Tập **hành động cho phép** trên mỗi bước cấu hình được: Ghi lại · Sửa · Hủy · Trình duyệt · Phê duyệt · Trả lại · **Từ chối** · **Uỷ quyền**.
- **FR-A05** — **Điều kiện chuyển bước** (rẽ nhánh) cấu hình dựa trên dữ liệu của form/nhiệm vụ (ví dụ: có phối hợp hay không, loại nhiệm vụ).
- **FR-A06** — Lưu quy trình dưới dạng **định nghĩa có phiên bản** (process definition versioned). Thay đổi quy trình **không cần phát hành code**. **Đồng tồn tại phiên bản là bắt buộc GĐ1:** mỗi nhiệm vụ **gắn cứng (snapshot) phiên bản định nghĩa tại thời điểm khởi tạo** và chạy hết vòng đời theo bản đó; nhiệm vụ mới dùng bản đã publish mới nhất. Nhiều phiên bản chạy song song là trạng thái bình thường. _Migration chủ động nhiệm vụ-đang-chạy sang bản mới = GĐ2 (xem addendum)._
- **FR-A07** — **Publish / Retire** một định nghĩa quy trình; chỉ bản đã publish mới khởi tạo nhiệm vụ mới.
- **FR-A08** — Gán **hạn xử lý (SLA)** cho từng bước và/hoặc toàn quy trình; hệ thống đánh dấu/cảnh báo **quá hạn** (→ trạng thái "Quá hạn", → Nhóm H thông báo).

### B. Form động (metadata-driven)

- **FR-B01** — **Form builder kéo-thả**: Admin IT thiết kế form bằng cách kéo trường vào canvas và cấu hình thuộc tính; form **sinh tự động từ metadata**, không cần code.
- **FR-B02** — Bộ loại trường GĐ1: văn bản ngắn/dài · số · ngày-giờ · có/không · danh sách chọn (dropdown/radio/checkbox) · **tải file đính kèm** · rich-text · **bảng/lưới nhiều dòng** (vd bảng tổng hợp ý kiến) · **chọn người/đơn vị theo cây tổ chức**. _Bộ trường có thể mở rộng ở giai đoạn sau._
- **FR-B03** — Thành phần **"Chọn nhân sự/đơn vị theo cây cơ cấu tổ chức"** (org-tree picker) — thiết kế như thành phần dùng lại, phục vụ cả form lẫn quy tắc phân công (→ Nhóm C). _Chuẩn bị sẵn kiến trúc ngay GĐ1._
- **FR-B04** — **Trường ẩn/hiện theo điều kiện** dựa trên giá trị của trường khác (vd: chọn "có phối hợp" mới hiện phần đơn vị phối hợp).
- **FR-B05** — **Quy tắc kiểm tra dữ liệu (validation)** cấu hình được: bắt buộc, định dạng, min/max, …
- **FR-B06** — **Gắn form vào bước** quy trình; mỗi bước có thể có form riêng.
- **FR-B07** — **Dữ liệu mang xuyên suốt nhiệm vụ.** Mặc định **ẩn/thu gọn** dữ liệu các bước trước; người dùng mở **lịch sử** để xem lại.
- **FR-B08** — **Quyền trường theo từng bước** (cấu hình được): cùng một trường có thể **chỉ đọc** ở bước này và **cho sửa** ở bước khác (hoặc ẩn).
- **FR-B09** — **Phiên bản định nghĩa form (bắt buộc GĐ1).** Form cũng được lưu có phiên bản; nhiệm vụ đang chạy **giữ snapshot định nghĩa form tại thời điểm khởi tạo** — sửa form **không làm vỡ** instance đang chạy (dữ liệu đã nhập và bố cục trường vẫn hợp lệ). Nhiệm vụ mới dùng bản form mới nhất gắn vào bước.

### C. Mô hình tổ chức & Phân công

- **FR-C01** — Quản lý cơ cấu tổ chức dạng **cây động, nhiều cấp** (độ sâu tùy ý): CRUD nút đơn vị ở mọi cấp và vị trí/chức danh. **Nhập & quản lý trực tiếp trong hệ thống** (GĐ1, không import HR/AD).
- **FR-C02** — Gán người dùng vào vị trí/chức danh. **Mỗi vị trí chỉ một người giữ** tại một thời điểm → giao theo vị trí giải quyết về đúng một người.
- **FR-C03** — Phân công bước theo **vị trí** (giải quyết muộn: ai đang giữ chức) hoặc theo **người đích danh**.
- **FR-C04** — **Uỷ quyền · chuyển tiếp/phân công lại · người thay thế khi vắng** — cấu hình được, đảm bảo việc không tắc.
- **FR-C05** — Cơ cấu thay đổi giữa chừng: giữ **snapshot lịch sử người thực hiện** cho audit. **Quy tắc:** task đang chạy **giữ nguyên người đã được giao tại thời điểm giao (snapshot)** — đổi cơ cấu/người giữ vị trí **không tự động** chuyển việc đang chạy; chỉ **việc mới** phát sinh sau thời điểm đổi mới giải về người giữ vị trí mới. Hỗ trợ hành động thủ công **"chuyển giao việc đang chạy"** (người có quyền) cho trường hợp người cũ nghỉ/rời — kèm ghi vết audit.
- **FR-C06** — **Phân quyền (RBAC)** theo vai trò/vị trí: kiểm soát ai được thấy/làm gì.
- **FR-C07** — **Xác thực đăng nhập nội bộ** (tài khoản riêng của hệ thống), không SSO/AD ở GĐ1: quản lý tài khoản, mật khẩu, gán vai trò.
- **FR-C08** — **Vị trí đích đang trống khi việc mới route tới.** Khi một bước (kể cả bước phê duyệt) phân công theo vị trí mà vị trí đó **chưa có người giữ**, việc **không được biến mất âm thầm**: hệ thống giữ việc ở hàng đợi **"chưa có người nhận"** của đơn vị, **cảnh báo người có quyền/cấp trên trực tiếp** (→ Nhóm H), và cho phép **gán tạm hoặc định tuyến lên cấp trên** để khơi thông. Mọi định tuyến tạm đều được ghi audit.

### D. Thực thi nhiệm vụ (Task Execution)

- **FR-D01** — Khởi tạo nhiệm vụ từ một quy trình đã publish (chọn loại nhiệm vụ/quy trình), điền form bước đầu (như Bước 1-1).
- **FR-D02** — **Hộp thư việc "Việc của tôi"**: việc đến tay theo vai trò/vị trí; phân nhóm *Chờ xử lý / Đang làm / Đã xong*; lọc & sắp xếp theo hạn, trạng thái, loại.
- **FR-D03** — **Trạng thái nhiệm vụ — tập cố định (fix cứng)**: Chờ phê duyệt · Đang xử lý · Đã hoàn thành · Hủy. **"Quá hạn" là CỜ trực giao** (overdue flag) chồng lên trạng thái xử lý, **không phải** một giá trị trong tập trạng thái — một việc có thể đồng thời *Đang xử lý + Quá hạn*. Việc gắn cờ/gỡ cờ quá hạn không làm mất trạng thái nghiệp vụ đang có. Kèm **tiến độ theo bước**. _(Hiển thị cho người dùng vẫn có thể gộp nhãn "Quá hạn" cho dễ đọc; về dữ liệu là cờ.)_
- **FR-D04** — Hành động trên việc theo vai trò & bước (Ghi lại/Sửa/Hủy/Trình duyệt/Phê duyệt/Trả lại/Từ chối/Uỷ quyền — từ FR-A04).
- **FR-D05** — Hoàn thành một bước → hệ thống **tự chuyển việc** sang người thực hiện bước kế theo định nghĩa & điều kiện.
- **FR-D06** — **Quy tắc sửa/hủy theo trạng thái**: trước khi trình duyệt thì được sửa/hủy tự do; sau khi trình, việc hủy cần được phê duyệt.
- **FR-D07** — Theo dõi hạn từng việc; tự **bật cờ Quá hạn** khi qua hạn. Khi **gia hạn được duyệt (FR-D09)** và hạn mới ở tương lai, hệ thống **gỡ cờ Quá hạn** (việc trở lại đúng hạn) — đảm bảo metric trễ hạn phản ánh đúng thực tế, không bị méo vĩnh viễn. Lịch sử từng-quá-hạn vẫn lưu trong audit.
- **FR-D08** — Mỗi nhiệm vụ là một **thực thi (instance)** của quy trình; lưu toàn bộ dữ liệu, lịch sử bước, người thực hiện, thời điểm.
- **FR-D09** — **Xin gia hạn**: người thực hiện đề xuất gia hạn hạn xử lý → cấp có thẩm quyền duyệt → cập nhật hạn mới và ghi vết audit. Cho phép xin gia hạn **cả khi việc đã quá hạn** (sau khi duyệt sẽ gỡ cờ Quá hạn theo FR-D07). _Bản thân yêu cầu gia hạn nên có hạn duyệt riêng để không treo (xem Câu hỏi mở)._
- **FR-D10** — **Cascade khi Hủy nhiệm vụ.** Khi một nhiệm vụ (task cha) bị Hủy mà đang có **nhánh phối hợp/sub-task con đang chờ** (Nhóm F): hệ thống **thu hồi/đóng các nhánh con** với lý do "task cha đã hủy", gỡ chúng khỏi hộp thư việc của người phối hợp, gửi thông báo, và ghi audit — **không để lại sub-task mồ côi**.

### E. Soạn thảo & Cho ý kiến

- **FR-E01** — **Soạn thảo văn bản trong hệ thống bằng trình soạn thảo tương đồng Word**, on-prem. _Khuyến nghị: **OnlyOffice Docs Community** (AGPL, miễn phí, có component Angular chính thức, độ trung thực .docx cao); runner-up Collabora Online. Chi tiết & lưu ý giấy phép/giới hạn 20 kết nối ở addendum._ Hỗ trợ **import file Word (.docx)** và **đính kèm file**.
- **FR-E02** — Thu thập **ý kiến phối hợp**: đơn vị/cá nhân phối hợp gửi văn bản ý kiến (có cấu trúc) kèm đính kèm, theo thời hạn phối hợp.
- **FR-E03** — **Bảng tổng hợp ý kiến**: hệ thống tự gom mọi ý kiến phối hợp vào một bảng để chủ trì xem & tiếp thu *(thủ công, không AI)*.
- **FR-E04** — Chủ trì hoàn thiện dự thảo dựa trên ý kiến; đánh dấu **tiếp thu / không tiếp thu (kèm giải trình)**.
- **FR-E05** — **Phê duyệt dự thảo**: lãnh đạo kiểm tra, phê duyệt hoặc **trả lại kèm nhận xét**.
- **FR-E06** — **Bình luận kiểu Jira** trên dự thảo/nhiệm vụ: comment có luồng (threaded), **@mention**, hiển thị theo thời gian — phục vụ trao đổi/góp ý nhanh bên cạnh văn bản ý kiến chính thức.
- **FR-E07** — Cho phép **sửa ý kiến đã gửi khi còn trong thời hạn** phối hợp.
- **FR-E08** — **Quản lý phiên bản dự thảo**: lưu các phiên bản, lịch sử chỉnh sửa (ai/khi nào), xem lại phiên bản trước.
- **FR-E09** — **Ghi nhận kết quả ký/ban hành** (khâu ký thực hiện *ngoài* hệ thống ở GĐ1, không tích hợp ĐHTN): văn thư/người có quyền nhập **số văn bản, ngày ban hành** và **upload bản scan đã ký (PDF)** → đánh dấu bước ký/ban hành = **Hoàn thành**. Hệ thống chỉ *theo dõi trạng thái* và lưu kết quả, không thực hiện ký số. _(Tích hợp ĐHTN để tự động hoá khâu này = GĐ sau.)_

### F. Phối hợp (nội bộ + liên đơn vị cơ bản — GĐ1)

- **FR-F01** — Một bước có thể yêu cầu phối hợp tới **≥1 đơn vị phối hợp** (song song).
- **FR-F02** — Luồng trong đơn vị phối hợp: vụ trưởng nhận yêu cầu → **phân công chuyên viên** → chuyên viên soạn ý kiến/góp ý → vụ trưởng **phê duyệt** → trả về đơn vị chủ trì.
- **FR-F03** — Một bước có thể yêu cầu phối hợp tới **≥1 cá nhân phối hợp trực tiếp** (không qua đơn vị).
- **FR-F04** — Mỗi yêu cầu phối hợp có **nội dung đề nghị + thời hạn riêng**; theo dõi trạng thái từng nhánh phối hợp.
- **FR-F05** — **Gộp/đồng bộ**: cấu hình chờ tất cả ý kiến phối hợp về (hoặc theo thời hạn) trước khi chủ trì tổng hợp ở bước kế. **Chống treo vô hạn:** khi một/nhiều nhánh phối hợp **không phản hồi tới hết thời hạn**, hệ thống áp **chính sách đóng cấu hình được** — *(a)* tự đóng phối hợp và cho chủ trì tổng hợp với **input một phần** (đánh dấu nhánh nào "không có ý kiến/quá hạn"), hoặc *(b)* leo thang/nhắc và chờ chủ trì quyết định thủ công. Mặc định: nhắc khi sắp hết hạn (Nhóm H) rồi cho chủ trì chủ động đóng. Trạng thái từng nhánh và quyết định đóng đều ghi audit.
- **FR-F06** — Nhánh **"không có phối hợp"**: bỏ qua bước phối hợp (theo gateway trong quy trình mẫu).
- _GĐ2 (ngoài phạm vi GĐ1):_ phối hợp đa tầng/lồng nhau, SLA leo thang liên đơn vị, dashboard điều phối liên đơn vị chuyên sâu, định tuyến phức tạp.

### G. Minh bạch & Thống kê

- **FR-G01** — **Dashboard điều hành**: tổng quan nhiệm vụ theo đơn vị/người/trạng thái; đếm số *đang xử lý / hoàn thành / quá hạn / sắp đến hạn*.
- **FR-G02** — **"Ai đang làm gì"**: danh sách nhân sự + nhiệm vụ đang đảm nhận + trạng thái/tiến độ.
- **FR-G03** — **Theo dõi cá nhân**: mỗi người xem thống kê nhiệm vụ của mình (đang làm / đã xong / trễ hạn).
- **FR-G04** — **Tra cứu/tìm kiếm** nhiệm vụ & hồ sơ theo nhiều tiêu chí (loại, trạng thái, đơn vị, người, thời gian, từ khóa).
- **FR-G05** — **Báo cáo thống kê** nhiệm vụ theo **4 lát cắt**: theo đơn vị · theo người · theo loại nhiệm vụ · theo đúng/trễ hạn — lọc theo khoảng thời gian.
- **FR-G06** — Cập nhật **gần realtime** khi trạng thái nhiệm vụ thay đổi: dashboard/hộp thư việc phản ánh thay đổi trong **≤ 5 giây** (mục tiêu GĐ1; cơ chế polling/refresh đủ đáp ứng, không bắt buộc push).
- **FR-G07** — **Xuất báo cáo ra Excel và PDF** (Excel để xử lý tiếp, PDF để in/trình).

### H. Thông báo & Giao việc

- **FR-H01** — Thông báo **in-app** cho các sự kiện: việc mới được giao · được phê duyệt/trả lại/từ chối · ý kiến phối hợp đã về · có comment/@mention · sắp đến hạn · quá hạn.
- **FR-H02** — Thông báo qua **email**, cấu hình **bật/tắt theo loại sự kiện**.
- **FR-H03** — **Nhắc hạn** trước X (cấu hình) + cảnh báo quá hạn.
- **FR-H04** — **Trung tâm thông báo**: danh sách thông báo, đánh dấu đã đọc.

### I. Audit & Lưu trữ tuân thủ

- **FR-I01** — **Audit trail append-only**: ghi mọi hành động (ai · làm gì · đối tượng · thời điểm · trước/sau) — không cho sửa/xóa.
- **FR-I02** — **Vết phê duyệt đầy đủ** cho mỗi nhiệm vụ (lịch sử các cấp duyệt + nhận xét).
- **FR-I03** — **Đóng hồ sơ & lưu trữ điện tử (cơ bản, nội bộ)** khi kết thúc quy trình: gom văn bản + dữ liệu + metadata thành hồ sơ; tra cứu lại được.
- **FR-I04** — Thiết kế cấu trúc hồ sơ/metadata theo hướng **chuẩn-sẵn** để giai đoạn sau nâng cấp xuất/nộp đúng quy chế **06-QC/VPTW · QĐ 4063-QĐ/VPTW · NĐ 45/2020/NĐ-CP**. **Bộ metadata tối thiểu (chốt GĐ1):** mã hồ sơ · tiêu đề hồ sơ · loại quy trình/nhiệm vụ · đơn vị lập · người lập · thời gian mở/đóng hồ sơ · **danh mục văn bản trong hồ sơ** (số, tên, loại, ngày ký, người ký) · thời hạn bảo quản · trạng thái hồ sơ. _(Nghiên cứu chuẩn chi tiết & xuất/nộp đúng định dạng = GĐ sau.)_

---

## Yêu cầu phi chức năng (NFR)

- **NFR-01 — Kiến trúc & công nghệ.** Angular (FE) · Java Spring (BE) · MariaDB. **Engine quy trình nhúng** (khuyến nghị Flowable — hỗ trợ BPMN/DMN/CMMN, nhúng JAR vào Spring; xem addendum). Soạn thảo: **OnlyOffice Docs Community** on-prem.
- **NFR-02 — Quy mô.** Phục vụ **100–500 người dùng**. ✅ **Cập nhật (từ Architecture, 2026-06-24):** OnlyOffice Docs Community **9.4** (5/2026) đã **gỡ bỏ vĩnh viễn giới hạn 20 kết nối đồng thời** + kiến trúc single-process nhẹ hơn → **rủi ro 20-kết-nối không còn**. Dùng bản **9.4+**. (Lịch sử: bản cũ giới hạn 20 kết nối.)
- **NFR-03 — Triển khai.** **On-prem có kết nối** ra ngoài (phục vụ email…). Cài trên hạ tầng nội bộ cơ quan.
- **NFR-04 — Bảo mật.** RBAC theo vai trò/vị trí; audit append-only (FR-I01); mã hóa kênh truyền (HTTPS/TLS); quản lý phiên & chính sách mật khẩu; phân tách quyền xem dữ liệu theo đơn vị.
- **NFR-05 — Khả dụng & sao lưu.** Sao lưu định kỳ CSDL & file; quy trình phục hồi; mục tiêu uptime trong giờ làm việc _[ASSUMPTION] mức cụ thể chờ xác nhận._
- **NFR-06 — Hiệu năng.** Thao tác thông thường phản hồi nhanh (_[ASSUMPTION] mục tiêu < 2s_); cập nhật dashboard/hộp thư việc **≤ 5s** (FR-G06).
- **NFR-07 — Ngôn ngữ.** Giao diện **tiếng Việt**; kiến trúc sẵn sàng đa ngôn ngữ về sau (tùy chọn).
- **NFR-08 — Tương thích.** Trình duyệt phổ biến bản hiện hành (Chrome/Edge/Firefox).
- **NFR-09 — Khả năng cấu hình (cốt lõi).** Thay đổi quy trình/form/phân công **không cần build lại & deploy** — hiện thực hóa mục tiêu "ít Dev".
- **NFR-10 — Lưu trữ dữ liệu form động.** Dùng **JSON column (MariaDB)** cho payload form linh hoạt + cột quan hệ cho trường cần query/báo cáo (tránh EAV thuần — xem addendum).
- **NFR-11 — Sao lưu nhất quán & phục hồi.** Sao lưu phải **nhất quán giữa nhiều kho** (CSDL MariaDB + kho file văn bản/đính kèm) tại cùng một điểm thời gian; đặt mục tiêu **RPO/RTO** rõ ràng _[ASSUMPTION] mức cụ thể chờ xác nhận, gợi ý RPO ≤ 24h, RTO ≤ 4h trong giờ làm việc._ Có quy trình khôi phục được kiểm thử định kỳ.
- **NFR-12 — Hiệu năng báo cáo trên dữ liệu form động.** Báo cáo/thống kê (Nhóm G) **không truy vấn trực tiếp trên JSON column** ở đường nóng: các trường cần lọc/tổng hợp được **trích sang cột quan hệ/bảng phụ có chỉ mục** (đồng bộ khi ghi). Mục tiêu báo cáo 4 lát cắt trên khối dữ liệu của 100–500 người trả kết quả trong thời gian chấp nhận được _[ASSUMPTION] mục tiêu ≤ 10s, chốt sau pilot._
- **NFR-13 — Vòng đời bảng audit.** Bảng audit append-only (FR-I01) tăng trưởng liên tục → thiết kế **phân vùng/đánh chỉ mục theo thời gian** và chính sách **lưu trữ/đóng băng dữ liệu cũ** để truy vấn audit & báo cáo không suy giảm theo thời gian.

---

## Hành trình người dùng (User Journey)

### UJ-1 — Xử lý quy trình "Phối hợp nghiên cứu, tham mưu" (quy trình mẫu #1)

*Nhân vật: anh Hùng — chuyên viên chủ trì tại Vụ A; chị Lan — Vụ trưởng Vụ A; anh Nam — chuyên viên Vụ B (đơn vị phối hợp).*

1. **Tạo nhiệm vụ.** Nhận công văn yêu cầu tham gia ý kiến, **anh Hùng** mở hệ thống, chọn quy trình "Phối hợp nghiên cứu, tham mưu", điền form (tên nhiệm vụ, văn bản căn cứ tải lên, loại nhiệm vụ, đơn vị/cá nhân phối hợp, hạn xử lý) rồi **Trình phê duyệt**. *(FR-D01, B0x, A0x)*
2. **Phê duyệt nhiệm vụ.** **Chị Lan** nhận thông báo, xem nhiệm vụ, **Phê duyệt** → trạng thái chuyển "Đang xử lý", hồ sơ lưu trữ được khởi tạo. *(FR-E05, H01, I03)*
3. **Phối hợp.** Hệ thống gửi yêu cầu phối hợp tới Vụ B và một cá nhân phối hợp (song song). Vụ trưởng Vụ B phân công **anh Nam**; anh Nam soạn ý kiến và gửi; vụ trưởng Vụ B duyệt, trả về Vụ A. *(FR-F01–F05)*
4. **Tổng hợp & soạn dự thảo.** Khi đủ ý kiến phối hợp về, anh Hùng xem **bảng tổng hợp ý kiến**, soạn **dự thảo hoàn thiện** trong trình soạn thảo Word-like, đánh dấu tiếp thu/không tiếp thu. *(FR-E01–E04, E08)*
5. **Duyệt & trình ký.** Chị Lan kiểm tra, **phê duyệt** dự thảo (hoặc **trả lại kèm nhận xét** để sửa); anh Hùng chuyển trình ký. *(FR-E05, A04)*
6. **Ký ban hành** (ngoài hệ thống ở GĐ1) → anh Hùng cập nhật kết quả.
7. **Đóng hồ sơ & lưu trữ.** Anh Hùng đóng hồ sơ; hệ thống gom văn bản + dữ liệu + metadata, lưu trữ và cho tra cứu. *(FR-I03)*

> Suốt hành trình: mọi hành động được ghi **audit trail**; **chị Lan/lãnh đạo** xem **dashboard** biết nhiệm vụ đang ở bước nào, ai đang giữ, có sắp/đang trễ không; **anh Hùng** luôn thấy nhiệm vụ trong "Việc của tôi". *(FR-G01–G03, I01)*

---

## Kế hoạch phân giai đoạn

### GĐ1 — MVP (toàn bộ FR A–I ở trên)

Chạy trọn vẹn quy trình mẫu #1 end-to-end: cấu hình quy trình (kéo-thả) + form động + phân công theo vị trí/người + thực thi nhiệm vụ + soạn thảo & cho ý kiến + **phối hợp liên đơn vị cơ bản** + minh bạch/thống kê + thông báo + audit & lưu trữ cơ bản. Đăng nhập nội bộ; chưa tích hợp ĐHTN; không AI.

### GĐ2 — Mở rộng phối hợp & vận hành

Phối hợp đa tầng/lồng nhau · SLA leo thang liên đơn vị · dashboard điều phối liên đơn vị chuyên sâu · **migration chủ động** nhiệm vụ-đang-chạy sang phiên bản quy trình/form mới (đồng-tồn-tại phiên bản cơ bản đã có ở GĐ1 — FR-A06/FR-B09) · vòng lặp cho ý kiến nhiều vòng (nếu cần) · tích hợp **ĐHTN** · lưu trữ đúng chuẩn quy chế 06/NĐ45.

### GĐ3 — Hoàn thiện & quy mô

Tối ưu hiệu năng/quy mô · mở rộng bộ loại trường form · báo cáo/dashboard nâng cao · SSO/AD · cân nhắc OnlyOffice Enterprise/Collabora nếu vượt giới hạn kết nối.

---

## Câu hỏi mở & Giả định

- **[ĐÃ CHỐT]** Thước đo: đặt mục tiêu khởi điểm (xem mục *Thước đo thành công*). _Còn lại:_ **[DEFER]** baseline % trễ hạn hiện trạng — người phụ trách: PM/đơn vị nghiệp vụ; revisit sau pilot 1 tháng.
- **[ĐÃ CHỐT]** Khâu ký/ban hành GĐ1 làm ngoài hệ thống → ghi nhận kết quả qua **FR-E09** (số văn bản, ngày ban hành, scan PDF đã ký).
- **[ĐÃ GIẢI QUYẾT — từ Architecture]** ~~Giới hạn 20 kết nối OnlyOffice~~ → bản **OnlyOffice Docs Community 9.4** đã gỡ giới hạn này. Dùng 9.4+. Không còn là rủi ro.
- **[ĐÃ CHỐT]** Quy tắc task đang chạy khi đổi cơ cấu/người giữ vị trí → định nghĩa tại **FR-C05** (snapshot, không tự động cướp việc, có chuyển giao thủ công).
- **[ĐÃ CHỐT]** Bộ metadata tối thiểu cho "lưu trữ chuẩn-sẵn" → định nghĩa tại **FR-I04**.
- **[ĐÃ CHỐT — Gói A reviewer]** Đồng tồn tại phiên bản process+form (FR-A06/FR-B09) · fallback vị trí trống (FR-C08) · "Quá hạn" là cờ trực giao (FR-D03/D07) · cascade khi Hủy (FR-D10) · chính sách đóng join phối hợp (FR-F05) · ngưỡng realtime ≤5s (FR-G06) · NFR vận hành on-prem (NFR-11/12/13) · quy ước acceptance đầu mục FR.
- **[OPEN]** Yêu cầu **gia hạn** (FR-D09) nên có **hạn duyệt riêng** để không treo — chốt mức cụ thể ở bước thiết kế.
- **[DEFER — chiến lược]** **C1 — tái định khung phạm vi GĐ1.** Reviewer cảnh báo GĐ1 hiện gói trọn 9 nhóm A–I generic ≈ một sản phẩm hoàn chỉnh (ước lượng 18–30 tháng). Lựa chọn để ngỏ: giữ scope GĐ1 rộng, hay tái cấu trúc theo **lát cắt dọc "chạy trọn quy trình mẫu #1"** (đẩy phần generic-đầy-đủ — mọi flow type/mọi loại trường — sang GĐ1.5). **Người phụ trách: PM + Architect; revisit BẮT BUỘC trước khi tạo Epics/Stories.** Cảnh báo: nếu sau này chọn lát cắt dọc, Epics có thể phải tái cấu trúc.
