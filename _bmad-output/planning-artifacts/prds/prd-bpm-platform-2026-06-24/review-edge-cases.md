# Rà soát Edge-Case — Nền tảng BPM (GĐ1 MVP)

> Người rà soát: Adversarial Edge-Case Hunter
> Ngày: 2026-06-24
> Phương pháp: Đi hết mọi nhánh rẽ (branching path) và điều kiện biên (boundary). **Chỉ báo cáo các edge case CHƯA được PRD/addendum xử lý.** Những gì PRD đã giải quyết (snapshot tổ chức FR-C05, versioning cơ bản FR-A06, giới hạn 20 kết nối OnlyOffice…) không được liệt kê lại như "gap".

Mỗi finding gồm: **Tình huống chưa xử lý · Vì sao quan trọng · Mức độ · Hướng giải quyết**.

---

## 1. Phối hợp song song — Join/Sync (FR-F01, F04, F05)

### EC-01 — Đơn vị phối hợp KHÔNG BAO GIỜ trả lời, gateway gộp treo vô hạn
**Tình huống:** FR-F05 cho phép "chờ tất cả ý kiến về *hoặc theo thời hạn*". Nhưng PRD không định nghĩa **chuyện gì xảy ra khi thời hạn phối hợp trôi qua mà một nhánh chưa trả lời**. Bước gộp tiến tiếp với input thiếu? Hay treo chờ chủ trì can thiệp thủ công? Trạng thái nhánh phối hợp quá hạn đó là gì — và nó có chặn nhánh chính (parent task) chuyển sang "Quá hạn" lây không?
**Vì sao quan trọng:** Đây là kịch bản phổ biến nhất trong phối hợp liên đơn vị. Không có quy tắc → instance kẹt mãi ở wait state, tốn token DB (addendum mục 6), và chủ trì không biết được phép đi tiếp hay không.
**Mức độ:** **Critical**
**Hướng giải quyết:** Định nghĩa rõ chính sách join khi hết hạn: (a) "soft join" tự bỏ qua nhánh chưa về và đánh dấu ý kiến đó là "Không phản hồi/Quá hạn phối hợp" trong bảng tổng hợp; (b) cấu hình được theo bước: chờ-tất-cả vs chờ-tối-thiểu-N vs chờ-đến-hạn-rồi-đi-tiếp; (c) hành động thủ công "đóng phối hợp sớm" cho chủ trì kèm audit.

### EC-02 — Collaborator TỪ CHỐI tham gia phối hợp
**Tình huống:** FR-A04 có hành động "Từ chối", nhưng ngữ cảnh là phê duyệt/dự thảo. Không có định nghĩa cho việc **đơn vị/cá nhân phối hợp từ chối cho ý kiến** (vd: "không thuộc thẩm quyền chúng tôi", "đề nghị chuyển đơn vị khác"). Nhánh phối hợp đó kết thúc thế nào? Có tính là "đã hoàn thành phối hợp" hay là lỗi quy trình?
**Vì sao quan trọng:** Liên đơn vị thực tế hay xảy ra "đẩy việc". Nếu hệ thống coi từ chối = chưa trả lời, nhánh sẽ kẹt (EC-01); nếu coi = hoàn thành, chủ trì mất ý kiến mà không biết.
**Mức độ:** **High**
**Hướng giải quyết:** Thêm trạng thái nhánh phối hợp "Từ chối phối hợp (có lý do)" riêng biệt; hiển thị trong bảng tổng hợp; cho phép chủ trì re-route sang đơn vị khác hoặc bỏ qua có giải trình.

### EC-03 — Vụ trưởng đơn vị phối hợp duyệt nhưng KHÔNG có ý kiến nào (chuyên viên gửi rỗng)
**Tình huống:** Luồng FR-F02: vụ trưởng phân công → chuyên viên soạn → vụ trưởng duyệt → trả về. Không có validation rằng nội dung ý kiến phải non-empty, cũng không định nghĩa "ý kiến: không có gì để góp" như một kết quả hợp lệ và khác biệt với "chưa làm".
**Vì sao quan trọng:** "Nhất trí, không bổ sung" là kết quả phối hợp hợp lệ cực phổ biến — nhưng nếu hệ thống không phân biệt nó với nhánh bỏ trống thì bảng tổng hợp ý kiến (FR-E03) sai lệch.
**Mức độ:** **Medium**
**Hướng giải quyết:** Cho phép kết quả phối hợp dạng "Nhất trí/Không có ý kiến bổ sung" như một lựa chọn tường minh, tách khỏi nhánh trống.

