# Đánh giá tính khả thi & thực tế phạm vi — GĐ1 MVP

> Vai trò: Reviewer sản phẩm/kỹ thuật hoài nghi, pressure-test phạm vi và tính khả thi của GĐ1.
> Ngày: 2026-06-24. Đối tượng: `prd.md` + `addendum.md` (BPM low-code, Angular + Spring + MariaDB, on-prem, 100–500 user).

## Kết luận tổng thể

GĐ1 như mô tả **KHÔNG phải là một MVP** theo nghĩa "sản phẩm khả dụng tối thiểu". Đây là **một bộ low-code iBPMS dọc gần-hoàn-chỉnh** — về bản chất là cố gắng tự xây một phần Appian/Nintex/K2 thu nhỏ — bị dán nhãn "Giai đoạn 1". Phần "MVP" gói trọn 9 nhóm năng lực A–I, trong đó có ít nhất **4 nhóm mà mỗi nhóm tự thân là một sản phẩm** (Process Designer cấu hình-được, Form builder động, Org-model + delegation/substitute, Soạn thảo Word-like + versioning).

Với một team **chưa biết quy mô/năng lực**, đây là khối lượng hợp lý của **18–30 tháng** với team trưởng thành, hoặc một dự án rủi ro-cao/dễ trượt-tiến-độ nếu ép vào "GĐ1". Counter-metric mà chính PRD ghi (mục Success Metrics: "độ phức tạp cấu hình không được tăng tới mức admin bỏ cuộc quay lại nhờ Dev") chính là rủi ro lớn nhất của cả dự án — và nó nằm ngay trong raison d'être.

**Khuyến nghị bao trùm:** Re-scope GĐ1 thành **"Walking Skeleton 1 quy trình"** — chạy đúng quy trình mẫu #1 end-to-end với năng lực cấu hình **giới hạn có chủ đích** (không phải "mọi flow type"), đẩy phần lớn năng lực generic/cấu-hình-tự-do sang GĐ1.5/GĐ2.

---

## Bảng tổng hợp severity

| Severity | Số lượng |
|---|---|
| Critical | 4 |
| High | 6 |
| Medium | 5 |
| Low | 2 |
| **Tổng** | **17** |

---

## Mục 1 — GĐ1 là MVP hay là một build 2–3 năm bị dán nhãn sai?

### F1. "MVP" gói trọn 9 nhóm năng lực = scope của một sản phẩm hoàn chỉnh, không phải MVP
- **Concern:** Mục "Kế hoạch phân giai đoạn → GĐ1 — MVP (toàn bộ FR A–I)" đặt **toàn bộ** A–I vào GĐ1. Đây không phải minimum: nó gồm process designer generic (A), form builder generic (B), org-model + delegation/substitute (C), task engine (D), Word editor + versioning + comment threaded + @mention (E), phối hợp liên đơn vị có join (F), dashboard + báo cáo 4 lát + xuất Excel/PDF (G), notification in-app + email (H), audit append-only + lưu trữ (I). Mỗi nhóm B, E, và một nửa A là sản phẩm độc lập. "Chạy trọn quy trình mẫu #1 end-to-end" KHÔNG đòi hỏi tính generic của A02/B01 — nó chỉ cần *một* cấu hình quy trình hoạt động.
- **Severity:** Critical
- **Khuyến nghị:** Tách khái niệm **"chạy được quy trình mẫu #1"** khỏi **"cấu hình được mọi quy trình"**. GĐ1 chỉ cam kết cái đầu. Đưa "designer kéo-thả hỗ trợ MỌI flow type như cấu hình thuần" (FR-A02) ra khỏi định nghĩa-xong của GĐ1; GĐ1 chỉ cần các flow type mà mẫu #1 dùng (tuần tự, 1 gateway điều kiện, phối hợp song song + join theo thời hạn).

