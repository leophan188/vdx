# Story 3.3 + 3.6: Theo dõi quy trình & Hủy theo trạng thái

Status: review

## Stories
- **3.3 Trạng thái nhiệm vụ / projection tiến độ bước**: xem các phiên chạy, bước hiện tại, người giữ việc, dòng thời gian.
- **3.6 Quy tắc sửa/hủy theo trạng thái**: hủy phiên chạy — chỉ khi đang RUNNING.

## Acceptance Criteria
1. **GET /instances/all**: danh sách phiên chạy (mọi quy trình) + trạng thái + **bước hiện tại** + **người đang giữ** (resolve userId→Họ tên). _(3.3)_
2. **GET /instances/{id}/timeline**: dòng thời gian các bước (userTask) từ Flowable history — DONE (có giờ kết thúc) / ACTIVE (đang xử lý) + người thực hiện. _(3.3)_
3. **POST /instances/{id}/cancel**: hủy phiên — **guard chỉ RUNNING**; xóa instance Flowable (thu hồi việc đang mở) + `status=CANCELLED` + audit `INSTANCE_CANCELLED`. Hủy phiên đã kết thúc → bị chặn. _(3.6)_

## Tasks / Subtasks
- [x] BE `WorkflowService.instances()` (sort mới nhất trước; current step/assignee từ active tasks) · `timeline()` (HistoryService userTask) · `cancel()` (guard + deleteProcessInstance + audit) · helper `userDisplay`.
- [x] BE inject `HistoryService`; DTO `InstanceListItem` · `StepHistory` · `InstanceTimeline` · `CancelRequest`; endpoints `/instances/all`, `/instances/{id}/timeline`, `/instances/{id}/cancel`.
- [x] FE `tracking/` (grid phiên chạy + badge trạng thái + bước/người giữ + **modal dòng thời gian** + nút **Hủy** confirm-dialog). Service `instances/timeline/cancel`. Nav "📡 Theo dõi quy trình" + route `/tracking`. Style `.timeline` vào design-system.
- [x] Test BE `monitor_listsInstance_timeline_andCancel` (list→RUNNING@Soạn, timeline ACTIVE, cancel→CANCELLED, hủy lần 2 chặn). **BE 50/50, FE 23/23.**

## Dev Notes
- **"Trả lại / quay lui"** (phần còn lại của duyệt): đạt được bằng **mô hình hóa** — gateway sau bước Duyệt với điều kiện `lastAction eq 'Trả lại'` → flow quay về bước trước (đã hỗ trợ bởi `BpmnConditionInjector` 3.5b). Không cần code thêm; người thiết kế vẽ vòng lặp + đặt điều kiện.
- Timeline dùng Flowable history (mức `audit` mặc định — đã bật). Hiển thị userTask; có thể mở rộng gateway/service task sau.
- Verify live: admin khởi tạo demo → `/instances/all` RUNNING@"Soạn / Tạo hồ sơ" giữ bởi Nguyễn Văn A → timeline ACTIVE → cancel → CANCELLED.
- **Còn Epic 3:** ROLE→candidate-group claim queue · 3.7 SLA quá hạn (trực giao theo dõi hạn) · 3.8 gia hạn · 3.9 cascade hủy nhiệm vụ con · 3.10–3.17 (OnlyOffice/comment/phối hợp song song/ký ban hành).

### References
[Source: epics.md#Story-3.3,3.6] · [Source: ARCHITECTURE-SPINE.md#AD-13]

## File List
BE: sửa `application/WorkflowService.java` (+HistoryService, instances/timeline/cancel/userDisplay), `api/dto/TaskDto.java` (+4 record), `api/WorkflowController.java` (+3 endpoint).
FE (mới): `tracking/tracking.ts`+`.html`; sửa `core/workflow.service.ts`, `app.routes.ts`, `app.html` (nav), `styles/_components.scss` (.timeline), `app.spec.ts` (nav→11).
