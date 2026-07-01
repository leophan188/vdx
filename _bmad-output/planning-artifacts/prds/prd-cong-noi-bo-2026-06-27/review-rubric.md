# Thẩm định chất lượng PRD — Cổng nội bộ ONEConnect (GĐ2)

## Phán định tổng thể

PRD này vững một cách bất ngờ so với nhãn "bản nháp Fast path": bốn nhóm năng lực được neo vào một luận điểm rõ (hợp nhất truyền thông + tác nghiệp lên nền tảng GĐ1 sẵn có), các quyết định lớn đã chốt và tách bạch khỏi giả định/câu hỏi mở, phần Ngoài phạm vi làm việc thật. Điểm yếu chính nằm ở done-ness: nhiều FR mô tả năng lực nhưng thiếu tiêu chí "xong" có thể kiểm chứng (đặc biệt FR-A04 chính sách mật khẩu/khoá, FR-D03 "tính toán theo công thức", FR-B03 phân trang vs cuộn vô hạn), và vài giả định nền tảng then chốt (tái dùng auth/RBAC, lưu media trên đĩa) đang được đánh dấu [GIẢ ĐỊNH] dù chúng là trụ kiến trúc. Với bối cảnh công cụ nội bộ, đây là một PRD đủ tốt để bóc epic, miễn là các khoảng trống done-ness được xử ở bước thiết kế story.

## Decision-readiness — strong

PRD này quyết đoán đúng nghĩa. §8 "Đã chốt (qua Discovery)" liệt kê một loạt quyết định dạng đánh đổi thực: "tự động khoá người vắng mặt (có xem trước)", "mạng XH chỉ admin đăng", "OT chỉ ghi nhận + báo cáo (không duyệt)", "import Excel mẫu cố định". Mỗi quyết định này đều có cái bị bỏ đi, và phần lớn được phản chiếu sang §6 Ngoài phạm vi (ví dụ "Duyệt OT", "Cấu hình mẫu báo cáo Excel linh hoạt") — tức là đánh đổi được nêu cả hai chiều, không tô phẳng về trung tính.

§9 Câu hỏi mở là câu hỏi mở thật (ánh xạ cột Sheet thực tế, công thức mẫu báo cáo, danh mục chủ đề), không phải câu hỏi tu từ có sẵn đáp án. PRD cũng thành thật về ranh giới: "không chặn bóc epic" được nêu rõ, tức người đọc biết mức độ chín của từng khoảng trống.

Điểm trừ nhỏ: PRD không dùng callout `[NOTE FOR PM]` ở các điểm căng thật. Ví dụ "Google Sheet link CSV công khai" (FR-A01, §7) là một đánh đổi bảo mật đáng để PM thấy rõ — dữ liệu nhân sự (mã NV, email, phòng ban) phơi qua link "ai có link đều xem". PRD chốt quyết định này nhưng không gắn cảnh báo căng thẳng tại đó.

### Findings
- **medium** Đánh đổi bảo mật của "Sheet công khai" chưa được nâng thành điểm căng (§ FR-A01, §7 Phụ thuộc) — link CSV "ai có link đều xem" phơi PII nhân sự ra ngoài kiểm soát RBAC; quyết định đã chốt nhưng rủi ro chưa được PM/stakeholder nhìn thẳng. *Fix:* thêm `[NOTE FOR PM]` tại FR-A01 nêu rõ đánh đổi (tiện lợi vs phơi PII qua link công khai) và phương án giảm thiểu (link khó đoán, xoay link định kỳ, hoặc service account thay vì public).

## Substance over theater — strong

Nội dung được "kiếm về", không phải đồ trang trí. Không có persona theater: §3 chỉ có 3 vai (Admin / Nhân viên / Trưởng phòng) và mỗi vai đều buộc vào quyết định phạm vi thật — "Trưởng phòng [GIẢ ĐỊNH] — như nhân viên (GĐ này chưa mở quyền đăng bài)" trực tiếp dẫn tới một mục Ngoài phạm vi. Đúng số lượng cho một công cụ nội bộ.

Không có innovation theater — PRD không tự xưng tính mới, ngược lại nó tự định vị là kế thừa ("UI kế thừa màn /home (ochome) đã dựng", NFR-01 "Không dựng song song"). Đây là sự khiêm tốn đúng cho công cụ nội bộ brownfield.

Phần lớn NFR có ngưỡng cụ thể chứ không boilerplate: NFR-03 "ảnh ≤ 10MB, video ≤ 100MB", NFR-04 "toàn công ty (100–500+ nhân sự)". Đây là điểm hơn hẳn mặt bằng PRD. Vision (§1) cũng không hoán đổi được sang PRD khác — nó gắn cụ thể vào BPM GĐ1 và bốn năng lực.