### EC-04 — Phối hợp tới ≥1 đơn vị nhưng một đơn vị đích KHÔNG TỒN TẠI/đã giải thể giữa chừng
**Tình huống:** FR-F01 cho ≥1 đơn vị. Tổ chức là cây động (FR-C01) có thể CRUD/xóa nút. Nếu đơn vị phối hợp bị xóa/sáp nhập sau khi yêu cầu đã gửi nhưng trước khi vụ trưởng nhận, yêu cầu phối hợp đi về đâu?
**Vì sao quan trọng:** FR-C05 chỉ snapshot *người thực hiện task đang chạy*, không nói gì về *node đơn vị đích của một yêu cầu phối hợp* khi node đó biến mất.
**Mức độ:** **High**
**Hướng giải quyết:** Snapshot cả đơn vị đích tại thời điểm gửi yêu cầu; nếu đơn vị bị giải thể, route tới đơn vị kế thừa (cấu hình mapping) hoặc cảnh báo chủ trì xử lý thủ công.

---

## 2. Uỷ quyền / Chuyển tiếp / Thay thế (FR-C04)

### EC-05 — Vòng lặp uỷ quyền (A→B→A) và uỷ-quyền-của-uỷ-quyền
**Tình huống:** FR-C04 liệt kê uỷ quyền/chuyển tiếp/thay thế nhưng **không có guard chống chu trình**. A uỷ quyền cho B, B (đang là người thay thế) lại uỷ quyền ngược về A, hoặc B uỷ tiếp cho C tạo chuỗi delegate-of-delegate dài vô tận.
**Vì sao quan trọng:** Vòng lặp khiến việc "biến mất" (ping-pong) hoặc trách nhiệm bị pha loãng đến mức không ai xử lý; vi phạm mục tiêu minh bạch điều hành.
**Mức độ:** **High**
**Hướng giải quyết:** Phát hiện chu trình khi xác lập uỷ quyền; giới hạn độ sâu chuỗi delegation (vd tối đa 1–2 cấp, cấu hình được); audit hiển thị chuỗi uỷ quyền đầy đủ.

### EC-06 — Người được uỷ quyền/thay thế RỜI ĐI hoặc cũng đi vắng giữa task
**Tình huống:** Substitute đảm bảo "việc không tắc khi người giữ vị trí vắng". Nhưng nếu *substitute cũng vắng*, hoặc người được uỷ quyền nghỉ việc giữa chừng task đang ở tay họ? FR-C05 xử lý người-giữ-vị-trí rời đi, nhưng delegate/substitute không nhất thiết gắn vị trí.
**Vì sao quan trọng:** Chuỗi backup một-cấp không đủ; tạo điểm chết mới.
**Mức độ:** **Medium**
**Hướng giải quyết:** Cho phép chuỗi thay thế nhiều cấp hoặc fallback về vị trí gốc/cấp trên; hành động "chuyển giao việc đang chạy" (FR-C05) phải áp dụng được cả cho task đang ở tay delegate/substitute, không chỉ người giữ vị trí.

### EC-07 — Phạm vi & thời hạn của uỷ quyền không xác định
**Tình huống:** PRD không nói uỷ quyền là **theo từng task** hay **toàn bộ việc trong khoảng thời gian** (vd nghỉ phép 1 tuần). Không có ngày hết hiệu lực uỷ quyền. Khi uỷ quyền hết hạn giữa lúc delegate đang xử lý dở một task thì sao?
**Vì sao quan trọng:** Uỷ quyền không thời hạn = rủi ro quyền lực tồn dư; uỷ quyền hết hạn giữa task = tranh chấp ai sở hữu việc.
**Mức độ:** **Medium**
**Hướng giải quyết:** Mô hình hóa uỷ quyền có phạm vi (task đơn / theo loại / toàn bộ) + khoảng hiệu lực; task đã nhận giữ nguyên người xử lý đến khi xong dù uỷ quyền hết hạn (giống nguyên tắc snapshot FR-C05).

### EC-08 — Uỷ quyền/thay thế của người phê duyệt phá vỡ tách bạch trách nhiệm (segregation of duties)
**Tình huống:** Nếu chuyên viên chủ trì cũng là người được uỷ quyền/thay thế cho chính vụ trưởng phê duyệt, thì người soạn lại tự phê duyệt việc của mình. PRD không có ràng buộc SoD.
**Vì sao quan trọng:** Phá vỡ vết phê duyệt (FR-I02) và tính tuân thủ; một người vừa trình vừa duyệt.
**Mức độ:** **High**
**Hướng giải quyết:** Quy tắc cấm self-approval: nếu người-trình ≡ người-duyệt (kể cả qua uỷ quyền), chặn và leo lên cấp trên kế tiếp.

---

## 3. Đổi cơ cấu giữa chừng + Vị trí trống (FR-C05, C02)

