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
import { loadPref, savePref } from '../shared/view-prefs';

interface WsTab { key: string; label: string; icon: string; feature?: string; }

/** Không gian làm việc của một dự án: tab Tổng quan (gộp thống kê) / Thành viên / Backlog / Kanban / Timeline / Bug / Báo cáo ngày-tuần. */
@Component({
  selector: 'app-project-workspace',
  imports: [PageHeader, PrjOverview, PrjMembers, PrjBacklog, PrjKanban, PrjTimeline, PrjBugs,
    PrjReportsPeriod, PrjTimesheet, PrjLog, PrjDiary, PrjTaskDetail],
  templateUrl: './project-workspace.html',
  styles: [`
    /* Bố cục: menu tab DỌC (trái, thu gọn được) + nội dung (phải). */
    .pw-layout { display: grid; grid-template-columns: auto 1fr; gap: var(--space-4); align-items: start; }
    .pw-nav { position: sticky; top: var(--space-4); display: flex; flex-direction: column; gap: 2px;
      min-width: 210px; padding: var(--space-2); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); }
    .pw-nav.is-collapsed { min-width: 0; }
    .pw-nav__toggle { display: flex; align-items: center; justify-content: flex-end; gap: 6px;
      padding: 4px 8px 8px; margin-bottom: 4px; border-bottom: 1px solid var(--color-border); }
    .pw-nav.is-collapsed .pw-nav__toggle { justify-content: center; }
    .pw-nav__toggle button { border: 1px solid var(--color-border); background: var(--color-surface);
      color: var(--color-text-muted); width: 26px; height: 26px; border-radius: var(--radius-md); cursor: pointer;
      display: flex; align-items: center; justify-content: center; font-size: 14px; }
    .pw-nav__toggle button:hover { color: var(--color-primary); border-color: var(--color-primary); }
    .pw-nav .tab { display: flex; align-items: center; gap: 10px; padding: 9px 12px; border: 0;
      background: transparent; border-radius: var(--radius-md); cursor: pointer; color: var(--color-text-muted);
      font: inherit; font-weight: var(--weight-medium); text-align: left; white-space: nowrap; width: 100%; }
    .pw-nav .tab:hover { background: var(--color-surface-alt); color: var(--color-text); }
    .pw-nav .tab.active { background: var(--color-primary-soft); color: var(--color-primary); font-weight: var(--weight-semibold); }
    .pw-nav .tab-ico { flex: 0 0 auto; width: 20px; text-align: center; }
    .pw-nav.is-collapsed .tab { justify-content: center; padding: 9px; }
    .pw-nav.is-collapsed .tab-label { display: none; }
    .pw-body { min-width: 0; }

    /* Màn hẹp: menu thành hàng ngang cuộn được, nội dung xuống dưới. */
    @media (max-width: 720px) {
      .pw-layout { grid-template-columns: 1fr; }
      .pw-nav { position: static; flex-direction: row; overflow-x: auto; min-width: 0; }
      .pw-nav.is-collapsed .tab-label { display: inline; }
      .pw-nav__toggle { display: none; }
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
  /** Thu gọn menu tab dọc (còn icon) — lưu localStorage. */
  readonly navCollapsed = signal<boolean>(loadPref<boolean>('bpm.pw.navCollapsed', false));
  toggleNav(): void {
    const v = !this.navCollapsed();
    this.navCollapsed.set(v);
    savePref('bpm.pw.navCollapsed', v);
  }

  // Chi tiết task (mở từ Backlog/Kanban)
  readonly detailTask = signal<ProjectTask | null>(null);
  readonly detailOpen = signal(false);

  readonly tabs: WsTab[] = [
    { key: 'overview', label: 'Tổng quan', icon: '📊' }, // luôn hiện (đã vào được dự án)
    { key: 'kanban', label: 'Kanban', icon: '📋', feature: 'PRJ_KANBAN' },
    { key: 'bugs', label: 'Bug / Issue', icon: '🐞', feature: 'PRJ_BUGS' },
    { key: 'backlog', label: 'Backlog', icon: '🗂️', feature: 'PRJ_BACKLOG' },
    { key: 'timeline', label: 'Timeline', icon: '📅', feature: 'PRJ_TIMELINE' },
    { key: 'timesheet', label: 'Timesheet', icon: '⏱️', feature: 'PRJ_TIMESHEET' },
    { key: 'log', label: 'Log', icon: '📜', feature: 'PRJ_LOG' },
    { key: 'diary', label: 'Nhật ký', icon: '📔', feature: 'PRJ_DIARY' },
    { key: 'reports-period', label: 'Báo cáo ngày/tuần', icon: '🗓️', feature: 'PRJ_REPORTS' },
    { key: 'members', label: 'Thành viên', icon: '👥', feature: 'PRJ_MEMBERS' }
  ];

  /** Tab hiển thị theo quyền (FEAT_PRJ_*); tab không có feature → luôn hiện. ADMIN thấy tất cả. */
  readonly visibleTabs = computed<WsTab[]>(() =>
    this.tabs.filter((t) => !t.feature || this.auth.hasFeature(t.feature)));

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
