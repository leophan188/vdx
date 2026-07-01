import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { StatCard } from '../../shared/stat-card/stat-card';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { BurndownChart } from './burndown-chart';
import { formatThousands } from '../../shared/format';
import {
  ProjectService, Project, ProjectReport, ProjectStatus, Burndown, TaskStatus, TaskType
} from '../../core/project.service';

interface StatusBar { status: TaskStatus; label: string; color: string; count: number; pct: number; }
interface TypeStat { type: TaskType; label: string; badge: string; count: number; }

/**
 * Tab "Tổng quan" HỢP NHẤT (selector app-prj-overview).
 * Gộp tóm tắt dự án (get) + báo cáo (report: tiến độ, est/spent, bug, overdue,
 * byStatus/byType/byAssignee) + biểu đồ BURNDOWN (burndown). Bố cục nhóm:
 * "Tiến độ" · "Burndown" · "Phân bổ" · "Theo người".
 */
@Component({
  selector: 'app-prj-overview',
  standalone: true,
  imports: [StatCard, DataGrid, GridCellDirective, EmployeeChip, BurndownChart],
  templateUrl: './overview.html',
  styles: [`
    .prj-ov { display: grid; gap: var(--space-4); }

    .prj-ov__head { display: flex; flex-wrap: wrap; align-items: flex-start;
      justify-content: space-between; gap: var(--space-3); padding: var(--space-5);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); }
    .prj-ov__title { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap;
      margin: 0 0 var(--space-2); font-size: var(--font-size-xl, 1.25rem); }
    .prj-ov__code { font-size: var(--font-size-sm); font-weight: 600; color: var(--color-text-muted);
      background: var(--color-surface-alt); padding: 2px var(--space-2); border-radius: var(--radius-sm); }
    .prj-ov__meta { display: flex; flex-wrap: wrap; gap: var(--space-4);
      color: var(--color-text-muted); font-size: var(--font-size-sm); }
    .prj-ov__meta b { color: var(--color-text); font-weight: 600; }

    /* Tiêu đề nhóm */
    .prj-ov__section-title { margin: var(--space-2) 0 0; font-size: var(--text-sm, .85rem);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .04em;
      color: var(--color-text-muted); }

    /* Tiến độ */
    .prj-ov__progress { padding: var(--space-5); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); }
    .prj-ov__progress-top { display: flex; align-items: baseline; justify-content: space-between;
      margin-bottom: var(--space-3); }
    .prj-ov__progress-label { font-weight: 600; }
    .prj-ov__progress-sub { color: var(--color-text-muted); font-size: var(--font-size-sm); }
    .prj-ov__progress-pct { font-size: 1.75rem; font-weight: 700; color: var(--color-primary);
      line-height: 1; }
    .prj-ov__bar { height: 16px; border-radius: var(--radius-full); overflow: hidden;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .prj-ov__bar-fill { height: 100%; border-radius: var(--radius-full);
      background: linear-gradient(90deg, var(--status-active), var(--status-done));
      transition: width .3s ease; }

    .prj-ov__stats { display: grid; gap: var(--space-3);
      grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); }

    /* Phân bổ (byStatus / byType) */
    .prj-ov__panels { display: grid; gap: var(--space-4);
      grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); }
    .prj-ov__panel { padding: var(--space-4); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); box-shadow: var(--shadow-sm); }
    .prj-ov__panel-title { margin: 0 0 var(--space-3); font-size: var(--text-sm, .85rem);
      font-weight: var(--weight-semibold); color: var(--color-text-muted); }

    .prj-ov__barrow { display: grid; grid-template-columns: 110px 1fr 40px; align-items: center;
      gap: var(--space-3); margin: var(--space-2) 0; font-size: var(--text-sm, .85rem); }
    .prj-ov__row-bar { height: 10px; border-radius: var(--radius-full);
      background: var(--color-surface-alt); overflow: hidden; }
    .prj-ov__row-fill { height: 100%; border-radius: var(--radius-full); }
    .prj-ov__row-val { text-align: right; color: var(--color-text-muted); }

    .prj-ov__types { display: flex; flex-wrap: wrap; gap: var(--space-2) var(--space-3); }
    .prj-ov__type { display: inline-flex; align-items: center; gap: var(--space-2); }
    .prj-ov__type-count { font-weight: var(--weight-semibold); color: var(--color-text); }

    .prj-ov__mini { display: flex; align-items: center; gap: var(--space-2); }
    .prj-ov__mini-bar { flex: 1; min-width: 60px; height: 8px; border-radius: var(--radius-full);
      background: var(--color-surface-alt); overflow: hidden; }
    .prj-ov__mini-fill { height: 100%; border-radius: var(--radius-full); background: var(--status-done); }

    /* Mô tả */
    .prj-ov__desc { padding: var(--space-5); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); }
    .prj-ov__desc h3 { margin: 0 0 var(--space-3); font-size: var(--font-size-md, 1rem); }
    .prj-ov__desc p { margin: 0; color: var(--color-text); line-height: 1.6; white-space: pre-wrap; }
    .prj-ov__desc .muted { color: var(--color-text-muted); font-style: italic; }

    .prj-ov__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
    .hint { color: var(--color-text-muted); }
  `]
})
export class PrjOverview {
  private svc = inject(ProjectService);

