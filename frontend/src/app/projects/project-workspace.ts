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
    /* Menu NGANG: Tổng quan trực tiếp + nhóm con dạng dropdown. */
    .pw-menubar { display: flex; align-items: center; gap: 4px; flex-wrap: wrap;
      border-bottom: 1px solid var(--color-border); margin-bottom: var(--space-4); padding-bottom: 6px; }
    .pw-mtab { display: inline-flex; align-items: center; gap: 8px; border: 0; background: transparent;
      padding: 9px 14px; border-radius: var(--radius-md); cursor: pointer; color: var(--color-text-muted);
      font: inherit; font-weight: var(--weight-medium); white-space: nowrap; }
    .pw-mtab:hover { background: var(--color-surface-alt); color: var(--color-text); }
    .pw-mtab.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: var(--weight-semibold); }
    .pw-mtab .tab-ico { flex: 0 0 auto; }
    .pw-menu { position: relative; }
    .pw-menu__chev { font-size: 11px; opacity: .7; margin-left: 2px; }
    .pw-menu__btn.open { background: var(--color-surface-alt); color: var(--color-text); }
    .pw-menu__scrim { position: fixed; inset: 0; z-index: 40; background: transparent; }
    .pw-menu__pop { position: absolute; top: calc(100% + 4px); left: 0; z-index: 41; min-width: 210px;
      display: flex; flex-direction: column; gap: 2px; padding: var(--space-2);
      background: var(--color-surface); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); box-shadow: var(--shadow-pop, 0 14px 30px rgba(0,0,0,.18)); }
    .pw-menu__item { display: flex; align-items: center; gap: 10px; border: 0; background: transparent;
      padding: 9px 12px; border-radius: var(--radius-md); cursor: pointer; color: var(--color-text);
      font: inherit; font-weight: var(--weight-medium); text-align: left; white-space: nowrap; }
    .pw-menu__item:hover { background: var(--color-surface-alt); }
    .pw-menu__item.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: var(--weight-semibold); }
    .pw-menu__item .tab-ico { flex: 0 0 auto; width: 20px; text-align: center; }
    .pw-body { min-width: 0; }

    @media (max-width: 720px) {
      .pw-menubar { flex-wrap: nowrap; overflow-x: auto; }
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

  /** Nhóm con đang mở dropdown trên menu NGANG (null = đóng). */
  readonly openMenu = signal<string | null>(null);
  toggleMenu(group: string): void { this.openMenu.update((c) => (c === group ? null : group)); }
  closeMenu(): void { this.openMenu.set(null); }
  /** Nhóm chứa tab đang chọn (tô sáng nút nhóm trên menu ngang). */
  readonly activeTabGroup = computed<string>(() =>
    this.tabs.find((t) => t.key === this.tab())?.group ?? '');
  /** Chọn tab từ dropdown: đổi tab + đóng menu. */
  pickTab(key: string): void { this.tab.set(key); this.openMenu.set(null); }

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
