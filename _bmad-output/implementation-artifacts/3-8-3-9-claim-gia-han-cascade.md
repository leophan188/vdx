# Story 3.8 + 3.9 + Claim queue: Gia hạn · Cascade hủy · Nhận việc theo vai trò

Status: review

## Stories
- **Claim queue (ROLE)**: việc giao theo vai trò vào hàng đợi "chờ nhận"; người giữ vai trò bấm **Nhận việc**.
- **3.8 Xin gia hạn**: cộng thêm giờ vào hạn SLA của việc.
- **3.9 Cascade khi hủy**: hủy phiên → hủy luôn snapshot phân công các việc đang mở.

## Acceptance Criteria
1. Bước `assigneeType=ROLE` (assigneeId = mã vai trò) → việc gắn **candidate-group = role code**; hộp thư của người giữ vai trò (qua vị trí) hiện việc với cờ **claimable**. **POST /tasks/{id}/claim** → guard ứng viên → `taskService.claim` → thành việc của tôi + audit `TASK_CLAIMED`.
2. **POST /tasks/{id}/extend** {hours, reason}: cộng `slaBonusHours` (task-local var) → **hạn thực tế = giờ tạo + (slaHours + bonus)**; audit `TASK_EXTENDED`. Việc quá hạn có thể hết quá hạn sau gia hạn.
3. **Hủy phiên** (3.6) → với mỗi việc đang mở, `TaskAssignment` tương ứng chuyển **CANCELLED** trước khi xóa instance Flowable; audit ghi `cascadeAssignments=N`.

## Tasks / Subtasks
- [x] `AssignmentStatus.CANCELLED` + `TaskAssignment.cancel()`.
- [x] `WorkflowService.inbox` gộp việc-của-tôi (assignee) + **việc chờ nhận** (candidateGroupIn = `roleService.roleCodesForUser`, unassigned), cờ `claimable`.
- [x] `WorkflowService.claim` (guard ứng viên) · `extend` (slaBonusHours local var) · `effectiveDue` (cộng bonus vào hạn) · cascade trong `cancel`.
- [x] DTO `InboxItem.claimable` · `ExtendRequest`; endpoints `/tasks/{id}/claim` · `/tasks/{id}/extend`.
- [x] FE: hộp thư phân biệt **✋ Nhận việc** (claimable) vs **Xử lý → / ⏰ gia hạn**; service `claim/extend`.
- [x] Test BE: `claim_roleTask_movesFromQueueToMyTask` · `extend_addsBonusHours_clearsOverdue` (Flowable clock) · `cancel_cascadesTaskAssignments`. **BE 54/54, FE 23/23.** Live smoke extend +24h → 200.

## Dev Notes
- **Vai trò của user** = vai trò gắn vào vị trí đang giữ (`PositionRole`), dùng `RoleService.roleCodesForUser`. Khớp với `assigneeId` ROLE designer lưu = `role.code`.
- **3.8 GĐ1 tự phục vụ**: người giữ việc tự gia hạn (ghi audit đầy đủ). **Gia hạn có phê duyệt** (gửi cấp trên duyệt) hoãn lại — cần kênh phê duyệt riêng; ghi nhận để làm sau.
- `slaBonusHours` lưu là **task-local variable** (Flowable) — sống cùng việc, không rò sang biến tiến trình.
- **Còn Epic 3 (cần hạ tầng):** 3.10 OnlyOffice soạn thảo nhúng · 3.11 thu thập ý kiến phối hợp · 3.12 tiếp thu/phê duyệt dự thảo · 3.13 comment Jira + mention · 3.14 sửa ý kiến trong hạn · 3.15 phối hợp song song join chống treo · 3.16 ký ban hành · 3.17 đóng hồ sơ lưu trữ.

### References
[Source: epics.md#Story-3.8,3.9] · [Source: ARCHITECTURE-SPINE.md#AD-14] · FR-C08

## File List
BE: `domain/assignment/AssignmentStatus.java` (+CANCELLED), `domain/assignment/TaskAssignment.java` (+cancel), `application/WorkflowService.java` (inbox claimable/claim/extend/effectiveDue/cascade; +RoleService,TaskAssignmentRepository), `api/dto/TaskDto.java` (+claimable,+ExtendRequest), `api/TaskInboxController.java` (+claim,+extend).
FE: `core/workflow.service.ts` (+claim,+extend,+claimable), `tasks/inbox.ts`+`.html` (Nhận việc/gia hạn).
