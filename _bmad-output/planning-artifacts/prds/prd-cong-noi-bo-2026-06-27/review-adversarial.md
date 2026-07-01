---
title: "Review ĐỐI KHÁNG — PRD Cổng nội bộ ONEConnect (GĐ2)"
reviewer: "Adversarial / hoài nghi"
date: 2026-06-27
target: "prd.md"
verdict: "CHƯA SẴN SÀNG bóc epic. Nhiều lỗ hổng bảo mật/dữ liệu nghiêm trọng và FR thiếu tiêu chí xong."
---

# Review đối kháng — PRD Cổng nội bộ ONEConnect (GĐ2)

Cách đọc: mỗi finding có **Severity** (critical/high/medium/low), **Vị trí**, **Mô tả vấn đề**, **Đề xuất sửa**.
Review này CHỈ chỉ ra cái sai/thiếu/mơ hồ/rủi ro. Không khen.

---

## A. BẢO MẬT & QUYỀN RIÊNG TƯ

### F-01 — [CRITICAL] Google Sheet "link công khai" làm nguồn nhân sự = rò rỉ PII toàn công ty
**Vị trí:** FR-A01, mục 7 Phụ thuộc, mục 8 "Đã chốt", NFR-05.
**Vấn đề:** Sheet để chế độ "ai có link đều xem" chứa mã NV, họ tên, **email công ty, phòng ban, chức danh** của 100–500+ người. Bất kỳ ai có link (chuyển tiếp, lọt ra ngoài, bị index, lịch sử trình duyệt, log proxy) đều tải được toàn bộ danh bạ nhân sự. Đây là rò rỉ PII có hệ thống — vi phạm cả nguyên tắc tối thiểu hoá lẫn (tuỳ ngành) quy định bảo vệ dữ liệu cá nhân (Nghị định 13/2023). NFR-05 nói "lưu link an toàn qua secret" nhưng **bản thân nội dung Sheet vẫn public** — secret hoá link không giải quyết gì khi dữ liệu đích đã công khai. Mâu thuẫn nội tại: NFR-05 đòi bảo mật trong khi FR-A01 cố tình công khai nguồn.
**Đề xuất:** Bỏ "link công khai". Dùng Google Service Account + Sheets API (quyền đọc cấp cho 1 service account, sheet KHÔNG public), hoặc upload file CSV thủ công qua giao diện admin (không cần sheet online). Nếu buộc dùng link public, phải có cảnh báo rủi ro được ký nhận và giới hạn cột tối thiểu — nhưng nên loại bỏ hẳn.

### F-02 — [CRITICAL] Tự động khoá tài khoản dựa trên Sheet công khai ai cũng sửa được = vector tấn công/DoS nội bộ
**Vị trí:** FR-A04 ("tự động khoá tài khoản không còn trong Sheet"), FR-A03.
**Vấn đề:** Nguồn quyết định khoá tài khoản là một Sheet mà — ở chế độ "ai có link đều xem" — nếu cấu hình nhầm thành "ai có link đều **sửa**", hoặc bất kỳ ai trong nhóm chỉnh sửa, có thể **xoá một dòng → người đó bị khoá**. Kể cả chỉ-xem, ai kiểm soát quyền chỉnh sửa Sheet thực tế? PRD không định nghĩa. Một thao tác sai (xoá nhầm vùng, sort hỏng, filter còn áp khi export, sheet trống do lỗi mạng Google) có thể tạo danh sách "sẽ khoá" hàng loạt. Bước xem trước FR-A03 chỉ là phòng tuyến con người — admin mệt/bấm nhanh sẽ duyệt nhầm.
**Đề xuất:** (1) Không bao giờ tự động khoá; chuyển sang "đánh dấu nghi vấn ngừng hoạt động" cần xác nhận thủ công từng người hoặc 2 người duyệt. (2) Thêm **ngưỡng an toàn (circuit breaker)**: nếu số "sẽ khoá" > X% (vd 5%) hoặc Sheet trả về < Y dòng so với lần trước → CHẶN đồng bộ, báo lỗi. (3) Soft-lock có thể đảo ngược, không xoá dữ liệu. (4) Chốt rõ ai có quyền sửa Sheet và cơ chế bảo vệ Sheet.