### F2. Các "sink" tiến độ lớn nhất trong GĐ1
- **Concern:** Theo thứ tự rủi ro/giờ-công:
  1. **FR-E01/E08 — OnlyOffice + import .docx + versioning ký JWT** (xem Mục 3). Tích hợp editor on-prem, callback persistence, version, comment, track-changes là một dòng-công-việc riêng, nhiều cạnh-sắc vận hành.
  2. **FR-A01/A02 — Process Designer canvas BPMN kéo-thả + mọi flow type như cấu hình** (xem Mục 2). UI designer + mapping config→engine + validate cấu hình do non-dev tạo.
  3. **FR-B01–B08 — Form builder động** với conditional show/hide (B04), validation cấu hình (B05), per-step field permission (B08), org-tree picker (B03). Form-engine runtime + designer là sản phẩm thứ hai bên cạnh process-engine.
  4. **FR-C04/C05 — delegation/forwarding/substitute + late binding + snapshot + chuyển giao thủ công**. Tổ hợp uỷ quyền/thay-thế/đổi-cơ-cấu-giữa-chừng là nguồn lỗi và edge-case lớn.
  5. **FR-G05/G07 — báo cáo 4 lát + xuất Excel/PDF** trên dữ liệu form động (JSON column) — truy vấn thống kê trên payload động là điểm đau hiệu năng (xem Mục 5).
- **Severity:** High
- **Khuyến nghị:** Áp dụng nguyên tắc "vertical slice trước, generic sau". Ưu tiên D + C-core + G-core + I-core (vòng lặp lõi: tạo việc → giao → xử lý → duyệt → đóng hồ sơ → audit). Designer và form-builder generic làm sau khi vòng-lõi chạy.

### F3. Có thể defer sang GĐ2 mà KHÔNG phá vòng-lõi
- **Concern:** Các item dưới đây không nằm trên đường tới-hạn của vòng lặp end-to-end mẫu #1:
  - **FR-A02 (vòng lặp nhiều vòng, quay-lại-bước, parallel tổng quát)** — mẫu #1 không cần loop nhiều vòng (PRD GĐ2 đã ghi "vòng lặp cho ý kiến nhiều vòng" là GĐ2). Giữ parallel-join *đặc thù phối hợp* (F05), bỏ parallel/loop *tổng quát cấu hình* khỏi GĐ1.
  - **FR-E06 (comment threaded kiểu Jira + @mention)** — tiện ích "nice-to-have"; văn bản ý kiến chính thức (E02) + bảng tổng hợp (E03) đã đủ cho vòng-lõi.
  - **FR-G07 (xuất PDF báo cáo)** — Excel đủ cho GĐ1; PDF in/trình có thể GĐ2.
  - **FR-D09 (xin gia hạn có duyệt)** — quy trình phụ; defer được.
  - **FR-B02 phần rich-text + bảng/lưới nhiều dòng** — chỉ giữ trường mà form mẫu #1 cần.
- **Severity:** High
- **Khuyến nghị:** Defer A02-loop/parallel-tổng-quát, E06, G07, D09 sang GĐ2. Mỗi item cắt được nhiều tuần. Việc này KHÔNG phá end-to-end của UJ-1.

---

## Mục 2 — Tham vọng "config-not-code generic BPMN engine": abstraction rò rỉ ở đâu?

### F4. "Hỗ trợ MỌI flow type như cấu hình thuần, do IT-staff làm, không recompile" là tham vọng leak-prone nhất
- **Concern:** FR-A02 liệt kê tuần tự · rẽ nhánh điều kiện · song song · join/sync · quay-lại-bước · loop nhiều vòng — "bật/cấu hình tuỳ chọn". Engine (Flowable) hỗ trợ các token-semantics này, nhưng **bề mặt cấu hình để non-programmer dùng đúng là rất lớn**. Các chỗ leak quay lại cần Dev:
  - **Điều kiện chuyển bước (FR-A05/A02 rẽ nhánh):** "điều kiện dựa trên dữ liệu form" cuối cùng là **biểu thức** (JUEL/SpEL/DMN). IT-staff viết biểu thức trên field động = lập trình trá hình. Hoặc bạn xây rule-builder UI (lại là sản phẩm con), hoặc Dev phải vào.
  - **Join/sync (FR-F05):** "chờ tất cả (hoặc theo thời hạn)" — semantics chờ-một-phần / timeout / huỷ-nhánh là nơi parallel-gateway hay sinh deadlock/token-kẹt nếu cấu hình sai. Non-dev rất khó tự-debug token kẹt.
  - **Quay-lại-bước + loop:** rollback dữ liệu form đã ghi, version nào của field, side-effect của notification/SLA khi lặp — đây là lớp ngữ nghĩa engine không tự lo; cần thiết kế & nhiều khi cần Dev.
  - Addendum §"Các điểm khó" #1 tự thừa nhận **gán theo vị trí KHÔNG có sẵn trong engine** → phải xây lớp org-resolution custom. Đó đã là code, không phải config.
