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

interface WsTab { key: string; label: string; icon: string; feature?: string; }

/** Không gian làm việc của một dự án: tab Tổng quan (gộp thống kê) / Thành viên / Backlog / Kanban / Timeline / Bug / Báo cáo ngày-tuần. */
@Component({
  selector: 'app-project-workspace',
  imports: [PageHeader, PrjOverview, PrjMembers, PrjBacklog, PrjKanban, PrjTimeline, PrjBugs,
    PrjReportsPeriod, PrjTimesheet, PrjLog, PrjDiary, PrjTaskDetail],
  templateUrl: './project-workspace.html',
  styles: [`
    .pw-tabs { display: flex; gap: 4px; flex-wrap: wrap; border-bottom: 1px solid var(--color-border); margin-bottom: var(--space-4); }
    .pw-tabs button { border: none; background: transparent; padding: 10px 14px; cursor: pointer; color: var(--color-text-muted);
      font: inherit; font-weight: var(--weight-medium); border-bottom: 2px solid transparent; }
    .pw-tabs button:hover { color: var(--color-text); }
    .pw-tabs button.active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: var(--weight-semibold); }
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
