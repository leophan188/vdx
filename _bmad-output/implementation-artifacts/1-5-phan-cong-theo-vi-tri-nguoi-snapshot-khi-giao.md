# Story 1.5: Phân công theo vị trí/người + snapshot khi giao

Status: review

## Story

As a **hệ thống**,
I want **khi giao việc thì snapshot người được resolve vào task và coi bảng app là nguồn sự thật phân công**,
so that **đổi cơ cấu sau đó không cướp việc đang chạy và không lệch với Flowable assignee (AD-7, AD-14)**.

## Acceptance Criteria

1. **Given** một bước phân công theo vị trí, **When** việc được giao, **Then** `TASK_ASSIGNMENT` lưu **người + vị trí tại thời điểm giao** (snapshot). _(FR-C03, AD-14)_
2. **Given** việc vừa được giao, **When** ghi snapshot, **Then** Flowable assignee được **mirror qua một `AssignmentPort` duy nhất, trong cùng giao dịch** với ghi app. _(AD-14)_
3. **Given** một việc đang chạy đã snapshot người A, **When** đổi người giữ vị trí sang B, **Then** việc đang chạy **vẫn ở A** (không cướp việc). _(FR-C05)_
4. **Given** đã đổi người giữ sang B, **When** giao **việc mới** theo cùng vị trí, **Then** việc mới resolve về **B** (người giữ hiện hành). _(AD-7)_
5. **Given** một vị trí **đang trống** (chưa có người giữ), **When** việc tới, **Then** không để task mất: app set cờ `UNASSIGNED` + candidate-group = đơn vị của vị trí; port mirror candidate-group thay vì assignee. _(FR-C08, AD-14)_
6. **Given** mọi lần giao/đổi assignee, **When** thực hiện, **Then** ghi audit qua `AuditPort` (AD-6).

## Tasks / Subtasks

- [ ] **Task 1 — Domain** (AC:1,5): `TaskAssignment` (id, taskId, positionId, assigneeUserId nullable, candidateGroupOrgUnitId nullable, status, assignedAt) — snapshot **bất biến**; `AssignmentStatus{ASSIGNED,UNASSIGNED}`; `AssignmentPort` (cổng mirror sang Flowable: `mirrorAssignee`, `mirrorCandidateGroup`).
- [ ] **Task 2 — Infra** (AC:2): `TaskAssignmentRepository`; `FlowableMirrorAssignmentAdapter` implements `AssignmentPort` — **placeholder no-op/log** cho tới khi Epic 2/3 nối Flowable engine (đúng AD-14: app là nguồn sự thật, Flowable chỉ mirror).
- [ ] **Task 3 — Service** (AC:1–6): `AssignmentService.assignTaskToPosition(taskId, positionId, actor)` — resolve người giữ hiện hành qua `PositionRepository`; có người → snapshot `ASSIGNED` + `port.mirrorAssignee`; trống → `UNASSIGNED` + candidate = orgUnit + `port.mirrorCandidateGroup`; toàn bộ `@Transactional` (app + mirror **một giao dịch**); audit.
- [ ] **Task 4 — REST** (AC:1,5): `/api/v1/assignments` POST (giao việc cho vị trí) + GET `/by-task/{taskId}`; ROLE_ADMIN (system-facing GĐ1).
- [ ] **Task 5 — Test** (AC:1–5): snapshot giữ A khi đổi người giữ; việc mới resolve B; vị trí trống → UNASSIGNED + candidate; **verify port mirror được gọi** trong giao dịch giao việc.

## Dev Notes