### EC-09 — Việc MỚI route tới một vị trí ĐANG TRỐNG (chưa ai giữ)
**Tình huống:** FR-C05 xử lý task *đang chạy* qua snapshot. Nhưng FR-C03 gán theo vị trí "giải quyết muộn" — nếu **việc mới phát sinh** và vị trí đích hiện **không có người giữ** (FR-C02: mỗi vị trí ≤1 người, nhưng có thể 0 người)? Late binding resolve về null.
**Vì sao quan trọng:** Đây chính là lỗ hổng then chốt mà câu hỏi context nêu. Engine resolve assignee = rỗng → task không vào hộp thư ai cả → "việc biến mất" âm thầm, không ai biết cho tới khi quá hạn.
**Mức độ:** **Critical**
**Hướng giải quyết:** Định nghĩa fallback khi vị trí trống: route lên cấp trên trực tiếp / vào hàng đợi "chưa phân công" hiển thị cho quản trị / chặn khởi tạo nếu vị trí bắt buộc đang trống — kèm thông báo cảnh báo.

### EC-10 — Vị trí của NGƯỜI PHÊ DUYỆT trống khi dự thảo cần duyệt
**Tình huống:** Trường hợp riêng và nghiêm trọng hơn EC-09: bước phê duyệt (FR-E05, Bước 2-2) route tới "Vụ trưởng đơn vị chủ trì" nhưng ghế đó đang trống (vừa nghỉ hưu, chưa bổ nhiệm). Toàn bộ quy trình tắc ở khâu duyệt.
**Vì sao quan trọng:** Khâu duyệt là cổ chai bắt buộc; không có người duyệt = mọi nhiệm vụ của cả đơn vị đông cứng.
**Mức độ:** **Critical**
**Hướng giải quyết:** Quy tắc kế nhiệm phê duyệt: tự leo lên cấp trên kế tiếp khi ghế duyệt trống; hoặc cơ chế "quyền phê duyệt tạm" gán bởi quản trị; cảnh báo nổi bật trên dashboard điều hành.

### EC-11 — Một người giữ NHIỀU vị trí, cả hai cùng là mắt xích trong một quy trình
**Tình huống:** PRD cho phép "một người giữ nhiều vai trò". Nếu cùng một người vừa là chuyên viên chủ trì vừa (qua vị trí khác) là người duyệt của cùng một instance → tự duyệt việc mình (biến thể của EC-08, nhưng không qua uỷ quyền mà do cấu trúc tổ chức).
**Vì sao quan trọng:** Tuân thủ/SoD; phổ biến ở đơn vị nhỏ thiếu nhân sự.
**Mức độ:** **Medium**
**Hướng giải quyết:** Kiểm tra runtime: nếu cùng identity xuất hiện ở hai vai trò xung khắc trong một instance, cảnh báo/định tuyến thay thế.

---

## 4. Hạn xử lý + Gia hạn × Tự đánh dấu Quá hạn (FR-D07, D09, A08)

### EC-12 — Xin gia hạn SAU KHI việc đã bị đánh dấu "Quá hạn"
**Tình huống:** FR-D07 tự chuyển trạng thái "Quá hạn". FR-D09 cho xin gia hạn. PRD không nói **gia hạn xin sau khi đã Quá hạn** có hợp lệ không, và nếu duyệt thì trạng thái có **lùi từ "Quá hạn" về "Đang xử lý"** không. Tập trạng thái fix cứng (FR-D03) không định nghĩa chuyển ngược Quá hạn→Đang xử lý.
**Vì sao quan trọng:** Trực tiếp là interplay D07×D09 mà context yêu cầu soi. Thực tế người ta hay xin gia hạn *vì đã trễ*. Nếu trạng thái không quay lại được, số liệu thống kê trễ hạn (FR-G05, success metric ≤10%) bị méo vĩnh viễn dù đã được duyệt gia hạn hợp lệ.
**Mức độ:** **High**
**Hướng giải quyết:** Định nghĩa rõ: gia hạn được duyệt → đặt hạn mới, trạng thái về "Đang xử lý", nhưng **giữ cờ lịch sử "đã từng quá hạn"** cho audit/thống kê (phân biệt "trễ rồi gia hạn" với "đúng hạn").

### EC-13 — Bản thân yêu cầu DUYỆT GIA HẠN bị quá hạn / không ai duyệt
**Tình huống:** FR-D09: gia hạn cần "cấp có thẩm quyền duyệt". Trong lúc chờ duyệt gia hạn, hạn gốc trôi qua → việc thành Quá hạn dù đang chờ duyệt. Nếu người duyệt gia hạn không hành động (hoặc ghế trống — EC-10) thì sao? Có SLA cho chính việc duyệt gia hạn không?
**Vì sao quan trọng:** Vòng lặp chết: việc trễ vì đang chờ duyệt gia hạn, mà duyệt gia hạn không có hạn → đổ lỗi nhầm cho người thực hiện.
**Mức độ:** **High**
**Hướng giải quyết:** Trong thời gian chờ duyệt gia hạn, "đóng băng" đồng hồ quá hạn hoặc đánh dấu trạng thái phụ "Chờ duyệt gia hạn"; SLA + leo thang cho chính việc duyệt gia hạn.