### F-03 — [HIGH] Không có xác thực tính toàn vẹn nguồn Sheet (không HTTPS pinning, không checksum, không phát hiện sheet rỗng/lỗi)
**Vị trí:** FR-A02, FR-A05.
**Vấn đề:** Khi bấm "Đồng bộ", nếu Google trả 302→trang đăng nhập, CSV rỗng, hoặc nội dung HTML lỗi thay vì CSV, hệ thống diễn giải thế nào? FR-A05 chỉ kiểm "thiếu khoá / trùng email / sai định dạng" ở mức dòng, không kiểm tính hợp lệ toàn cục của payload. Một CSV rỗng = "tất cả vắng mặt" = đề xuất khoá toàn bộ (liên hệ F-02).
**Đề xuất:** Thêm FR: validate Content-Type là CSV, số dòng tối thiểu, so sánh delta với lần trước; nếu lệch bất thường → abort. Ghi nhận lỗi mạng/HTTP riêng biệt với lỗi dữ liệu.

### F-04 — [HIGH] Mật khẩu khởi tạo & vòng đời tài khoản mới chưa định nghĩa
**Vị trí:** FR-A04 ("mật khẩu khởi tạo theo chính sách").
**Vấn đề:** "Theo chính sách" là tham chiếu rỗng — chính sách nào? Mật khẩu khởi tạo gửi cho nhân viên qua kênh nào (email công khai trong sheet?)? Có bắt đổi lần đầu? Tài khoản tạo qua sync có active ngay (đăng nhập được) hay chờ kích hoạt? Nếu sync tạo 300 tài khoản active với mật khẩu đoán được → rủi ro chiếm tài khoản hàng loạt.
**Đề xuất:** Định nghĩa rõ: mật khẩu ngẫu nhiên mạnh, không gửi qua kênh không an toàn, bắt buộc đổi lần đầu / hoặc dùng luồng "đặt mật khẩu qua link kích hoạt". Bổ sung AC.

### F-05 — [HIGH] Media lưu trên đĩa server, phục vụ "công khai nội bộ" — thiếu kiểm soát truy cập file
**Vị trí:** FR-B01, NFR-03, mục 7.
**Vấn đề:** Ảnh/video bài viết phạm vi "một phòng ban" là dữ liệu giới hạn. Nếu file lưu trên đĩa và phục vụ qua URL tĩnh đoán được (vd /uploads/123.mp4), thì kiểm soát "chỉ phòng ban được xem" ở tầng feed bị vô hiệu — ai có URL đều tải. PRD không nói URL media có qua kiểm tra phân quyền không.
**Đề xuất:** FR rõ ràng: media phục vụ qua endpoint có kiểm tra RBAC + phạm vi bài viết, URL không đoán được (UUID/ký), không phục vụ tĩnh trực tiếp.

---

## B. UPLOAD MEDIA & NỘI DUNG ĐỘC HẠI

### F-06 — [HIGH] Không kiểm tra loại file thực sự / file độc hại; chỉ dựa "định dạng phổ biến"
**Vị trí:** FR-B01, NFR-03 ("chỉ chấp nhận định dạng ảnh/video phổ biến").
**Vấn đề:** "Định dạng phổ biến" mơ hồ và thường được kiểm bằng phần mở rộng / Content-Type do client gửi → giả mạo dễ. Cho phép tải lên file thực thi đổi đuôi, SVG chứa script (stored XSS khi render inline), polyglot, hoặc video chứa payload. Không nhắc magic-byte/MIME sniffing thật, không nhắc tách domain phục vụ media, không nhắc quét virus.
**Đề xuất:** FR: validate magic bytes, whitelist MIME thật, từ chối SVG hoặc sanitize, đặt Content-Disposition/`X-Content-Type-Options: nosniff`, phục vụ media từ domain/đường dẫn cô lập, cân nhắc quét AV. Tiêu chí xong cho upload an toàn.

