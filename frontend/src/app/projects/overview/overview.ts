import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { StatCard } from '../../shared/stat-card/stat-card';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { formatThousands } from '../../shared/format';
import { categoryStats, CatStat } from '../work-stats';
import {
  ProjectService, Project, ProjectReport, ProjectStatus, TaskStatus, TaskType, ProjectTask,
  ProjectMember, ProjectActivityItem
} from '../../core/project.service';

interface StatusBar { status: TaskStatus; label: string; color: string; count: number; pct: number; }
interface TypeStat { type: TaskType; label: string; badge: string; count: number; }

/**
 * Tab "Tổng quan" HỢP NHẤT (selector app-prj-overview).
 * Gộp tóm tắt dự án (get) + báo cáo (report: tiến độ, est/spent, bug, overdue,
 * byStatus/byType/byAssignee) + thống kê Task/Bug/Issue. Bố cục nhóm:
 * "Tiến độ" · "Phân bổ" · "Theo người".
 */
@Component({
  selector: 'app-prj-overview',
  standalone: true,
  imports: [StatCard, EmployeeChip],
  templateUrl: './overview.html',
  styles: [`
    .ov2 { display: grid; gap: var(--space-4); }

    /* Header */
    .ov2__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
      gap: var(--space-3); padding: var(--space-4) var(--space-5); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); }
    .ov2__title { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; margin: 0 0 var(--space-2); font-size: 1.35rem; }
    .ov2__code { font-size: .8rem; font-weight: 600; color: var(--color-text-muted); background: var(--color-surface-alt);
      padding: 2px 8px; border-radius: var(--radius-sm); }
    .ov2__meta { display: flex; flex-wrap: wrap; gap: var(--space-4); color: var(--color-text-muted); font-size: var(--text-sm); }
    .ov2__meta b { color: var(--color-text); font-weight: 600; }

    /* Panel trên: tiến độ + lưới thẻ */
    .ov2__top { display: grid; gap: var(--space-3); grid-template-columns: minmax(240px, 300px) 1fr; align-items: stretch; }
    @media (max-width: 720px) { .ov2__top { grid-template-columns: 1fr; } }
    .ov2__progress { padding: var(--space-5); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); display: flex; flex-direction: column; gap: var(--space-2); justify-content: center; }
    .ov2__progress-label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: 600; }
    .ov2__progress-pct { font-size: 2.4rem; font-weight: 800; color: var(--color-primary); line-height: 1; }
    .ov2__progress-sub { font-size: var(--text-xs); color: var(--color-text-muted); }
    .ov2__bar { height: 10px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; margin-top: var(--space-1); }
    .ov2__bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--status-active), var(--status-done)); transition: width .3s; }
    .ov2__stats { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(155px, 1fr)); }

    .ov2__h { margin: var(--space-2) 0 0; font-size: 1rem; font-weight: var(--weight-semibold); }

    /* Thẻ theo loại (Công việc / Bug / Issue) */
    .ov2__cats { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
    .ov2__cat { padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; gap: var(--space-3); }
    .ov2__cat-head { display: flex; align-items: center; gap: var(--space-2); }
    .ov2__cat-ico { font-size: 1.1rem; }
    .ov2__cat-name { font-weight: var(--weight-semibold); }
    .ov2__cat-total { margin-left: auto; font-size: 1.8rem; font-weight: 800; line-height: 1; color: var(--cat, var(--color-primary)); font-variant-numeric: tabular-nums; }
    .ov2__cat-bar { height: 7px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .ov2__cat-fill { height: 100%; border-radius: 999px; background: var(--cat, var(--status-done)); }
    .ov2__cat-sub { font-size: var(--text-xs); color: var(--color-text-muted); }
    .ov2__cat-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
    .ov2__cat-list li { display: flex; align-items: center; justify-content: space-between; padding: 5px 10px;
      border-radius: var(--radius-md); background: var(--color-surface-alt); font-size: var(--text-sm); }
    .ov2__cat-list b { font-variant-numeric: tabular-nums; }
    .ov2__cat-link { align-self: flex-start; margin-top: 2px; border: 0; background: none; color: var(--color-primary);
      cursor: pointer; font: inherit; font-size: var(--text-sm); font-weight: 600; padding: 0; }
    .ov2__cat-link:hover { text-decoration: underline; }

    /* Panel chung + thông tin dự án */
    .ov2__panel { padding: var(--space-5); border: 1px solid var(--color-border); border-radius: var(--radius-lg); background: var(--color-surface); }
    .ov2__panel-h { margin: 0 0 var(--space-4); font-size: 1rem; font-weight: var(--weight-semibold); }
    .ov2__info { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
    .ov2__info-k { font-size: var(--text-xs); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .03em; }
    .ov2__info-v { font-weight: 600; margin-top: 2px; }

    /* 2 cột đáy */
    .ov2__two { display: grid; gap: var(--space-4); grid-template-columns: 1fr 1fr; }
    @media (max-width: 860px) { .ov2__two { grid-template-columns: 1fr; } }

    .ov2__act { display: flex; flex-direction: column; }
    .ov2__act-row { display: flex; gap: var(--space-2); padding: var(--space-2) 0; border-bottom: 1px solid var(--color-border); font-size: var(--text-sm); }
    .ov2__act-row:last-child { border-bottom: 0; }
    .ov2__act-ico { flex: 0 0 auto; }
    .ov2__act-body { flex: 1; min-width: 0; }
    .ov2__act-main { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ov2__act-main b { font-weight: 600; }
    .ov2__act-time { color: var(--color-text-muted); font-size: var(--text-xs); }

    .ov2__mem { display: flex; flex-direction: column; }
    .ov2__mem-row { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) 0; border-bottom: 1px solid var(--color-border); }
    .ov2__mem-row:last-child { border-bottom: 0; }
    .ov2__mem-role { margin-left: auto; }
    .ov2__mem-md { color: var(--color-text-muted); font-size: var(--text-xs); min-width: 56px; text-align: right; }

    .ov2__empty { color: var(--color-text-muted); font-style: italic; font-size: var(--text-sm); padding: var(--space-2) 0; }
    .ov2__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
    .ov2__actions { display: flex; gap: var(--space-2); align-items: flex-start; }

    /* Tiến độ EPIC / Story */
    .ov2__es-row { display: flex; align-items: center; gap: var(--space-3); padding: 6px 0; border-bottom: 1px solid var(--color-border); }
    .ov2__es-row:last-child { border-bottom: 0; }
    .ov2__es-title { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ov2__es-bar { width: 160px; flex: 0 0 auto; height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .ov2__es-fill { height: 100%; border-radius: 999px; background: var(--status-done); }
    .ov2__es-pct { flex: 0 0 auto; min-width: 44px; text-align: right; font-variant-numeric: tabular-nums; }

    /* ===== TRANG IN / PDF Tổng quan (báo cáo khách) ===== */
    .rp-overlay { position: fixed; inset: 0; z-index: 1000; overflow: auto; background: #5b6472;
      padding: 20px 12px 40px; display: flex; flex-direction: column; align-items: center; }
    .ovp-bar { position: sticky; top: 0; z-index: 2; width: 210mm; max-width: 100%; display: flex; align-items: center; gap: 10px;
      padding: 8px 12px; margin-bottom: 14px; background: #1f2937; color: #e5e7eb; border-radius: 8px; font-size: 12px; }
    .rp-page { width: 210mm; max-width: 100%; background: #fff; box-shadow: 0 6px 30px rgba(0,0,0,.35); padding: 12mm 12mm 14mm; }
    .ovp { color: #1f2937; font-family: system-ui, "Segoe UI", Roboto, sans-serif; font-size: 12px; }
    .ovp__head { background: #1e3a5f; color: #fff; border-radius: 8px; padding: 12px 16px; text-align: center; margin-bottom: 14px; }
    /* 3 thẻ số liệu màu — đồng bộ mẫu PDF báo cáo ngày/tuần */
    .ovp__cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 14px; }
    .ovp__card { border-radius: 10px; padding: 10px 12px; text-align: center; border: 1.5px solid;
      -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .ovp__card-lbl { font-size: 11px; font-weight: 800; padding: 4px 0; border-radius: 6px; color: #fff; margin: -10px -12px 8px; }
    .ovp__card-num { font-size: 30px; font-weight: 800; line-height: 1; }
    .ovp__card--done { border-color: #2ea05a; background: #eef8f1; color: #1e7e42; }
    .ovp__card--done .ovp__card-lbl { background: #2ea05a; }
    .ovp__card--doing { border-color: #1e50a0; background: #eef2fb; color: #1e50a0; }
    .ovp__card--doing .ovp__card-lbl { background: #1e50a0; }
    .ovp__card--total { border-color: #1e3a5f; background: #eef1f6; color: #1e3a5f; }
    .ovp__card--total .ovp__card-lbl { background: #1e3a5f; }
    .ovp__title { font-size: 18px; font-weight: 800; letter-spacing: .3px; }
    .ovp__proj { margin-top: 4px; font-size: 12px; opacity: .92; }
    .ovp__progress { display: flex; align-items: center; gap: 16px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px 16px; margin-bottom: 14px; }
    .ovp__pct { font-size: 30px; font-weight: 800; color: #1e50a0; line-height: 1; }
    .ovp__psub { font-size: 11px; color: #64748b; margin-top: 4px; }
    .ovp__bar { flex: 1; height: 12px; border-radius: 999px; background: #edf0f4; overflow: hidden; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .ovp__bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg,#2563eb,#22c55e); }
    .ovp__h { background: #1e3a5f; color: #fff; font-size: 12px; font-weight: 800; padding: 5px 10px; border-radius: 6px; margin: 14px 0 8px;
      -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .ovp__info { width: 100%; border-collapse: collapse; }
    .ovp__info th { background: #f1f5f9; text-align: left; font-weight: 700; color: #475569; padding: 6px 10px; border: 1px solid #e2e8f0; width: 18%; white-space: nowrap; }
    .ovp__info td { padding: 6px 10px; border: 1px solid #e2e8f0; font-weight: 600; }
    .ovp__stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .ovp__stat { border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 12px; display: flex; align-items: baseline; justify-content: space-between; }
    .ovp__stat-l { color: #64748b; font-size: 11px; }
    .ovp__stat b { font-size: 18px; font-weight: 800; }
    .ovp__cat { width: 100%; border-collapse: collapse; }
    .ovp__cat th { background: #eef2f7; color: #475569; font-size: 10px; text-transform: uppercase; font-weight: 800; padding: 6px; border: 1px solid #e2e8f0; text-align: center; }
    .ovp__cat td { padding: 6px; border: 1px solid #eef0f2; text-align: center; }
    .ovp__cat .l { text-align: left; }
    .ovp__cat th:last-child, .ovp__cat td:last-child { min-width: 96px; }
    .ovp__es td.l { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 0; }
    .ovp__es-tag { display: inline-block; font-size: 8px; font-weight: 800; padding: 1px 5px; border-radius: 4px;
      margin-right: 6px; background: #e3ecf9; color: #1e50a0; }
    .ovp__es-tag--epic { background: #efe6fb; color: #6b3fb0; }
    .ovp__pbar { position: relative; height: 14px; border-radius: 999px; background: #edf0f4; overflow: hidden; }
    .ovp__pbar-fill { position: absolute; left: 0; top: 0; height: 100%; border-radius: 999px; background: #3fbf6a; }
    .ovp__pbar span { position: relative; z-index: 1; font-size: 9px; font-weight: 700; color: #14532d; line-height: 14px; padding-right: 6px; display: block; text-align: right; }
    .ovp__foot { margin-top: 16px; text-align: center; font-size: 10px; color: #94a3b8; }
  `]
})
export class PrjOverview {
  private svc = inject(ProjectService);