Lưu ý nhẹ: NFR-04 "tải media tối ưu" và NFR-02 "Bảo vệ chặt các thao tác admin" là hai chỗ rơi vào tính từ thay vì ngưỡng — xem mục Done-ness.

## Strategic coherence — strong

PRD có luận điểm: §1 đặt cược rằng hợp nhất truyền thông nội bộ + công cụ tác nghiệp lên cùng tài khoản/phân quyền/tổ chức GĐ1 sẽ "tăng gắn kết" và "giảm thao tác thủ công". Bốn nhóm năng lực phục vụ đúng hai mục tiêu này: Nhóm B phục vụ gắn kết; Nhóm A/C/D phục vụ giảm thủ công. Không phải backlog rời rạc.

§2 Success Metrics validate luận điểm chứ không chỉ đo hoạt động, và — đáng khen — có counter-metric cho từng mục tiêu: "≥ 95% tài khoản tạo/cập nhật qua đồng bộ" đi kèm counter "Số lần sửa tay sau đồng bộ"; "tương tác ≥ 40%/tuần" đi kèm "Tỷ lệ bài 0 tương tác". Việc dùng tỷ lệ tương tác có counter "bài 0 tương tác" cho thấy PRD đo chất lượng gắn kết chứ không phải DAU rỗng — đúng tinh thần rubric.

MVP scope kind nhất quán: đây là hỗn hợp problem-solving (giảm thủ công) + experience (bảng tin), và logic phạm vi khớp — cắt mọi thứ không phục vụ hai mục tiêu (chat, tự đăng bài, duyệt OT, mẫu báo cáo linh hoạt).

Lưu ý: thứ tự ưu tiên giữa 4 nhóm chưa được nêu (nhóm nào làm trước). Với công cụ nội bộ điều này không nghiêm trọng nhưng sẽ hữu ích khi bóc epic.

### Findings
- **low** Thiếu thứ tự ưu tiên giữa 4 nhóm năng lực (§4) — PRD không nói nhóm nào là trục đầu tiên; bóc epic sẽ phải tự suy. *Fix:* thêm một dòng ưu tiên (ví dụ A → B → C → D, hoặc theo giá trị/rủi ro) trong §1 hoặc đầu §4.

## Done-ness clarity — thin

Đây là chiều yếu nhất và là rủi ro lớn nhất cho bước story. Nhiều FR mô tả năng lực rõ ràng nhưng thiếu hệ quả kiểm chứng được:

- **FR-A04** "tạo tài khoản mới (mật khẩu khởi tạo theo chính sách)" — "theo chính sách" nào? Không có tiêu chí. "tự động khoá tài khoản không còn trong Sheet" — khoá là gì (disable login? ẩn? giữ dữ liệu?) chưa định nghĩa hệ quả kiểm được.
- **FR-D03** "Tính toán theo công thức của mẫu → sinh báo cáo kết quả" — công thức để ở §9 câu hỏi mở (chấp nhận được cho mẫu đầu), nhưng FR-D03 như một năng lực chung lại không có bất kỳ tiêu chí done nào; một engineer không biết "tính toán xong" trông ra sao.
- **FR-B03** mâu thuẫn nội tại: "phân trang / cuộn vô hạn" — đây là hai cơ chế khác nhau, chọn cái nào? NFR-04 lại nói "bảng tin phân trang". Drift này khiến done không xác định.
- **FR-A05 / FR-D02** "không chặn các dòng hợp lệ" / "liệt kê dòng/cột sai, cho phép sửa & tải lại" — tốt, đây là hệ quả kiểm được. Nhóm này khá hơn.
- **NFR-02** "Bảo vệ chặt các thao tác admin" và **NFR-04** "tải media tối ưu" — tính từ, không có bounds. NFR-04 có một nửa tốt ("100–500+ nhân sự") nhưng không nêu ngưỡng thời gian phản hồi cho bảng tin/tổng hợp OT.
- **FR-B06** "tác giả sửa/xoá bình luận của mình (trong hạn — kế thừa cơ chế 3.14 GĐ1)" — "trong hạn" bao lâu chưa nêu (dựa vào tham chiếu GĐ1, có thể chấp nhận nếu cơ chế đó đã cố định).

PRD không có mục Acceptance riêng và phần lớn FR không tự mang theo tiêu chí done. Với công cụ nội bộ, không nhất thiết phải có AC đầy đủ, nhưng các FR có hệ quả vận hành (khoá tài khoản, tính toán báo cáo, chốt kỳ) cần ít nhất một điều kiện kiểm được mỗi cái.

