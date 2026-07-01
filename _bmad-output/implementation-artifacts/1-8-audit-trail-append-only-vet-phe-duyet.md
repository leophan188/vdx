# Story 1.8: Audit trail append-only + vết phê duyệt

Status: review

## Story

As a **kiểm toán viên**,
I want **mọi thay đổi trạng thái/dữ liệu được ghi append-only qua một cổng audit**,
so that **có vết đầy đủ, không sửa được, truy vết được các cấp phê duyệt (AD-6, NFR-13, FR-I02)**.

## Acceptance Criteria

1. **Given** một hành động làm thay đổi trạng thái/dữ liệu, **When** thực thi, **Then** một bản ghi audit (ai·làm gì·đối tượng·thời điểm·trước/sau) được ghi **qua `AuditPort`** vào bảng append-only. _(AD-6)_
2. **Given** một bản ghi audit đã ghi, **When** thử **UPDATE/DELETE**, **Then** **bị chặn** (append-only, ghi-một-lần). _(AD-6, NFR-13)_
3. **Given** Flowable history (`ACT_HI_*`), **When** cần vết nghiệp vụ, **Then** **không** dùng làm audit — mọi vết FR-I đi qua `AuditPort`. _(AD-6)_
4. **Given** một nhiệm vụ (task), **When** truy vết, **Then** lấy được **lịch sử theo thứ tự thời gian** các sự kiện (giao/uỷ quyền/chuyển tiếp/gán tạm/leo thang …) + actor + chi tiết — nền cho vết phê duyệt FR-I02. _(FR-I02)_
5. **Given** một đối tượng bất kỳ (type+id), **When** truy vết, **Then** trả về chuỗi audit của đối tượng đó theo thời gian (cho kiểm toán). _(FR-I01)_

## Tasks / Subtasks

- [ ] **Task 1 — Append-only** (AC:2): `AuditEvent` thêm guard `@PreUpdate`/`@PreRemove` ném lỗi (ghi-một-lần ở tầng JPA). Cột đã `updatable=false`; phân vùng thời gian + REVOKE UPDATE/DELETE ở DDL/vận hành để Story 5.5 (đã ghi assumption trong entity).
- [ ] **Task 2 — Query** (AC:4,5): `AuditEventRepository.findByObjectTypeAndObjectIdOrderByCreatedAtAsc`; `AuditQueryService.trail(objectType, objectId)` + `trailForTask(taskId)` (resolve `TaskAssignment` theo taskId → trail của nó).
- [ ] **Task 3 — REST** (AC:4,5): `GET /api/v1/audit?objectType=&objectId=`; `GET /api/v1/audit/task/{taskId}`. ROLE_ADMIN (kiểm toán viên dùng vai trò ADMIN GĐ1).
- [ ] **Task 4 — Test** (AC:2,4,5): DELETE một audit bị chặn; `trailForTask` trả đúng thứ tự `TASK_ASSIGNED → TASK_DELEGATE …`; `trail(type,id)` trả chuỗi của đối tượng.

## Dev Notes

- **Nền sẵn:** `AuditPort` + `JpaAuditAdapter` + `AuditEvent` (cột `updatable=false`) đã có từ 1.1; mọi story 1.1–1.7 **đã** ghi audit qua cổng này (AD-6 đã được tuân thủ xuyên suốt). Story này **đóng** lại đảm bảo: chặn sửa/xoá ở tầng app + cung cấp **truy vết** đọc.
- **AC-2 (append-only):** GĐ1 enforce ở tầng JPA bằng `@PreUpdate`/`@PreRemove`. Phân vùng thời gian (PARTITION BY created_at) + REVOKE quyền UPDATE/DELETE ở DB là **vận hành** → Story 5.5 (entity đã ghi `[ASSUMPTION]`).
- **AC-3 (Flowable history):** chưa nối engine; khi vào (Epic 2/3) **không** map `ACT_HI_*` thành audit nghiệp vụ — listener phát event → `AuditPort`. Ghi rõ để không lệch ở các story sau.
- **AC-4 (vết phê duyệt):** mỗi task có **một** `TaskAssignment` (mutate tại chỗ); mọi sự kiện vòng đời phân công của task audit cùng `objectId = TaskAssignment.id` → `trailForTask` gom đủ. Bước phê duyệt + nhận xét (comment) đầy đủ là Epic 3; story này dựng **cơ chế truy vết** + đảm bảo bất biến.
- **Tái dụng:** `AuditEventRepository`, `TaskAssignmentRepository`. Bảo vệ `/api/v1/audit/**` ROLE_ADMIN.
- **Phạm vi:** UI trang kiểm toán/timeline lộ ra ở Epic 3/4.

### References
- [Source: epics.md#Story-1.8] · [Source: ARCHITECTURE-SPINE.md#AD-6] (FR-I01, FR-I02, NFR-13)

## Dev Agent Record

### Agent Model Used
claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 29/29 pass** (3 test mới phủ AC 2,4,5):
  - `auditRecord_cannotBeDeleted_appendOnly` — DELETE một audit **bị chặn** bởi `@PreRemove` (AC-2, AD-6).
  - `trailForTask_returnsLifecycleEventsInOrder` — `trailForTask` trả `[TASK_ASSIGNED, TASK_DELEGATE]` đúng thứ tự (AC-4, FR-I02).
  - `trailByObject_returnsObjectHistoryInOrder` — `trail("Position", id)` trả `[POSITION_CREATED, POSITION_ASSIGNED]` (AC-5, FR-I01).
- ✅ **Append-only enforce ở tầng JPA:** `AuditEvent.@PreUpdate/@PreRemove` ném `UnsupportedOperationException` (cột đã `updatable=false` từ 1.1). Phân vùng thời gian + REVOKE ở DB là vận hành → Story 5.5.
- ✅ **Truy vết đọc:** `AuditQueryService.trail(type,id)` + `trailForTask(taskId)` (resolve `TaskAssignment` theo task → trail của nó); REST `GET /api/v1/audit?objectType=&objectId=`, `GET /api/v1/audit/task/{taskId}` (ROLE_ADMIN, matcher mới).
- ✅ **AC-3 (Flowable history):** ghi rõ trong Dev Notes — khi nối engine (Epic 2/3) **không** map `ACT_HI_*` thành audit nghiệp vụ; mọi vết FR-I qua `AuditPort`.
- 🔎 **Phát hiện do guard:** append-only guard bắt được `auditRepo.deleteAll()` trong `AuthAndUserAdminIntegrationTest.@BeforeEach` (vi phạm AD-6) → đã bỏ; test vốn đã đếm theo delta `before/after` nên không cần xoá.
- ⚠️ **Follow-up:** comment/nhận xét theo bước duyệt + UI timeline kiểm toán → Epic 3/4; vai trò AUDITOR riêng (hiện dùng ADMIN).

### File List

Backend (mới): `application/AuditQueryService.java`, `api/AuditController.java`, `api/dto/AuditEventDto.java`, `test/AuditTrailTest.java`; sửa `domain/audit/AuditEvent.java` (+@PreUpdate/@PreRemove guard), `infrastructure/AuditEventRepository.java` (+findByObjectTypeAndObjectIdOrderByCreatedAtAsc), `infrastructure/auth/SecurityConfig.java` (+matcher `/api/v1/audit/**`), `test/AuthAndUserAdminIntegrationTest.java` (bỏ deleteAll vi phạm append-only).
