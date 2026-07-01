# Story 2.2: Loại luồng cốt lõi + điều kiện chuyển bước

Status: review

## Story

As an **admin IT**,
I want **cấu hình tuần tự, rẽ nhánh điều kiện, chạy song song, gộp/đồng bộ (join)**,
so that **mô hình hóa được quy trình mẫu #1 (FR-A02 cốt lõi, FR-A05)**.

## Acceptance Criteria

1. **Given** designer, **When** thêm gateway rẽ nhánh (exclusive ✕) / song song (parallel ＋) / join và luồng nối, **Then** vẽ được trên canvas. _(FR-A02 — bpmn-js hỗ trợ sẵn palette)_
2. **Given** một nhánh (sequence flow) rời gateway, **When** đặt **điều kiện dựa trên dữ liệu form**, **Then** lưu điều kiện vào định nghĩa + hiển thị nhãn trên nhánh. _(FR-A05)_
3. **Given** quy trình mẫu #1, **When** mô hình hóa, **Then** đủ loại luồng cốt lõi. _(Lặp/quay-lại-bước → Epic 5.)_

## Tasks / Subtasks

- [x] **FE designer**: nhận diện chọn **SequenceFlow** (nhánh) tách khỏi chọn bước; nút **🔀 Điều kiện nhánh** + nhấp đúp nhánh → modal điều kiện.
- [x] **Modal điều kiện**: chọn **trường dữ liệu** (gom từ trường-thêm + biểu mẫu gắn các bước) + toán tử (có giá trị / bằng / khác) + giá trị; **Lưu** ghi `condition` vào `stepsMeta[flowId]` và đặt **nhãn nhánh** trên canvas; **Xóa điều kiện** → nhánh mặc định.
- [x] Loại luồng (gateway/parallel/join/sequence) dùng **palette bpmn-js sẵn có** — không cần code thêm.

## Dev Notes

- **Không đổi schema BE:** `condition{field, op(eq/ne/truthy), value}` lưu trong `stepsMeta` keyed theo elementId của nhánh (cùng map với meta bước, JSON opaque).
- **Trường điều kiện** gom từ: `fields[]` ad-hoc của các bước + trường của **biểu mẫu gắn bước** (load schema). Vd điều kiện `muc_uu_tien = "Cao"` hoặc `co_phoi_hop có giá trị`.
- **Thực thi định tuyến (Flowable)** = **Epic 3** (Story 3.5). 2.2 lo **mô hình hóa + điều kiện** ở mức định nghĩa. Việc map điều kiện → Flowable `conditionExpression` khi nối engine.
- **Tái dụng:** designer 2.1 + form data 2.6/2.9 + design-system. Build clean, FE 23/23.

### References
- [Source: epics.md#Story-2.2] · [Source: ARCHITECTURE-SPINE.md#AD-1] (FR-A02, FR-A05)

## Dev Agent Record

### Completion Notes List
- ✅ FE **23/23**, build clean. Designer nay xử lý cả bước lẫn nhánh: chọn nhánh → "🔀 Điều kiện nhánh" → đặt điều kiện theo dữ liệu form, nhãn hiện trên canvas, lưu vào stepsMeta. Loại luồng vẽ bằng palette bpmn-js.

### File List
Frontend: sửa `processes/designer/designer.ts` (+FlowCondition, isFlow, onSelect nhánh, collectDataFields, openFlowConfig/saveFlowCondition/clearFlowCondition/condLabel, COND_OPS, dataFields), `designer.html` (+nút Điều kiện nhánh, +modal điều kiện).
