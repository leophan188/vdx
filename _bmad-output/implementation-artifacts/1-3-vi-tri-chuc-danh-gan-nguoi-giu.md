# Story 1.3: Vị trí/chức danh & gán người giữ

Status: done

## Story

As a **quản trị**,
I want **tạo vị trí trong đơn vị và gán đúng một người giữ mỗi vị trí tại một thời điểm**,
so that **việc giao theo vị trí (late-binding) luôn giải về đúng một người, làm nền cho phân công AD-14**.

## Acceptance Criteria

1. **Given** một đơn vị, **When** tạo vị trí (chức danh) trong đơn vị, **Then** vị trí thuộc đúng đơn vị và ban đầu chưa có người giữ. _(FR-C02)_
2. **Given** một vị trí, **When** gán người giữ, **Then** vị trí có tối đa **một người giữ hiện hành**. _(FR-C02)_
3. **Given** một vị trí đang có người giữ, **When** gán người mới, **Then** nhiệm kỳ người cũ **kết thúc (lưu lịch sử, endedAt)** và người mới thành hiện hành. _(FR-C02)_
4. **Given** mọi thay đổi (tạo vị trí, gán/đổi người), **When** thực hiện, **Then** ghi audit qua AuditPort. _(AD-6)_
5. **Given** một đơn vị còn vị trí, **When** xóa đơn vị, **Then** bị từ chối (hoàn tất ràng buộc AC-5 của Story 1.2). _(FR-C01)_

## Tasks / Subtasks

- [ ] **Task 1 — Domain** (AC: 1,2,3): `Position` (id, title, orgUnitId, currentHolderUserId nullable); `PositionAssignment` (id, positionId, userId, assignedAt, endedAt nullable — hiện hành = endedAt null).
- [ ] **Task 2 — Service** (AC: 1,2,3,4): createPosition, assignHolder (kết thúc nhiệm kỳ hiện hành → tạo nhiệm kỳ mới → cập nhật currentHolder), listByOrgUnit, currentHolder. Audit mọi mutation.
- [ ] **Task 3 — Ràng buộc xóa đơn vị** (AC: 5): cổng `OrgUnitUsageGuard` (port ở domain/org); `PositionUsageGuard` implement (đơn vị còn vị trí → in-use). `OrgUnitService.delete` tham vấn mọi guard (tôn trọng AD-12: org định nghĩa port, position phụ thuộc port của core).
- [ ] **Task 4 — REST** (AC: tất cả): `/api/v1/positions` (POST tạo, GET theo đơn vị, PATCH gán người). Chỉ ROLE_ADMIN.
- [ ] **Task 5 — FE**: chọn đơn vị → liệt kê vị trí + tạo vị trí + gán người (dropdown user).
- [ ] **Task 6 — Test**: BE (gán kết thúc nhiệm kỳ cũ, một người hiện hành, audit, xóa đơn vị bị chặn khi còn vị trí); FE component.

## Dev Notes

- **Tái dụng:** `UserAccountRepository` (Story 1.1) cho danh sách user; `OrgUnitRepository`; `AuditPort`. Bảo vệ `/api/v1/positions/**` ROLE_ADMIN.
- **AD-14 chuẩn bị:** `currentHolderUserId` là điểm late-binding để phân công bước "theo vị trí" resolve về người — dùng ở Epic 2/3.
- **AD-12:** không để `OrgUnitService` import trực tiếp `PositionRepository`; dùng port `OrgUnitUsageGuard`.
- **Phạm vi:** một người có thể giữ nhiều vị trí (không chặn); một vị trí chỉ một người hiện hành.

### References

- [Source: epics.md#Story-1.3] · [Source: ARCHITECTURE-SPINE.md#AD-7, #AD-12, #AD-14]

## Dev Agent Record

### Agent Model Used

claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 12/12 pass** (2 test mới): gán người mới đóng nhiệm kỳ cũ (lưu lịch sử `endedAt`) + giữ đúng MỘT nhiệm kỳ hiện hành; xóa đơn vị bị chặn khi còn vị trí (qua port `OrgUnitUsageGuard` — tôn trọng AD-12, không để org import position). Audit mọi mutation.
- ✅ **Frontend — build OK + Vitest 12/12 pass** (3 test mới): chọn đơn vị → liệt kê/tạo vị trí + gán người (dropdown user); route `/positions`.
- ✅ **AC 1–5 phủ end-to-end.** AC-5 (chặn xóa đơn vị còn vị trí) hoàn tất ràng buộc bỏ ngỏ từ Story 1.2.
- 📌 **Ghi chú:** đổi tên bảng `position`→`org_position` (tránh từ khóa SQL). `currentHolderUserId` là điểm late-binding sẵn cho phân công AD-14 (Epic 2/3).
- ⚠️ Follow-up: PrimeNG; FE chưa có gỡ-người-giữ/đổi tên vị trí (BE có thể bổ sung khi cần).

### File List

Backend (mới):
- `backend/src/main/java/com/bpm/domain/position/{Position,PositionAssignment}.java`
- `backend/src/main/java/com/bpm/domain/org/OrgUnitUsageGuard.java` (port)
- `backend/src/main/java/com/bpm/infrastructure/{PositionRepository,PositionAssignmentRepository,PositionUsageGuard}.java`
- `backend/src/main/java/com/bpm/application/PositionService.java`
- `backend/src/main/java/com/bpm/api/PositionController.java` + `api/dto/PositionDto.java`
- `backend/src/main/java/com/bpm/application/OrgUnitService.java` (inject guards), `infrastructure/auth/SecurityConfig.java` (matcher positions)
- `backend/src/test/java/com/bpm/PositionServiceTest.java`

Frontend (mới):
- `frontend/src/app/core/position.service.ts`
- `frontend/src/app/positions/{positions.ts,positions.html,positions.spec.ts}`
- `frontend/src/app/app.routes.ts` (route /positions)