### EC-14 — Gia hạn không giới hạn số lần / SLA bước vs SLA toàn quy trình mâu thuẫn
**Tình huống:** FR-D09 không giới hạn số lần xin gia hạn. FR-A08 cho SLA cả bước *và* toàn quy trình; nếu gia hạn bước đẩy quá SLA tổng thì xử lý ra sao? Hai đồng hồ chọi nhau.
**Vì sao quan trọng:** Lạm dụng gia hạn vô hạn vô hiệu hóa kỷ luật thời hạn; xung đột SLA bước/tổng không có trọng tài.
**Mức độ:** **Medium**
**Hướng giải quyết:** Cấu hình số lần gia hạn tối đa + ai duyệt theo cấp; định nghĩa quan hệ ưu tiên SLA bước vs tổng (vd gia hạn bước không được vượt SLA tổng trừ khi gia hạn cả tổng).

### EC-15 — Hạn rơi vào ngày nghỉ / không định nghĩa lịch làm việc
**Tình huống:** Hạn xử lý (FR-A08) — PRD không nói tính theo *ngày làm việc* hay *ngày lịch*, không có lịch nghỉ lễ. "≤ 1 ngày làm việc" xuất hiện ở metric nhưng không có khái niệm working-calendar trong FR.
**Vì sao quan trọng:** Việc giao chiều thứ Sáu hạn "1 ngày" thành quá hạn sáng thứ Bảy là sai nghiệp vụ; đập thẳng vào metric trễ hạn.
**Mức độ:** **Medium**
**Hướng giải quyết:** Định nghĩa lịch làm việc/nghỉ lễ cấu hình; SLA tính theo giờ làm việc.

### EC-16 — Đồng hồ quá hạn khi việc bị TRẢ LẠI hoặc QUAY VỀ BƯỚC TRƯỚC
**Tình huống:** Khi dự thảo bị trả lại (FR-E05) hay quay lại bước trước (FR-A02), hạn xử lý của bước nhận-lại được **reset, kế thừa, hay tiếp tục đồng hồ cũ**? Không định nghĩa.
**Vì sao quan trọng:** Ảnh hưởng trực tiếp ai bị tính trễ; nếu giữ đồng hồ cũ thì người sửa lại gần như chắc chắn quá hạn ngay.
**Mức độ:** **Medium**
**Hướng giải quyết:** Quy tắc reset SLA khi tái nhập bước (cấu hình: reset / cộng dồn / kế thừa).

---

## 5. State machine & Hủy (FR-D03, D06)

### EC-17 — "Hủy" task cha khi còn các nhánh phối hợp con đang chạy
**Tình huống:** Trạng thái "Hủy" tồn tại. Nếu chủ trì/lãnh đạo hủy nhiệm vụ chính giữa lúc 3 đơn vị phối hợp đang soạn ý kiến, **các sub-task phối hợp đó thành gì**? PRD không nói cascade. Chuyên viên Vụ B vẫn thấy việc trong "Việc của tôi" và tiếp tục soạn cho một nhiệm vụ đã chết.
**Vì sao quan trọng:** Trực tiếp là câu hỏi context. Sub-task mồ côi gây lãng phí công sức, sai lệch thống kê "ai đang làm gì", và token DB treo.
**Mức độ:** **High**
**Hướng giải quyết:** Định nghĩa cascade khi Hủy task cha: tự Hủy mọi sub-task phối hợp đang mở + thông báo các collaborator + ghi audit; hoặc chặn Hủy đến khi đóng nhánh con.

### EC-18 — Tập trạng thái fix cứng thiếu trạng thái trung gian quan trọng
**Tình huống:** Tập cố định: Chờ phê duyệt · Đang xử lý · Đã hoàn thành · Quá hạn · Hủy. **Thiếu:** "Bị trả lại/đang sửa lại", "Bị từ chối" (FR-A04 có hành động Từ chối nhưng không có trạng thái tương ứng), "Tạm dừng/chờ phụ thuộc bên ngoài (ĐHTN ký)", "Chờ duyệt gia hạn" (EC-13). Việc bị từ chối map về trạng thái nào?
**Vì sao quan trọng:** Hành động (FR-A04) đông hơn trạng thái (FR-D03) → mất thông tin. "Từ chối" và "Trả lại" gộp vào "Đang xử lý" khiến dashboard không phân biệt được việc lành mạnh với việc bị bật ngược.
**Mức độ:** **High**
**Hướng giải quyết:** Hoặc mở rộng tập trạng thái có kiểm soát, hoặc tách "trạng thái nhiệm vụ" (5 giá trị) khỏi "trạng thái bước/sub-state" để biểu diễn trả lại/từ chối/chờ-ký mà vẫn giữ 5 trạng thái tổng.

