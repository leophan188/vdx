# Story 2.1: Process designer kéo-thả + metadata bước

Status: review

## Story

As an **admin IT**,
I want **thiết kế quy trình trên canvas kéo-thả (bpmn-js) và khai báo metadata mỗi bước**,
so that **tạo quy trình mới mà không cần viết code (FR-A01, FR-A03, NFR-09)**.

## Acceptance Criteria

1. **Given** canvas designer (UX-DR5), **When** kéo bước/gateway/luồng nối, **Then** quy trình lưu được dưới dạng **định nghĩa BPMN (XML)**. _(FR-A01)_
2. **Given** một bước (userTask), **When** chọn bước và khai báo **metadata** (vị trí/vai trò đảm nhiệm, hạn SLA, tập hành động cho phép), **Then** metadata lưu kèm định nghĩa (JSON theo elementId). _(FR-A03)_
3. **Given** thao tác thiết kế/lưu, **When** thực hiện, **Then** **không yêu cầu build lại/deploy** — lưu tức thì qua REST, cấu hình động. _(NFR-09)_
4. **Given** danh sách quy trình, **When** quản trị, **Then** CRUD quy trình (tạo/đổi tên/xóa) + trạng thái `DRAFT` (publish/retire để Story 2.4) theo design-system. _(FR-A01)_
5. **Given** mọi thay đổi quy trình, **When** thực hiện, **Then** ghi audit qua `AuditPort` (AD-6).

## Tasks / Subtasks

- [ ] **Task 1 — Backend domain** (AC:1,2): `ProcessDefinition` (id, processKey, name, status `DRAFT/PUBLISHED/RETIRED`, `bpmnXml` text, `stepsMetaJson` text, version, createdAt, updatedAt). Repo.
- [ ] **Task 2 — Backend service/REST** (AC:1–5): `ProcessService` (create, list, get, `saveDesign(id, bpmnXml, stepsMetaJson)`, rename, delete; audit). `ProcessController` `/api/v1/processes` (POST/GET/GET{id}/PATCH{id}/PUT{id}/design/DELETE). ROLE_ADMIN. Test.
- [ ] **Task 3 — FE danh sách** (AC:4): `process.service.ts` + màn `processes/` (design-system grid: tạo/đổi tên/xóa + badge trạng thái + nút "Thiết kế"). Nav nhóm "Cấu hình quy trình" + route.
- [ ] **Task 4 — FE designer** (AC:1–3): route lazy `processes/:id` dùng **bpmn-js `BpmnModeler`** trên `.canvas`; load/save XML; panel thuộc tính bên phải cho userTask đang chọn (vị trí, SLA giờ, hành động) lưu vào `stepsMeta` theo elementId; nút "Lưu" → `PUT .../design`.

## Dev Notes

- **bpmn-js 18.x** đã cài. Modeler gắn vào container (`AfterViewInit`), `importXML`/`saveXML({format:true})`. CSS `diagram-js.css` + `bpmn-js.css` + `bpmn-font/css/bpmn.css` thêm vào `angular.json` styles. Route designer **lazy** để không phình main bundle.
- **Metadata bước** GĐ này: `position` (vị trí đảm nhiệm), `slaHours`, `actions` (mảng). Lưu `stepsMetaJson` = `{ [bpmnElementId]: {position, slaHours, actions} }`. Form gắn/điều kiện/notification → Story 2.6–2.9.
- **Versioning/publish/retire** + snapshot → **Story 2.4** (giờ chỉ `DRAFT`). Flowable engine thực thi → Epic 3 (chưa nối ở 2.1; chỉ lưu định nghĩa).
- **Tái dụng:** design-system v2 (page-header, data-grid, modal, confirm, toast, `.canvas`) — xem [[bpm-design-system]]. Backend theo [[bpm-dev-conventions]] (AuditPort, DTO record, guard).