### Findings
- **high** Chính sách mật khẩu & ngữ nghĩa "khoá tài khoản" chưa định nghĩa (§ FR-A04) — đây là thao tác phá huỷ (vô hiệu hoá truy cập của người vắng mặt); thiếu định nghĩa "khoá" nghĩa là gì và mật khẩu khởi tạo ra sao khiến story dễ làm sai/không nhất quán. *Fix:* định nghĩa "khoá" (disable đăng nhập, giữ dữ liệu/bài viết) và chính sách mật khẩu khởi tạo (hoặc trỏ tới chính sách GĐ1 cụ thể).
- **high** FR-D03 "tính toán theo công thức" không có tiêu chí done (§ FR-D03) — như một năng lực chung, không có cách nào để engineer biết "tính đúng" nghĩa là gì ngoài mẫu đầu tiên. *Fix:* nêu rõ FR-D03 là khung chạy công thức theo mẫu, và tiêu chí done bám theo từng mẫu (mẫu đầu = FR-D06, công thức chốt ở thiết kế); thêm điều kiện kiểm được như "output khớp file đối chiếu mẫu".
- **medium** Mâu thuẫn phân trang vs cuộn vô hạn (§ FR-B03 vs NFR-04) — FR-B03 nói "phân trang / cuộn vô hạn", NFR-04 nói "phân trang"; chọn một. *Fix:* chốt một cơ chế và đồng bộ hai chỗ.
- **medium** NFR hiệu năng/bảo mật dùng tính từ thay vì ngưỡng (§ NFR-02, NFR-04) — "bảo vệ chặt", "tải media tối ưu" không kiểm được. *Fix:* thêm bound cho bảng tin (ví dụ thời gian tải trang đầu < N giây với M bài) và phát biểu kiểm được cho RBAC admin (mọi endpoint admin require role X, trả 403 nếu thiếu).

## Scope honesty — strong

Đây là điểm sáng nhất. §6 Ngoài phạm vi làm việc thật và đầy đủ: chat, tự đăng bài, duyệt OT, mẫu báo cáo linh hoạt, comment đa cấp/mention, ghi ngược Sheet, chữ ký số/PDF — mỗi mục đều là một omission có thể bị giả định ngầm nếu không nêu. Việc de-scope được làm công khai, không lén.

Giả định được tag nhất quán bằng `[GIẢ ĐỊNH]` và gom lại ở §8 ("Giả định còn lại"): khoá định danh = mã NV (fallback email), media lưu đĩa server, comment 1 cấp, kỳ OT = tháng. Roundtrip khá tốt — các giả định inline (FR-B06, FR-C02, NFR-03, §3 Trưởng phòng) đều xuất hiện ở §8.

Mật độ open-items hợp lý so với stakes: 3 câu hỏi mở + ~6 giả định + 0 NOTE FOR PM, tất cả được tuyên bố là "không chặn bóc epic". Với công cụ nội bộ mức này hoàn toàn chấp nhận được.

Cảnh báo duy nhất: một số "giả định" thực ra là trụ kiến trúc, không phải biến phụ — xem Shape fit / Findings.

### Findings
- **medium** Vài giả định nền tảng quá then chốt để ở dạng [GIẢ ĐỊNH] chưa xác nhận (§8) — "tái dùng nguyên auth/RBAC/tổ chức/notification/audit GĐ1" và "media lưu đĩa server" là trụ kiến trúc; nếu một trong số sai thì toàn bộ phạm vi lay động, không phải chi tiết thiết kế. *Fix:* xác nhận sớm các giả định nền tảng này (tách khỏi nhóm giả định chi tiết như "kỳ OT = tháng") trước khi bóc epic, hoặc nâng thành quyết định đã chốt nếu GĐ1 thực sự cung cấp.

## Downstream usability — adequate

PRD này là chain-top (sẽ feed `bmad-create-epics-and-stories` theo §122), nên chiều này có trọng số.

ID tốt: FR-A01..A07, B01..B08, C01..C05, D01..D06 — liên tục, duy nhất, có tiền tố nhóm rõ. Dễ trích xuất theo nhóm. NFR-01..08 cũng vậy.

Mỗi nhóm FR đứng riêng khá tốt nhờ tiền tố và mô tả tự chứa. Tham chiếu chéo phần lớn dùng tên miền ("audit", "notification center", "màn /home") hoặc trỏ GĐ1 cụ thể (3.13, 3.14).