- **AD-14 — app là nguồn sự thật, Flowable mirror:** `TASK_ASSIGNMENT` (snapshot người+vị trí lúc giao) là nguồn sự thật; Flowable assignee chỉ mirror, set **qua một `AssignmentPort` duy nhất**, ghi app + Flowable **trong một giao dịch**. Mọi reassign/uỷ quyền (Story 1.6) đi qua port này.
- **Flowable chưa nối:** engine sẽ thêm ở Epic 2/3 (xem `pom.xml` note). Story này định nghĩa **port + adapter no-op** để lõi phân công sẵn sàng; khi engine vào chỉ thay adapter, không đụng service. `taskId` là chuỗi opaque (sau này = Flowable task id).
- **Snapshot bất biến:** `TaskAssignment.assigneeUserId` chốt lúc tạo; **không code path nào sửa** nó khi đổi người giữ. `PositionService.assignHolder` chỉ đụng `Position`/`PositionAssignment` → snapshot tự được bảo toàn (AC-3).
- **FR-C08 (vị trí trống):** story này chỉ chốt cờ `UNASSIGNED` + candidate-group để **không mất task**; hàng đợi/UI cảnh báo đầy đủ ở Story 1.7.
- **Tái dụng:** `PositionRepository.currentHolder`, `OrgUnitRepository`, `AuditPort`. Bảo vệ `/api/v1/assignments/**` ROLE_ADMIN.
- **Phạm vi:** chưa có UI — story "As a hệ thống", phân công lộ ra người dùng ở Epic 3 (hộp thư "Việc của tôi"). FE để Epic 3.

### References
- [Source: epics.md#Story-1.5] · [Source: ARCHITECTURE-SPINE.md#AD-7] · [Source: ARCHITECTURE-SPINE.md#AD-14]

## Dev Agent Record

### Agent Model Used
claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 18/18 pass** (4 test mới phủ AC 1–5):
  - `assign_snapshotsHolder_andMirrorsAssignee_inOneTx` — snapshot người+vị trí (AC-1) + **verify `port.mirrorAssignee` được gọi** (AC-2, AD-14).
  - `changingHolder_doesNotStealRunningTask_butNewTaskResolvesNewHolder` — đổi người giữ A→B: việc đang chạy **giữ A** (AC-3, FR-C05), việc mới **resolve B** (AC-4).
  - `emptyPosition_yieldsUnassignedWithCandidateGroup` — vị trí trống → `UNASSIGNED` + candidate-group đơn vị, không mất task (AC-5, FR-C08) + verify `port.mirrorCandidateGroup`.
  - `doubleAssign_sameTask_rejected` — chống giao trùng một task.
- ✅ **Lõi phân công (AD-14):** `AssignmentService.assignTaskToPosition` resolve người giữ hiện hành → snapshot bất biến vào `TASK_ASSIGNMENT` (nguồn sự thật) → mirror Flowable qua **một `AssignmentPort`** — tất cả `@Transactional` (app + mirror cùng giao dịch). Audit `TASK_ASSIGNED`/`TASK_UNASSIGNED`.
- ✅ **Snapshot bất biến tự bảo toàn:** không code path nào sửa `assigneeUserId` khi đổi người giữ; `PositionService.assignHolder` chỉ đụng `Position`/`PositionAssignment`.
- ✅ **REST** `/api/v1/assignments` POST + GET `/by-task/{id}` (ROLE_ADMIN, system-facing).
- ⚠️ **Follow-up:** `FlowableMirrorAssignmentAdapter` hiện là **placeholder log** — Epic 2/3 nối engine sẽ thay thân hàm bằng `setAssignee`/`addCandidateGroup` trong cùng tx (không đụng service). UNASSIGNED queue + UI cảnh báo đầy đủ ở Story 1.7. Uỷ quyền/chuyển tiếp/thay thế (FR-C04) đi qua cùng `AssignmentPort` ở Story 1.6. Chưa có FE — phân công lộ ra ở Epic 3 (hộp thư).

### File List

Backend (mới): `domain/assignment/{TaskAssignment,AssignmentStatus,AssignmentPort}.java`, `infrastructure/{TaskAssignmentRepository,FlowableMirrorAssignmentAdapter}.java`, `application/AssignmentService.java`, `api/AssignmentController.java`, `api/dto/AssignmentDto.java`, `test/AssignmentServiceTest.java`; sửa `infrastructure/auth/SecurityConfig.java` (matcher `/api/v1/assignments/**` ROLE_ADMIN).