  readonly projectId = input.required<string>();
  /** Chuyển sang tab khác (link "Xem chi tiết →"). */
  readonly openTab = output<string>();

  readonly project = signal<Project | null>(null);
  readonly report = signal<ProjectReport | null>(null);
  readonly tasks = signal<ProjectTask[]>([]);
  readonly members = signal<ProjectMember[]>([]);
  readonly activity = signal<ProjectActivityItem[]>([]);
  readonly loading = signal(true);

  /** Xuất báo cáo Tổng quan cho khách (Excel + PDF) — KHÔNG có người thực hiện & trễ hạn. */
  readonly exporting = signal(false);
  readonly printMode = signal(false);
  exportExcel(): void {
    if (this.exporting()) return;
    this.exporting.set(true);
    this.svc.exportOverview(this.projectId()).subscribe({
      next: (b) => { ProjectService.downloadBlob(b, 'tong-quan-' + (this.project()?.code || 'du-an') + '.xlsx'); this.exporting.set(false); },
      error: () => { this.exporting.set(false); }
    });
  }
  openPrint(): void { this.printMode.set(true); setTimeout(() => this.printNow(), 150); }
  printNow(): void {
    const prev = document.title;
    document.title = 'Tong quan - ' + (this.project()?.code || 'du an');
    const done = () => { document.title = prev; window.removeEventListener('afterprint', done); };
    window.addEventListener('afterprint', done);
    window.print();
  }
  /** "Chưa làm" = Cần làm + Backlog (gộp cho báo cáo khách). */
  notStarted(c: CatStat): number { return c.todo + c.backlog; }

