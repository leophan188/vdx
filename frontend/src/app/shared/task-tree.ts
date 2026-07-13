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
 * Ngày (start|due) của các LEAF descendants (node KHÔNG con) — KHÔNG lấy ngày RIÊNG của node CHA
 * ở bất kỳ cấp nào (Epic/Story/TASK có con đều là khung tổng hợp). Bỏ node Huỷ.
 * → Ngày cha TỰ TỔNG HỢP từ các việc thật bên trong, không "vống" theo ngày riêng/seed của cha.
 */
export function subtreeLeafDates(taskId: string, tree: TaskTree, kind: 'start' | 'due'): Date[] {
  const kids = tree.childrenOf.get(taskId) ?? [];
  const self = tree.byId.get(taskId);
  if (kids.length === 0) {
    if (self?.status === 'CANCELLED') return [];
    const d = parseDmy(kind === 'start' ? self?.startDate : self?.dueDate);
    return d ? [d] : [];
  }
  const out: Date[] = [];
  for (const k of kids) out.push(...subtreeLeafDates(k.id, tree, kind));
  return out;
}
/** Ngày bắt đầu hiệu lực: lá → của nó; cha → MIN ngày các LÁ con (không dùng ngày riêng cha).
 *  Chỉ fallback ngày riêng cha khi KHÔNG lá nào có ngày (vd task chỉ gắn Bug không ngày). */
export function effectiveStart(task: ProjectTask, tree: TaskTree): string | null {
  if (!hasChildren(task.id, tree)) return task.startDate ?? null;
  const ds = subtreeLeafDates(task.id, tree, 'start');
  return ds.length ? fmtDmy(new Date(Math.min(...ds.map((d) => d.getTime())))) : (task.startDate ?? null);
}
/** Ngày kết thúc hiệu lực: lá → của nó; cha → MAX ngày các LÁ con (không dùng ngày riêng cha). */
export function effectiveDue(task: ProjectTask, tree: TaskTree): string | null {
  if (!hasChildren(task.id, tree)) return task.dueDate ?? null;
  const ds = subtreeLeafDates(task.id, tree, 'due');
  return ds.length ? fmtDmy(new Date(Math.max(...ds.map((d) => d.getTime())))) : (task.dueDate ?? null);
}