### EC-19 — "Quá hạn" là trạng thái đầy đủ hay cờ chồng lên trạng thái khác?
**Tình huống:** FR-D03 liệt kê "Quá hạn" ngang hàng với "Đang xử lý". Một việc *đang xử lý* mà *quá hạn* thì nó ở trạng thái nào — chỉ một? Khi đó nó không còn "Đang xử lý" trên dashboard? Khi hoàn thành một việc đã quá hạn, nó về "Đã hoàn thành" và **mất dấu là đã từng trễ** (liên quan EC-12).
**Vì sao quan trọng:** Quá hạn về bản chất là thuộc tính trực giao (orthogonal) với tiến trình, không phải trạng thái loại trừ lẫn nhau. Mô hình hóa sai làm hỏng cả báo cáo đúng/trễ hạn (FR-G05).
**Mức độ:** **High**
**Hướng giải quyết:** Tách "tình trạng trễ hạn" thành cờ/dimension riêng (đúng hạn / quá hạn / hoàn thành-trễ) song song với trạng thái vòng đời; giữ FR-D03 cho hiển thị nhưng nội bộ model 2 chiều.

### EC-20 — Hủy việc đã "Đã hoàn thành" / đã đóng hồ sơ
**Tình huống:** FR-D06 nói sửa/hủy theo trạng thái trước/sau khi trình duyệt, nhưng không định nghĩa biên cuối: có được Hủy việc đã hoàn thành & đã đóng hồ sơ lưu trữ (FR-I03) không? Audit append-only (FR-I01) cấm xóa — nhưng "Hủy" một hồ sơ đã lưu trữ tuân thủ là vấn đề nghiệp vụ/pháp lý.
**Vì sao quan trọng:** Xung đột giữa hành động Hủy và bất biến của lưu trữ tuân thủ.
**Mức độ:** **Medium**
**Hướng giải quyết:** Cấm Hủy sau khi đóng hồ sơ; nếu cần thu hồi → cơ chế "hồ sơ thu hồi/đính chính" riêng có vết audit, không xóa.

---

## 6. Vòng lặp & Quay về bước trước (FR-A02)

### EC-21 — Không có guard chống vòng lặp vô hạn / số vòng tối đa
**Tình huống:** FR-A02 hỗ trợ "quay lại bước trước · vòng lặp nhiều vòng". Không có giới hạn số vòng, không có ngắt mạch. Trả lại → sửa → trình → trả lại… vô hạn.
**Vì sao quan trọng:** Vòng lặp cấu hình bởi admin nghiệp vụ (không phải Dev) dễ tạo loop chết; cũng là loop ping-pong người-người. Engine cứ persist token mỗi vòng (addendum 6).
**Mức độ:** **High**
**Hướng giải quyết:** Đếm số vòng lặp mỗi cạnh, ngưỡng cấu hình kèm cảnh báo/leo thang khi vượt; tĩnh-kiểm phát hiện loop không có điều kiện thoát lúc publish.

### EC-22 — Versioning dữ liệu form khi quay về bước trước & THU THẬP LẠI ý kiến
**Tình huống:** Khi quay về bước phối hợp lần 2, **ý kiến phối hợp cũ** còn giữ không? Bảng tổng hợp (FR-E03) gom cả ý kiến vòng cũ lẫn vòng mới? Collaborator thấy bản nháp đã đổi của vòng trước hay bản gốc? FR-B07 nói dữ liệu mang xuyên suốt + ẩn lịch sử, nhưng không định nghĩa ngữ nghĩa khi *tái thực thi* một bước.
**Vì sao quan trọng:** Trộn lẫn ý kiến giữa các vòng làm sai bảng tổng hợp; mất dấu "ý kiến này cho phiên bản dự thảo nào".
**Mức độ:** **High**
**Hướng giải quyết:** Gắn ý kiến/dữ liệu thu thập với **vòng + phiên bản dự thảo (FR-E08)** cụ thể; mặc định không re-collect nếu không yêu cầu; bảng tổng hợp lọc theo vòng.