  /** Hoạt động gần đây (8 mục mới nhất). */
  readonly recentActivity = computed(() => this.activity().slice(0, 8));
  /** Khách hàng suy từ tiền tố CHỮ của mã dự án (VCB26118 → VCB); '—' nếu không có. */
  readonly customer = computed(() => {
    const code = this.project()?.code ?? '';
    const m = code.match(/^[A-Za-z]+/);
    return m ? m[0].toUpperCase() : '—';
  });

  /** Thống kê RIÊNG BIỆT Task / Bug / Issue (tổng, xong, đang làm, chưa làm, trễ hạn, %). */
  readonly catStats = computed<CatStat[]>(() => categoryStats(this.tasks()));

  /** Tiến độ % EPIC/Story theo THỨ TỰ CÂY (Story nằm dưới Epic cha) — kèm level để thụt lề. */
  readonly epicStoryRows = computed(() => {
    const es = this.tasks().filter((t) => t.type === 'EPIC' || t.type === 'STORY');
    const esIds = new Set(es.map((t) => t.id));
    const childrenOf = new Map<string, ProjectTask[]>();
    for (const t of es) {
      const k = t.parentId && esIds.has(t.parentId) ? t.parentId : '';
      (childrenOf.get(k) ?? childrenOf.set(k, []).get(k)!).push(t);
    }
    const out: { code: string; title: string; type: string; pct: number; level: number }[] = [];
    const walk = (parentKey: string, level: number) => {
      for (const t of childrenOf.get(parentKey) ?? []) {
        out.push({ code: t.code, title: t.title, type: t.type,
          pct: Math.max(0, Math.min(100, Math.round(t.progressPct ?? 0))), level });
        walk(t.id, level + 1);
      }
    };
    walk('', 0);
    return out;
  });