Khoảng trống chính:
- **Không có Glossary.** Một số danh từ miền dùng chưa hoàn toàn nhất quán: "khoá định danh" (A02) vs "khoá" theo nghĩa khoá tài khoản (A04) — cùng từ "khoá" hai nghĩa, dễ gây nhầm khi trích xuất. "kỳ" / "chốt kỳ" (C02/C05) dùng tốt nhưng chưa định nghĩa tập trung.
- **Không có User Journeys.** Với công cụ nội bộ điều này phần lớn ổn (xem Shape fit), nhưng vài luồng đa bước (đồng bộ Sheet với xem trước → xác nhận → khoá; import Excel → kiểm tra → sửa → chạy → tải) sẽ giúp story nếu có 2–3 UJ ngắn cho luồng phức tạp nhất.
- Tham chiếu "3.13"/"3.14"/"cơ chế GĐ1" trỏ ra ngoài tài liệu; người bóc epic cần truy được tài liệu GĐ1 tương ứng — không resolve được trong PRD này.

### Findings
- **medium** Thiếu Glossary và xung đột nghĩa từ "khoá" (§ FR-A02 "khoá định danh" vs FR-A04 "khoá tài khoản") — cùng một từ hai khái niệm khác nhau, rủi ro nhầm khi trích xuất story. *Fix:* thêm Glossary ngắn định nghĩa "khoá định danh" (identity key), "khoá tài khoản" (disable account), "kỳ", "chốt kỳ", "phạm vi xem"; hoặc đổi tên một trong hai để tránh trùng.
- **low** Tham chiếu GĐ1 ("3.13", "3.14", "audit trail GĐ1") không resolve trong PRD (§ FR-B06, FR-A06, §6) — người bóc epic phải tìm tài liệu GĐ1. *Fix:* thêm liên kết/đường dẫn tới tài liệu GĐ1 tương ứng, hoặc tóm tắt một dòng cơ chế được tái dùng.

## Shape fit — strong

PRD khớp hình dạng sản phẩm rất tốt. Đây là công cụ nội bộ brownfield, đa vai nhẹ (admin vs nhân viên), và PRD chọn đúng hình capability-spec: FR theo nhóm năng lực, SM mang tính vận hành/áp dụng ("≥ 95% qua đồng bộ", "≥ 80% OT qua hệ thống") thay vì user-facing rỗng.

Việc KHÔNG có User Journey ở đây là lựa chọn đúng, không phải thiếu sót — với công cụ nội bộ single-operator-ish, UJ density sẽ là overhead. Rubric nói thẳng điều này. PRD không bị over-formalize (không nhồi persona/UJ giả) cũng không under-formalize (vẫn có NFR, ngoài phạm vi, giả định).

Tính chất brownfield được xử đúng: NFR-01 và §7 nêu rõ phần kế thừa GĐ1, và phân biệt cái mới (4 nhóm năng lực) với cái tái dùng (auth/RBAC/audit/notification/design-system). Tham chiếu existing-code (màn /home ochome, cơ chế 3.13/3.14) là đặc trưng brownfield đúng — chỉ cần đảm bảo chúng chính xác (xem Downstream).

Không có finding nâng cấp cho chiều này — hình dạng đúng.

## Ghi chú cơ học (mechanical)

- **ID continuity:** FR-A/B/C/D và NFR liên tục, không trùng, không gap. Tốt.
- **Glossary drift:** từ "khoá" mang hai nghĩa (khoá định danh vs khoá tài khoản) — đã nêu ở Downstream. "Bảng tin/feed" dùng song song nhất quán (có chú thích "(feed)"). "đồng bộ" dùng nhất quán.
- **Assumptions roundtrip:** các `[GIẢ ĐỊNH]` inline (§3 Trưởng phòng, FR-B06 comment 1 cấp, FR-C02 kỳ=tháng, NFR-03 lưu media) đều có mặt ở §8. Roundtrip đạt. Một mục §8 ("media lưu như cách GĐ1") hơi trùng với NFR-03 — gộp được.
- **Cross-refs:** tham chiếu nội bộ ("xem Ngoài phạm vi", "xem Câu hỏi mở") resolve được trong tài liệu. Tham chiếu ngoài ("3.13", "3.14", "GĐ1") không resolve trong PRD này.
- **Required sections:** đủ cho stakes và loại sản phẩm — Tổng quan/Vision, SM + counter-metric, Người dùng, FR, NFR, Ngoài phạm vi, Phụ thuộc, Quyết định/Giả định, Câu hỏi mở. Thiếu Glossary (medium) và Acceptance (một phần bù bằng hệ quả FR, nhưng done-ness vẫn mỏng).

## Tổng kết findings theo severity

- **critical:** 0
- **high:** 2 (FR-A04 chính sách mật khẩu/ngữ nghĩa khoá; FR-D03 thiếu tiêu chí done)
- **medium:** 5 (đánh đổi Sheet công khai; phân trang vs cuộn; NFR tính từ; giả định nền tảng then chốt; thiếu Glossary/xung đột "khoá")
- **low:** 2 (thiếu thứ tự ưu tiên nhóm; tham chiếu GĐ1 không resolve)
