# Story 2.3: Tập hành động cho phép trên bước

Status: review

## Story
As an **admin IT**, I want **cấu hình tập hành động mỗi bước (Ghi lại/Sửa/Hủy/Trình duyệt/Phê duyệt/Trả lại/Từ chối/Uỷ quyền)**,
so that **hành vi cho phép của từng bước khớp nghiệp vụ (FR-A04)**.

## Acceptance Criteria
1. **Given** một bước, **When** chọn tập hành động cho phép, **Then** lưu vào định nghĩa bước. _(FR-A04)_
2. **Given** thực thi, **When** tới bước, **Then** chỉ hành động đã cấu hình hiển thị cho đúng vai trò; mỗi hành động map hành vi Flowable. _(runtime → Epic 3)_

## Dev Notes
- **Phần CẤU HÌNH đã hoàn tất ở Story 2.1**: tab "Người thực hiện" của modal cấu hình bước có **8 hành động** dạng checkbox → lưu `actions[]` trong `stepsMeta`. DemoSeeder: Task_Tao=[RECORD,EDIT,SUBMIT], Task_Duyet=[APPROVE,RETURN,REJECT].
- **Còn lại = runtime (Epic 3, Story 3.4):** hiển thị nút hành động theo `actions[]` + vai trò người dùng tại bước; **map mỗi hành động → hành vi Flowable** (complete/ reject/ delegate…) khi nối engine.
- Không có thay đổi mã riêng cho 2.3 ngoài phần đã làm ở 2.1; story này **chốt phạm vi cấu hình**.

### References
- [Source: epics.md#Story-2.3] · liên quan [[2-1-process-designer-keo-tha-metadata-buoc]]