  readonly projectId = input.required<string>();

  readonly project = signal<Project | null>(null);
  readonly report = signal<ProjectReport | null>(null);
  readonly burndown = signal<Burndown | null>(null);
  readonly loading = signal(true);

  /** % hoàn thành ưu tiên report (chi tiết hơn), fallback Project.completionPct. */
  readonly pct = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.report()?.completionPct ?? this.project()?.completionPct ?? 0)))
  );

  readonly dateRange = computed(() => {
    const p = this.project();
    if (!p) return '—';
    const s = p.startDate || '?';
    const d = p.dueDate || '?';
    return s === '?' && d === '?' ? '—' : `${s} → ${d}`;
  });

  // ----- byStatus → thanh ngang -----
  private readonly statusMeta: { status: TaskStatus; label: string; color: string }[] = [
    { status: 'BACKLOG', label: 'Backlog', color: 'var(--color-text-muted)' },
    { status: 'TODO', label: 'Cần làm', color: 'var(--status-pending)' },
    { status: 'IN_PROGRESS', label: 'Đang làm', color: 'var(--status-active)' },
    { status: 'IN_REVIEW', label: 'Đang review', color: 'var(--color-info)' },
    { status: 'DONE', label: 'Hoàn thành', color: 'var(--status-done)' }
  ];

  readonly statusBars = computed<StatusBar[]>(() => {
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
      if (id) this.load(id);
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.svc.get(id).subscribe({
      next: (p) => this.project.set(p),
      error: () => this.project.set(null)
    });
    this.svc.report(id).subscribe({
      next: (r) => { this.report.set(r); this.loading.set(false); },
      error: () => { this.report.set(null); this.loading.set(false); }
    });
    this.svc.burndown(id).subscribe({
      next: (b) => this.burndown.set(b),
      error: () => this.burndown.set(null)
    });
  }

  /** Ngân sách (VND) — phân tách hàng nghìn (helper chung), '—' nếu trống. */
  formatBudget(n: number | null | undefined): string {
    return (n == null) ? '—' : formatThousands(n) + ' ₫';
  }
  /** Nỗ lực MM — 2 chữ số thập phân, '0' nếu trống. */
  formatMM(n: number | null | undefined): string {
    return (n == null ? 0 : n).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
  }
  /** Chênh lệch tuyệt đối nỗ lực thực tế vs kế hoạch (MM), làm tròn 2 chữ số. */
  effortVariance(p: Project): number {
    const plan = p.plannedEffortMm ?? 0;
    return Math.round(Math.abs(p.totalEffortMM - plan) * 100) / 100;
  }

  statusLabel(s: ProjectStatus | undefined): string {
    switch (s) {
      case 'PLANNING': return 'Lập kế hoạch';
      case 'ACTIVE': return 'Đang thực hiện';
      case 'ON_HOLD': return 'Tạm dừng';
      case 'DONE': return 'Hoàn thành';
      case 'CANCELLED': return 'Đã hủy';
      default: return '—';
    }
  }

  statusBadge(s: ProjectStatus | undefined): string {
    switch (s) {
      case 'PLANNING': return 'badge--pending';
      case 'ACTIVE': return 'badge--active';
      case 'ON_HOLD': return 'badge--neutral';
      case 'DONE': return 'badge--done';
      case 'CANCELLED': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }
}
