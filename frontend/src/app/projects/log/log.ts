import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { TypeFilter, TypeChip } from '../../shared/type-filter/type-filter';
import { ProjectService, ProjectActivityItem, TaskActivityAction, ProjectTask } from '../../core/project.service';

/** Metadata hiển thị theo loại hành động. */
interface ActionMeta { icon: string; verb: string; label: string; }

const ACTION_META: Record<string, ActionMeta> = {
  CREATED: { icon: '🆕', verb: 'tạo', label: 'Tạo' },
  STATUS: { icon: '🔄', verb: 'đổi trạng thái', label: 'Trạng thái' },
  ASSIGN: { icon: '👤', verb: 'giao người', label: 'Giao người' },
  EDIT: { icon: '✎', verb: 'sửa', label: 'Sửa' },
  COMMENT: { icon: '💬', verb: 'bình luận', label: 'Bình luận' },
  ATTACH: { icon: '📎', verb: 'đính kèm', label: 'Đính kèm' },
  SPENT: { icon: '⏱️', verb: 'log giờ', label: 'Log giờ' },
  DIARY: { icon: '📔', verb: 'ghi nhật ký', label: 'Nhật ký' }
};

/**
 * Tab LOG DỰ ÁN (selector app-prj-log) — nhật ký hoạt động toàn dự án (mới → cũ).
 * Đọc /projects/{id}/activity → dòng thời gian: icon theo action + actorName (đậm) + hành động
 * + detail + [taskCode] + tiêu đề task + thời gian (dd/MM/yyyy HH:mm). Lọc nhanh theo loại action.
 */
@Component({
  selector: 'app-prj-log',
  imports: [TypeFilter],
  templateUrl: './log.html',
  styles: [`
    .lg { display: grid; gap: var(--space-4); font-size: var(--text-sm); color: var(--color-text); }

    .lg__head { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .lg__count { font-size: var(--text-xs); color: var(--color-text-muted);
      background: var(--color-surface-alt); padding: 1px var(--space-2); border-radius: var(--radius-full); }

    .lg__list { display: grid; gap: 2px; }
    .lg__row { display: grid; grid-template-columns: auto 1fr auto; align-items: baseline; gap: var(--space-3);
      padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); }
    .lg__row:hover { border-color: var(--color-primary); }
    .lg__icon { font-size: var(--text-base); line-height: 1.2; }
    .lg__body { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 6px; min-width: 0; }
    .lg__actor { font-weight: var(--weight-semibold); color: var(--color-text); }
    .lg__verb { color: var(--color-text-muted); }
    .lg__detail { color: var(--color-text); background: var(--color-surface-alt);
      padding: 0 6px; border-radius: var(--radius-sm); }
    .lg__code { font-weight: var(--weight-semibold); color: var(--color-primary); }
    .lg__code--link { background: none; border: 0; padding: 0; font: inherit; cursor: pointer; }
    .lg__code--link:hover { text-decoration: underline; }
    .lg__title { color: var(--color-text-muted); overflow-wrap: anywhere; }
    .lg__deleted { color: var(--color-text-muted); font-style: italic; }
    .lg__time { color: var(--color-text-muted); font-size: var(--text-xs); white-space: nowrap;
      font-variant-numeric: tabular-nums; }

    .lg__empty, .lg__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
  `]
})
export class PrjLog {
  readonly projectId = input.required<string>();

  private svc = inject(ProjectService);

  readonly loading = signal(true);
  readonly items = signal<ProjectActivityItem[]>([]);
  readonly selected = signal<Set<string>>(new Set());

  /** Bấm mã công việc → mở chi tiết (cha render app-prj-task-detail). */
  readonly openTask = output<ProjectTask>();
  private readonly tasksById = signal<Map<string, ProjectTask>>(new Map());
  hasTask(taskId: string | null): boolean { return !!taskId && this.tasksById().has(taskId); }
  openDetail(taskId: string | null): void {
    if (!taskId) return;
    const t = this.tasksById().get(taskId);
    if (t) this.openTask.emit(t);
  }

  /** Chip lọc: chỉ các loại action THỰC SỰ xuất hiện trong log. */
  readonly filterOptions = computed<TypeChip[]>(() => {
    const present = new Set(this.items().map((i) => i.action));
    return Object.keys(ACTION_META)
      .filter((a) => present.has(a as TaskActivityAction))
      .map((a) => ({ value: a, label: `${ACTION_META[a].icon} ${ACTION_META[a].label}` }));
  });

  readonly filtered = computed<ProjectActivityItem[]>(() => {
    const sel = this.selected();
    const all = this.items();
    return sel.size === 0 ? all : all.filter((i) => sel.has(i.action));
  });

  constructor() {
    effect(() => {
      const pid = this.projectId();
      if (!pid) return;
      this.loading.set(true);
      this.svc.projectActivity(pid).subscribe({
        next: (rows) => { this.items.set(rows ?? []); this.loading.set(false); },
        error: () => { this.items.set([]); this.loading.set(false); }
      });
      // Nạp danh sách task để tra khi bấm mã (mở chi tiết).
      this.svc.listTasks(pid).subscribe({
        next: (ts) => {
          const m = new Map<string, ProjectTask>();
          for (const t of ts ?? []) m.set(t.id, t);
          this.tasksById.set(m);
        },
        error: () => this.tasksById.set(new Map())
      });
    });
  }

  toggleFilter(v: string): void {
    const next = new Set(this.selected());
    if (next.has(v)) next.delete(v); else next.add(v);
    this.selected.set(next);
  }

  icon(a: string): string { return ACTION_META[a]?.icon ?? '•'; }
  verb(a: string): string { return ACTION_META[a]?.verb ?? a; }

  /** Nhãn trạng thái tiếng Việt (dịch cả bản ghi CŨ đã lưu mã TODO/IN_REVIEW…). */
  private static readonly STATUS_VI: Record<string, string> = {
    BACKLOG: 'Backlog', TODO: 'Cần làm', IN_PROGRESS: 'Đang làm', IN_REVIEW: 'Kiểm thử', DONE: 'Hoàn thành'
  };
  displayDetail(detail: string | null): string {
    if (!detail) return '';
    return detail.replace(/\b(BACKLOG|TODO|IN_PROGRESS|IN_REVIEW|DONE)\b/g, (m) => PrjLog.STATUS_VI[m] ?? m);
  }

  /** ISO Instant → "dd/MM/yyyy HH:mm" (giờ địa phương). */
  when(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    const p = (n: number) => (n < 10 ? '0' + n : String(n));
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }
}
