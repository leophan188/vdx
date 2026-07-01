# Story 2.4: Versioning quy trình + publish/retire + snapshot

Status: review

## Story

As an **admin IT**,
I want **lưu quy trình có phiên bản, publish/retire, và snapshot phiên bản lúc ban hành**,
so that **sửa quy trình không làm vỡ nhiệm vụ đang chạy (AD-3, FR-A06, FR-A07)**.

## Acceptance Criteria

1. **Given** quy trình DRAFT có sơ đồ, **When** ban hành, **Then** tạo **phiên bản BẤT BIẾN** (snapshot bpmnXml + metadata), `publishedVersion` tăng, trạng thái PUBLISHED. _(FR-A06)_
2. **Given** quy trình đã ban hành, **When** sửa bản nháp rồi ban hành lại, **Then** phiên bản mới (v+1) được snapshot; **phiên bản cũ giữ nguyên nội dung** (instance đang chạy không bị ảnh hưởng — AD-3). _(AD-3)_
3. **Given** quy trình PUBLISHED, **When** ngừng dùng (retire), **Then** trạng thái RETIRED, phiên bản đang ban hành chuyển RETIRED; không khởi tạo nhiệm vụ mới. _(FR-A07)_
4. **Given** chưa có sơ đồ, **When** ban hành, **Then** bị chặn.
5. **Given** UI, **When** xem danh sách, **Then** hiển thị badge trạng thái + **"Phiên bản vX"** (UX-DR10) + nút Ban hành/Ngừng dùng; ghi audit (AD-6).

## Tasks / Subtasks

- [x] **BE domain**: `ProcessVersion` (snapshot bất biến: processId, version, bpmnXml, stepsMetaJson, status PUBLISHED/RETIRED, publishedAt/By) + repo. `ProcessDefinition` thêm `publishedVersion`.
- [x] **BE service/REST**: `ProcessService.publish` (snapshot v=publishedVersion+1, status PUBLISHED), `retire` (version+process → RETIRED), `listVersions`; `POST /{id}/publish`, `POST /{id}/retire`, `GET /{id}/versions`. Audit. Test.
- [x] **FE**: `process.service` publish/retire/versions; màn `processes/` cột "Phiên bản vX" + nút **🚀 Ban hành** / **⏸ Ngừng dùng** (confirm-dialog) + toast.

## Dev Notes

- **Snapshot bất biến (AD-3):** `ProcessVersion` chỉ INSERT, cột `updatable=false`. Ban hành lại tạo bản mới, không sửa bản cũ → instance giữ phiên bản đã snapshot. Verify live: publish v1 → sửa nháp → publish v2 → versions=[v2,v1], nội dung v1 giữ nguyên; retire → RETIRED.
- **"Bản mới nhất khởi tạo nhiệm vụ" (FR-A07):** phiên bản PUBLISHED có version cao nhất là bản hoạt động. **Khởi tạo nhiệm vụ thực tế từ bản publish = Epic 3 (Story 3.1)** — chưa nối Flowable; 2.4 mới lo định nghĩa + versioning.
- **Tái dụng:** design-system v2 (data-grid, confirm-dialog, toast, status-badge). BE theo [[bpm-dev-conventions]].

### References
- [Source: epics.md#Story-2.4] · [Source: ARCHITECTURE-SPINE.md#AD-3]

## Dev Agent Record

### Completion Notes List
- ✅ BE `mvn test` **40/40** (+2 test: snapshot bất biến khi republish, chặn publish khi chưa có sơ đồ + retire). FE **23/23**, build clean. Verify live: DRAFT→PUBLISHED v1→v2 (2 snapshot giữ nguyên)→RETIRED.

### File List
Backend (mới): `domain/process/ProcessVersion.java`, `infrastructure/ProcessVersionRepository.java`; sửa `domain/process/ProcessDefinition.java` (+publishedVersion), `application/ProcessService.java` (+publish/retire/listVersions, +versionRepo, delete dọn version), `api/ProcessController.java` (+publish/retire/versions), `api/dto/ProcessDto.java` (+publishedVersion, +Version record), `test/ProcessServiceTest.java`.
Frontend: sửa `core/process.service.ts` (+publish/retire/versions, publishedVersion), `processes/{processes.ts,processes.html}` (cột phiên bản + nút ban hành/ngừng dùng + confirm).
