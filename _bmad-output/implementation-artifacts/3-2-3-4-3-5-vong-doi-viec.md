# Story 3.2 + 3.4 + 3.5: Vòng đời việc (hộp thư · hành động · tự chuyển bước)

Status: review (gộp 3 story — chung một lát cắt runtime)

## Stories
- **3.2 Hộp thư "Việc của tôi"**: user thấy các việc đang chờ mình xử lý.
- **3.4 Hành động trên việc theo bước/vai trò**: mở việc → nhập form (theo quyền trường) → chọn hành động → hoàn thành.
- **3.5 Tự chuyển việc sang bước kế**: việc hoàn thành → luồng Flowable tiến → việc bước kế **tự gán** đúng người.

## Acceptance Criteria
1. **GET /inbox** trả về việc Flowable đang giao cho user đăng nhập (assignee = userId). _(3.2)_
2. **GET /tasks/{id}** trả cấu hình bước: form gắn, **quyền trường** (EDIT/READONLY/HIDDEN), tập **hành động**, dữ liệu hiện có. _(3.4)_
3. **POST /tasks/{id}/complete** {action, formData}: ghi biến tiến trình + hoàn thành việc → audit `TASK_COMPLETED`. _(3.4)_
4. Sau khi hoàn thành, **việc bước kế** vừa sinh được **gán tự động** theo stepsMeta (assign-after, không listener) → vào hộp thư người kế. _(3.5)_
5. Instance hết việc → `WorkflowInstance.status = COMPLETED` + audit `INSTANCE_COMPLETED`.

## Tasks / Subtasks
- [x] BE `WorkflowService.inbox/detail/complete` + helper `assignActiveTasks` tái dùng cho cả start & complete.
- [x] BE `TaskInboxController` `/inbox` · `/tasks/{id}` · `/tasks/{id}/complete` (mọi user đăng nhập, không chỉ ADMIN).
- [x] BE `TaskDto` (InboxItem · Detail · CompleteRequest). Map username→userId qua `UserAccountRepository`.
- [x] FE `WorkflowService` (start/inbox/detail/complete) + màn **Việc của tôi** (`tasks/inbox`) data-grid + **modal xử lý việc** render form theo quyền trường + nút hành động.
- [x] FE nav "📥 Việc của tôi" (mọi user) + route `/inbox`; nút **▶ Khởi tạo** trên quy trình đã ban hành (processes list).
- [x] Test BE full-loop (2 bước, 2 người): start → inbox u1 → complete → inbox u2 (bước kế tự gán) → complete → done. **BE 48/48, FE 23/23.**

## Dev Notes
- **assign-after thay vì TASK_CREATED listener**: hoàn thành việc → `taskService.complete` (đồng bộ, luồng tiến trong cùng command) → sau đó query việc đang-mở của instance → gán. Tránh reentrancy/ordering của event listener; đủ cho mọi bước (start + mỗi lần complete).
- **Quyền trường** lấy từ `stepsMeta[stepKey].fieldPerms` (Story 2.9); FE ẩn HIDDEN, disable READONLY, validate required cho EDIT.
- **Verify live**: admin khởi tạo demo → `chuyenvien` inbox "Soạn / Tạo hồ sơ" (form gắn) → complete → trống → `truongphong` tự nhận "Duyệt".
- **Hạn chế GĐ này / việc tiếp theo Epic 3:** inbox theo **assignee** (POSITION/USER); việc ROLE→candidate-group (claim từ hàng đợi) = 3.x. Định tuyến gateway theo điều kiện (map `stepsMeta.condition`→Flowable `conditionExpression`) chưa nối — demo tuyến tính. Trạng thái/tiến độ bước (3.3 projection) hiển thị chi tiết hơn = story sau. Trả lại/thu hồi/hủy (3.6/3.9), SLA quá hạn (3.7) chưa làm.

### References
[Source: epics.md#Story-3.2,3.4,3.5] · [Source: ARCHITECTURE-SPINE.md#AD-13,AD-14]

## Bổ sung 3.5b — Định tuyến gateway theo điều kiện
- **`BpmnConditionInjector`**: lúc deploy, tiêm `<conditionExpression>` (JUEL) vào nhánh ra của exclusive/inclusive gateway từ `stepsMeta[flowId].condition` (Story 2.2). Chỉ tiêm trên flow xuất phát từ gateway (tránh kẹt token ở luồng thường). Map: `eq`→`${field == 'v'}`, `ne`→`${field != 'v'}`, `truthy`("có giá trị")→`${field == true || (field != null && field != '')}`. Biến = tên trường form (đã là biến tiến trình).
- Verify test `gateway_routesByFormValue_condition`: `muc=Cao`→nhánh "Ưu tiên cao"; khác→nhánh `default`. **BE 49/49.**
- Demo hiện vẫn tuyến tính; engine rẽ nhánh đã sẵn sàng cho quy trình có gateway.

## File List
BE (mới): `api/dto/TaskDto.java`, `api/TaskInboxController.java`; sửa `application/WorkflowService.java` (inbox/detail/complete + helpers, +UserAccountRepository).
FE (mới): `core/workflow.service.ts`, `tasks/inbox.ts`, `tasks/inbox.html`; sửa `app.routes.ts` (+/inbox), `app.html` (+nav), `processes/processes.ts`+`.html` (+▶ Khởi tạo), `app.spec.ts` (nav 8→9).