- **Severity:** Critical
- **Khuyến nghị:** Định nghĩa rõ **ranh giới "config envelope"** trong GĐ1: tập gateway/flow được phép cấu hình là **một danh mục đóng** (sequential, 1 exclusive gateway với điều-kiện-chọn-từ-dropdown trên field đã-khai-báo, parallel-join đặc thù phối hợp). Mọi thứ ngoài envelope = "yêu cầu Dev" và phải được thừa nhận công khai. Đừng hứa "mọi flow type". Bổ sung một FR rõ ràng cho **rule/condition builder bằng UI** (không cho gõ biểu thức tự do) — và nếu rule-builder bị defer, thì FR-A05 không thật sự "config-not-code" trong GĐ1.

### F5. Success metric "≥80% thay đổi bằng cấu hình, ≤1 ngày" mâu thuẫn với độ phức tạp envelope
- **Concern:** Mục Success Metrics chốt ≥80% thay đổi không cần release code và ≤1 ngày đưa quy trình mới vào vận hành. Nhưng nếu config-envelope hẹp (F4) thì nhiều thay đổi thực tế sẽ rơi ra ngoài → cần Dev → không đạt 80%. Nếu envelope rộng (mọi flow type) thì độ phức tạp UI vượt khả năng IT-staff → counter-metric kích hoạt (admin bỏ cuộc). Đây là **thế lưỡng nan nội tại**.
- **Severity:** High
- **Khuyến nghị:** Hạ kỳ vọng metric cho GĐ1 thành "đo trên một danh mục thay-đổi-mẫu đã định nghĩa" thay vì "≥80% mọi thay đổi". Xác thực sớm bằng pilot: cho IT-staff thật cấu hình mẫu #1 + 2 biến thể, đo thời gian/độ-bỏ-cuộc trước khi mở rộng scope.

### F6. Versioning/migration đẩy GĐ2 nhưng org-resolution late-binding lại bắt buộc GĐ1 — mức độ "code" bị đánh giá thấp
- **Concern:** Addendum §"điểm khó" #1 và #2 cho thấy hai lớp custom bắt buộc (org-resolution + version handling) đều là **engine-level code**, không phải config. PRD framing "Admin IT cấu hình, không viết code" che mất việc **đội Dev phải xây sẵn rất nhiều khung** trước khi bất kỳ config nào chạy. Đây không sai về kiến trúc, nhưng làm scope GĐ1 bị under-estimate.
- **Severity:** Medium
- **Khuyến nghị:** Trong kế hoạch, tách bạch "platform-build (Dev, một lần)" vs "process-config (IT-staff, lặp lại)". Phần lớn rủi ro GĐ1 nằm ở platform-build — cần phản ánh vào ước lượng và vào định nghĩa MVP.

---

## Mục 3 — OnlyOffice Community giới hạn 20 kết nối đồng thời vs 100–500 user

### F7. Giới hạn 20 kết nối: KHÔNG phải blocker GĐ1, nhưng cần khẳng định bằng số chứ không bằng giả định
- **Concern:** NFR-02 và mục Câu-hỏi-mở đã gắn nhãn [DEFER — rủi ro đã biết]. Phân tích: 100–500 *user hệ thống* khác xa số *người soạn thảo .docx đồng thời*. Trong UJ-1, chỉ chuyên viên chủ trì (bước 2-1) và người duyệt (2-2) thực sự mở editor; phần lớn user chỉ điền form / xem / duyệt — không chiếm "editing connection". Khả năng cao số soạn-thảo-đồng-thời ở đỉnh < 20 với 100–500 user. **Vì vậy chưa nên coi là blocker GĐ1.**
- **NHƯNG:** rủi ro thật là (a) "20 kết nối" tính cả co-editing/viewer tuỳ cấu hình; (b) AGPL §13 (addendum) — nhúng qua API thường không buộc mở mã, nhưng **cần rà soát pháp lý trước**, không để tới lúc go-live; (c) nếu sai giả định, fallback (Enterprise/Collabora) là **thay-thế-thành-phần giữa chừng** — tốn kém.
- **Severity:** Medium (về kỹ thuật) / High (về rủi ro-quyết-định nếu không đo sớm)
- **Khuyến nghị:** Giữ OnlyOffice cho GĐ1 nhưng **bắt buộc một spike đo tải trong 2 tuần đầu** (mô phỏng N người co-edit), và **rà soát pháp lý AGPL trước khi cam kết**. Đặt cờ quyết định: nếu đỉnh đo-được > ~15 → kích hoạt phương án trả phí trước khi xây sâu. Đồng thời thiết kế lớp editor sau một **interface trừu tượng** để swap (OnlyOffice ↔ Collabora) không phải viết lại.

