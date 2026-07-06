import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { StatCard } from '../../shared/stat-card/stat-card';
import { Modal } from '../../shared/modal/modal';
import {
  ProjectService, PeriodReport, ReportTaskItem, TaskStatus, TaskType, TaskPriority
} from '../../core/project.service';
import { WORK_CATS, catOf, WorkCat, TYPE_META } from '../work-stats';

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

/** Cột trạng thái cho ma trận loại × trạng thái. */
const STATUS_META: { key: TaskStatus; label: string }[] = [
  { key: 'BACKLOG', label: 'Backlog' },
  { key: 'TODO', label: 'Cần làm' },
  { key: 'IN_PROGRESS', label: 'Đang làm' },
  { key: 'IN_REVIEW', label: 'Kiểm thử' },
  { key: 'DONE', label: 'Hoàn thành' }
];

/** Mức ưu tiên (thống kê bug/issue). */
const PRIORITY_META: { key: TaskPriority; label: string; color: string }[] = [
  { key: 'URGENT', label: 'Khẩn cấp', color: 'var(--overdue, #e5484d)' },
  { key: 'HIGH', label: 'Cao', color: 'var(--status-pending, #d97706)' },
  { key: 'MEDIUM', label: 'Trung bình', color: 'var(--status-active, #2563eb)' },
  { key: 'LOW', label: 'Thấp', color: 'var(--color-text-muted, #64748b)' }
];

interface TypeStatusRow {
  key: WorkCat; label: string; icon: string; color: string;
  byStatus: Record<string, number>; total: number;
}
interface PriorityStat { key: TaskPriority; label: string; color: string; count: number; pct: number; }
interface PersonStat {
  userId: string | null; name: string;
  total: number; task: number; bug: number; issue: number; done: number;
  items: ReportTaskItem[];
}
interface BugPerson { userId: string | null; name: string; count: number; items: ReportTaskItem[]; }

/**
 * Báo cáo Daily & Weekly (selector app-prj-reports-period).
 * Ngoài số liệu tổng quan + 4 khối danh sách (thu gọn được), bổ sung:
 *  1) Ma trận Task/Bug/Issue × trạng thái.
 *  2) Bug/Issue theo mức ưu tiên.
 *  3) Các trạng thái công việc (Đã xong/Trễ/Đang làm/Sắp làm) — mỗi phần thu gọn được.
 *  4) Thống kê theo nhân sự; bấm 1 người → popup danh sách công việc của người đó.
 */
