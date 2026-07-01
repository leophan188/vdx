# Story 2.10: Versioning form + snapshot mọi node

Status: review

## Story
As an **admin IT**, I want **form có phiên bản và mọi node tạo snapshot phiên bản form**,
so that **publish form mới không vỡ instance/sub-task đang chạy (AD-3, FR-B09)**.

## Acceptance Criteria
1. **Given** form đang dùng, **When** publish phiên bản mới, **Then** node giữ snapshot `formVersion` lúc instantiate; dữ liệu đã nhập vẫn hợp lệ; node mới dùng bản mới nhất. _(AD-3)_
2. **Given** form, **When** ban hành/ngừng dùng, **Then** trạng thái + phiên bản cập nhật; ghi audit.

## Dev Notes
- Đối xứng **Story 2.4** (process versioning): `FormVersion` (INSERT-only, `updatable=false`) + `FormDefinition.publishedVersion` + `FormService.publish/retire/listVersions` + REST `/forms/{id}/publish|retire|versions` + FE nút Ban hành/Ngừng dùng + cột "Phiên bản vX".
- **Snapshot per-node lúc instantiate** = runtime **Epic 3** (Story 3.1 lưu `formDefinitionVersionId` trên task). 2.10 lo cơ chế phiên bản + snapshot bất biến.
- Verify live: publish v1 → sửa → publish v2 → versions=[v2,v1] (bất biến) → retire. BE **45/45**, FE **23/23**.

## File List
BE (mới): `domain/form/FormVersion.java`, `infrastructure/FormVersionRepository.java`; sửa `FormDefinition`(+publishedVersion), `FormService`(+publish/retire/listVersions), `FormController`(+endpoints), `FormDto`(+publishedVersion,+Version), `FormServiceTest`.
FE: `core/form.service.ts`(+publish/retire/versions), `forms/{forms.ts,forms.html}`(badge+phiên bản+nút ban hành/ngừng dùng).
