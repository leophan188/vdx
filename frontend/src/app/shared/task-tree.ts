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
    return self?.estimateHours || 0;
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
