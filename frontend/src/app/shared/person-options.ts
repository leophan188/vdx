import { SelectOption } from './searchable-select/searchable-select';
import { Person, ProjectMember } from '../core/project.service';

/**
 * Tiện ích CHUNG cho dropdown chọn người — DÙNG LẠI ở mọi màn (backlog, bug/issue, thành viên…).
 * Đồng bộ phần "thông tin đi kèm": sub = `mã NV · chức danh · bộ phận`.
 * Xem quy ước hiển thị nhân sự ở memory bpm-fe-conventions.
 */
export function personSub(p: Pick<Person, 'empCode' | 'jobPosition' | 'deptCode'>): string {
  return [p.empCode, p.jobPosition, p.deptCode].filter(Boolean).join(' · ');
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
export function memberPersonOptions(members: ProjectMember[]): SelectOption[] {
  return members.map((m) => ({
    value: m.userId,
    label: m.name,
    sub: personSub(m) || undefined
  }));
}
