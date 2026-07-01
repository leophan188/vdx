# Addendum — Chiều sâu kỹ thuật & bối cảnh (không thuộc PRD chính)

> Nội dung dành cho tài liệu kiến trúc / giải pháp downstream. Thu thập trong quá trình Discovery.

## Quy trình mẫu #1 — "Phối hợp nghiên cứu, tham mưu" (anchor cho PRD)

Nguồn: sơ đồ BPMN người dùng cung cấp. Dùng làm ví dụ xuyên suốt minh họa các năng lực.

**Cấu trúc cột của sơ đồ (mô hình hóa "định nghĩa bước" cần hỗ trợ):** Thực hiện (vai trò) · Sự kiện kích hoạt · Dữ liệu đầu vào · Trình tự thực hiện · Mô tả bước · Dữ liệu đầu ra · Mẫu file văn bản đã tiếp nhận · Số bước · Thời gian thực hiện (hạn).

**Luồng các bước:**
- **Bắt đầu** — kích hoạt bởi công văn yêu cầu tham gia ý kiến kèm văn bản dự thảo (file vật lý / từ hệ thống ĐHTN).
- **Bước 1-1** — *Chuyên viên chủ trì* tạo nhiệm vụ tham gia ý kiến, góp ý. Thông tin nhiệm vụ: tên nhiệm vụ, văn bản căn cứ (upload/chiếu từ ĐHTN), loại nhiệm vụ, lãnh đạo đơn vị chủ trì, chuyên viên chủ trì, đơn vị phối hợp (+ nội dung đề nghị, thời hạn), cá nhân phối hợp, hạn xử lý, trạng thái, hành động (Ghi lại / Sửa / Hủy / Trình phê duyệt). Quy tắc phê duyệt/hủy: trước khi gửi lãnh đạo, chủ trì được phép sửa/hủy; sau khi gửi cần lãnh đạo phê duyệt việc hủy.
- **Bước 1-2** — *Vụ trưởng đơn vị chủ trì* phê duyệt nhiệm vụ, khởi tạo lập hồ sơ lưu trữ. Hành động: Phê duyệt / Sửa→Ghi lại→Phê duyệt. Sau phê duyệt: trạng thái → "Đang xử lý"; có tiện ích tóm tắt dữ liệu đầu vào.
- **Gateway** — *Có phối hợp?* → Không có phối hợp (bỏ qua) / Đơn vị phối hợp / Cá nhân phối hợp.
- **Bước trung gian 1** — *Đơn vị phối hợp*: (1) Vụ trưởng đơn vị phối hợp phân công chuyên viên; (2) Chuyên viên tạo dự thảo ý kiến/góp ý; (3) Vụ trưởng phê duyệt, chuyển về đơn vị chủ trì. (Mỗi đơn vị phối hợp có thể chỉ định ≥1 cá nhân.)
- **Bước trung gian 2** — *Cá nhân phối hợp* tham gia ý kiến, góp ý gửi về đơn vị chủ trì (khi còn hiệu lực thì được Sửa nội dung đã gửi).
- **Bước 2-1** — *Chuyên viên chủ trì* xây dựng dự thảo tổng hợp: (1) tiện ích tóm tắt dự thảo; (2) bảng tổng hợp ý kiến góp ý của đơn vị/cá nhân phối hợp. Có *Trợ lý ảo AI hỗ trợ soạn thảo* (triển khai sau). Output: văn bản dự thảo hoàn thiện.
- **Bước 2-2** — *Lãnh đạo đơn vị chủ trì* kiểm tra, phê duyệt dự thảo, chuyển trình ký.
- **Bước 2-3** — *Chuyên viên chủ trì* chuyển trình ký.
- **Bước 3** — *Lãnh đạo Ban + Văn thư Ban* ký duyệt, ban hành (thao tác bên **ĐHTN**).
- **Bước 4** — *Chuyên viên chủ trì* đóng hồ sơ & lưu trữ điện tử theo quy chế 06-QC/VPTW (26/4/2025), QĐ 4063-QĐ/VPTW, NĐ 45/2020/NĐ-CP. Kết thúc.