### F-07 — [HIGH] Không có kiểm duyệt nội dung TRƯỚC khi hiển thị; chỉ "ẩn/xoá sau"
**Vị trí:** FR-B08 ("kiểm duyệt nhẹ: ẩn/xoá sau"), bối cảnh "admin đăng".
**Vấn đề:** Vì chỉ admin đăng bài nên bài viết rủi ro thấp, NHƯNG **comment do toàn bộ nhân viên tạo** (FR-B06) hiển thị ngay, không lọc. Nội dung xấu/quấy rối/bôi nhọ hiển thị công khai nội bộ tới khi admin thủ công xoá — không có hàng đợi, không filter từ khoá, không cơ chế report của nhân viên. "Kiểm duyệt nhẹ" chỉ phản ứng, không phòng ngừa.
**Đề xuất:** Thêm: nút "Báo cáo bình luận" cho nhân viên, hàng đợi kiểm duyệt, tuỳ chọn lọc từ khoá cơ bản, định nghĩa rõ "ẩn" khác "xoá", và audit ai ẩn/xoá (NFR-06 có nhắc audit nhưng FR-B08 nên ghi AC).

### F-08 — [MEDIUM] Giới hạn dung lượng có nhưng thiếu kiểm soát tổng dung lượng đĩa & dọn rác
**Vị trí:** NFR-03, NFR-04.
**Vấn đề:** Video ≤100MB/bài nhưng không giới hạn số bài, không hạn ngạch tổng, không chiến lược khi đĩa đầy (đặc biệt on-prem NFR-07). 500 nhân sự + video → đĩa đầy làm sập cả nền tảng GĐ1 dùng chung. Media của bài bị xoá có được dọn không? Không nói.
**Đề xuất:** Thêm NFR: hạn ngạch tổng/giám sát dung lượng đĩa, cảnh báo ngưỡng, dọn media khi xoá bài, hành vi khi đầy đĩa (từ chối upload, không sập).

### F-09 — [LOW] Không nhắc thumbnail/transcode video & tương thích trình duyệt
**Vị trí:** FR-B01, NFR-04.
**Vấn đề:** Cho upload "video phổ biến" nhưng .mov/.avi/codec lạ có thể không phát được inline trên trình duyệt; không transcode → trải nghiệm hỏng. NFR-04 "tải media tối ưu" không có tiêu chí.
**Đề xuất:** Hoặc whitelist hẹp (mp4/H.264), hoặc transcode; ghi rõ kỳ vọng.

---

## C. DỮ LIỆU & ĐỒNG BỘ

### F-10 — [HIGH] Xung đột khoá định danh: mã NV vs email, đổi mã/đổi email không xử lý
**Vị trí:** FR-A02 ("theo mã NV hoặc email"), mục 8 ("mã NV, fallback email").
**Vấn đề:** Dùng "mã NV HOẶC email" gây nhập nhằng: nếu một người đổi email (kết hôn, đổi tên miền) nhưng giữ mã NV → khớp mã; nhưng nếu mã NV thay đổi (tái cấu trúc) mà email giữ → hệ thống coi là người mới (thêm mới) + người cũ "không còn" (khoá) = nhân đôi + khoá nhầm. Hai khoá cùng lúc dễ mâu thuẫn: dòng có mã trùng người A nhưng email trùng người B → khớp ai? PRD không định nghĩa thứ tự ưu tiên/giải quyết xung đột.
**Đề xuất:** Chốt một khoá định danh ổn định duy nhất (mã NV), email là thuộc tính. Định nghĩa rõ tình huống đổi mã/đổi email và quy tắc giải quyết khi cả hai cùng xuất hiện mâu thuẫn. AC cho từng case.

### F-11 — [CRITICAL] Khoá người "đang có việc/quy trình dở" không có xử lý — mồ côi tác vụ GĐ1
**Vị trí:** FR-A04, toàn bộ Nhóm A; liên hệ nền tảng BPM GĐ1.
**Vấn đề:** GĐ1 là nền BPM với quy trình/phê duyệt. Nếu sync khoá một người đang là approver/assignee của task đang chạy, hoặc là chủ một quy trình dở → task mồ côi, quy trình treo, không ai duyệt được. PRD hoàn toàn không nhắc kiểm tra ràng buộc trước khi khoá. Đây là rủi ro làm tắc nghiệp vụ thật.
**Đề xuất:** Trước khi khoá, kiểm tra ràng buộc nghiệp vụ (task/quy trình đang gán, OT chưa chốt). Nếu có → cảnh báo, yêu cầu reassign trước, hoặc chặn khoá. Định nghĩa hành vi với dữ liệu thuộc về người bị khoá.

