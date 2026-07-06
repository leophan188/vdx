import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { StatCard } from '../../shared/stat-card/stat-card';
import {
  ProjectService, PeriodReport, ReportTaskItem, TaskStatus, TaskType
} from '../../core/project.service';
import { WORK_CATS, catOf, WorkCat } from '../work-stats';

/** Một dòng thống kê theo loại (Task/Bug/Issue) cho kỳ báo cáo. */
interface CatRow {
  key: WorkCat; label: string; icon: string; color: string;
  done: number; doing: number; upcoming: number; overdue: number; total: number;
}

/** Kỳ báo cáo đang xem. */
type Period = 'daily' | 'weekly';

/** Một khối danh sách task (Đã xong / Đang làm / Sắp làm / Trễ hạn). */
interface ReportBlock {
  key: 'done' | 'inProgress' | 'upcoming' | 'overdue';
  icon: string;
  title: string;
  rows: ReportTaskItem[];
  emptyText: string;
}

/**
 * Báo cáo Daily & Weekly cho module Quản lý dự án (selector app-prj-reports-period).
 * 2 nút chuyển Hàng ngày / Hàng tuần → reportDaily/reportWeekly. Hiện periodLabel,
 * khu Số liệu tổng quan (overview: thanh % lớn + stat-card) và 4 khối danh sách task
 * (✅ Đã xong · 🔄 Đang làm · 📋 Sắp làm · ⛔ Trễ hạn) — mỗi khối là 1 data-grid.
 * Thuần CSS, không thư viện chart, không CDK.
 */
@Component({
  selector: 'app-prj-reports-period',
  imports: [DataGrid, GridCellDirective, EmployeeChip, StatCard],
  templateUrl: './reports-period.html',
  styles: [`
    .rpp { display: grid; gap: var(--space-4); font-size: var(--text-sm); color: var(--color-text); }

    /* Đầu trang: nút chuyển kỳ + nhãn kỳ */
    .rpp__head { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .rpp__switch { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
    .rpp__switch button { border: 0; background: var(--color-surface); color: var(--color-text-muted);
      padding: 0 var(--space-4); height: var(--control-h-sm); font: inherit; cursor: pointer; }
    .rpp__switch button + button { border-left: 1px solid var(--color-border); }
    .rpp__switch button.is-active { background: var(--color-primary); color: var(--color-text-invert); font-weight: var(--weight-medium); }
    .rpp__period { font-weight: var(--weight-semibold); color: var(--color-text); }

    /* Khu tổng quan: thanh % lớn + stat-card */
    .rpp__hero {
      display: grid; gap: var(--space-2); padding: var(--space-5);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm);
    }
    .rpp__hero-top { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-3); }
    .rpp__hero-label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: var(--weight-semibold); }
    .rpp__hero-pct { font-size: 28px; font-weight: var(--weight-semibold); color: var(--color-primary); line-height: 1; }
    .rpp__hero-bar { height: 16px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rpp__hero-fill { height: 100%; border-radius: var(--radius-full);
      background: linear-gradient(90deg, var(--status-active), var(--status-done)); transition: width .3s ease; }

    .rpp__stats { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); }

    /* Các khối danh sách */
    .rpp__blocks { display: grid; gap: var(--space-4); }
    .rpp__block { display: grid; gap: var(--space-2); }
    .rpp__block-title { display: flex; align-items: center; gap: var(--space-2); margin: 0;
      font-size: var(--text-base); font-weight: var(--weight-semibold); }
    .rpp__count { font-size: var(--text-xs); font-weight: var(--weight-medium); color: var(--color-text-muted);
      background: var(--color-surface-alt); padding: 1px var(--space-2); border-radius: var(--radius-full); }

    /* Ô % trong lưới */
    .rpp__pct-cell { display: flex; align-items: center; gap: var(--space-2); }
    .rpp__mini-bar { flex: 1; min-width: 56px; height: 8px; border-radius: var(--radius-full);
      background: var(--color-surface-alt); overflow: hidden; }
    .rpp__mini-fill { height: 100%; border-radius: var(--radius-full); background: var(--status-done); }
    .rpp__pct-val { font-size: var(--text-xs); color: var(--color-text-muted); min-width: 32px; text-align: right; }

    .rpp__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }

    /* Thống kê theo loại (Task/Bug/Issue) trong kỳ */
    .rpp__cats { border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); padding: var(--space-4); }
    .rpp__cats-title { margin: 0 0 var(--space-3); font-size: var(--text-sm);
      font-weight: var(--weight-semibold); color: var(--color-text-muted); }
    .rpp__cats-grid { display: grid; gap: 2px; overflow-x: auto; }
    .rpp__cats-row { display: grid; grid-template-columns: minmax(150px, 1.6fr) repeat(5, minmax(64px, 1fr));
      align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-md); font-size: var(--text-sm); font-variant-numeric: tabular-nums; }
    .rpp__cats-row > span:not(.rpp__cats-name) { text-align: center; }
    .rpp__cats-row:not(.rpp__cats-row--head) { background: var(--color-surface-alt); }
    .rpp__cats-row--head { color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .03em; }
    .rpp__cats-name { display: inline-flex; align-items: center; gap: var(--space-2); font-weight: var(--weight-medium); }
    .rpp__cats-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--cat-color, var(--color-primary)); }
    .rpp__cats-total { font-weight: var(--weight-semibold); }
    .rpp__cats-over { color: var(--overdue, #e5484d); font-weight: var(--weight-semibold); }
  `]
})
export class PrjReportsPeriod {
  readonly projectId = input.required<string>();