**Quan sát rút ra cho thiết kế năng lực:**
- "Định nghĩa bước" là một bản ghi giàu metadata (vai trò thực hiện, trigger, input, mô tả, output, mẫu file, hạn) → khớp mô hình form động + cấu hình bước.
- Phân công kép: theo **đơn vị phối hợp** (rồi vụ trưởng phân công chuyên viên) và theo **cá nhân phối hợp** trực tiếp → cần org-model + gán theo vị trí lẫn theo người.
- Nhiều **trạng thái nhiệm vụ** + quy tắc hành động theo trạng thái/vai trò (sửa/hủy/trình phê duyệt) → state machine cấu hình được.
- **Bảng tổng hợp ý kiến** và **tóm tắt dự thảo (AI)** là tiện ích gắn vào bước soạn thảo.
- Tích hợp **ĐHTN** ở khâu ký/ban hành và nguồn văn bản căn cứ → ranh giới hệ thống cần làm rõ.
- **Lưu trữ điện tử tuân thủ** là bước bắt buộc cuối quy trình (concern compliance).


## Khảo sát nền tảng tương đương (nghiên cứu nền — 2026-06-24)

**Phân khúc thị trường:** (a) Engine BPMN mã nguồn mở nhúng được (Camunda, Flowable, Bonita, jBPM, Activiti) — ship runtime + modeler, ta tự xây app quanh nó; (b) Bộ low-code iBPMS (Appian, Pega, Nintex/K2, ServiceNow, Power Platform) — đóng gói engine + form designer + UI + governance. Sản phẩm này thực chất là **bộ low-code dọc, nội bộ, xây trên engine nhúng** — nằm ở giữa.

**Lựa chọn engine cho stack Angular + Spring + MariaDB:**
- **Flowable** — dễ nhúng nhất vào ứng dụng Java/Spring dưới dạng JAR; hỗ trợ BPMN + DMN (quyết định) + CMMN (case management) on-premise. Phù hợp nhất.
- **Camunda 8 (Zeebe)** — cloud-native, scale ngang, nhưng là engine *từ xa* (gRPC/job-workers), không nhúng; cam kết vận hành lớn hơn.
- **Camunda 7** — nhúng được nhưng đang EOL-track.
- **jBPM + Drools** — chỉ thắng khi cần rules engine nặng.
- Khuyến nghị: **nhúng Flowable (hoặc Camunda 7) như thư viện trong Spring Boot**, expose REST API cho Angular. Xây engine custom hiếm khi đáng (phải tự làm lại versioning, persistence, token semantics, timers).

**"Cấu hình bởi admin nghiệp vụ" (low-code) thường gồm:** process designer (palette + flow), form designer theo metadata, trình soạn quy tắc gán việc (người vs vị trí), console deploy/versioning — tất cả ghi *config* (không phải code) để engine diễn giải lúc runtime.

## Khảo sát trình soạn thảo Word miễn phí (on-prem) (nghiên cứu nền — 2026-06-24)

Yêu cầu: soạn công văn/văn bản với định dạng chuẩn Word, import .docx, quản lý phiên bản, triển khai HOÀN TOÀN nội bộ (air-gapped). Chỉ có 2 lựa chọn đạt độ trung thực Word thật sự; các editor HTML nhẹ chỉ chuyển đổi HTML lossy.

| Lựa chọn | Giấy phép / miễn phí on-prem? | Độ trung thực .docx | Tích hợp Angular+Spring | Collab/comment | Lưu ý |
|---|---|---|---|---|---|
| **OnlyOffice Docs Community** ⭐ | AGPL v3, free self-host | **Xuất sắc** (engine OOXML, gần Word nhất) | **Dễ nhất**: có component Angular chính thức `@onlyoffice/document-editor-angular`; Spring phục vụ file + callback ký JWT để lưu/versioning | Có (co-edit, comment, track changes) | Giới hạn **20 kết nối đồng thời**; AGPL §13: sửa mã nguồn phải công khai; giữ logo (white-label = trả phí). Nhúng qua API thường KHÔNG buộc mở mã app — nên rà soát pháp lý |
| **Collabora Online (CODE)** | MPLv2 (mã thật sự FOSS) | Rất tốt (engine LibreOffice; .docx đôi khi lệch layout nhẹ) | Trung bình: tích hợp qua **WOPI** (Spring làm WOPI host); Angular nhúng iframe | Có | CODE là bản dev rolling, "không cho production", giới hạn ~10 docs/20 kết nối. Production cần bản trả phí |
| CKEditor5 / TinyMCE / TipTap | docx import/export = **TRẢ PHÍ**, thường cloud-metered | Yếu | Dễ nhúng editor | Comment/track-change trả phí | Cloud-metered → KHÔNG hợp mạng kín gov |
| SunEditor (MIT) / Quill (BSD) | Miễn phí hoàn toàn | **Không** (HTML thuần) | Tầm thường | Cơ bản | Cần mammoth.js (import) + docx4j (xuất) — lossy |
| mammoth.js (import, BSD/MIT) | Free | .docx→HTML lossy | Client/Node | — | Companion để preview import |
| docx4j (server, Apache 2.0) | Free | Tạo/sửa .docx phía Java | Lib Spring native | — | Lập trình, không phải editor UI; tốt cho sinh template chuẩn |