### F-12 — [MEDIUM] Trùng email/mã NV trong Sheet chỉ "liệt kê", không định nghĩa hệ quả với bản ghi hiện có
**Vị trí:** FR-A05.
**Vấn đề:** "Email trùng → liệt kê, không chặn dòng hợp lệ" — nhưng nếu 2 dòng trong Sheet cùng email, dòng nào "hợp lệ"? Cả hai cùng cố cập nhật 1 tài khoản? Trùng với email của một tài khoản KHÁC đang tồn tại (không phải khoá định danh) thì sao? Vi phạm ràng buộc unique DB → exception giữa chừng, đồng bộ một phần.
**Đề xuất:** Định nghĩa rõ: trùng trong file → bỏ cả cụm + báo; trùng với tài khoản khác → từ chối dòng; đảm bảo đồng bộ có tính giao dịch/đảo ngược được (xem F-13).

### F-13 — [HIGH] Đồng bộ không nói tính nguyên tử / khả năng rollback; lỗi giữa chừng để DB nửa vời
**Vị trí:** FR-A04, FR-A06.
**Vấn đề:** Áp 300 thay đổi; nếu lỗi ở dòng 150 (vi phạm ràng buộc, mất kết nối) → 149 đã ghi, phần còn lại chưa. Trạng thái không nhất quán, không có "undo lần đồng bộ". Nhật ký FR-A06 chỉ ghi lại, không khôi phục.
**Đề xuất:** Đồng bộ trong transaction hoặc cơ chế apply-all-or-nothing / hoặc lưu snapshot để hoàn tác một lần sync. Bổ sung NFR.

### F-14 — [MEDIUM] Không định nghĩa chuẩn hoá dữ liệu Sheet (khoảng trắng, hoa/thường email, encoding, dấu phẩy trong CSV)
**Vị trí:** FR-A02, FR-A05.
**Vấn đề:** Email " A@x.com " vs "a@x.com" coi là khác → tạo trùng/khoá nhầm. CSV có dấu phẩy/xuống dòng trong ô, encoding UTF-8 BOM, tên tiếng Việt có dấu — không nhắc xử lý. Phòng ban free-text trong sheet không khớp cơ cấu tổ chức GĐ1 → ánh xạ phòng ban thất bại.
**Đề xuất:** Quy tắc chuẩn hoá (trim, lowercase email, parser CSV chuẩn RFC). Định nghĩa: phòng ban trong Sheet ánh xạ thế nào tới cơ cấu tổ chức GĐ1 (theo mã/tên?); xử lý phòng ban không tồn tại.

---

## D. OT KHÔNG DUYỆT

### F-15 — [HIGH] OT không duyệt + không đối chiếu chấm công bắt buộc = gian lận giờ, số liệu không đáng tin
**Vị trí:** FR-C01–C05, mục 6, counter-metric "Sai lệch số giờ vs chấm công".
**Vấn đề:** Nhân viên tự khai OT, không ai duyệt, đi thẳng vào báo cáo tổng hợp (FR-C03/04) có thể dùng để tính lương/thưởng. Không có ràng buộc đối chiếu với chấm công (counter-metric nhắc "sai lệch vs chấm công" nhưng KHÔNG có FR nào thực hiện đối chiếu). Mở cửa khai khống: OT chồng giờ làm chính, OT ngày nghỉ giả, giờ vô lý (vd 0–24h).
**Đề xuất:** Tối thiểu: validate logic (giờ kết thúc > bắt đầu, không vượt trần/ngày, không chồng lấn đăng ký khác, không tương lai xa/quá khứ ngoài kỳ). Làm rõ OT này có dùng tính lương không — nếu có, "không duyệt" là rủi ro lớn, nên cân nhắc đưa duyệt vào phạm vi hoặc ghi rõ OT chỉ để thống kê không ràng buộc tài chính.

