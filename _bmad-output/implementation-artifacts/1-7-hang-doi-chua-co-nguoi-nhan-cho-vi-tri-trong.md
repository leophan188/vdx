# Story 1.7: Hàng đợi "chưa có người nhận" cho vị trí trống

Status: review

## Story

As a **hệ thống**,
I want **khi việc mới route tới vị trí đang trống thì giữ ở hàng đợi đơn vị và cảnh báo**,
so that **việc không biến mất âm thầm và đơn vị không tắc ở khâu duyệt (FR-C08, AD-14)**.

## Acceptance Criteria

1. **Given** một vị trí chưa có người giữ, **When** việc mới route tới, **Then** việc vào **hàng đợi "chưa có người nhận"** của đơn vị + cờ `UNASSIGNED` + candidate-group = đơn vị (AD-14). _(FR-C08)_
2. **Given** một việc rơi vào hàng đợi trống, **When** ghi nhận, **Then** **cảnh báo cấp trên** (đơn vị cha) qua audit alert — không để việc trôi âm thầm. _(FR-C08)_
3. **Given** một việc trong hàng đợi, **When** người có quyền **gán tạm** cho một người, **Then** việc chuyển `ASSIGNED` về người đó **qua `AssignmentPort`** (app + Flowable một giao dịch), rời hàng đợi, ghi audit. _(FR-C08, AD-14)_
4. **Given** một việc trong hàng đợi không ai nhận, **When** **định tuyến lên cấp trên (escalate)**, **Then** candidate-group chuyển sang **đơn vị cha** (vẫn `UNASSIGNED`), việc nằm ở hàng đợi cấp trên, ghi audit. _(FR-C08)_
5. **Given** truy vấn hàng đợi theo đơn vị, **When** liệt kê, **Then** trả về các việc `UNASSIGNED` có candidate-group = đơn vị đó.

## Tasks / Subtasks

- [ ] **Task 1 — Domain** (AC:3,4): `TaskAssignment.claim(userId)` (UNASSIGNED→ASSIGNED, xóa candidate); `TaskAssignment.escalateTo(parentOrgUnitId)` (giữ UNASSIGNED, đổi candidate sang cha). `TaskAssignmentRepository.findByStatusAndCandidateGroupOrgUnitId`.
- [ ] **Task 2 — Service** (AC:1–5): `assignTaskToPosition` (nhánh trống) **cảnh báo cấp trên** (audit alert tới đơn vị cha — AC-2); `unassignedQueue(orgUnitId)` (AC-5); `claimUnassigned(taskId, toUserId, actor)` (AC-3, guard chỉ UNASSIGNED + user tồn tại); `escalateUnassigned(taskId, actor)` (AC-4, guard có cha mới leo thang).
- [ ] **Task 3 — REST** (AC:3,4,5): `GET /api/v1/assignments/unassigned?orgUnitId=`; `POST /api/v1/assignments/{taskId}/claim`; `POST /api/v1/assignments/{taskId}/escalate`. ROLE_ADMIN GĐ1.
- [ ] **Task 4 — Test** (AC:1–5): việc tới vị trí trống → vào queue + cảnh báo cha; gán tạm → ASSIGNED + verify port + rời queue; escalate → candidate sang cha + còn UNASSIGNED; escalate ở gốc (không cha) bị chặn.

## Dev Notes