**KHUYẾN NGHỊ:** Triển khai **OnlyOffice Docs Community** on-prem, nhúng qua component Angular chính thức, lưu/versioning qua callback ký JWT vào MariaDB; tùy chọn dùng **docx4j** phía server để sinh template văn bản chuẩn. Runner-up: Collabora (nếu cần .odt hoặc ưu tiên giấy phép MPL, nhưng tốn công WOPI + bản production trả phí). Tránh CKEditor/TinyMCE/TipTap cho yêu cầu .docx trong mạng kín.

**Cân nhắc cho PRD/kiến trúc:** giới hạn 20 kết nối đồng thời của OnlyOffice Community cần đối chiếu với số người soạn thảo đồng thời thực tế; nếu vượt → cân nhắc bản Enterprise (trả phí) hoặc Collabora production.

## Các điểm khó đã biết — cần thiết kế sớm

1. **Gán việc theo chức danh/vị trí KHÔNG có sẵn trong engine.** Engine chỉ hỗ trợ *candidate group* / *assignee*. Gán theo "ai đang giữ vị trí X" phải xây **lớp org-resolution** map vị trí → người đang giữ vị trí tại thời điểm tạo task (late binding). Đây là điều cho phép quy trình vẫn hoạt động đúng khi cơ cấu tổ chức thay đổi giữa chừng.
2. **Versioning process definition — điểm khó kinh điển.** Mặc định (Camunda + Flowable): instance đang chạy hoàn tất trên version đã khởi tạo; instance mới dùng version mới nhất. Migration bị ràng buộc (instance phải ở wait state, chỉ một số element được migrate, không đổi được implementation type của task). Quy trình liên đơn vị chạy dài làm vấn đề này gay gắt — phải tính tới việc nhiều version song song.
3. **Form động + lưu trữ:** chọn giữa EAV vs JSON-column. JSON column (MariaDB hỗ trợ) là mặc định thực dụng cho payload form linh hoạt; dành cột quan hệ cho field cần query/báo cáo. EAV thuần giết hiệu năng query ở quy mô lớn.
4. **Audit trail:** log sự kiện append-only, hash-chained, chỉ INSERT (không UPDATE/DELETE thủ công) — yêu cầu cho audit trail cấp chữ ký điện tử (ESIGN/UETA/eIDAS).
5. **Thay đổi cơ cấu tổ chức giữa chừng:** resolve vị trí muộn (lúc gán), giữ snapshot lịch sử cho audit, định nghĩa quy tắc reassign cho task đang chạy khi người giữ vị trí rời đi.
6. **Hiệu năng ở quy mô:** engine nhúng gặp trần DB-contention khi persist token ở khối lượng lớn — cần cấp đúng dung lượng MariaDB và một chiến lược history-cleanup có chủ đích.

## Nguồn tham khảo
- Capital One — Open Source BPM Comparison
- Camunda vs Flowable (ONLU AG; Version 1/Medium)
- Camunda docs — Versioning process definitions; Process instance migration
- Flowable — Spring Boot embedding docs; Baeldung — Embedded Camunda in Spring Boot
- Appian vs Pega vs ServiceNow (PeerSpot); So sánh PowerApps/Mendix/OutSystems/Appian/Pega (Medium)
- E-Signature Audit Trail Schema (Anvil)
