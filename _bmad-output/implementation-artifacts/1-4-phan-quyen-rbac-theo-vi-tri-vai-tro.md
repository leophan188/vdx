# Story 1.4: Phân quyền RBAC theo vị trí → vai trò

Status: done

## Story

As a **quản trị**,
I want **định nghĩa vai trò (kèm quyền) và gán vai trò cho VỊ TRÍ**,
so that **người đang giữ vị trí tự nhận đúng quyền, đổi người giữ thì kế thừa quyền — quyền resolve qua vị trí, không gán trực tiếp cho người (AD-9)**.

## Acceptance Criteria

1. **Given** vai trò định nghĩa tập quyền, **When** gán vai trò cho một vị trí, **Then** người đang giữ vị trí nhận đúng quyền của vai trò đó. _(FR-C06)_
2. **Given** một vị trí có vai trò, **When** đổi người giữ vị trí, **Then** người mới **tự kế thừa** quyền (không gán lại). _(FR-C06)_
3. **Given** người dùng đăng nhập, **When** dựng authorities, **Then** authorities = vai trò tài khoản (legacy) ∪ vai trò từ các vị trí đang giữ ∪ quyền của các vai trò đó.
4. **Given** giao diện, **When** đăng nhập, **Then** menu/khu chức năng **lọc theo vai trò** (chỉ ADMIN thấy khu quản trị). _(FR-C06)_
5. **Given** mọi thay đổi vai trò/gán vai trò, **When** thực hiện, **Then** ghi audit (AD-6).

## Tasks / Subtasks

- [ ] **Task 1 — Domain** (AC:1): `Role` (code PK, name, permissions Set<String>); `PositionRole` (positionId, roleCode) composite.
- [ ] **Task 2 — Service** (AC:1,2,3,5): RoleService (tạo vai trò, liệt kê, gán/gỡ vai trò cho vị trí, `permissionsForUser` resolve qua vị trí đang giữ). Audit.
- [ ] **Task 3 — Resolve authorities** (AC:2,3): `AppUserDetailsService` build authorities = ROLE_<accountRole> ∪ (mỗi vị trí đang giữ → ROLE_<roleCode> + permissions). Thêm `findByCurrentHolderUserId`.
- [ ] **Task 4 — REST** (AC: tất cả): `/api/v1/roles` (POST tạo, GET liệt kê) + gán vai trò cho vị trí. ROLE_ADMIN.
- [ ] **Task 5 — FE** (AC:4): trang vai trò (tạo/list); **lọc menu theo authorities** (ADMIN mới thấy khu quản trị).
- [ ] **Task 6 — Test**: BE (gán vai trò cho vị trí → người giữ có quyền; đổi người giữ kế thừa); FE (nav lọc theo role).

## Dev Notes

- **AD-9:** quyền resolve qua vị trí→vai trò, không gán trực tiếp người. Account.role giữ lại như cơ chế bootstrap (admin seed).
- **Tái dụng:** `PositionRepository` (1.3), `AuditPort`. Bảo vệ `/api/v1/roles/**` ADMIN.
- **Phạm vi:** quyền (permission) là chuỗi tự do GĐ1 (vd `USER_ADMIN`, `ORG_ADMIN`); ánh xạ quyền→chức năng chi tiết để story sau. Trọng tâm story này: cơ chế resolve.

### References
- [Source: epics.md#Story-1.4] · [Source: ARCHITECTURE-SPINE.md#AD-9]

## Dev Agent Record

### Agent Model Used
claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 14/14 pass** (2 test mới): gán vai trò cho vị trí → người giữ nhận đúng quyền; **đổi người giữ → người mới kế thừa, người cũ mất quyền** (resolve động qua vị trí, không gán lại); người không giữ vị trí → không có quyền. `AppUserDetailsService` dựng authorities = ROLE tài khoản ∪ ROLE từ vị trí ∪ permissions.
- ✅ **Frontend — build OK + Vitest 16/16 pass** (3 test mới gồm nav-filter): trang **Vai trò** (tạo vai trò + quyền, list, gán vai trò cho vị trí qua dropdown); **menu lọc theo vai trò** — chỉ ROLE_ADMIN thấy khu quản trị (AC-4).
- ✅ **Menu điều hướng** + **prefill admin/Admin@123** ở login (theo yêu cầu) — kèm CSS topbar/bảng/form bám token DESIGN.
- ✅ AC 1–5 phủ end-to-end.
- ⚠️ Follow-up: ánh xạ permission→chức năng chi tiết (GĐ1 permission là chuỗi tự do); guard route FE theo quyền (hiện chỉ ẩn menu).

### File List

Backend (mới): `domain/role/{Role,PositionRole}.java`, `infrastructure/{RoleRepository,PositionRoleRepository}.java`, `application/RoleService.java`, `api/RoleController.java`, `api/dto/RoleDto.java`; sửa `AppUserDetailsService.java` (resolve authorities), `PositionRepository.java` (+findByCurrentHolderUserId), `PositionService/PositionController` (+all), `SecurityConfig` (matcher roles), `test/RoleServiceTest.java`.

Frontend (mới): `core/role.service.ts`, `roles/{roles.ts,roles.html,roles.spec.ts}`; sửa `app.ts`/`app.html` (nav lọc role + logout), `app.routes.ts` (+/roles), `core/position.service.ts` (+all), `login/{login.ts,login.html}` (prefill), `styles.scss` (CSS), `app.spec.ts` (test nav).
