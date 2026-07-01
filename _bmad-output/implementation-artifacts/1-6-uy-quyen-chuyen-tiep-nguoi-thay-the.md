# Story 1.6: Uỷ quyền, chuyển tiếp & người thay thế

Status: review

## Story

As a **người giữ vị trí**,
I want **uỷ quyền/chuyển tiếp việc hoặc đặt người thay thế khi vắng**,
so that **công việc không bị tắc — và mọi reassign đi qua một `AssignmentPort` duy nhất (AD-14), có guard chống lặp/self-approval**.

## Acceptance Criteria

1. **Given** một việc đang ở người A, **When** A **uỷ quyền (delegate)** hoặc **chuyển tiếp (forward)** cho B, **Then** việc chuyển sang B **qua `AssignmentPort`** (app `TASK_ASSIGNMENT` + Flowable mirror trong **một giao dịch**), ghi audit. _(FR-C04, AD-14)_
2. **Given** chuỗi uỷ quyền của một việc, **When** reassign tới một người **đã từng giữ việc đó** (A→B→A …), **Then** **bị từ chối** (guard chống lặp). _(FR-C04)_
3. **Given** một việc, **When** reassign về **chính người đang giữ** (self), **Then** bị từ chối (no-op/tách quyền — không tự uỷ cho mình). _(FR-C04)_
4. **Given** một vị trí có cấu hình **người thay thế** đang bật, **When** giao **việc mới** theo vị trí đó, **Then** việc resolve về **người thay thế** thay vì người giữ (khi giữ vắng); audit ghi rõ substitution. _(FR-C04, AD-7)_
5. **Given** mọi uỷ quyền/chuyển tiếp/đặt-gỡ người thay thế, **When** thực hiện, **Then** ghi audit append-only qua `AuditPort` (AD-6).

## Tasks / Subtasks

- [ ] **Task 1 — Domain** (AC:1,2): `Delegation` (id, taskId, fromUserId, toUserId, kind, reason, createdAt) + `DelegationKind{DELEGATE,FORWARD}`; `TaskAssignment.reassignTo(userId, fromUserId)` (cập nhật assignee hiện hành + `delegatedFromUserId`). `Substitute` (positionId, substituteUserId, active) — tối đa một bản active/vị trí.
- [ ] **Task 2 — Infra** (AC:1,4): `DelegationRepository.findByTaskIdOrderByCreatedAtAsc`; `SubstituteRepository.findByPositionIdAndActiveTrue`.
- [ ] **Task 3 — Service** (AC:1–5): `AssignmentService.reassignTask(taskId, toUserId, kind, reason, actor)` — guard self (AC-3) + guard chống lặp dựa trên người đã từng giữ việc (AC-2); ghi `Delegation` + `reassignTo` + `port.mirrorAssignee`, **một `@Transactional`**; audit. `SubstituteService.setSubstitute/clearSubstitute`. `assignTaskToPosition` ưu tiên người thay thế active (AC-4).
- [ ] **Task 4 — REST** (AC:1,4): `POST /api/v1/assignments/{taskId}/reassign` (kind+toUserId+reason); `PUT/DELETE /api/v1/positions/{id}/substitute`; ROLE_ADMIN GĐ1.
- [ ] **Task 5 — Test** (AC:1–4): delegate & forward chuyển assignee + verify port; **guard chống lặp** A→B→A bị chặn; **guard self** bị chặn; vị trí có substitute active → việc mới về substitute.

## Dev Notes