### F-16 — [MEDIUM] "Chốt kỳ" thiếu định nghĩa: ai chốt phạm vi nào, đăng ký trễ sau chốt, kỳ = tháng còn giả định
**Vị trí:** FR-C02, FR-C05, mục 8 ("kỳ = tháng [GIẢ ĐỊNH]").
**Vấn đề:** Sau khi chốt, nhân viên quên khai OT cuối tháng thì sao (không sửa được — mất quyền lợi)? Chốt toàn công ty hay từng phòng? Có mở lại kỳ không, ai có quyền, có audit? "Kỳ = tháng" vẫn là giả định chưa chốt nhưng FR-C02/C05 phụ thuộc nó.
**Đề xuất:** Chốt định nghĩa kỳ; định nghĩa luồng đăng ký trễ (admin nhập hộ / mở lại có kiểm soát + audit); làm rõ phạm vi chốt.

### F-17 — [MEDIUM] OT bị khoá tài khoản giữa kỳ → dữ liệu OT của họ xử lý ra sao
**Vị trí:** giao FR-A04 × Nhóm C.
**Vấn đề:** Người bị sync khoá giữa tháng: OT đã đăng ký của họ còn trong báo cáo không? Họ không đăng nhập để sửa được. Liên hệ F-11.
**Đề xuất:** Định nghĩa: dữ liệu OT giữ lại cho báo cáo kỳ; admin có thể thao tác hộ.

---

## E. IMPORT EXCEL → BÁO CÁO

### F-18 — [HIGH] CSV/Formula Injection khi xuất .xlsx và khi đọc file đầu vào
**Vị trí:** FR-D01–D04, FR-A05 (export OT .xlsx ở FR-C04 cũng dính).
**Vấn đề:** Dữ liệu người dùng (tên, ghi chú OT, nội dung sheet) bắt đầu bằng `=`, `+`, `-`, `@` khi ghi ra .xlsx sẽ thành công thức thực thi khi mở (CSV/formula injection → có thể chạy lệnh, lộ dữ liệu). PRD hoàn toàn không nhắc neutralize. Chiều ngược lại: đọc file Excel đầu vào có thể chứa công thức độc, external links, macro.
**Đề xuất:** FR/NFR bảo mật xuất: prefix `'` hoặc escape ô bắt đầu bằng ký tự công thức; khi đọc, chỉ lấy giá trị (không eval công thức), bỏ external links/macro. Áp cho TẤT CẢ điểm xuất Excel (Nhóm C và D).

### F-19 — [HIGH] File Excel lớn / nhiều dòng → cạn bộ nhớ (Apache POI), không giới hạn kích thước/dòng, zip-bomb
**Vị trí:** FR-D01, NFR-04, mục 7 (Apache POI).
**Vấn đề:** POI đọc DOM ngốn RAM; file vài trăm nghìn dòng hoặc xlsx "zip bomb" (file nhỏ giải nén khổng lồ) làm OOM, sập server dùng chung GĐ1. Không có giới hạn kích thước file, số dòng, timeout.
**Đề xuất:** Giới hạn kích thước upload, số dòng tối đa, dùng streaming reader (SXSSF/streaming SAX), timeout, chống zip-bomb (giới hạn tỉ lệ giải nén).