### F8. Soạn thảo Word-like + import + versioning là sink lớn dễ bị coi nhẹ
- **Concern:** FR-E01 + E08 gộp: editor on-prem, import .docx trung-thực, lưu/version qua callback ký JWT, track-changes/comment. Đây là **nhiều tháng tích hợp + vận hành** (JWT key mgmt, document storage, conflict khi co-edit, dọn version). PRD trình bày như một dòng FR.
- **Severity:** High
- **Khuyến nghị:** Cân nhắc GĐ1 dùng mô hình nhẹ hơn: **upload/replace .docx + version theo file** (không co-edit in-browser), hoặc giới hạn editor cho 1–2 vai trò soạn thảo. Full co-edit + comment in-document đẩy GĐ2. Việc này gỡ phụ-thuộc-tới-hạn lớn nhất của GĐ1.

---

## Mục 4 — Mâu thuẫn nội tại giữa tham vọng generic và scoping GĐ1

### F9. Versioning đẩy GĐ2 nhưng instance liên-đơn-vị chạy-dài sẽ sống lâu hơn lần sửa quy trình → vấn đề phát sinh NGAY GĐ1
- **Concern:** FR-A06 chốt hành vi cơ-bản (instance mới dùng bản mới, instance đang chạy giữ bản cũ) và đẩy migration nâng cao sang GĐ2. Addendum §"điểm khó" #2 cảnh báo chính xác: quy trình liên đơn vị chạy-dài làm vấn-đề-version gay gắt, "phải tính tới nhiều version song song". Mâu thuẫn: PRD muốn "đưa quy trình mới ≤1 ngày" (admin sửa thường xuyên) + instance phối hợp sống nhiều tuần → **nhiều version song song chạy đồng thời là điều chắc chắn xảy ra trong GĐ1**, không phải GĐ2. Nếu UI/DB/báo cáo không xử lý "instance ở version cũ" ngay từ GĐ1, sẽ có bug ngữ nghĩa (việc đang chạy hiển thị theo định nghĩa mới, SLA/bước lệch).
- **Severity:** Critical
- **Khuyến nghị:** GĐ1 PHẢI hỗ trợ đầy đủ **co-existence của nhiều version đang chạy** (đọc đúng định nghĩa theo version của instance) — đây là minimum, không phải "migration nâng cao". Chỉ *migration in-flight* (chuyển instance đang chạy sang version mới) mới được defer. Làm rõ FR-A06 để tách hai khái niệm này; như viết hiện tại dễ bị hiểu nhầm là "không cần lo version trong GĐ1".

### F10. "Bộ trạng thái fix cứng" (FR-D03) mâu thuẫn với tham vọng "mọi quy trình cấu hình được"
- **Concern:** FR-D03 chốt tập trạng thái cố định (Chờ phê duyệt · Đang xử lý · Hoàn thành · Quá hạn · Hủy). Nhưng một engine generic "mọi flow type, mọi quy trình" thường cần trạng thái/substate tuỳ quy trình. Với mẫu #1 thì tập cứng OK; với tham vọng generic thì sẽ leak (quy trình khác cần "Chờ phối hợp", "Trả lại", "Tạm hoãn"…). FR-A04 đã liệt kê hành động "Trả lại/Từ chối/Uỷ quyền" mà không có trạng thái tương ứng trong D03.
- **Severity:** Medium
- **Khuyến nghị:** Đây thực ra là một **lựa-chọn-scope tốt cho GĐ1** (giữ trạng thái cứng) — nhưng hãy ghi rõ nó **giới hạn tính generic**, và đối chiếu: "Trả lại"/"Từ chối" (FR-A04/D04) map vào trạng thái nào? Bổ sung mapping rõ ràng để tránh khoảng trống định nghĩa.

