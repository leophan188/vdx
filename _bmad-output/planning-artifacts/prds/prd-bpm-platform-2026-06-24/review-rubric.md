# PRD Quality Review — Nền tảng BPM cho Tập đoàn (prd-bpm-platform-2026-06-24)

## Overall verdict

Đây là một PRD vững vàng và có chủ đích rõ ràng: luận điểm cốt lõi ("cấu hình thay vì code" + "minh bạch điều hành") được phát biểu rành mạch và xuyên suốt từ Tổng quan → Goals → Success Metrics → FR → NFR, với các quyết định gai góc nhất (versioning, gán theo vị trí, đổi cơ cấu giữa chừng, giới hạn 20 kết nối OnlyOffice) được nêu thẳng thay vì làm mượt về trung tính. Điểm yếu lớn nhất nằm ở **done-ness**: phần lớn FR mô tả năng lực rõ nhưng thiếu hệ quả kiểm thử được (acceptance), và một vài chỗ dùng tính từ ("phản hồi nhanh", "gần realtime") thay cho ngưỡng — đây là rủi ro cao cho khâu tạo story/epic downstream. Phụ thuộc OnlyOffice 20 kết nối ở quy mô 100–500 người là rủi ro kiến trúc đã được nhận diện nhưng chưa được chốt thành quyết định, treo lơ lửng.

## Decision-readiness — strong

PRD này hành động được. Các quyết định được phát biểu *là quyết định*, không giấu dưới dạng "cân nhắc": § *Câu hỏi mở & Giả định* dùng nhãn `[ĐÃ CHỐT]` / `[DEFER]` rất kỷ luật và trỏ thẳng về FR cụ thể (vd "Quy tắc task đang chạy... định nghĩa tại **FR-C05**"). Trade-off được nêu kèm cái bị đánh đổi: NFR-10 chọn JSON column "(tránh EAV thuần)", NFR-02 nêu thẳng "⚠️ **Rủi ro:** OnlyOffice Community giới hạn **20 kết nối**". `[DEFER]` ở Success Metrics (baseline % trễ hạn) có người phụ trách + thời điểm revisit, là Open Question thật chứ không tu từ.

Điểm trừ duy nhất đáng nói: rủi ro 20-kết-nối được lặp lại 3 lần (NFR-02, addendum, Open Questions) nhưng vẫn ở trạng thái treo — chưa có quyết định "đo trong pilot rồi mới quyết" được nâng thành `[NOTE FOR PM]` ở mức độ chặn build hay không. Với một MVP "green-light-to-build", đây là tension nên được dứt điểm hơn.

### Findings
- **medium** Rủi ro 20-kết-nối chưa thành quyết định (§ NFR-02 / Câu hỏi mở) — được nêu là rủi ro 3 lần nhưng vẫn treo `[DEFER]`, chưa rõ liệu nó có chặn MVP nếu số người soạn đồng thời thực tế vượt 20. *Fix:* thêm ngưỡng quyết định rõ ("nếu đỉnh đồng thời > N trong pilot → bắt buộc chuyển bản trả phí trước go-live") và ước lượng sơ bộ số người soạn .docx đồng thời thực tế.

## Substance over theater — strong

Nội dung được "kiếm" chứ không phải đồ trang trí. Personas (§ *Vai trò*) đều load-bearing: mỗi vai trò ánh xạ trực tiếp vào FR (Chuyên viên chủ trì → FR-D01/E01-E04; Vụ trưởng phối hợp → FR-F02; Admin nghiệp vụ → FR-A/B). Success Metrics có ngưỡng sản phẩm-cụ thể (≥80% thay đổi bằng cấu hình, ≤1 ngày đưa quy trình mới vào vận hành) chứ không phải DAU/MAU, và có **counter-metric** thật (thời gian cấu hình không được tăng tới mức admin "bỏ cuộc quay lại nhờ Dev") — đúng tinh thần chống NFR theater.

Không có innovation theater: § khảo sát trong addendum định vị sản phẩm trung thực ("bộ low-code dọc, nội bộ, xây trên engine nhúng — nằm ở giữa"), không tự xưng novelty giả. NFR phần lớn có bound cụ thể (NFR-02 quy mô, NFR-10 chiến lược lưu trữ). Chỉ NFR-05/06 rơi vào boilerplate (xem Done-ness).