  private svc = inject(ProjectService);

  // ----- Trạng thái -----
  readonly period = signal<Period>('daily');
  readonly report = signal<PeriodReport | null>(null);
  readonly loading = signal(true);

  /** % hoàn thành đã kẹp 0..100 cho thanh lớn. */
  readonly pct = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.report()?.overview.completionPct ?? 0)))
  );

  /** 4 khối danh sách task (theo thứ tự hiển thị). */
  readonly blocks = computed<ReportBlock[]>(() => {
    const r = this.report();
    return [
      { key: 'done', icon: '✅', title: 'Đã xong', rows: r?.done ?? [], emptyText: 'Chưa có công việc nào hoàn thành trong kỳ.' },
      { key: 'inProgress', icon: '🔄', title: 'Đang làm', rows: r?.inProgress ?? [], emptyText: 'Không có công việc đang làm.' },
      { key: 'upcoming', icon: '📋', title: 'Sắp làm', rows: r?.upcoming ?? [], emptyText: 'Không có công việc sắp tới.' },
      { key: 'overdue', icon: '⛔', title: 'Trễ hạn', rows: r?.overdue ?? [], emptyText: 'Không có công việc trễ hạn. 🎉' }
    ];
  });

  /** Thống kê RIÊNG Task / Bug / Issue theo kỳ — đếm trong từng nhóm bucket (đã xong/đang làm/sắp/trễ). */
  readonly catRows = computed<CatRow[]>(() => {
    const r = this.report();
    const cnt = (items: ReportTaskItem[], key: WorkCat) => (items ?? []).filter((i) => catOf(i.type) === key).length;
    return WORK_CATS.map((c) => {
      const done = cnt(r?.done ?? [], c.key);
      const doing = cnt(r?.inProgress ?? [], c.key);
      const upcoming = cnt(r?.upcoming ?? [], c.key);
      const overdue = cnt(r?.overdue ?? [], c.key);
      const ids = new Set<string>();
      for (const it of [...(r?.done ?? []), ...(r?.inProgress ?? []), ...(r?.upcoming ?? []), ...(r?.overdue ?? [])]) {
        if (catOf(it.type) === c.key) ids.add(it.taskId);
      }
      return { key: c.key, label: c.label, icon: c.icon, color: c.color, done, doing, upcoming, overdue, total: ids.size };
    });
  });

  /** Cột chung cho cả 4 lưới task. */
  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '90px', sortable: true },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'assigneeName', header: 'Người làm', width: '180px' },
    { key: 'estimateHours', header: 'Est (h)', align: 'center', width: '90px', sortable: true },
    { key: 'dueDate', header: 'Hạn', align: 'center', width: '120px', sortable: true },
    { key: 'progressPct', header: '% hoàn thành', width: '170px', sortable: true }
  ];

  constructor() {
    // Tải lại khi đổi projectId hoặc đổi kỳ (effect đọc 2 signal).
    effect(() => {
      const pid = this.projectId();
      const p = this.period();
      if (!pid) return;
      this.loading.set(true);
      const src$ = p === 'weekly' ? this.svc.reportWeekly(pid) : this.svc.reportDaily(pid);
      src$.subscribe({
        next: (r) => { this.report.set(r); this.loading.set(false); },
        error: () => { this.report.set(null); this.loading.set(false); }
      });
    });
  }

  setPeriod(p: Period): void { this.period.set(p); }

  clampPct(v: number): number { return Math.max(0, Math.min(100, Math.round(v ?? 0))); }

  /** Badge trạng thái (dùng class .badge--* design-system). */
  statusBadge(s: TaskStatus): string {
    switch (s) {
      case 'BACKLOG': return 'badge--neutral';
      case 'TODO': return 'badge--pending';
      case 'IN_PROGRESS': return 'badge--active';
      case 'IN_REVIEW': return 'badge--active';
      case 'DONE': return 'badge--done';
      default: return 'badge--neutral';
    }
  }
  statusLabel(s: TaskStatus): string {
    switch (s) {
      case 'BACKLOG': return 'Tồn đọng';
      case 'TODO': return 'Chờ làm';
      case 'IN_PROGRESS': return 'Đang làm';
      case 'IN_REVIEW': return 'Chờ duyệt';
      case 'DONE': return 'Hoàn thành';
      default: return s;
    }
  }
  typeLabel(t: TaskType): string {
    switch (t) {
      case 'EPIC': return 'Epic';
      case 'STORY': return 'Story';
      case 'TASK': return 'Task';
      case 'SUBTASK': return 'Subtask';
      case 'BUG': return 'Bug';
      case 'ISSUE': return 'Issue';
      default: return t;
    }
  }
}
