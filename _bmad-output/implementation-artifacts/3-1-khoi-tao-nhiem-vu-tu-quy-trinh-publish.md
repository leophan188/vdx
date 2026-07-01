# Story 3.1: Khởi tạo nhiệm vụ từ quy trình publish

Status: review

## Story
As a **hệ thống**, I want **khởi tạo nhiệm vụ từ quy trình đã publish + dữ liệu form bước 1**,
so that **việc xuất hiện đúng người thực hiện, snapshot phiên bản (AD-3, FR-D01, FR-D08)**.

## Acceptance Criteria
1. **Given** quy trình đã publish, **When** khởi tạo, **Then** tạo **instance Flowable** + lưu app `WorkflowInstance` (snapshot phiên bản process + stepsMeta). _(AD-1, AD-3)_
2. **Given** instance vừa tạo, **When** sinh việc bước đầu, **Then** việc **gán đúng người thực hiện** qua lõi phân công (AssignmentPort mirror Flowable assignee). _(AD-14)_
3. **Given** quy trình **chưa publish**, **When** khởi tạo, **Then** **bị chặn**.

## Tasks / Subtasks
- [x] **Flowable engine**: thêm `flowable-spring-boot-starter-process@7.1.0` (AD-1); config `database-schema-update`, tắt async, `check-process-definitions=false`. **Boot xanh, 45→47 test pass.**
- [x] **`WorkflowInstance`** (snapshot processVersion + stepsMeta + flowableInstanceId) + repo.
- [x] **`WorkflowService.start`**: lấy phiên bản PUBLISHED → **deploy BPMN → start instance** (variables = form data) → lưu WorkflowInstance + audit `INSTANCE_STARTED`.
- [x] **Phân công việc bước đầu**: `assignActiveTasks` đọc stepsMeta → POSITION qua **`AssignmentService.assignTaskToPosition`** (snapshot người + mirror) · USER → assignee trực tiếp · ROLE → candidate-group.
- [x] **`FlowableMirrorAssignmentAdapter` HẾT placeholder**: nay `taskService.setAssignee/addCandidateGroup` (an toàn nếu task không tồn tại) → khép vòng AD-14.
- [x] REST `POST /api/v1/instances` {processId, formData} + `GET ?processId=`. Test.

## Dev Notes
- **Lõi phân công Epic 1 nay hoạt động thật:** `assignTaskToPosition(flowableTaskId, positionId)` resolve người giữ → `TaskAssignment` (nguồn sự thật) → `AssignmentPort.mirrorAssignee` → adapter set Flowable assignee.
- **Phân công bước KẾ (khi việc hoàn thành → việc mới):** GĐ này gán việc đang-mở lúc start; gán động khi luồng tiến (TASK_CREATED listener) làm ở Story 3.4/3.5. Định tuyến gateway theo điều kiện (map stepsMeta.condition → Flowable `conditionExpression`) = 3.5.
- Verify live: publish demo `demo-tao-duyet` → `POST /instances` `201` → flowableInstance v1 → việc bước đầu gán Chuyên viên (Nguyễn Văn A).

### References
- [Source: epics.md#Story-3.1] · [Source: ARCHITECTURE-SPINE.md#AD-1,AD-3,AD-14]

## File List
BE (mới): `domain/workflow/WorkflowInstance.java`, `infrastructure/WorkflowInstanceRepository.java`, `application/WorkflowService.java`, `api/WorkflowController.java`, `api/dto/WorkflowDto.java`, `test/WorkflowServiceTest.java`; sửa `pom.xml` (+flowable), `application.yml` (+flowable cfg), `SecurityConfig` (+/instances), `infrastructure/FlowableMirrorAssignmentAdapter.java` (set assignee thật).