## Strategic coherence — strong

PRD có luận điểm và đặt cược vào nó. Thesis rõ: tổ chức có "quá nhiều quy trình, biến động liên tục" + "mù thông tin điều hành" → đặt cược vào engine động cấu hình-được + minh bạch. Prioritization theo thesis chứ không theo "dễ làm trước": § *Bản đồ năng lực* đánh dấu A (Process Designer), B (Form động), G (Minh bạch) là **"trụ cột"** — đúng trọng tâm của value prop, không phải các năng lực dễ. Success Metrics validate đúng thesis (đo % cấu hình-không-code, đo độ phủ realtime ≥95%), không đo activity suông. MVP scope kind là **platform** và scope logic khớp: chọn 1 quy trình mẫu chạy end-to-end để chứng minh engine, thay vì làm nhiều quy trình nông.

Không phát hiện "backlog có heading". Việc neo toàn bộ FR vào một quy trình mẫu #1 (addendum) là quyết định chiến lược tốt: nó buộc MVP phải chứng minh tính tổng quát qua một ca thật.

## Done-ness clarity — thin

Đây là dimension yếu nhất và là rủi ro lớn nhất cho downstream. Phần lớn FR mô tả *năng lực* rõ ràng nhưng thiếu **hệ quả kiểm thử được** ở cấp từng FR. Nhiều FR là danh sách tính năng dạng bullet (vd FR-A02 liệt kê 6 dạng luồng, FR-B02 liệt kê loại trường) mà không nói điều kiện "done" cho mỗi cái. Không có § Acceptance Criteria riêng, và hệ quả kiểm thử không phải lúc nào cũng tự mang trong FR.

Một số FR mạnh và testable rõ: FR-C02 ("mỗi vị trí chỉ một người giữ → giao về đúng một người"), FR-C05 (quy tắc snapshot, không tự động cướp việc — nêu rõ hành vi quan sát được), FR-D03 (tập trạng thái fix cứng, liệt kê đủ), FR-E09 (nhập số văn bản + ngày + scan PDF → đánh dấu Hoàn thành). Đây là chuẩn mực mà các FR khác nên đạt tới.

Ngược lại, nhiều tính từ không-bound cần bị gắn cờ: NFR-06 "Thao tác thông thường **phản hồi nhanh**" (có `[ASSUMPTION] <2s` nhưng vẫn là giả định chưa chốt); FR-G06/NFR-06 "**gần realtime**" không định nghĩa độ trễ chấp nhận được (5s? 30s?); NFR-05 "uptime trong giờ làm việc `[ASSUMPTION] mức cụ thể chờ xác nhận`". FR-A01 "kéo-thả **trực quan**", FR-D02 "lọc & sắp xếp" — thiếu tiêu chí done. Khâu story creation sẽ phải tự bịa acceptance cho phần lớn FR.

### Findings
- **high** Thiếu Acceptance Criteria/hệ quả kiểm thử ở cấp FR (§ toàn bộ FR A–I) — đa số FR là mô tả năng lực, không có điều kiện "done" verify được; vd FR-A02 liệt kê 6 dạng luồng nhưng không nói cách kiểm chứng "gộp/đồng bộ chờ tất cả nhánh" hoạt động đúng; FR-D05 "tự chuyển việc... theo định nghĩa & điều kiện" không có hệ quả quan sát được. *Fix:* thêm ít nhất một hệ quả kiểm thử được cho mỗi FR (hoặc § Acceptance theo nhóm năng lực), ưu tiên các FR trụ cột A/B/D/G.
- **high** "Gần realtime" không có ngưỡng (§ FR-G06, NFR-06) — đây là một trong hai value prop cốt lõi và Success Metric "≥95% nhiệm vụ" phụ thuộc nó, nhưng "gần realtime" không có độ trễ mục tiêu. *Fix:* chốt độ trễ tối đa chấp nhận được cho cập nhật dashboard (vd ≤ X giây) và cơ chế (polling/push).
- **medium** NFR hiệu năng & khả dụng còn là tính từ + ASSUMPTION (§ NFR-05, NFR-06) — "phản hồi nhanh", "uptime trong giờ làm việc" chưa chốt bound. *Fix:* chốt mục tiêu p95 latency cho thao tác thường và mục tiêu uptime/RTO/RPO cho sao lưu-phục hồi.
- **low** Tính từ không-bound rải rác (§ FR-A01 "trực quan", FR-B01 "kéo-thả", FR-G06) — chấp nhận được ở mô tả UI nhưng nên có tiêu chí done tối thiểu cho Process/Form designer (vd "tạo được quy trình mẫu #1 hoàn chỉnh chỉ bằng kéo-thả, không sửa code"). *Fix:* dùng chính quy trình mẫu #1 làm acceptance cụ thể cho FR-A01/B01.

