import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { ProjectService, Project, ProjectTask } from '../core/project.service';
import { AuthService } from '../core/auth.service';
import { PrjOverview } from './overview/overview';
import { PrjMembers } from './members/members';
import { PrjBacklog } from './backlog/backlog';
import { PrjKanban } from './kanban/kanban';
import { PrjTimeline } from './timeline/timeline';
import { PrjBugs } from './bugs/bugs';
import { PrjReportsPeriod } from './reports-period/reports-period';
import { PrjTimesheet } from './timesheet/timesheet';
import { PrjLog } from './log/log';
import { PrjDiary } from './diary/diary';
import { PrjTaskDetail } from './task-detail/task-detail';

interface WsTab { key: string; label: string; icon: string; feature?: string; group: string; }
interface TabGroup { group: string; tabs: WsTab[]; }

/** Không gian làm việc của một dự án: tab Tổng quan (gộp thống kê) / Thành viên / Backlog / Kanban / Timeline / Bug / Báo cáo ngày-tuần. */
@Component({
  selector: 'app-project-workspace',
  imports: [PageHeader, PrjOverview, PrjMembers, PrjBacklog, PrjKanban, PrjTimeline, PrjBugs,
    PrjReportsPeriod, PrjTimesheet, PrjLog, PrjDiary, PrjTaskDetail],
  templateUrl: './project-workspace.html',
  styles: [`
    /* Menu DỌC (1 click) — chức năng con của dự án + nội dung. */
    .pw-layout { display: grid; grid-template-columns: 200px 1fr; gap: var(--space-4); align-items: start; }
    .pw-nav { position: sticky; top: var(--space-4); display: flex; flex-direction: column; gap: 2px;
      padding: var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-lg); background: var(--color-surface); }
    .pw-nav .tab-group { display: block; padding: 10px 12px 4px; font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .04em; color: var(--color-text-muted); }
    .pw-nav .tab-group:first-child { padding-top: 2px; }
    .pw-nav .tab { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border: 0;
      background: transparent; border-radius: var(--radius-md); cursor: pointer; color: var(--color-text-muted);
      font: inherit; font-weight: var(--weight-medium); text-align: left; white-space: nowrap; width: 100%; }
    .pw-nav .tab:hover { background: var(--color-surface-alt); color: var(--color-text); }
    .pw-nav .tab.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: var(--weight-semibold); }
    .pw-nav .tab-ico { flex: 0 0 auto; width: 20px; text-align: center; }
    .pw-body { min-width: 0; }

    /* Màn hẹp: menu thành hàng ngang cuộn được. */
    @media (max-width: 760px) {
      .pw-layout { grid-template-columns: 1fr; }
      .pw-nav { position: static; flex-direction: row; overflow-x: auto; }
      .pw-nav .tab-group { display: none; }
    }
  `]
})
export class ProjectWorkspace implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ProjectService);
  private auth = inject(AuthService);

  readonly id = signal<string>('');
  readonly project = signal<Project | null>(null);
  readonly tab = signal('overview');
  readonly refresh = signal(0); // tăng để buộc tab tải lại sau khi đổi từ chi tiết

  // Chi tiết task (mở từ Backlog/Kanban)
  readonly detailTask = signal<ProjectTask | null>(null);
  readonly detailOpen = signal(false);

  readonly tabs: WsTab[] = [
    { key: 'overview', label: 'Tổng quan', icon: '📊', group: '' }, // luôn hiện; đứng đầu, không nhóm
    // Nhóm CÔNG VIỆC
    { key: 'kanban', label: 'Kanban', icon: '📋', feature: 'PRJ_KANBAN', group: 'Công việc' },
    { key: 'backlog', label: 'Backlog', icon: '🗂️', feature: 'PRJ_BACKLOG', group: 'Công việc' },
    { key: 'bugs', label: 'Bug / Issue', icon: '🐞', feature: 'PRJ_BUGS', group: 'Công việc' },
    { key: 'timeline', label: 'Timeline', icon: '📅', feature: 'PRJ_TIMELINE', group: 'Công việc' },
    // Nhóm THEO DÕI & BÁO CÁO
    { key: 'timesheet', label: 'Timesheet', icon: '⏱️', feature: 'PRJ_TIMESHEET', group: 'Theo dõi & Báo cáo' },
    { key: 'log', label: 'Log', icon: '📜', feature: 'PRJ_LOG', group: 'Theo dõi & Báo cáo' },
    { key: 'diary', label: 'Nhật ký', icon: '📔', feature: 'PRJ_DIARY', group: 'Theo dõi & Báo cáo' },
    { key: 'reports-period', label: 'Báo cáo ngày/tuần', icon: '🗓️', feature: 'PRJ_REPORTS', group: 'Theo dõi & Báo cáo' },
    // Nhóm QUẢN LÝ
    { key: 'members', label: 'Thành viên', icon: '👥', feature: 'PRJ_MEMBERS', group: 'Quản lý' }
  ];

  /** Tab hiển thị theo quyền (FEAT_PRJ_*); tab không có feature → luôn hiện. ADMIN thấy tất cả. */
  readonly visibleTabs = computed<WsTab[]>(() =>
    this.tabs.filter((t) => !t.feature || this.auth.hasFeature(t.feature)));

  /** Tab đã gom theo NHÓM CON (giữ thứ tự; nhóm '' = mục đứng đầu không tiêu đề). */
  readonly tabGroups = computed<TabGroup[]>(() => {
    const out: TabGroup[] = [];
    for (const t of this.visibleTabs()) {
      let g = out.find((x) => x.group === t.group);
      if (!g) { g = { group: t.group, tabs: [] }; out.push(g); }
      g.tabs.push(t);
    }
    return out;
  });


  /** Đảm bảo tab đang chọn nằm trong tab được phép; nếu không → về tab đầu tiên được phép. */
  selectTab(key: string): void { this.tab.set(key); }
  private ensureAllowedTab(): void {
    const allowed = new Set(this.visibleTabs().map((t) => t.key));
    if (!allowed.has(this.tab())) {
      this.tab.set(this.visibleTabs()[0]?.key ?? 'overview');
    }
  }

  ngOnInit(): void {
    const pid = this.route.snapshot.paramMap.get('id') ?? '';
    this.id.set(pid);
    this.ensureAllowedTab(); // nếu tab mặc định không được phép → về tab đầu tiên được phép
    this.loadProject();
  }

  private loadProject(): void {
    if (this.id()) this.api.get(this.id()).subscribe({ next: (p) => this.project.set(p), error: () => {} });
  }

  openDetail(t: ProjectTask): void {
    this.detailTask.set(t);
    this.detailOpen.set(true);
  }
  closeDetail(): void { this.detailOpen.set(false); }
  onTaskChanged(): void {
    this.refresh.update((v) => v + 1); // buộc tab hiện tại tải lại
    this.loadProject();                 // cập nhật % tổng ở header/tổng quan
  }
}