### F-20 — [HIGH] "Tính toán theo công thức của mẫu" chưa định nghĩa — không có tiêu chí đúng/sai, không kiểm thử được
**Vị trí:** FR-D03, FR-D06, Câu hỏi mở #2.
**Vấn đề:** Cốt lõi của Nhóm D là "tính đúng" nhưng cột đầu vào & công thức của mẫu duy nhất (chấm công/OT) **chưa chốt** (Câu hỏi mở #2). Vậy FR-D03/D06 không có tiêu chí xong, không thể bóc thành story dev/test được — mâu thuẫn với tuyên bố cuối PRD "không chặn bóc epic". Sai công thức = báo cáo sai có thể ảnh hưởng lương.
**Đề xuất:** Phải chốt cột vào + công thức + ví dụ input/output mẫu (golden file) TRƯỚC khi bóc story Nhóm D. Đánh dấu Nhóm D phụ thuộc giải quyết Câu hỏi mở #2. Yêu cầu test đối chiếu kết quả với golden file.

### F-21 — [MEDIUM] Sai lệch định nghĩa "kết quả đúng" giữa Excel import và đối chiếu chấm công
**Vị trí:** FR-D06 (mẫu = tổng hợp chấm công/OT) vs Nhóm C (OT nhập trong hệ thống).
**Vấn đề:** Có HAI nguồn giờ OT: (a) nhân viên tự đăng ký trong hệ thống (Nhóm C), (b) file Excel chấm công import (Nhóm D). Hai nguồn này quan hệ thế nào? Trùng lặp? Cái nào là chân lý? PRD không định nghĩa — dễ tính OT hai lần hoặc mâu thuẫn số liệu.
**Đề xuất:** Làm rõ mối quan hệ và quy tắc hợp nhất/đối chiếu giữa OT tự khai và bảng chấm công import.

---

## F. MÂU THUẪN NỘI TẠI & FR THIẾU TIÊU CHÍ XONG

### F-22 — [MEDIUM] Mâu thuẫn: "phân trang / cuộn vô hạn" — chọn cả hai là không chọn
**Vị trí:** FR-B03 ("phân trang / cuộn vô hạn"), NFR-04 ("bảng tin phân trang").
**Vấn đề:** FR-B03 để ngỏ cả hai cơ chế bằng dấu "/", còn NFR-04 khẳng định "phân trang". Mâu thuẫn trực tiếp. Cuộn vô hạn và phân trang có hệ quả khác nhau về API, vị trí bài ghim, deep-link, hiệu năng. Không thể test "xong".
**Đề xuất:** Chốt một cơ chế (đề xuất cuộn vô hạn theo cursor + bài ghim tách riêng cố định đầu feed), thống nhất FR-B03 với NFR-04.

### F-23 — [MEDIUM] Quá nhiều "[GIẢ ĐỊNH]" nền tảng được coi như đã chốt — rủi ro tái dùng GĐ1 không kiểm chứng
**Vị trí:** mục 8 "Giả định còn lại", NFR-01, FR-B06/B07 (kế thừa 3.13/3.14, notification center).
**Vấn đề:** "Tái dùng nguyên auth/RBAC/tổ chức/notification/audit GĐ1" là giả định chưa kiểm chứng nhưng toàn bộ NFR-01 và nhiều FR đặt cược vào nó. Notification center GĐ1 có hỗ trợ loại sự kiện mới (bài mới/comment)? RBAC GĐ1 có khái niệm "phạm vi phòng ban cho bài viết"? Cơ cấu tổ chức GĐ1 có map được phòng ban từ Sheet? Nếu một trong số này thiếu, ước lượng vỡ. "Kế thừa 3.13/3.14" tham chiếu tài liệu ngoài không có trong PRD — không tự kiểm chứng được.
**Đề xuất:** Trước khi bóc epic, xác minh thực tế từng điểm tái dùng (spike) thay vì giả định. Nội suy rõ phần nào của notification/RBAC GĐ1 cần MỞ RỘNG (không chỉ "tái dùng").

### F-24 — [MEDIUM] Phạm vi xem bài & quyền sửa/xoá comment thiếu cạnh biên
**Vị trí:** FR-B04, FR-B06, FR-B08.
**Vấn đề:** (a) Nhân viên đổi phòng ban (qua sync) → còn thấy/ mất quyền với bài/comment cũ ở phòng cũ? (b) FR-B06 "tác giả sửa/xoá comment của mình trong hạn" — "trong hạn" bao lâu, kế thừa 3.14 nhưng không nêu số. (c) Admin xoá bài có phạm vi phòng ban khác mình quản? RBAC admin có phân theo phòng không? Không rõ. (d) Like rồi bài bị ẩn → số liệu tương tác (metric 40%) tính sao.
**Đề xuất:** Định nghĩa AC cho đổi phòng, thời hạn sửa comment (con số cụ thể), phạm vi quyền admin, hành vi metric với bài ẩn.

### F-25 — [LOW] Chỉ số thành công không đo được / thiếu mốc & nguồn dữ liệu
**Vị trí:** mục 2.
**Vấn đề:** "Tương tác ≥40%/tuần", "≥95% qua sync", "≥80% OT qua hệ thống" — không có baseline, không định nghĩa tử/mẫu số, không nói lấy số ở đâu, mốc thời gian đánh giá. Counter-metric "sai lệch số giờ vs chấm công" lại không có FR đối chiếu (xem F-15). Không đo được = không nghiệm thu được.
**Đề xuất:** Định nghĩa công thức đo, nguồn dữ liệu, baseline, thời điểm đánh giá cho từng metric; hoặc hạ xuống "mục tiêu định hướng" và đánh dấu rõ.

### F-26 — [MEDIUM] FR thiếu Acceptance Criteria xuyên suốt — nhiều FR không có tiêu chí "xong"
**Vị trí:** toàn bộ mục 4.
**Vấn đề:** PRD liệt kê FR ở mức một dòng, gần như không FR nào có acceptance criteria/edge case rõ ràng (vd FR-A05 "sai định dạng" gồm những gì; FR-D02 "kiểu dữ liệu" cụ thể; FR-B07 "bài mình theo dõi" — cơ chế theo dõi định nghĩa ở đâu?). Tuyên bố "không chặn bóc epic" quá lạc quan: phần lớn FR chưa đủ để viết story testable. Câu hỏi mở #1, #2, #3 (ánh xạ cột, công thức, danh mục) thực ra chặn Nhóm A và Nhóm D.
**Đề xuất:** Bổ sung AC cho mỗi FR. Phân loại: FR nào thực sự không chặn, FR nào BỊ chặn bởi câu hỏi mở (A, D bị chặn) — không bóc story cho phần bị chặn cho tới khi chốt.

### F-27 — [LOW] "Trưởng phòng" để [GIẢ ĐỊNH] = như nhân viên, nhưng phạm vi xem bài theo phòng cần khái niệm quản lý phòng
**Vị trí:** mục 3, FR-B01 (đăng theo phòng), FR-C03 (tổng hợp theo phòng).
**Vấn đề:** Bài/OT có chiều "phòng ban" nhưng vai trò trưởng phòng bị làm phẳng thành nhân viên → ai xem tổng hợp OT cấp phòng nếu chỉ admin? Ai là chủ thể "phòng ban" cho bài? Mơ hồ vai trò.
**Đề xuất:** Xác nhận GĐ này thực sự không cần vai trò quản lý phòng cho bất kỳ FR nào; nếu FR-C03/B01 ngầm cần thì phải bổ sung.

---

## Tổng kết mức độ

- **CRITICAL (4):** F-01 (PII sheet công khai), F-02 (auto-khoá từ sheet ai sửa cũng được), F-11 (khoá người đang có quy trình dở), F-12→thực ra liệt kê lại; critical chuẩn: F-01, F-02, F-11. (F-20 cận-critical do chặn dev + ảnh hưởng lương.)
- **HIGH:** F-03, F-04, F-05, F-06, F-07, F-10, F-13, F-15, F-18, F-19, F-20.
- **MEDIUM:** F-08, F-12, F-14, F-16, F-17, F-21, F-22, F-23, F-24, F-26.
- **LOW:** F-09, F-25, F-27.

## Khuyến nghị tổng thể (đối kháng)
1. **Chặn bóc epic** cho Nhóm A và Nhóm D cho tới khi: bỏ/thay cơ chế sheet công khai (F-01), thêm circuit breaker + bỏ auto-khoá (F-02), chốt khoá định danh (F-10), chốt công thức báo cáo + golden file (F-20).
2. **Bổ sung một mục NFR Bảo mật chuyên biệt**: upload an toàn, formula injection, kiểm soát truy cập media, giới hạn kích thước/dòng/zip-bomb, vòng đời mật khẩu.
3. **Bổ sung AC + edge case cho mọi FR**; ánh xạ rõ FR nào bị chặn bởi Câu hỏi mở.
4. **Giải quyết tương tác chéo**: khoá tài khoản × task/OT đang dở (F-11/F-17); OT tự khai × chấm công import (F-21/F-15).
5. **Sửa mâu thuẫn** phân trang vs cuộn vô hạn (F-22) và NFR-05 "bảo mật link" vs FR-A01 "công khai" (F-01).
