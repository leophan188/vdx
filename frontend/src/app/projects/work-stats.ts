import { TaskType, TaskStatus, ProjectTask } from '../core/project.service';
import { SelectOption } from '../shared/searchable-select/searchable-select';

/**
 * Nhóm thống kê công việc: gộp Task (Task+Sub-task), Bug, Issue riêng biệt. Bỏ EPIC/STORY (cấp nhóm).
 * Nhãn để nguyên tiếng Anh (Task/Bug/Issue) cho đồng bộ với tên LOẠI ở mọi màn.
 */
export type WorkCat = 'TASK' | 'BUG' | 'ISSUE';

export const WORK_CATS: { key: WorkCat; label: string; icon: string; color: string }[] = [
  { key: 'TASK', label: 'Task', icon: '📋', color: 'var(--type-task, var(--status-active))' },
  { key: 'BUG', label: 'Bug', icon: '🐞', color: 'var(--status-cancel, #e5484d)' },
  { key: 'ISSUE', label: 'Issue', icon: '⚠️', color: 'var(--status-pending, #f5a623)' }
];

/**
 * Cột TRẠNG THÁI dùng chung cho mọi ma trận "loại/nhân sự × trạng thái"
 * (báo cáo kỳ, tổng quan dự án). Thứ tự cố định: Backlog → Huỷ.
 */
export const STATUS_META: { key: TaskStatus; label: string; color: string; icon: string }[] = [
  { key: 'BACKLOG', label: 'Backlog', color: '#94a3b8', icon: '📋' },
  { key: 'TODO', label: 'Cần làm', color: '#3b82f6', icon: '🗒️' },
  { key: 'IN_PROGRESS', label: 'Đang làm', color: '#f59e0b', icon: '🔄' },
  { key: 'IN_REVIEW', label: 'Kiểm thử', color: '#8b5cf6', icon: '🔍' },
  { key: 'DONE', label: 'Hoàn thành', color: '#22c55e', icon: '✅' },
  { key: 'CANCELLED', label: 'Huỷ', color: '#cbd5e1', icon: '🚫' }
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

/** Phần tối thiểu để suy CHỦ HIỆN TẠI (khớp cả ProjectTask lẫn dữ liệu báo cáo). */
export interface OwnableTask {
  type: TaskType; status: TaskStatus;
  assigneeUserId: string | null; assigneeName: string | null;
  testerUserId?: string | null; testerName?: string | null;
  reporterUserId?: string | null; reporterName?: string | null;
}

/**
 * CHỦ HIỆN TẠI của một công việc — ai đang thực sự giữ việc tại trạng thái này.
 * Hệ thống giữ 3 vai RIÊNG BIỆT và không đổi khi chuyển trạng thái (người thực hiện /
 * người kiểm thử / người log), nên phải suy chủ theo trạng thái:
 *  - Kiểm thử + task thường → NGƯỜI KIỂM THỬ (bắt buộc chọn trước khi chuyển).
 *  - Kiểm thử + bug/issue   → NGƯỜI LOG (hệ thống bàn giao ngầm để verify).
 *  - Các trạng thái khác    → NGƯỜI THỰC HIỆN.
 * Thiếu vai tương ứng thì lùi về người thực hiện để không việc nào rơi ra ngoài bảng.
 * PHẢI khớp với ProjectReportService.ownerUserId ở backend.
 */
export function ownerOf(t: OwnableTask): { id: string | null; name: string | null } {
  // Chỉ xét theo ID (không xét tên) để khớp TỪNG TRƯỜNG HỢP với backend.
  if (t.status === 'IN_REVIEW') {
    if (t.type === 'BUG' || t.type === 'ISSUE') {
      if (t.reporterUserId) return { id: t.reporterUserId, name: t.reporterName ?? null };
    } else if (t.testerUserId) {
      return { id: t.testerUserId, name: t.testerName ?? null };
    }
  }
  return { id: t.assigneeUserId, name: t.assigneeName };
}

/**
 * Lần chuyển trạng thái này có phải MỐC BÀN GIAO cần ghi giờ không, và với vai nào.
 * PHẢI khớp ProjectTaskService.workRoleFor ở backend — backend sẽ từ chối nếu thiếu giờ,
 * nên mọi màn đổi trạng thái đều phải hỏi giờ đúng những trường hợp này.
 */
export function workRoleForTransition(
  t: { type: TaskType; status: TaskStatus; leaf?: boolean; skipTest?: boolean }, next: TaskStatus
): 'DEV' | 'TEST' | null {
  if (next === t.status) return null;
  if (t.type === 'EPIC' || t.type === 'STORY') return null;
  if (t.leaf === false) return null;              // task cha: trạng thái do rollup
  if (next === 'IN_REVIEW') return 'DEV';
  if (next === 'DONE') {
    // Việc không cần kiểm thử (PM/BA): người thực hiện tự hoàn thành → giờ tính vai LẬP TRÌNH.
    // Bug/Issue luôn phải kiểm thử nên cờ không áp dụng.
    const skips = t.skipTest && t.type !== 'BUG' && t.type !== 'ISSUE';
    return skips ? 'DEV' : 'TEST';
  }
  return null;
}

/**
 * GIỜ HIỆU LỰC dùng làm trọng số khi tính % — PHẢI khớp ProjectTask.effectiveHours ở backend:
 * ưu tiên est đã nhập; chưa nhập thì suy từ số NGÀY CÔNG (T2–T6) trong [bắt đầu, kết thúc] × 8h.
 * Nhờ vậy task chưa ước lượng vẫn có sức nặng thay vì tàng hình với % hoàn thành.
 */
export function effectiveHours(t: { estimateHours: number; startDate: string | null; dueDate: string | null }): number {
  if (t.estimateHours > 0) return t.estimateHours;
  const s = parseDmyLocal(t.startDate);
  const d = parseDmyLocal(t.dueDate);
  if (!s || !d || d < s) return 0;
  let workdays = 0;
  for (const cur = new Date(s); cur <= d; cur.setDate(cur.getDate() + 1)) {
    const w = cur.getDay();
    if (w >= 1 && w <= 5) workdays++;
  }
  return workdays * 8;
}
function parseDmyLocal(s: string | null): Date | null {
  if (!s) return null;
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s);
  return m ? new Date(+m[3], +m[2] - 1, +m[1]) : null;
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
  total: number; done: number; doing: number; review: number; todo: number; backlog: number; cancel: number; overdue: number; donePct: number;
}

/** dueDate "dd/MM/yyyy" đã quá hôm nay (mốc 00:00) chưa? */
export function isOverdue(dueDate: string | null, status: TaskStatus): boolean {
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
    const todo = list.filter((it) => it.status === 'TODO').length;
    const backlog = list.filter((it) => it.status === 'BACKLOG').length;
    const cancel = list.filter((it) => it.status === 'CANCELLED').length;
    const overdue = list.filter((it) => isOverdue(it.dueDate, it.status)).length;
    const total = list.length;
    const scope = total - cancel; // Huỷ = ngoài phạm vi khi tính % hoàn thành
    return {
      key: c.key, label: c.label, icon: c.icon, color: c.color,
      total, done, doing, review, todo, backlog, cancel, overdue,
      donePct: scope > 0 ? Math.round((done / scope) * 100) : 0
    };
  });
}