### EC-23 — Quay về bước trước nhưng người giữ bước đó đã đổi (giao thoa với FR-C05)
**Tình huống:** Khi quay lại Bước 1-1, snapshot FR-C05 nói giữ người cũ. Nhưng nếu người cũ đã rời và việc đã được "chuyển giao thủ công" trước đó, vòng lặp quay về thì việc về tay ai — người snapshot gốc (đã rời) hay người được chuyển giao?
**Vì sao quan trọng:** Tương tác giữa loop và snapshot không được định nghĩa → có thể route về người đã nghỉ.
**Mức độ:** **Medium**
**Hướng giải quyết:** Loop phải resolve theo bản chuyển giao mới nhất, không theo snapshot gốc cứng.

---

## 7. Quyền trường theo bước & Đồng-soạn (FR-B08, B04, E01, E08)

### EC-24 — Quyền trường xung đột giữa các collaborator chạy SONG SONG cùng bước
**Tình huống:** FR-B08 cho quyền đọc/ghi theo *bước*. Nhưng nhiều đơn vị phối hợp xử lý **song song trên cùng một bước phối hợp**. Hai collaborator cùng ghi vào một trường chia sẻ (vd bảng tổng hợp) — ai thắng? Quyền là theo bước, không theo *nhánh song song/người*.
**Vì sao quan trọng:** Last-write-wins âm thầm ghi đè ý kiến của đơn vị khác; mất dữ liệu không vết.
**Mức độ:** **High**
**Hướng giải quyết:** Phân vùng dữ liệu theo từng nhánh phối hợp (mỗi collaborator ghi vào "ô" của mình, chủ trì là người duy nhất ghi vùng tổng hợp); quyền trường resolve theo (bước × nhánh × vai trò).

### EC-25 — Đồng-soạn OnlyOffice vượt 20 kết nối ↔ versioning dự thảo (FR-E08)
**Tình huống:** PRD đã *biết* giới hạn 20 kết nối (NFR-02, DEFER). **Edge case CHƯA xử lý:** chuyện gì xảy ra với người dùng thứ 21 — bị từ chối mở? mở read-only? mất dữ liệu đang gõ? Và xung đột giữa **versioning của OnlyOffice (callback JWT)** với **versioning dự thảo FR-E08** ở tầng ứng dụng — hai cơ chế version chồng nhau, không định nghĩa nguồn sự thật.
**Vì sao quan trọng:** PRD đã khoanh *rủi ro công suất* nhưng chưa khoanh *hành vi degrade* và *mô hình version kép*. Người dùng thứ 21 mất việc đang gõ là hỏng dữ liệu.
**Mức độ:** **High**
**Hướng giải quyết:** Định nghĩa hành vi quá tải (queue/read-only graceful + thông báo); chốt một nguồn sự thật version (callback OnlyOffice là canonical, FR-E08 ánh xạ từ đó); chính sách hợp nhất khi co-edit kết thúc.

### EC-26 — FR-E07 "sửa ý kiến khi còn hạn" đua với việc chủ trì đã tổng hợp
**Tình huống:** Collaborator sửa ý kiến đã gửi (FR-E07, còn trong hạn) **sau khi** chủ trì đã kéo ý kiến đó vào bảng tổng hợp và bắt đầu soạn dự thảo. Bảng tổng hợp có tự cập nhật? Chủ trì có được cảnh báo "ý kiến anh đã tiếp thu vừa bị sửa"?
**Vì sao quan trọng:** Race condition giữa quyền sửa của collaborator và quyền tổng hợp của chủ trì → chủ trì làm việc trên dữ liệu cũ mà không biết.
**Mức độ:** **Medium**
**Hướng giải quyết:** Khóa ý kiến khi chủ trì đã "tiếp thu", hoặc đánh dấu ý kiến đã sửa + thông báo chủ trì re-review; ghi version ý kiến.

---

## 8. Thông báo tới người vắng / vị trí trống (FR-H01–H04)

### EC-27 — Thông báo gửi tới vị trí TRỐNG hoặc người đã rời/đang vắng
**Tình huống:** FR-H01 thông báo "việc mới được giao", "sắp đến hạn". Khi đích là vị trí trống (EC-09) hoặc người đang vắng có substitute, thông báo đi đâu? Tới ghế trống = vào hư không; tới người chính đang vắng mà không tới substitute = substitute không biết có việc.
**Vì sao quan trọng:** Thông báo lạc đích vô hiệu hóa cả cơ chế substitute lẫn minh bạch; việc âm thầm quá hạn.
**Mức độ:** **High**
**Hướng giải quyết:** Định tuyến thông báo bám theo cùng logic resolve assignee (substitute/delegate/cấp trên khi trống); thông báo nhành chính cho quản trị khi không resolve được người nhận.