## Scope honesty — strong

Omissions được nêu tường minh. § *Ngoài phạm vi* loại bỏ AI "**hoàn toàn**, mọi giai đoạn" và đính chính rõ "bảng tổng hợp ý kiến là thủ công, không phải AI — vẫn giữ" — chính xác vì addendum (Bước 2-1) có nhắc "Trợ lý ảo AI", nên đính chính này đóng một silent-assumption thật. Tích hợp ĐHTN, SSO/AD, import HR/AD (FR-C01), versioning nâng cao đều được gán giai đoạn rõ. De-scoping được làm công khai: GĐ1 vs GĐ2/GĐ3 ranh giới rõ ở mỗi nhóm (vd FR-F nêu thẳng "GĐ2: phối hợp đa tầng/lồng nhau").

Open-items density hợp lý so với stakes: chỉ 1 `[DEFER]` thực sự còn treo (baseline % trễ hạn) + 1 rủi ro kỹ thuật (20-kết-nối), phần còn lại đã `[ĐÃ CHỐT]`. Với một MVP green-light, mật độ này thấp và lành mạnh — không phải PRD né quyết định.

Một lưu ý nhỏ: § *Ngoài phạm vi* và GĐ1 MVP không gọi tên một số non-goal vận hành có thể bị ngầm giả định (vd: không có app mobile? không có ký số trong GĐ1 — cái này có nêu gián tiếp qua FR-E09 nhưng không thành `[NON-GOAL]`).

### Findings
- **low** Vài non-goal vận hành chưa nêu tường minh (§ Ngoài phạm vi) — mobile app, ký số nội bộ, đa ngôn ngữ thực thi (NFR-07 nói "sẵn sàng" nhưng không rõ GĐ1 có/không) có thể bị ngầm giả định bởi stakeholder. *Fix:* bổ sung các `[NON-GOAL for MVP]` ngắn cho mobile/ký-số/đa-ngôn-ngữ để đóng giả định.

## Downstream usability — adequate

PRD này là chain-top (feeds UX → architecture → epics) nên dimension này quan trọng. ID FR contiguous, unique, có tiền tố nhóm rõ (FR-A01..A08, B01..B08, …, I01..I04) — dễ trích xuất. Cross-reference trong FR và UJ resolve tốt (UJ-1 trỏ "(FR-F01–F05)", "(FR-I03)"; FR-A03 trỏ "→ Nhóm B", "→ Nhóm H"). Các § FR đứng riêng vẫn hiểu được vì tham chiếu qua tên năng lực, không phải "see above".

Thiếu sót chính: **không có § Glossary**. Các danh từ miền quan trọng (ĐHTN, "đơn vị chủ trì" vs "đơn vị phối hợp", "vị trí/chức danh", "giải quyết muộn/late binding", "định nghĩa quy trình có phiên bản", "hồ sơ") được dùng nhất quán nhưng định nghĩa nằm rải rác (ĐHTN chỉ giải thích ở Bối cảnh; "giải quyết muộn" giải thích ở § Mô hình tổ chức). UX/architecture sẽ phải tự gom. UJ chỉ có 1 (UJ-1) và có nhân vật được đặt tên rõ (anh Hùng/chị Lan/anh Nam) — không floating, nhưng độ phủ thấp: các luồng quan trọng khác (admin cấu hình quy trình; quản lý xem dashboard; xin gia hạn FR-D09; chuyển giao việc đang chạy FR-C05) không có UJ.