- **AD-14:** mọi uỷ quyền/chuyển tiếp/thay thế/reassign đi qua **một `AssignmentPort`** (đã dựng ở 1.5), ghi app + Flowable một giao dịch. Story này tái dụng port đó, không thêm port mới.
- **Snapshot vs reassign:** 1.5 đảm bảo đổi *cơ cấu* (đổi người giữ) **không** cướp việc đang chạy. Reassign ở 1.6 là hành động **chủ đích** của người giữ → có sửa assignee hiện hành của việc; không vi phạm bất biến snapshot-org của 1.5 (đường đi khác nhau).
- **Guard chống lặp (AC-2)** GĐ1: từ chối reassign tới bất kỳ ai **đã từng giữ việc** (tập người trong chuỗi `Delegation` + assignee hiện hành). Đủ chặn vòng lặp + tránh trả việc lòng vòng. **Self-approval đầy đủ theo bước duyệt** (tách quyền duyệt trên một step) hoàn thiện khi có engine quy trình (Epic 2/3) — ghi follow-up.
- **Người thay thế (AC-4):** `assignTaskToPosition` ưu tiên substitute active → resolve substitute (đại diện khi giữ vắng); nếu không có substitute → người giữ; không có người giữ → `UNASSIGNED` (1.7).
- **Tái dụng:** `AssignmentPort`, `TaskAssignmentRepository`, `PositionRepository`, `AuditPort`. Bảo vệ ROLE_ADMIN (system/admin-facing GĐ1; nút uỷ quyền cho end-user lộ ra ở Epic 3).

### References
- [Source: epics.md#Story-1.6] · [Source: ARCHITECTURE-SPINE.md#AD-14] · [Source: 1-5-...#AssignmentPort]

## Dev Agent Record

### Agent Model Used
claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 22/22 pass** (4 test mới phủ AC 1–4):
  - `delegate_movesAssigneeToTarget_viaPort` — A uỷ quyền B → assignee=B, `delegatedFromUserId`=A, **verify `port.mirrorAssignee`** (AC-1, AD-14).
  - `forward_chain_thenCycleBackIsRejected` — A→B forward ok; B→A (đã từng giữ) **bị chặn** chống lặp (AC-2).
  - `reassignToSelf_isRejected` — uỷ quyền cho chính người đang giữ **bị chặn** (AC-3).
  - `activeSubstitute_routesNewTaskToSubstitute_notHolder` — có substitute active → việc mới về substitute; gỡ → quay lại người giữ (AC-4).
- ✅ **Reassign qua cùng `AssignmentPort` của 1.5** (app + Flowable một `@Transactional`): `AssignmentService.reassignTask(kind=DELEGATE/FORWARD)` ghi `Delegation` append-only + `TaskAssignment.reassignTo` + mirror; audit `TASK_DELEGATE`/`TASK_FORWARD`.
- ✅ **Guard:** self (toUser==assignee) + chống lặp (toUser ∈ tập người đã từng giữ việc, dựng từ chuỗi `Delegation`).
- ✅ **Người thay thế:** `SubstituteService.set/clear` (một bản active/vị trí, substitute≠người giữ); `assignTaskToPosition` **ưu tiên substitute active** rồi mới tới người giữ — không cướp việc đang chạy (snapshot 1.5 vẫn giữ).
- ✅ **REST:** `POST /api/v1/assignments/{taskId}/reassign`; `PUT/DELETE /api/v1/positions/{id}/substitute`. ROLE_ADMIN (matcher sẵn có).
- ⚠️ **Follow-up:** tách-quyền-duyệt đầy đủ trên một **bước duyệt** (no self-approval theo step) cần engine quy trình — Epic 2/3. Nút uỷ quyền/chuyển tiếp cho end-user lộ ra ở Epic 3 (hộp thư). FlowableMirror vẫn placeholder (1.5).

### File List

Backend (mới): `domain/assignment/{Delegation,DelegationKind}.java`, `domain/position/Substitute.java`, `infrastructure/{DelegationRepository,SubstituteRepository}.java`, `application/SubstituteService.java`, `test/DelegationAndSubstituteTest.java`; sửa `domain/assignment/TaskAssignment.java` (+delegatedFromUserId, reassignTo), `application/AssignmentService.java` (reassignTask + guard + substitute-aware resolve), `api/AssignmentController.java` (+reassign), `api/dto/AssignmentDto.java` (+ReassignRequest, +delegatedFromUserId), `api/PositionController.java` (+substitute set/clear).
