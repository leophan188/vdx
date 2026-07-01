import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ToastService } from '../../shared/toast/toast.service';
import { ProjectService, ProjectReport, TaskStatus, TaskType } from '../../core/project.service';

interface StatusBar {
  status: TaskStatus;
  label: string;
  count: number;
  pct: number;
}
interface TypeStat {
  type: TaskType;
  label: string;
  count: number;
  badge: string;
}

/**
 * Báo cáo dự án từ report(projectId): % hoàn thành (thanh lớn), các con số tổng quan,
 * byStatus (thanh ngang), byType (badge + số), byAssignee (data-grid).
 * Thanh tiến độ bằng div CSS — không dùng thư viện chart.
 */
@Component({
  selector: 'app-prj-report',
  imports: [DataGrid, GridCellDirective, EmployeeChip],
  templateUrl: './report.html',
  styles: [`
    .rp-hero {
      display: grid; gap: var(--space-2); padding: var(--space-5);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); margin-bottom: var(--space-4);
    }
    .rp-hero__head { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-3); }
    .rp-hero__label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: var(--weight-semibold); }
    .rp-hero__pct { font-size: 28px; font-weight: var(--weight-semibold); color: var(--color-primary); }
    .rp-progress { height: 16px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rp-progress__fill { height: 100%; border-radius: var(--radius-full); background: var(--color-primary); transition: width .3s ease; }

    .rp-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: var(--space-3); margin-bottom: var(--space-4); }
    .rp-stat {
      display: grid; gap: var(--space-1); padding: var(--space-4);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm);
    }
    .rp-stat__num { font-size: 22px; font-weight: var(--weight-semibold); color: var(--color-text); }
    .rp-stat__label { font-size: var(--text-sm); color: var(--color-text-muted); }
    .rp-stat--bug .rp-stat__num { color: var(--status-pending); }
    .rp-stat--overdue .rp-stat__num { color: var(--overdue); }

    .rp-panels { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: var(--space-4); margin-bottom: var(--space-4); }
    .rp-panel {
      padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm);
    }
    .rp-panel__title { font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--color-text-muted); margin: 0 0 var(--space-3); }

    .rp-barrow { display: grid; grid-template-columns: 110px 1fr 40px; align-items: center; gap: var(--space-3); margin: var(--space-2) 0; font-size: var(--text-sm); }
    .rp-bar { height: 10px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rp-bar__fill { height: 100%; border-radius: var(--radius-full); }
    .rp-barrow__val { text-align: right; color: var(--color-text-muted); }

    .rp-types { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .rp-type { display: inline-flex; align-items: center; gap: var(--space-2); }
    .rp-type__count { font-weight: var(--weight-semibold); color: var(--color-text); }

    .rp-progress-cell { display: flex; align-items: center; gap: var(--space-2); }
    .rp-mini-bar { flex: 1; min-width: 60px; height: 8px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rp-mini-bar__fill { height: 100%; border-radius: var(--radius-full); background: var(--status-done); }
  `]
})
export class PrjReport {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();

  readonly report = signal<ProjectReport | null>(null);
  readonly loading = signal(true);

  // ----- byStatus → thanh ngang -----
  private readonly statusMeta: { status: TaskStatus; label: string; color: string }[] = [
    { status: 'BACKLOG', label: 'Backlog', color: 'var(--color-text-muted)' },
    { status: 'TODO', label: 'Cần làm', color: 'var(--status-pending)' },
    { status: 'IN_PROGRESS', label: 'Đang làm', color: 'var(--status-active)' },
    { status: 'IN_REVIEW', label: 'Kiểm thử', color: 'var(--color-info)' },
    { status: 'DONE', label: 'Hoàn thành', color: 'var(--status-done)' }
  ];

  readonly statusBars = computed(() => {
    const r = this.report();
    if (!r) return [];
    const max = Math.max(1, ...this.statusMeta.map((m) => r.byStatus[m.status] ?? 0));
    return this.statusMeta.map((m) => {
      const count = r.byStatus[m.status] ?? 0;
      return { ...m, count, pct: Math.round((count / max) * 100) };
    });
  });

  // ----- byType → badge + số -----
  private readonly typeMeta: { type: TaskType; label: string; badge: string }[] = [
    { type: 'EPIC', label: 'Epic', badge: 'badge--active' },
    { type: 'STORY', label: 'Story', badge: 'badge--done' },
    { type: 'TASK', label: 'Task', badge: 'badge--neutral' },
    { type: 'SUBTASK', label: 'Subtask', badge: 'badge--neutral' },
    { type: 'BUG', label: 'Bug', badge: 'badge--cancel' },
    { type: 'ISSUE', label: 'Issue', badge: 'badge--pending' }
  ];

  readonly typeStats = computed<TypeStat[]>(() => {
    const r = this.report();
    if (!r) return [];
    return this.typeMeta
      .map((m) => ({ type: m.type, label: m.label, badge: m.badge, count: r.byType[m.type] ?? 0 }))
      .filter((t) => t.count > 0);
  });

  // ----- byAssignee → data-grid -----
  readonly assigneeRows = computed(() => {
    const r = this.report();
    if (!r) return [];
    return r.byAssignee.map((a) => ({
      ...a,
      donePct: a.total > 0 ? Math.round((a.done / a.total) * 100) : 0
    }));
  });

  readonly assigneeCols: GridColumn[] = [
    { key: 'name', header: 'Người phụ trách' },
    { key: 'total', header: 'Tổng task', align: 'center', width: '110px', sortable: true },
    { key: 'done', header: 'Hoàn thành', align: 'center', width: '120px', sortable: true },
    { key: 'estimate', header: 'Est (h)', align: 'center', width: '100px', sortable: true },
    { key: 'donePct', header: '% hoàn thành', width: '200px', sortable: true }
  ];

  constructor() {
    effect(() => {
      const id = this.projectId();
      if (id) this.reload(id);
    });
  }

  reload(id = this.projectId()): void {
    this.loading.set(true);
    this.svc.report(id).subscribe({
      next: (r) => { this.report.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được báo cáo dự án'); this.loading.set(false); }
    });
  }
}