### Findings
- **medium** Thiếu Glossary cho danh từ miền (§ toàn PRD) — ĐHTN, đơn vị chủ trì/phối hợp, vị trí/chức danh, late binding, process definition versioned, hồ sơ... dùng nhất quán nhưng định nghĩa phân tán; downstream phải tự gom. *Fix:* thêm § Glossary gom 8–12 thuật ngữ cốt lõi, trỏ về FR canonical.
- **medium** Độ phủ UJ thấp cho một số luồng load-bearing (§ User Journey) — chỉ có UJ thực thi nghiệp vụ; thiếu UJ cho persona **Admin nghiệp vụ** cấu hình quy trình/form (chính là value prop #1 "cấu hình không cần Dev") và persona **Quản lý** dùng dashboard (value prop #2). *Fix:* thêm UJ-2 (Admin cấu hình quy trình mẫu #1 từ đầu bằng kéo-thả) và UJ-3 (lãnh đạo dùng dashboard phát hiện việc sắp trễ) — hai UJ này chứng minh trực tiếp hai value prop cốt lõi.

## Shape fit — strong

Shape khớp với sản phẩm. Đây là **internal tool / multi-stakeholder B2B** với UX có ý nghĩa (nhiều vai trò người dùng thực, luồng phối hợp liên đơn vị) → UJ với nhân vật được đặt tên là load-bearing, và PRD đã làm đúng (UJ-1 có protagonist rõ). Không bị over-formalized (không có UJ density thừa cho thao tác đơn lẻ) cũng không under-formalized nghiêm trọng — chỉ thiếu vài UJ (đã nêu ở Downstream).

Đặc biệt phù hợp: PRD tách **addendum** chứa chiều sâu kỹ thuật (engine Flowable, EAV vs JSON, versioning, org-resolution layer) ra khỏi PRD chính — đúng shape cho chain-top feeding architecture, giữ PRD ở đúng altitude (what/why) còn để how cho tài liệu kiến trúc. Concern compliance (06-QC/VPTW, NĐ 45/2020) được xử lý đúng mức: không phải regulatory-update PRD nên không cần constraint-traceability đầy đủ, nhưng FR-I04 vẫn thiết kế metadata "chuẩn-sẵn" — cân bằng hợp lý giữa "tuân thủ về sau" và "không gold-plate GĐ1".

## Mechanical notes

- **ID continuity:** FR liên tục, không trùng/khuyết trong từng nhóm (A01–A08, B01–B08, C01–C07, D01–D09, E01–E09, F01–F06, G01–G07, H01–H04, I01–I04). NFR-01–10 liên tục. Tốt.
- **Assumptions Index roundtrip:** Có nhãn inline `[ASSUMPTION]` (NFR-05, NFR-06) nhưng **không được index** ở § *Câu hỏi mở & Giả định* — § đó chỉ liệt kê các `[ĐÃ CHỐT]`/`[DEFER]`, bỏ sót 2 `[ASSUMPTION]` của NFR. Roundtrip không khép kín. *Khuyến nghị:* đưa 2 ASSUMPTION của NFR vào § Câu hỏi mở để mọi giả định inline đều được index.
- **Glossary drift:** Không có Glossary (đã nêu ở Downstream). Một drift nhẹ về tên vai trò: "Lãnh đạo đơn vị chủ trì (Vụ trưởng)" trong bảng Actors vs "Vụ trưởng đơn vị chủ trì" trong addendum (Bước 1-2) — cùng một vai trò, cách gọi khác nhau. Mức thấp.
- **Cross-ref:** UJ-1 bước 1 ghi "(FR-D01, B0x, A0x)" — dùng placeholder "B0x/A0x" thay vì ID cụ thể; nên trỏ FR thật (FR-B06 gắn form, FR-A03 metadata bước) để cross-ref resolve được.
- **Required sections:** Có đủ Tổng quan, Vấn đề, Goals, Success Metrics (+counter-metric), Actors, FR, NFR, UJ, Phasing, Open Questions. Thiếu Glossary và (tùy chọn) § Acceptance — đã nêu trên.
