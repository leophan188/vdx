import { TaskType, TaskStatus, ProjectTask } from '../core/project.service';
import { SelectOption } from '../shared/searchable-select/searchable-select';

/** Nhóm thống kê công việc: gộp Task (Task+Sub-task), Bug, Issue riêng biệt. Bỏ EPIC/STORY (cấp nhóm). */
export type WorkCat = 'TASK' | 'BUG' | 'ISSUE';

export const WORK_CATS: { key: WorkCat; label: string; icon: string; color: string }[] = [
  { key: 'TASK', label: 'Công việc', icon: '📋', color: 'var(--type-task, var(--status-active))' },
  { key: 'BUG', label: 'Bug', icon: '🐞', color: 'var(--status-cancel, #e5484d)' },
  { key: 'ISSUE', label: 'Issue', icon: '⚠️', color: 'var(--status-pending, #f5a623)' }
];

/** Nhãn NGẮN + màu pill theo LOẠI công việc (dùng cho badge ở dropdown chọn cha). */
export const TYPE_META: Record<TaskType, { short: string; color: string }> = {
  EPIC:    { short: 'Epic',     color: 'var(--type-epic, #7c3aed)' },
  STORY:   { short: 'Story',    color: 'var(--type-story, #2563eb)' },
  TASK:    { short: 'Task',     color: 'var(--type-parent, #0f9488)' },
  SUBTASK: { short: 'Sub-task', color: 'var(--color-text-muted, #64748b)' },
  BUG:     { short: 'Bug',      color: 'var(--overdue, #e5484d)' },
  ISSUE:   { short: 'Issue',    color: 'var(--status-pending, #d97706)' }
};

/** Chuỗi TỔ TIÊN (Epic › Story › Task cha) của một task — để hiện "thuộc gì" khi chọn cha. Rỗng nếu là gốc. */
export function ancestorPath(t: ProjectTask, byId: Map<string, ProjectTask>): string {
  const chain: string[] = [];
  let cur = t.parentId ? byId.get(t.parentId) : undefined;
  let guard = 0;
  while (cur && guard++ < 12) {
    chain.unshift(`${TYPE_META[cur.type]?.short ?? cur.type}: ${cur.title}`);
    cur = cur.parentId ? byId.get(cur.parentId) : undefined;
  }
  return chain.length ? '↳ thuộc ' + chain.join(' › ') : '';
}

/** Một option cho dropdown CHỌN TASK CHA: badge loại + [mã] tiêu đề + dòng phụ chuỗi cha. */
export function parentOptionOf(t: ProjectTask, byId: Map<string, ProjectTask>): SelectOption {
  const meta = TYPE_META[t.type];
  const path = ancestorPath(t, byId);
  const opt: SelectOption = {
    value: t.id,
    label: `[${t.code}] ${t.title}`,
    badge: meta?.short ?? t.type,
    badgeColor: meta?.color
  };
  if (path) opt.sub = path;
  return opt;
}

/** Build options chọn cha ĐỒNG BỘ (dùng chung cho Backlog / Tạo nhanh / Bug). */
export function buildParentOptions(all: ProjectTask[], filtered: ProjectTask[]): SelectOption[] {
  const byId = new Map<string, ProjectTask>();
  for (const t of all) byId.set(t.id, t);
  return filtered.map((t) => parentOptionOf(t, byId));
}

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
  total: number; done: number; doing: number; review: number; todo: number; cancel: number; overdue: number; donePct: number;
}

/** dueDate "dd/MM/yyyy" đã quá hôm nay (mốc 00:00) chưa? */
function isOverdue(dueDate: string | null, status: TaskStatus): boolean {
  if (!dueDate || status === 'DONE' || status === 'CANCELLED') return false;
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
    const doing = list.filter((it) => it.status === 'IN_PROGRESS').length;
    const review = list.filter((it) => it.status === 'IN_REVIEW').length;
    const todo = list.filter((it) => it.status === 'BACKLOG' || it.status === 'TODO').length;
    const cancel = list.filter((it) => it.status === 'CANCELLED').length;
    const overdue = list.filter((it) => isOverdue(it.dueDate, it.status)).length;
    const total = list.length;
    const scope = total - cancel; // Huỷ = ngoài phạm vi khi tính % hoàn thành
    return {
      key: c.key, label: c.label, icon: c.icon, color: c.color,
      total, done, doing, review, todo, cancel, overdue,
      donePct: scope > 0 ? Math.round((done / scope) * 100) : 0
    };
  });
}