### References
- [Source: epics.md#Story-2.1] · [Source: ARCHITECTURE-SPINE.md] (AD-1 BPMN/Flowable, AD-4 JSON data) · UX-DR5 canvas.

## Dev Agent Record

### Agent Model Used
claude-opus-4-8 — dev agent.

### Completion Notes List

- ✅ **Backend — `mvn test` 38/38 pass** (3 test mới `ProcessServiceTest`): create→saveDesign lưu đúng XML + metadata JSON; chặn trùng `processKey`; rename + delete. `ProcessDefinition` (DRAFT, version=1, bpmnXml/stepsMetaJson TEXT) + `ProcessService` (create/list/get/rename/saveDesign/delete, audit AD-6) + `ProcessController` `/api/v1/processes` (POST/GET/GET{id}/PATCH{id}/PUT{id}/design/DELETE, ROLE_ADMIN). Verify live: 201/200/204, get giữ XML+meta.
- ✅ **FE danh sách** `processes/`: design-system grid (mã/tên/badge trạng thái/phiên bản) + tạo (→ mở thẳng designer)/đổi tên/xóa + toast. Nav nhóm **"Cấu hình quy trình" → Quy trình**.
- ✅ **FE designer** `processes/designer/` (route **lazy** `/processes/:id`, chunk 572 kB tách riêng): **bpmn-js `BpmnModeler`** trên `.designer__canvas` (palette kéo-thả + context-pad sẵn có); panel thuộc tính phải cho task đang chọn — **tên bước, vị trí đảm nhiệm (từ PositionService), SLA giờ, tập hành động** (8 hành động) lưu vào `stepsMeta` theo elementId; nút **Lưu** → `saveXML({format:true})` + JSON → `PUT .../design`. Canvas nền trắng kể cả dark mode.
- ✅ **Redesign UX cấu hình bước (2026-06-25):** canvas **full chiều ngang**; chọn bước → nút **"⚙ Cấu hình bước"** (hoặc nhấp đúp) mở **modal `<app-tabs>` 3 tab**:
  - **Người thực hiện**: phân công theo **Vai trò / Vị trí-Chức danh / Người cụ thể** (`assigneeType` + `assigneeId`, dữ liệu từ Role/Position/User service) + SLA + tập hành động.
  - **Khai báo metadata**: danh sách trường **có kiểu** — text/number/date/**dropdown**/**radio**/checkbox/**richtext**, có `required`; dropdown/radio chọn **nguồn lựa chọn**: *Tự khai báo* (STATIC, nhập options) hoặc *Theo danh mục dùng chung* (CATALOG, nhập mã danh mục — quản lý danh mục là story sau).
  - **Thông báo**: 3 danh sách người nhận **Email / Noti app / CC**, mỗi người nhận theo *Người thực hiện bước / Vai trò / Vị trí / Người cụ thể* + **Tiêu đề** + **Nội dung**.
  - **Schema `stepsMeta[elementId]`** (Epic 3 đọc để thực thi): `{assigneeType, assigneeId, slaHours, actions[], fields[{key,label,type,required,optionSource,options|catalog}], notify{emailTo[],appTo[],cc[] (mỗi recipient {type,id}), subject, content}}`.
  - **DemoSeeder** cập nhật shape mới: Task_Tao (4 field text/dropdown/date/richtext + notify), Task_Duyet (radio/text + notify) — verify live.
- ⚠️ **Phạm vi sau:** loại luồng/điều kiện gateway (2.2), tập hành động map Flowable (2.3), versioning/publish/retire + snapshot (2.4), SLA runtime (2.5), form builder (2.6+). **Flowable engine chưa nối** — 2.1 chỉ lưu định nghĩa (thực thi ở Epic 3). bpmn-js properties-panel chính thức (nặng) chưa dùng — panel tự viết gọn.

### File List

Backend (mới): `domain/process/{ProcessDefinition,ProcessStatus}.java`, `infrastructure/ProcessDefinitionRepository.java`, `application/ProcessService.java`, `api/ProcessController.java`, `api/dto/ProcessDto.java`, `test/ProcessServiceTest.java`; sửa `infrastructure/auth/SecurityConfig.java` (matcher `/api/v1/processes/**`).

Frontend (mới): `core/process.service.ts`, `processes/{processes.ts,processes.html}`, `processes/designer/{designer.ts,designer.html}`, `bpmn-js.d.ts`; sửa `app.routes.ts` (+processes, +lazy designer), `app.html` (nav nhóm Cấu hình quy trình), `app.spec.ts` (nav=7), `angular.json` (+bpmn-js CSS). Thêm dep `bpmn-js@18`.