  /** Bug/Issue của dự án — để kiểm soát chất lượng theo nhân sự. */
  private readonly bugList = computed(() => this.tasks().filter((t) => t.type === 'BUG' || t.type === 'ISSUE'));
  readonly bugCount = computed(() => this.bugList().length);
  /** Tester ĐÃ log bug (nhóm theo người tạo/report). */
  readonly bugByReporter = computed(() => this.rankBugs('reporter'));
  /** Dev BỊ log bug (nhóm theo người thực hiện). */
  readonly bugByAssignee = computed(() => this.rankBugs('assignee'));
  private rankBugs(kind: 'reporter' | 'assignee'): { name: string; count: number }[] {
    const map = new Map<string, { name: string; count: number }>();
    for (const b of this.bugList()) {
      const id = kind === 'reporter' ? b.reporterUserId : b.assigneeUserId;
      const name = kind === 'reporter' ? b.reporterName : b.assigneeName;
      const key = id || name || '__none__';
      const cur = map.get(key) ?? { name: name || '— Không rõ —', count: 0 };
      cur.count++;
      map.set(key, cur);
    }
    return [...map.values()].sort((a, b) => b.count - a.count);
  }

  /** % hoàn thành theo EST (ưu tiên report, fallback Project.completionPct). */
  readonly pct = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.report()?.completionPct ?? this.project()?.completionPct ?? 0)))
  );
  /** % hoàn thành theo SỐ LƯỢNG task lá (đếm). */
  readonly taskPct = computed(() => {
    const r = this.report();
    return r && r.leafTasks ? Math.round((r.leafDoneTasks / r.leafTasks) * 100) : 0;
  });

  /** Đang làm = IN_PROGRESS + IN_REVIEW (cho thẻ số liệu báo cáo). */
  readonly doingCount = computed(() => {
    const b = (this.report()?.byStatus ?? {}) as Record<string, number>;
    return (b['IN_PROGRESS'] || 0) + (b['IN_REVIEW'] || 0);
  });

  /** Est done/tổng — làm tròn cho gọn thẻ (tránh tràn "83.45 / 948.45"). */
  readonly estValue = computed(() => {
    const r = this.report();
    return r ? Math.round(r.doneEstimate) + ' / ' + Math.round(r.totalEstimate) : '—';
  });

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
    { status: 'IN_REVIEW', label: 'Kiểm thử', color: 'var(--color-info)' },
    { status: 'DONE', label: 'Hoàn thành', color: 'var(--status-done)' },
    { status: 'CANCELLED', label: 'Huỷ', color: 'var(--status-cancel)' }
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
    return r.byAssignee.map((a) => {
      const scope = a.total - a.cancel; // Huỷ ngoài phạm vi % hoàn thành
      return { ...a, donePct: scope > 0 ? Math.round((a.done / scope) * 100) : 0 };
    });
  });

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
    this.svc.listTasks(id).subscribe({
      next: (t) => this.tasks.set(t ?? []),
      error: () => this.tasks.set([])
    });
    this.svc.listMembers(id).subscribe({
      next: (m) => this.members.set(m ?? []),
      error: () => this.members.set([])
    });
    this.svc.projectActivity(id).subscribe({
      next: (a) => this.activity.set(a ?? []),
      error: () => this.activity.set([])
    });
  }

  /** Nhãn hành động cho dòng hoạt động (nhận cả DIARY từ nhật ký dự án). */
  actionLabel(a: string): string {
    switch (a) {
      case 'CREATED': return 'tạo';
      case 'STATUS': return 'đổi trạng thái';
      case 'ASSIGN': return 'gán người';
      case 'EDIT': return 'sửa';
      case 'COMMENT': return 'bình luận';
      case 'ATTACH': return 'đính kèm';
      case 'SPENT': return 'ghi giờ';
      case 'DIARY': return 'ghi nhật ký';
      default: return String(a).toLowerCase();
    }
  }
  actionIcon(a: string): string {
    switch (a) {
      case 'CREATED': return '✨';
      case 'STATUS': return '🔄';
      case 'ASSIGN': return '👤';
      case 'EDIT': return '✏️';
      case 'COMMENT': return '💬';
      case 'ATTACH': return '📎';
      case 'SPENT': return '⏱️';
      case 'DIARY': return '📔';
      default: return '•';
    }
  }
  /** Thời gian tương đối gọn: "5 phút trước", "2 giờ trước", "3 ngày trước". */
  relTime(iso: string | null): string {
    if (!iso) return '';
    const t = Date.parse(iso);
    if (isNaN(t)) return '';
    const diff = Date.now() - t;
    const min = Math.floor(diff / 60000);
    if (min < 1) return 'vừa xong';
    if (min < 60) return min + ' phút trước';
    const hr = Math.floor(min / 60);
    if (hr < 24) return hr + ' giờ trước';
    const day = Math.floor(hr / 24);
    if (day < 30) return day + ' ngày trước';
    const mon = Math.floor(day / 30);
    return mon + ' tháng trước';
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
