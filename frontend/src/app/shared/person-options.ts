import { SelectOption } from './searchable-select/searchable-select';
import { Person, ProjectMember } from '../core/project.service';

/**
 * Tiện ích CHUNG cho dropdown chọn người — DÙNG LẠI ở mọi màn (backlog, bug/issue, thành viên…).
 * Đồng bộ phần "thông tin đi kèm": sub = `mã NV · vị trí · chức danh · bộ phận`.
 * Xem quy ước hiển thị nhân sự ở memory bpm-fe-conventions.
 */
export function personSub(p: Pick<Person, 'empCode' | 'jobPosition' | 'title' | 'deptCode'>): string {
  return [p.empCode, p.jobPosition, p.title, p.deptCode].filter(Boolean).join(' · ');
}

export function personOptions(people: Person[]): SelectOption[] {
  return people.map((p) => ({
    value: p.userId,
    label: p.name,
    sub: personSub(p) || undefined
  }));
}

/**
 * Options từ THÀNH VIÊN DỰ ÁN (chỉ người trong dự án) — dùng cho gán người ở bug/issue, task…
 * thay vì toàn bộ nhân sự hệ thống. Cùng định dạng sub mã·chức danh·bộ phận.
 */
export function memberPersonOptions(members: ProjectMember[], keepUserId?: string | null): SelectOption[] {
  // Người đã TẠM NGƯNG không còn vào được dự án nên không nên gán việc mới cho họ. Vẫn giữ trong
  // danh sách nếu họ đang là người được gán (keepUserId) — bỏ đi thì ô chọn hiện trống, nhìn như mất dữ liệu.
  return members
    .filter((m) => m.active || m.userId === keepUserId)
    .map((m) => ({
      value: m.userId,
      label: m.active ? m.name : m.name + ' (đã ngưng)',
      sub: personSub(m) || undefined
    }));
}