### EC-28 — @mention người không có quyền xem nhiệm vụ / đã rời đơn vị
**Tình huống:** FR-E06 cho @mention kiểu Jira. Có thể @mention người ngoài đơn vị/không có quyền RBAC xem nhiệm vụ đó (NFR-04 phân tách quyền xem theo đơn vị)? Họ nhận thông báo nhưng click vào bị chặn? Hay @mention vô tình lộ dữ liệu hạn chế?
**Vì sao quan trọng:** Xung đột giữa tiện ích mention và phân tách quyền xem dữ liệu → rò rỉ hoặc thông báo cụt.
**Mức độ:** **Medium**
**Hướng giải quyết:** Giới hạn danh sách @mention theo phạm vi quyền xem nhiệm vụ; nếu cho mention cross-unit thì cấp quyền xem tối thiểu có kiểm soát + audit.

### EC-29 — Email out-of-band (NFR-03 on-prem có kết nối ngoài) thất bại/không tới
**Tình huống:** FR-H02 email theo loại sự kiện. Không định nghĩa retry/bounce-handling khi server email nội bộ chết hoặc địa chỉ sai. Nhắc hạn (FR-H03) phụ thuộc email mà email rớt → người dùng lỡ hạn.
**Vì sao quan trọng:** Mạng cơ quan, email dễ nghẽn; thông báo "best effort" không đảm bảo là rủi ro vận hành.
**Mức độ:** **Low**
**Hướng giải quyết:** In-app là kênh tin cậy chính (đã có FR-H04 trung tâm thông báo); email best-effort có retry + log thất bại; không để SLA phụ thuộc duy nhất vào email.

---

## 9. Versioning quy trình khi instance đang chạy (FR-A06, A07)

### EC-30 — Retire/republish định nghĩa khi nhiều instance cũ đang span qua thay đổi
**Tình huống:** FR-A06 nói instance đang chạy giữ bản cũ (migration là GĐ2). Nhưng FR-A07 cho **Retire**. Nếu admin Retire một định nghĩa đang có hàng chục instance dở dang (quy trình liên đơn vị chạy dài — addendum 2 cảnh báo), instance cũ có chạy tiếp được không, hay Retire chặn cả việc resolve bước kế? PRD không nói Retire ảnh hưởng instance đang chạy ra sao.
**Vì sao quan trọng:** Đây là câu hỏi context: instance span qua thay đổi. Nếu Retire làm chết bước kế của instance đang chạy → kẹt hàng loạt. Nếu không → admin tưởng đã gỡ quy trình nhưng nó vẫn sống.
**Mức độ:** **High**
**Hướng giải quyết:** Định nghĩa rõ Retire = không khởi tạo mới nhưng instance cũ chạy hết trên version của nó; dashboard liệt kê instance đang chạy theo version để admin biết trước khi retire; cảnh báo nếu retire version còn instance sống.

### EC-31 — Đổi định nghĩa FORM (FR-B) trong khi instance cũ chứa dữ liệu form cũ
**Tình huống:** Versioning ở FR-A06 nói về *process definition*. Nhưng **form (Nhóm B) có versioning riêng không?** Admin sửa form (xóa trường, đổi validation FR-B05, đổi quyền trường FR-B08) — instance đang chạy đọc/ghi JSON payload (NFR-10) theo schema cũ hay mới? Trường đã xóa nhưng dữ liệu cũ còn trong JSON thì hiển thị thế nào?
**Vì sao quan trọng:** Form là trụ cột GĐ1 thay đổi liên tục (mục tiêu ≥80% đổi bằng cấu hình). Không version form = sửa form làm vỡ instance đang chạy, hoặc validation mới từ chối dữ liệu cũ hợp lệ.
**Mức độ:** **Critical**
**Hướng giải quyết:** Form cũng phải versioned và bound theo instance như process definition; instance giữ schema form tại thời điểm khởi tạo; đổi form chỉ áp cho instance mới.

### EC-32 — Đổi quy tắc PHÂN CÔNG (FR-C03/C04) giữa chừng
**Tình huống:** Tương tự EC-31 nhưng cho quy tắc gán việc. Admin sửa quy tắc "bước X gán cho vị trí Y" thành "vị trí Z" trong khi instance chưa tới bước X. FR-C05 chỉ snapshot *task đã giao*, không nói về *quy tắc gán của bước chưa tới* trong instance đang chạy.
**Vì sao quan trọng:** Bước chưa resolve sẽ dùng quy tắc cũ hay mới? Không định nghĩa → hành vi bất ngờ, audit khó giải thích.
**Mức độ:** **Medium**
**Hướng giải quyết:** Quy tắc gán đi kèm version định nghĩa quy trình; instance dùng quy tắc của version nó khởi tạo.

---

## 10. Lưu trữ, audit & khởi tạo (FR-D01, I01–I03)

