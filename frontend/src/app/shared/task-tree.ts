import { ProjectTask } from '../core/project.service';

/**
 * Tiện ích CHUNG cây task — DÙNG LẠI ở backlog, timeline… (quy ước rollup cha: memory bpm-fe-conventions).
 * Task CHA (có con) KHÔNG nhập est/%/người: est = Σ est các task LÁ trong cây con; % lấy progressPct (BE rollup).
 */
export interface TaskTree {
  byId: Map<string, ProjectTask>;
  childrenOf: Map<string | null, ProjectTask[]>;
}

export function buildTree(tasks: ProjectTask[]): TaskTree {
  const byId = new Map<string, ProjectTask>();
  const childrenOf = new Map<string | null, ProjectTask[]>();
  for (const t of tasks) {
    byId.set(t.id, t);
    const k = t.parentId ?? null;
    (childrenOf.get(k) ?? childrenOf.set(k, []).get(k)!).push(t);
  }
  return { byId, childrenOf };
}

/** Có task con không (→ là task cha, áp dụng rollup). */
export function hasChildren(taskId: string, tree: TaskTree): boolean {
  return (tree.childrenOf.get(taskId)?.length ?? 0) > 0;
}

/** Σ estimateHours của các task LÁ trong cây con (chính task nếu là lá). */
export function subtreeLeafEstimate(taskId: string, tree: TaskTree): number {
  const kids = tree.childrenOf.get(taskId) ?? [];
  const self = tree.byId.get(taskId);
  if (kids.length === 0) {
    // Lá Huỷ = ngoài phạm vi → không góp est (khớp % tổng + summary Backlog).
    return self?.status === 'CANCELLED' ? 0 : (self?.estimateHours || 0);
  }
  let sum = 0;
  for (const k of kids) {
    sum += subtreeLeafEstimate(k.id, tree);
  }
  return Math.round(sum * 100) / 100;
}

/** Est hiển thị cho 1 task: lá → est của nó; cha → tổng est lá cây con. */
export function effectiveEstimate(task: ProjectTask, tree: TaskTree): number {
  return hasChildren(task.id, tree) ? subtreeLeafEstimate(task.id, tree) : (task.estimateHours || 0);
}

// ===== Rollup NGÀY (dd/MM/yyyy) từ task con (dùng chung backlog + timeline) =====
function parseDmy(s: string | null | undefined): Date | null {
  if (!s) return null;
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s);
  return m ? new Date(+m[3], +m[2] - 1, +m[1]) : null;
}
function fmtDmy(d: Date | null): string | null {
  if (!d) return null;
  const p = (n: number) => String(n).padStart(2, '0');
  return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
}
/**
 * Mọi ngày (start|due) trong cây con — gồm ngày CỦA CHÍNH node ở MỌI cấp (không chỉ lá).
 * Nhờ vậy: node cha giữ được ngày của chính nó + gộp thêm ngày của Sub-task/Bug con
 * (tránh trường hợp gắn Bug không ngày làm mất ngày vốn có của cha → hiện "—").
 */
export function subtreeLeafDates(taskId: string, tree: TaskTree, kind: 'start' | 'due'): Date[] {
  const self = tree.byId.get(taskId);
  // Node Huỷ = ngoài phạm vi → không góp ngày của chính nó (nhưng vẫn duyệt con phòng khi con còn hiệu lực).
  const own = self?.status === 'CANCELLED' ? null : parseDmy(kind === 'start' ? self?.startDate : self?.dueDate);
  const out: Date[] = own ? [own] : [];
  for (const k of tree.childrenOf.get(taskId) ?? []) {
    out.push(...subtreeLeafDates(k.id, tree, kind));
  }
  return out;
}
/** Ngày bắt đầu hiệu lực: lá → của nó; cha → MIN ngày bắt đầu các lá (gồm Bug/Sub-task). */
export function effectiveStart(task: ProjectTask, tree: TaskTree): string | null {
  if (!hasChildren(task.id, tree)) return task.startDate ?? null;
  const ds = subtreeLeafDates(task.id, tree, 'start');
  return ds.length ? fmtDmy(new Date(Math.min(...ds.map((d) => d.getTime())))) : null;
}
/** Ngày kết thúc hiệu lực: lá → của nó; cha → MAX ngày kết thúc các lá (gồm Bug/Sub-task). */
export function effectiveDue(task: ProjectTask, tree: TaskTree): string | null {
  if (!hasChildren(task.id, tree)) return task.dueDate ?? null;
  const ds = subtreeLeafDates(task.id, tree, 'due');
  return ds.length ? fmtDmy(new Date(Math.max(...ds.map((d) => d.getTime())))) : null;
}