@Component({
  selector: 'app-prj-reports-period',
  imports: [DataGrid, GridCellDirective, EmployeeChip, StatCard, Modal],
  templateUrl: './reports-period.html',
  styles: [`
    .rpp { display: grid; gap: var(--space-4); font-size: var(--text-sm); color: var(--color-text); }

    .rpp__head { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .rpp__switch { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
    .rpp__switch button { border: 0; background: var(--color-surface); color: var(--color-text-muted);
      padding: 0 var(--space-4); height: var(--control-h-sm); font: inherit; cursor: pointer; }
    .rpp__switch button + button { border-left: 1px solid var(--color-border); }
    .rpp__switch button.is-active { background: var(--color-primary); color: var(--color-text-invert); font-weight: var(--weight-medium); }
    .rpp__period { font-weight: var(--weight-semibold); color: var(--color-text); }

    .rpp__hero { display: grid; gap: var(--space-2); padding: var(--space-5);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); }
    .rpp__hero-top { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-3); }
    .rpp__hero-label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: var(--weight-semibold); }
    .rpp__hero-pct { font-size: 28px; font-weight: var(--weight-semibold); color: var(--color-primary); line-height: 1; }
    .rpp__hero-bar { height: 16px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rpp__hero-fill { height: 100%; border-radius: var(--radius-full);
      background: linear-gradient(90deg, var(--status-active), var(--status-done)); transition: width .3s ease; }
    .rpp__stats { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); }

    /* Section chung (thu gọn được) */
    .rpp__sec { border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); overflow: hidden; }
    .rpp__sec-head { display: flex; align-items: center; gap: var(--space-2); width: 100%;
      padding: var(--space-3) var(--space-4); background: var(--color-surface); border: 0; cursor: pointer;
      font: inherit; color: var(--color-text); font-weight: var(--weight-semibold); text-align: left; }
    .rpp__sec-head:hover { background: var(--color-surface-alt); }
    .rpp__sec-caret { transition: transform .15s ease; color: var(--color-text-muted); }
    .rpp__sec-caret.is-collapsed { transform: rotate(-90deg); }
    .rpp__sec-count { margin-left: auto; font-size: var(--text-xs); font-weight: var(--weight-medium);
      color: var(--color-text-muted); background: var(--color-surface-alt); padding: 1px var(--space-2); border-radius: var(--radius-full); }
    .rpp__sec-body { padding: var(--space-4); border-top: 1px solid var(--color-border); display: grid; gap: var(--space-3); }

    /* Ma trận loại × trạng thái */
    .rpp__matrix { display: grid; gap: 2px; overflow-x: auto; }
    .rpp__mrow { display: grid; grid-template-columns: minmax(140px, 1.4fr) repeat(5, minmax(58px, 1fr)) minmax(60px, .8fr);
      align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-md); font-variant-numeric: tabular-nums; }
    .rpp__mrow > span:not(.rpp__mname) { text-align: center; }
    .rpp__mrow:not(.rpp__mrow--head) { background: var(--color-surface-alt); }
    .rpp__mrow--head { color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .03em; }
    .rpp__mname { display: inline-flex; align-items: center; gap: var(--space-2); font-weight: var(--weight-medium); }
    .rpp__mdot { width: 8px; height: 8px; border-radius: 50%; background: var(--cat-color, var(--color-primary)); }
    .rpp__mtotal { font-weight: var(--weight-semibold); }
    .rpp__zero { color: var(--color-text-muted); opacity: .5; }

    /* Ưu tiên bug/issue */
    .rpp__prio { display: grid; gap: var(--space-2); }
    .rpp__prio-row { display: grid; grid-template-columns: 110px 1fr 44px; align-items: center; gap: var(--space-3); }
    .rpp__prio-name { display: inline-flex; align-items: center; gap: var(--space-2); }
    .rpp__prio-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--p-color); }
    .rpp__prio-bar { height: 10px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rpp__prio-fill { height: 100%; border-radius: var(--radius-full); background: var(--p-color); }
    .rpp__prio-val { text-align: right; font-variant-numeric: tabular-nums; font-weight: var(--weight-semibold); }
    .rpp__empty-note { color: var(--color-text-muted); font-size: var(--text-sm); }

    /* Theo nhân sự */
    .rpp__people { display: grid; gap: 2px; }
    .rpp__prow { display: grid; grid-template-columns: minmax(180px, 2fr) repeat(4, minmax(60px, 1fr)) minmax(66px, .9fr);
      align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); border-radius: var(--radius-md);
      font-variant-numeric: tabular-nums; }
    .rpp__prow > span:not(.rpp__pname) { text-align: center; }
    .rpp__prow--head { color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .03em; }
    button.rpp__prow { width: 100%; border: 0; background: var(--color-surface-alt); cursor: pointer;
      font: inherit; color: var(--color-text); text-align: left; }
    button.rpp__prow:hover { background: var(--color-primary-soft); color: var(--color-primary); }
    .rpp__pname { display: inline-flex; align-items: center; gap: var(--space-2); font-weight: var(--weight-medium); }
    .rpp__ptotal { font-weight: var(--weight-semibold); }
    .rpp__pchev { color: var(--color-text-muted); }

    /* Bug/Issue theo nhân sự: tester log vs dev bị log */
    .rpp__bugcols { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
    .rpp__bugcol-title { font-size: var(--text-sm); font-weight: var(--weight-semibold);
      color: var(--color-text); margin: 0 0 var(--space-2); display: flex; align-items: center; gap: 6px; }
    .rpp__bugrow { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: var(--space-2); width: 100%;
      padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--color-surface-alt);
      border: 0; cursor: pointer; font: inherit; color: var(--color-text); text-align: left; margin-bottom: 2px; }
    .rpp__bugrow:hover { background: var(--color-primary-soft); color: var(--color-primary); }
    .rpp__bugrow-name { display: inline-flex; align-items: center; gap: var(--space-2); min-width: 0; }
    .rpp__bugrank { color: var(--color-text-muted); font-size: var(--text-xs); min-width: 18px; }
    .rpp__bugcount { font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums;
      background: color-mix(in srgb, var(--overdue, #e5484d) 15%, transparent); color: var(--overdue, #e5484d);
      padding: 0 9px; border-radius: 999px; font-size: var(--text-xs); }

    /* Dòng phụ chuỗi cha trong lưới danh sách */
    .rpp__li-parent { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 520px; }
    /* Số lượng đã hiện ở tiêu đề section → ẩn "N mục" của lưới bên trong (tránh trùng). */
    .rpp__sec-body ::ng-deep .grid__count { display: none; }

    .rpp__pct-cell { display: flex; align-items: center; gap: var(--space-2); }
    .rpp__mini-bar { flex: 1; min-width: 56px; height: 8px; border-radius: var(--radius-full);
      background: var(--color-surface-alt); overflow: hidden; }
    .rpp__mini-fill { height: 100%; border-radius: var(--radius-full); background: var(--status-done); }
    .rpp__pct-val { font-size: var(--text-xs); color: var(--color-text-muted); min-width: 32px; text-align: right; }

    .rpp__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
    .rpp__type-badge { font-size: var(--text-xs); font-weight: 700; padding: 1px 7px; border-radius: 999px;
      color: var(--tb-color); background: color-mix(in srgb, var(--tb-color) 14%, transparent);
      border: 1px solid color-mix(in srgb, var(--tb-color) 36%, transparent); }
  `]
})
export class PrjReportsPeriod {
  readonly projectId = input.required<string>();