### EC-33 — Khởi tạo lập hồ sơ ở Bước 1-2 nhưng nhiệm vụ sau đó bị Hủy/Từ chối
**Tình huống:** UJ-1 bước 2 + addendum Bước 1-2: phê duyệt nhiệm vụ → "khởi tạo lập hồ sơ lưu trữ". Nếu sau đó nhiệm vụ bị Hủy (EC-17) hoặc dự thảo bị từ chối hẳn, **hồ sơ đã khởi tạo** thành gì? Hồ sơ rỗng/dở dang nằm trong kho lưu trữ?
**Vì sao quan trọng:** Hồ sơ mồ côi làm bẩn kho lưu trữ tuân thủ (FR-I04) và sai thống kê.
**Mức độ:** **Medium**
**Hướng giải quyết:** Phân biệt "khởi tạo hồ sơ nháp" vs "đóng hồ sơ chính thức"; Hủy nhiệm vụ → đánh dấu hồ sơ "Hủy/không ban hành" có vết audit, không để rỗng lẫn vào hồ sơ hợp lệ.

### EC-34 — Audit append-only hash-chained (addendum 4) khi xảy ra ghi đồng thời/khôi phục backup
**Tình huống:** Addendum nêu hash-chained audit. Edge case: ghi audit đồng thời từ nhiều giao dịch song song (phối hợp song song) cần thứ tự chain nhất quán; và khi **khôi phục từ backup (NFR-05)**, chain có bị gãy/đứt đoạn? PRD không định nghĩa tính toàn vẹn chain qua restore.
**Vì sao quan trọng:** Chain gãy = mất giá trị bằng chứng của audit (mục tiêu tuân thủ); nhưng đây là chi tiết addendum, không phải FR cốt lõi.
**Mức độ:** **Low**
**Hướng giải quyết:** Sequencer tập trung cho audit; kiểm tra toàn vẹn chain sau restore; tài liệu hóa ranh giới khôi phục.

### EC-35 — Đính kèm file (FR-B02/E01): trùng tên, dung lượng, loại file, xóa file đang được tham chiếu
**Tình huống:** Upload file đính kèm khắp nơi (văn bản căn cứ, ý kiến, scan PDF đã ký FR-E09). PRD không nói giới hạn dung lượng, loại file cho phép, virus-scan, hay điều gì xảy ra khi file bị xóa nhưng còn tham chiếu trong hồ sơ đã đóng.
**Vì sao quan trọng:** Mạng cơ quan air-gapped vẫn có rủi ro file độc; thiếu giới hạn = DoS lưu trữ; xóa file của hồ sơ đã lưu trữ phá tính toàn vẹn FR-I03.
**Mức độ:** **Medium**
**Hướng giải quyết:** Chính sách file (whitelist loại, max size, scan); file của hồ sơ đã đóng là bất biến (không xóa, chỉ thay bằng phiên bản mới có vết).

---

## Tổng kết mức độ

| Mức độ | Số lượng | Mã |
|---|---|---|
| **Critical** | 5 | EC-01, EC-09, EC-10, EC-12*, EC-31 |
| **High** | 14 | EC-02, EC-04, EC-05, EC-08, EC-13, EC-17, EC-18, EC-19, EC-21, EC-22, EC-24, EC-25, EC-27, EC-30 |
| **Medium** | 13 | EC-03, EC-06, EC-07, EC-11, EC-14, EC-15, EC-16, EC-20, EC-23, EC-26, EC-28, EC-32, EC-33, EC-35 |
| **Low** | 3 | EC-29, EC-34 |

> \*EC-12 được phân **High** trong chi tiết nhưng là interplay then chốt của hệ thống số liệu; nâng lên Critical-watch. Tổng cộng **35 findings**.

### 5 gap quan trọng nhất (ưu tiên xử lý GĐ1)
1. **EC-09/EC-10 — Vị trí đích/vị trí phê duyệt đang TRỐNG khi việc mới route tới:** việc biến mất hoặc cả đơn vị tắc ở khâu duyệt. Cần fallback resolve bắt buộc.
2. **EC-31 — Form không có versioning riêng:** sửa form (trụ cột thay đổi liên tục) làm vỡ instance đang chạy. Phải version + bind form theo instance.
3. **EC-01 — Join phối hợp song song treo khi một nhánh không trả lời/quá hạn:** thiếu chính sách đóng phối hợp khi hết hạn.
4. **EC-17 — Hủy task cha để lại sub-task phối hợp mồ côi:** chưa định nghĩa cascade.
5. **EC-12/EC-19 — "Quá hạn" mô hình hóa như trạng thái loại trừ thay vì cờ trực giao; gia hạn sau khi đã quá hạn không lùi được trạng thái:** méo vĩnh viễn metric trễ hạn (≤10%).
