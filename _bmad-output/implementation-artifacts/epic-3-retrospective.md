# Epic 3 — Retrospective (mốc giữa kỳ, 2026-06-26)

Trạng thái: **10/17 story `review`** · epic-3 `in-progress`. BE **54/54**, FE **23/23**. Đã verify live end-to-end.

## Đã hoàn thành (review)
| Story | Nội dung | Điểm nhấn |
|---|---|---|
| 3.1 | Khởi tạo nhiệm vụ từ quy trình publish | Nối **Flowable 7.1** thật; deploy động + snapshot phiên bản |
| 3.2 | Hộp thư "Việc của tôi" | assignee + **hàng đợi nhận việc theo vai trò** |
| 3.3 | Theo dõi quy trình + projection tiến độ | grid phiên chạy + **dòng thời gian** (Flowable history) |
| 3.4 | Hành động trên việc theo bước/vai trò | render form theo **quyền trường** EDIT/READONLY/HIDDEN |
| 3.5 + 3.5b | Tự chuyển bước kế + **định tuyến gateway** | `BpmnConditionInjector` tiêm conditionExpression |
| 3.6 | Hủy theo trạng thái | guard RUNNING + xóa instance |
| 3.7 | Cờ quá hạn SLA | dueAt = tạo + slaHours; badge đỏ |
| 3.8 | Xin gia hạn | `slaBonusHours` task-local var |
| 3.9 | Cascade khi hủy | `TaskAssignment` → CANCELLED |

→ **Nền tảng vận hành quy trình động end-to-end**: thiết kế (Epic 2) → ban hành → khởi tạo → giao việc đúng người (lõi phân công Epic 1) → xử lý form → rẽ nhánh điều kiện → theo dõi → gia hạn/hủy. Tất cả qua cấu hình, không hard-code.

## Quyết định kiến trúc tốt
- **assign-after thay vì TASK_CREATED listener**: gán việc bước kế ngay sau `taskService.complete` (đồng bộ, trong cùng command) — tránh reentrancy/ordering của event listener. Đơn giản, chạy đúng cho cả start lẫn mỗi lần complete.
- **Tiêm conditionExpression lúc deploy** (không sửa stepsMeta schema): điều kiện rẽ nhánh do designer lưu trong `stepsMeta[flowId].condition`, runtime mới dịch sang JUEL — giữ định nghĩa BPMN sạch ở khâu thiết kế.
- **Lõi phân công Epic 1 nay hoạt động thật**: `FlowableMirrorAssignmentAdapter` hết placeholder → app `TaskAssignment` là nguồn sự thật, mirror sang Flowable assignee. Khép vòng AD-14.
- **Snapshot stepsMeta lên WorkflowInstance**: instance giữ định nghĩa lúc khởi tạo, không đổi khi publish bản mới (AD-3).

## Nợ kỹ thuật / hoãn lại có chủ đích
- **Gia hạn có phê duyệt** (3.8): GĐ1 tự phục vụ; cần kênh duyệt riêng để gửi cấp trên.
- **Demo tuyến tính**: engine rẽ nhánh đã sẵn nhưng quy trình demo chưa có gateway — nên bổ sung 1 demo có nhánh Duyệt/Trả lại để trình diễn 3.5b + vòng quay lui.
- **Phân công bước kế chỉ ở complete/start**: nếu luồng tiến do timer/sự kiện ngoài (chưa dùng) sẽ cần listener — chưa cần GĐ1.
- **N+1 query** ở inbox/instances (mỗi task 1 truy vấn instance/var) — chấp nhận ở quy mô 100–500 user; tối ưu sau nếu cần.
- `positionRepo` còn inject thừa trong `WorkflowService` sau khi chuyển sang `roleService.roleCodesForUser` — dọn khi tiện.

## CHẶN: cần quyết trước khi làm 3.10–3.17
Nhóm còn lại đều phụ thuộc **hạ tầng nặng**, không làm tiếp được nếu chưa chốt:
- **3.10–3.12, 3.16**: **OnlyOffice Document Server** (soạn thảo .docx nhúng, import/version dự thảo, ký ban hành) → cần dựng OnlyOffice + quyết lưu trữ tài liệu (đã có trong kế hoạch: OnlyOffice 9.4) + callback/JWT.
- **3.13–3.15**: comment kiểu Jira + @mention, phối hợp **song song** (join chống treo), sửa ý kiến trong hạn → cần mô hình collaboration + có thể realtime.

**Khuyến nghị:** chốt **C1 (phạm vi GĐ1)** — có đưa OnlyOffice + collaboration vào GĐ1 không, hay cắt phạm vi để chạy trước phần phê duyệt thuần (đã xong). Nếu làm: bước kế là **dựng OnlyOffice Document Server (docker) + spike tích hợp** (1 spike riêng trước story 3.10).

## Số liệu
- BE: 54 test (từ 45 đầu Epic 3) · FE: 23 test · 0 fail.
- Story review trong epic: 10 · backlog: 7 (3.10–3.17 trừ trùng) · retrospective: mốc giữa kỳ này.
