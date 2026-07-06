import { TaskType, TaskStatus } from '../core/project.service';

/** Nhóm thống kê công việc: gộp Task (Task+Sub-task), Bug, Issue riêng biệt. Bỏ EPIC/STORY (cấp nhóm). */
export type WorkCat = 'TASK' | 'BUG' | 'ISSUE';

export const WORK_CATS: { key: WorkCat; label: string; icon: string; color: string }[] = [
  { key: 'TASK', label: 'Công việc', icon: '📋', color: 'var(--type-task, var(--status-active))' },
  { key: 'BUG', label: 'Bug', icon: '🐞', color: 'var(--status-cancel, #e5484d)' },
  { key: 'ISSUE', label: 'Issue', icon: '⚠️', color: 'var(--status-pending, #f5a623)' }
];

/** Loại task → nhóm (null nếu là EPIC/STORY — không tính vào 3 nhóm). */
export function catOf(type: TaskType): WorkCat | null {
  if (type === 'TASK' || type === 'SUBTASK') return 'TASK';
  if (type === 'BUG') return 'BUG';
  if (type === 'ISSUE') return 'ISSUE';
  return null;
}

/** Phần tử tối thiểu để tính thống kê (khớp cả ProjectTask lẫn ReportTaskItem). */
export interface WorkItem { type: TaskType; status: TaskStatus; dueDate: string | null; }

export interface CatStat {
  key: WorkCat; label: string; icon: string; color: string;
  total: number; done: number; doing: number; todo: number; overdue: number; donePct: number;
}

/** dueDate "dd/MM/yyyy" đã quá hôm nay (mốc 00:00) chưa? */
function isOverdue(dueDate: string | null, status: TaskStatus): boolean {
  if (!dueDate || status === 'DONE') return false;
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(dueDate);
  if (!m) return false;
  const due = new Date(+m[3], +m[2] - 1, +m[1]).getTime();
  const t = new Date(); t.setHours(0, 0, 0, 0);
  return due < t.getTime();
}

/** Thống kê 3 nhóm (Task/Bug/Issue) từ danh sách công việc. Luôn trả đủ 3 nhóm (kể cả total=0). */
export function categoryStats(items: WorkItem[]): CatStat[] {
  return WORK_CATS.map((c) => {
    const list = items.filter((it) => catOf(it.type) === c.key);
    const done = list.filter((it) => it.status === 'DONE').length;
    const doing = list.filter((it) => it.status === 'IN_PROGRESS' || it.status === 'IN_REVIEW').length;
    const todo = list.filter((it) => it.status === 'BACKLOG' || it.status === 'TODO').length;
    const overdue = list.filter((it) => isOverdue(it.dueDate, it.status)).length;
    const total = list.length;
    return {
      key: c.key, label: c.label, icon: c.icon, color: c.color,
      total, done, doing, todo, overdue,
      donePct: total > 0 ? Math.round((done / total) * 100) : 0
    };
  });
}