### F11. FR-C05 (không tự-động-cướp-việc) vs FR-C04 (substitute khi vắng) — quy tắc giao thoa chưa rõ
- **Concern:** FR-C05 nói task đang chạy giữ snapshot người-đã-giao, đổi cơ cấu không tự chuyển việc. FR-C04 nói "người thay thế khi vắng đảm bảo việc không tắc". Hai cái va nhau: nếu người giữ snapshot đi vắng, substitute có tự nhận việc-đang-chạy không (mâu thuẫn "không tự động"), hay phải "chuyển giao thủ công"? Ngữ nghĩa delegation vs substitute vs reassign chồng lấn chưa được phân định.
- **Severity:** Medium
- **Khuyến nghị:** Split FR-C04 thành các FR riêng (delegation / reassign / substitute) với quy tắc kích-hoạt và phạm-vi (áp cho việc-mới hay cả việc-đang-chạy) rõ ràng, và đối chiếu tường minh với FR-C05. Đây là vùng edge-case sinh lỗi cao.

### F12. "Phối hợp liên đơn vị cơ bản" GĐ1 nhưng có join/parallel — ranh giới "cơ bản" mờ
- **Concern:** Nhóm F đặt parallel + join (F01, F05) vào GĐ1 nhưng gọi là "cơ bản", trong khi F02 (luồng trong đơn vị phối hợp: phân công → soạn → duyệt → trả về) là một **sub-process nhiều bước nhiều vai trò** — không hề "cơ bản". Parallel + join + sub-process chính là phần kỹ thuật khó nhất của cả engine (token, đồng bộ, timeout nhánh). Nhãn "cơ bản" làm under-estimate.
- **Severity:** High
- **Khuyến nghị:** Thừa nhận F01/F02/F05 là phần lõi-khó của GĐ1 (không phải "cơ bản"). Giữ chúng (mẫu #1 cần), nhưng phản ánh đúng chi phí; defer rõ phần đa-tầng/lồng-nhau/leo-thang (đã đúng trong PRD).

---

## Mục 5 — NFR thiếu & mối lo vận hành on-prem doanh nghiệp

### F13. Backup/restore & upgrade path mới ở mức [ASSUMPTION] — chưa đủ cho on-prem gov
- **Concern:** NFR-05 nói "sao lưu định kỳ + quy trình phục hồi" nhưng để [ASSUMPTION] mức cụ thể. Thiếu hẳn: RPO/RTO, backup consistency giữa **MariaDB + document store (OnlyOffice files) + audit chain** (ba kho phải nhất quán điểm-thời-gian, nếu lệch sẽ hỏng audit/version), và **không có upgrade path** cho chính nền tảng + engine Flowable (schema migration của engine khi nâng cấp).
- **Severity:** High
- **Khuyến nghị:** Thêm NFR backup nhất-quán-đa-kho (DB + file + audit cùng điểm phục hồi) với RPO/RTO chốt số; thêm NFR upgrade/runbook cho nâng cấp app + engine + OnlyOffice; định nghĩa rollback.

### F14. Data migration & seed cơ cấu tổ chức ban đầu không có NFR/FR
- **Concern:** FR-C01 chốt "nhập & quản lý trực tiếp trong hệ thống, không import HR/AD". Với 100–500 user + cây tổ chức nhiều cấp + gán vị trí, nhập tay là gánh nặng go-live và nguồn lỗi. Không có FR cho **bulk import/seed** (kể cả import file Excel một-lần).
- **Severity:** Medium
- **Khuyến nghị:** Thêm tối thiểu một công cụ import-một-lần (Excel/CSV) cho cây tổ chức + user + gán vị trí cho go-live, dù SSO/AD vẫn defer. Không cần đầy đủ, nhưng cần đủ để onboard.

### F15. Hiệu năng truy vấn báo cáo trên form động (JSON column) chưa có NFR cụ thể
- **Concern:** NFR-10 chọn JSON column + cột quan hệ cho field cần query (đúng hướng), nhưng FR-G04 (tìm kiếm đa tiêu chí), FR-G05 (báo cáo 4 lát), FR-G07 (xuất) sẽ truy vấn xuyên dữ liệu form. Addendum §"điểm khó" #3/#6 cảnh báo EAV/JSON giết hiệu năng và DB-contention khi persist token. NFR-06 chỉ [ASSUMPTION] <2s, không gắn với truy vấn thống kê/khối lượng. **Chỗ này dễ vỡ ở quy mô.**
- **Severity:** High
- **Khuyến nghị:** Thêm NFR hiệu năng có số cho báo cáo (vd: báo cáo 4-lát trên N tháng dữ liệu trả < X giây với M instance) và bắt buộc thiết kế **read-model/bảng tổng-hợp (denormalized) hoặc cột-promote** cho mọi field xuất hiện trong G05. Định nghĩa rõ field nào "report-able" phải lên cột quan hệ ngay khi cấu hình form (ràng buộc designer ↔ báo cáo).

### F16. Tăng trưởng bảng audit append-only + history engine không có chiến lược vòng đời
- **Concern:** FR-I01 (audit append-only, không xoá) + addendum #4 (hash-chained) + #6 (history-cleanup có chủ đích) → bảng audit và history của Flowable **chỉ lớn lên**. Không có NFR về retention, partition, archival của chính bảng audit, hay tác động hiệu năng khi audit table đạt hàng chục triệu dòng (mọi hành động đều ghi). Mâu thuẫn tiềm tàng: "không cho sửa/xoá" vs nhu cầu archival/partition vận hành.
- **Severity:** High
- **Khuyến nghị:** Thêm NFR vòng-đời audit/history: partition theo thời gian, chiến lược archival (move-to-cold, vẫn append-only & vẫn verify được hash-chain), và history-level của Flowable (audit vs full) phải chốt sớm vì ảnh hưởng cả hiệu năng lẫn dung lượng.

### F17. Thiếu NFR cho concurrency/khóa task & idempotency khi chuyển bước
- **Concern:** FR-D05 (hoàn thành bước → tự chuyển việc) + phối hợp song song (F) + nhiều người thao tác đồng thời → cần khóa lạc quan/idempotency để tránh double-transition, double-notification (H), hoặc join bị kích-hoạt hai lần. Không có NFR/FR đề cập tính nhất quán giao dịch khi nhiều nhánh về cùng lúc.
- **Severity:** Low (engine lo phần lớn, nhưng cần khẳng định)
- **Khuyến nghị:** Thêm một dòng NFR yêu cầu giao-dịch & idempotency cho chuyển-bước và phát-thông-báo; dựa vào transaction semantics của engine nhưng test rõ trường hợp join đồng-thời.

---

## Tổng kết khuyến nghị tái-scope GĐ1

**Giữ trong GĐ1 (vòng-lõi end-to-end mẫu #1):**
- D (task execution), C-core (org-tree, gán vị trí/người, RBAC, snapshot — FR-C01/02/03/05/06/07), G-core (dashboard, "ai làm gì", cá nhân, tìm kiếm, báo cáo 4-lát + xuất Excel), I-core (audit, vết duyệt, đóng hồ sơ + metadata tối thiểu), H (in-app + email), F01/F02/F05 (phối hợp + join — thừa nhận là phần khó), E01/E02/E03/E04/E05/E09 (soạn thảo + ý kiến + bảng tổng hợp + ghi nhận ký).
- A/B ở mức **đủ để cấu hình mẫu #1** (config-envelope đóng), KHÔNG phải generic mọi flow type.
- **Co-existence nhiều version đang chạy** (F9) — bắt buộc.

**Đẩy sang GĐ2 / GĐ1.5:**
- FR-A02 loop nhiều vòng + parallel/return tổng quát ngoài envelope; rule-builder biểu thức tự do; FR-E06 (comment threaded + @mention); FR-G07 (xuất PDF); FR-D09 (xin gia hạn); co-edit + in-document comment của OnlyOffice (giữ upload/replace + version theo file ở GĐ1); migration in-flight giữa version.

**Phải bổ sung (NFR/FR còn thiếu):**
- Backup nhất-quán đa-kho + RPO/RTO + upgrade/rollback runbook (F13).
- Import-một-lần cây tổ chức/user (F14).
- NFR hiệu năng báo cáo có số + read-model/promote-column (F15).
- NFR vòng-đời audit/history (partition/archival) (F16).
- NFR giao-dịch/idempotency chuyển-bước (F17).
- Rà soát pháp lý AGPL + spike đo tải OnlyOffice trong 2 tuần đầu (F7).

**Phán quyết:** GĐ1 hiện tại là một sản phẩm, không phải MVP. Khả thi về *kiến trúc* (lựa chọn Flowable + OnlyOffice + JSON-column đều hợp lý), nhưng *không khả thi về tiến độ* nếu giữ nguyên scope "toàn bộ A–I generic" dưới nhãn "MVP". Re-scope theo vertical-slice là điều kiện tiên quyết.