  private svc = inject(ProjectService);

  readonly period = signal<Period>('daily');
  readonly report = signal<PeriodReport | null>(null);
  readonly loading = signal(true);

  readonly statusMeta = STATUS_META;
  readonly priorityMeta = PRIORITY_META;

  /** Phần đang thu gọn (ẩn). Mặc định mở hết. */
  readonly collapsed = signal<Set<string>>(new Set());
  isCollapsed(key: string): boolean { return this.collapsed().has(key); }
  toggle(key: string): void {
    const s = new Set(this.collapsed());
    s.has(key) ? s.delete(key) : s.add(key);
    this.collapsed.set(s);
  }

  /** Popup danh sách công việc chi tiết (theo nhân sự / theo bug). */
  readonly detailModal = signal<{ title: string; items: ReportTaskItem[] } | null>(null);

  readonly pct = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.report()?.overview.completionPct ?? 0)))
  );

  /** Hợp nhất mọi công việc trong kỳ (khử trùng theo taskId). */
  readonly allItems = computed<ReportTaskItem[]>(() => {
    const r = this.report();
    if (!r) return [];
    const map = new Map<string, ReportTaskItem>();
    for (const it of [...r.done, ...r.inProgress, ...r.upcoming, ...r.overdue]) map.set(it.taskId, it);
    return [...map.values()];
  });

  // ===== (1) Ma trận Task/Bug/Issue × trạng thái =====
  readonly typeStatusRows = computed<TypeStatusRow[]>(() => {
    const items = this.allItems();
    return WORK_CATS.map((c) => {
      const list = items.filter((i) => catOf(i.type) === c.key);
      const byStatus: Record<string, number> = {};
      for (const s of STATUS_META) byStatus[s.key] = list.filter((i) => i.status === s.key).length;
      return { key: c.key, label: c.label, icon: c.icon, color: c.color, byStatus, total: list.length };
    });
  });

  // ===== (2) Bug/Issue theo mức ưu tiên =====
  readonly bugPriority = computed<PriorityStat[]>(() => {
    const bugs = this.allItems().filter((i) => i.type === 'BUG' || i.type === 'ISSUE');
    const max = Math.max(1, ...PRIORITY_META.map((p) => bugs.filter((b) => b.priority === p.key).length));
    return PRIORITY_META.map((p) => {
      const count = bugs.filter((b) => b.priority === p.key).length;
      return { key: p.key, label: p.label, color: p.color, count, pct: Math.round((count / max) * 100) };
    });
  });
  readonly bugTotal = computed(() => this.allItems().filter((i) => i.type === 'BUG' || i.type === 'ISSUE').length);

  // ===== (4) Theo nhân sự =====
  readonly byPerson = computed<PersonStat[]>(() => {
    const map = new Map<string, PersonStat>();
    for (const it of this.allItems()) {
      const key = it.assigneeUserId || it.assigneeName || '__none__';
      let p = map.get(key);
      if (!p) {
        p = { userId: it.assigneeUserId, name: it.assigneeName || '— Chưa gán —',
          total: 0, task: 0, bug: 0, issue: 0, done: 0, items: [] };
        map.set(key, p);
      }
      p.items.push(it);
      p.total++;
      if (it.type === 'BUG') p.bug++;
      else if (it.type === 'ISSUE') p.issue++;
      else if (catOf(it.type) === 'TASK') p.task++;
      if (it.status === 'DONE') p.done++;
    }
    return [...map.values()].sort((a, b) => b.total - a.total);
  });

  // ===== Bug/Issue theo nhân sự: tester đã LOG vs dev BỊ LOG =====
  readonly bugsInPeriod = computed<ReportTaskItem[]>(() =>
    this.allItems().filter((i) => i.type === 'BUG' || i.type === 'ISSUE'));

  /** Tester ĐÃ log bug (nhóm theo người tạo/report). */
  readonly bugByReporter = computed<BugPerson[]>(() => this.groupBugs('reporter'));
  /** Dev BỊ log bug (nhóm theo người thực hiện). */
  readonly bugByAssignee = computed<BugPerson[]>(() => this.groupBugs('assignee'));

  private groupBugs(kind: 'reporter' | 'assignee'): BugPerson[] {
    const map = new Map<string, BugPerson>();
    for (const b of this.bugsInPeriod()) {
      const id = kind === 'reporter' ? b.reporterUserId : b.assigneeUserId;
      const name = kind === 'reporter' ? b.reporterName : b.assigneeName;
      const key = id || name || '__none__';
      let p = map.get(key);
      if (!p) { p = { userId: id, name: name || '— Không rõ —', count: 0, items: [] }; map.set(key, p); }
      p.count++;
      p.items.push(b);
    }
    return [...map.values()].sort((a, b) => b.count - a.count);
  }

  openBugPerson(p: BugPerson, prefix: string): void {
    this.detailModal.set({ title: prefix + p.name, items: p.items });
  }

  // ===== (3) 4 khối trạng thái =====
  readonly blocks = computed<ReportBlock[]>(() => {
    const r = this.report();
    return [
      { key: 'done', icon: '✅', title: 'Đã hoàn thành', rows: r?.done ?? [], emptyText: 'Chưa có công việc nào hoàn thành trong kỳ.' },
      { key: 'overdue', icon: '⛔', title: 'Trễ hạn', rows: r?.overdue ?? [], emptyText: 'Không có công việc trễ hạn. 🎉' },
      { key: 'inProgress', icon: '🔄', title: 'Đang làm', rows: r?.inProgress ?? [], emptyText: 'Không có công việc đang làm.' },
      { key: 'upcoming', icon: '📋', title: 'Sắp làm', rows: r?.upcoming ?? [], emptyText: 'Không có công việc sắp tới.' }
    ];
  });

  /** Cột cho lưới danh sách task (khối trạng thái). */
  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '90px', sortable: true },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'assigneeName', header: 'Người làm', width: '180px' },
    { key: 'estimateHours', header: 'Est (h)', align: 'center', width: '90px', sortable: true },
    { key: 'dueDate', header: 'Hạn', align: 'center', width: '120px', sortable: true },
    { key: 'progressPct', header: '% hoàn thành', width: '170px', sortable: true }
  ];

  /** Cột cho popup chi tiết theo nhân sự (thêm Loại + Trạng thái). */
  readonly personCols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '90px', sortable: true },
    { key: 'type', header: 'Loại', width: '92px' },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '120px' },
    { key: 'dueDate', header: 'Hạn', align: 'center', width: '110px', sortable: true },
    { key: 'progressPct', header: '% HT', width: '150px', sortable: true }
  ];

  constructor() {
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

  openPerson(p: PersonStat): void { this.detailModal.set({ title: 'Công việc của ' + p.name, items: p.items }); }

  typeColor(t: TaskType): string { return TYPE_META[t]?.color ?? 'var(--color-primary)'; }

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
      case 'BACKLOG': return 'Backlog';
      case 'TODO': return 'Cần làm';
      case 'IN_PROGRESS': return 'Đang làm';
      case 'IN_REVIEW': return 'Kiểm thử';
      case 'DONE': return 'Hoàn thành';
      default: return s;
    }
  }
  typeLabel(t: TaskType): string { return TYPE_META[t]?.short ?? t; }
}
