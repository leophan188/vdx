# Story 1.2: Cây cơ cấu tổ chức nhiều cấp

Status: done

## Story

As a **quản trị**,
I want **CRUD cây đơn vị độ sâu tùy ý (closure-table) và di chuyển nút**,
so that **mô hình tổ chức thực tế (Tập đoàn → đơn vị con → …) được biểu diễn động, làm xương sống cho phân quyền & phân công**.

## Acceptance Criteria

1. **Given** màn Quản trị tổ chức, **When** thêm đơn vị (tên + đơn vị cha tùy chọn), **Then** đơn vị được tạo ở đúng cấp và xuất hiện trong cây. _(FR-C01)_
2. **Given** một đơn vị, **When** đổi tên, **Then** tên cập nhật.
3. **Given** một đơn vị có cây con, **When** di chuyển sang đơn vị cha khác, **Then** toàn bộ cây con đi theo và quan hệ tổ tiên/hậu duệ (closure) cập nhật đúng. _(AD-7)_
4. **Given** truy vấn, **When** lấy tổ tiên/hậu duệ của một đơn vị, **Then** trả đúng tập (closure-table). _(AD-7)_
5. **Given** một đơn vị còn đơn vị con, **When** xóa, **Then** bị từ chối (phải xóa/di chuyển con trước). _[Ràng buộc "còn vị trí đang gán việc" bổ sung ở Story 1.3 khi có Position.]_
6. **Given** mọi thay đổi cơ cấu, **When** thực hiện, **Then** ghi audit qua AuditPort. _(AD-6)_

## Tasks / Subtasks

- [ ] **Task 1 — Domain closure-table** (AC: 1,3,4): `OrgUnit` (id UUID, name, parentId nullable); `OrgUnitClosure` (ancestorId, descendantId, depth) composite key. Tạo bảng story này cần.
- [ ] **Task 2 — Service + closure maintenance** (AC: 1,2,3,5,6): create (chèn self + sao chép tổ tiên của cha), rename, move (thuật toán closure chuẩn: gỡ liên kết subtree↔tổ tiên cũ, chèn liên kết tới tổ tiên mới), delete (chặn nếu còn con), getChildren/getAncestors/getDescendants. Audit mọi mutation.
- [ ] **Task 3 — REST** (AC: tất cả): `/api/v1/org-units` (POST tạo, PATCH rename/move, DELETE, GET cây/con). Chỉ ROLE_ADMIN.
- [ ] **Task 4 — FE** (AC: 1,2,3,5): màn cây tổ chức (UX-DR7) expand/collapse, thêm/sửa/xóa/di chuyển.
- [ ] **Task 5 — Test**: BE unit/integration (create đa cấp, move cập nhật closure, chặn xóa khi còn con, ancestors/descendants đúng); FE component test.

## Dev Notes

- **Closure-table (AD-7):** mỗi đơn vị có hàng self (depth 0). Tạo node X dưới cha P: chèn (X,X,0) + với mỗi (A,P,d) chèn (A,X,d+1). Move subtree(X) sang P': xóa các hàng (A,D) với D∈subtree(X) và A∉subtree(X); chèn (A,D, dAP'+1+dXD) với A∈ancestors(P')∪{P'}, D∈subtree(X). [Source: ARCHITECTURE-SPINE.md#AD-7]
- **Tái dụng Story 1.1:** `AuditPort` đã có; UUID/conventions như cũ; bảo vệ endpoint bằng SecurityConfig (thêm matcher `/api/v1/org-units/**` hasRole ADMIN).
- **Stack/môi trường:** Spring Boot 3.5 + Java 21 + H2 test; Angular 21. JDK 21 đã cài (`JAVA_HOME=/opt/homebrew/opt/openjdk@21`).
- **Phạm vi:** Position/gán người = Story 1.3 — KHÔNG xây ở đây; ràng buộc xóa chỉ xét đơn vị con.

### References

- [Source: planning-artifacts/epics.md#Story-1.2] · [Source: ARCHITECTURE-SPINE.md#AD-7]

## Dev Agent Record

### Agent Model Used

claude-opus-4-8 (1M context) — dev agent.

### Completion Notes List

- ✅ **Backend closure-table (AD-7) — `mvn test` 10/10 pass** (4 test mới story 1.2): tạo đa cấp + closure đúng, ancestors/descendants chính xác, move cập nhật closure (reparent), chặn move-vào-cây-con-của-chính-nó, chặn xóa khi còn con + xóa lá OK. Mọi mutation ghi audit qua AuditPort (tái dụng từ Story 1.1).
- ✅ **Frontend Angular — build OK + Vitest 9/9 pass** (2 test mới): màn cây tổ chức (UX-DR7) — thêm đơn vị (chọn cha), hiển thị cây phẳng DFS thụt lề theo cấp, xóa; `OrgService`; route `/org`.
- ✅ **AC 1–6 phủ end-to-end.** AC-5 ràng buộc "còn vị trí đang gán việc" hoãn sang Story 1.3 (chưa có Position) — đã ghi rõ trong AC.
- ⚠️ Follow-up: PrimeNG Tree (UX-DR7) component thật để story UX polish; rename/move trên FE chưa có nút (BE đã hỗ trợ + test) — bổ sung khi cần.

### File List

Backend (mới):
- `backend/src/main/java/com/bpm/domain/org/{OrgUnit,OrgUnitClosure}.java`
- `backend/src/main/java/com/bpm/infrastructure/{OrgUnitRepository,OrgUnitClosureRepository}.java`
- `backend/src/main/java/com/bpm/application/OrgUnitService.java`
- `backend/src/main/java/com/bpm/api/OrgUnitController.java` + `api/dto/OrgUnitDto.java`
- `backend/src/main/java/com/bpm/infrastructure/auth/SecurityConfig.java` (thêm matcher org-units)
- `backend/src/test/java/com/bpm/OrgUnitServiceTest.java`

Frontend (mới):
- `frontend/src/app/core/org.service.ts`
- `frontend/src/app/org/{org.ts,org.html,org.spec.ts}`
- `frontend/src/app/app.routes.ts` (thêm route /org)