- **Nền sẵn từ 1.5:** `assignTaskToPosition` đã tạo snapshot `UNASSIGNED` + candidate-group = đơn vị khi vị trí trống. Story này **hoàn thiện** vòng đời: liệt kê hàng đợi, cảnh báo cấp trên, gán tạm, leo thang.
- **Cảnh báo cấp trên (AC-2):** chưa có trung tâm thông báo (Epic 4) → GĐ1 ghi **audit alert** `TASK_UNASSIGNED_ALERT` tới đơn vị cha (`OrgUnit.parentId`); nếu là gốc thì cảnh báo chính đơn vị đó. Thông báo in-app/email đầy đủ ở Epic 4.
- **Gán tạm vs uỷ quyền:** gán tạm (1.7) là chuyển UNASSIGNED→ASSIGNED bởi người có quyền, **không** phải chuỗi uỷ quyền (1.6) → không ghi `Delegation`, audit `TASK_CLAIMED`. Đều qua cùng `AssignmentPort`.
- **Leo thang:** dùng `OrgUnit.parentId` trực tiếp; vẫn giữ `UNASSIGNED` để không bao giờ mất việc (AD-14). Việc nằm ở hàng đợi cấp trên cho tới khi có người gán tạm hoặc vị trí có người.
- **Tái dụng:** `AssignmentPort`, `TaskAssignmentRepository`, `OrgUnitRepository`, `UserAccountRepository`, `AuditPort`. Bảo vệ `/api/v1/assignments/**` ROLE_ADMIN (matcher sẵn có).
- **Phạm vi:** UI hàng đợi + chip cảnh báo lộ ra ở Epic 3/4 (hộp thư + dashboard). Story này backend.

### References
- [Source: epics.md#Story-1.7] · [Source: ARCHITECTURE-SPINE.md#AD-14] (FR-C08) · [Source: 1-5-...#TaskAssignment]

## Dev Agent Record

### Agent Model Used
claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 26/26 pass** (4 test mới phủ AC 1–5):
  - `emptyPosition_taskEntersQueue_andAlertsSuperior` — việc tới vị trí trống → vào `unassignedQueue(child)` (AC-1,5) + **audit `TASK_UNASSIGNED_ALERT` tới đơn vị cha** (AC-2).
  - `claim_movesTaskOutOfQueue_toAssignee_viaPort` — gán tạm → ASSIGNED + verify `port.mirrorAssignee` + rời hàng đợi (AC-3).
  - `escalate_movesCandidateToParent_staysUnassigned` — leo thang → candidate sang cha, vẫn UNASSIGNED, chuyển hàng đợi con→cha (AC-4).
  - `escalateAtRoot_isRejected` — leo thang ở gốc (không cha) bị chặn.
- ✅ **Hoàn thiện vòng đời UNASSIGNED** trên nền 1.5: `unassignedQueue`, `claimUnassigned` (UNASSIGNED→ASSIGNED, guard chỉ-UNASSIGNED + user tồn tại), `escalateUnassigned` (đổi candidate sang `OrgUnit.parentId`). Tất cả mutate qua cùng `AssignmentPort` (app + Flowable một tx). `requireUnassigned` guard chung.
- ✅ **Cảnh báo cấp trên:** nhánh UNASSIGNED của `assignTaskToPosition` gọi `alertSuperior` → audit `TASK_UNASSIGNED_ALERT` objectId=đơn vị cha (gốc thì chính đơn vị). Thay cho trung tâm thông báo (Epic 4).
- ✅ **REST:** `GET /api/v1/assignments/unassigned?orgUnitId=`, `POST /{taskId}/claim`, `POST /{taskId}/escalate`. ROLE_ADMIN (matcher sẵn có).
- ⚠️ **Follow-up:** UI hàng đợi + chip cảnh báo + badge ở hộp thư/dashboard → Epic 3/4; thông báo in-app/email thật → Epic 4. Gán tạm khác chuỗi uỷ quyền (không ghi `Delegation`).

### File List

Backend (mới): `test/UnassignedQueueTest.java`; sửa `domain/assignment/TaskAssignment.java` (+claim, +escalateTo), `infrastructure/TaskAssignmentRepository.java` (+findByStatusAndCandidateGroupOrgUnitId), `application/AssignmentService.java` (+OrgUnitRepository/UserAccountRepository, alertSuperior, unassignedQueue, claimUnassigned, escalateUnassigned, requireUnassigned), `api/AssignmentController.java` (+unassigned/claim/escalate), `api/dto/AssignmentDto.java` (+ClaimRequest).
