import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
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
import { PROJECT_TABS, PrjTab } from './project-tabs';

/** Không gian làm việc của một dự án: tab Tổng quan (gộp thống kê) / Thành viên / Backlog / Kanban / Timeline / Bug / Báo cáo ngày-tuần. */
@Component({
  selector: 'app-project-workspace',
  imports: [PageHeader, PrjOverview, PrjMembers, PrjBacklog, PrjKanban, PrjTimeline, PrjBugs,
    PrjReportsPeriod, PrjTimesheet, PrjLog, PrjDiary, PrjTaskDetail],
  templateUrl: './project-workspace.html',
  styles: [``]
})
export class ProjectWorkspace implements OnInit {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private api = inject(ProjectService);
  private auth = inject(AuthService);

  readonly id = signal<string>('');
  readonly project = signal<Project | null>(null);
  readonly tab = signal('overview');
  readonly refresh = signal(0); // tăng để buộc tab tải lại sau khi đổi từ chi tiết

  // Chi tiết task (mở từ Backlog/Kanban)
  readonly detailTask = signal<ProjectTask | null>(null);
  readonly detailOpen = signal(false);

  readonly tabs: PrjTab[] = PROJECT_TABS;

  /** Tab hiển thị theo quyền (FEAT_PRJ_*); tab không có feature → luôn hiện. ADMIN thấy tất cả. */
  readonly visibleTabs = computed<PrjTab[]>(() =>
    this.tabs.filter((t) => !t.feature || this.auth.hasFeature(t.feature)));

  /** Chọn tab = điều hướng ?tab= (sidebar app cũng dùng link này) — một nguồn sự thật là URL. */
  selectTab(key: string): void {
    this.router.navigate([], { relativeTo: this.route, queryParams: { tab: key }, queryParamsHandling: 'merge' });
  }
  /** Ép tab đang chọn nằm trong tab được phép; nếu không → tab đầu tiên được phép. */
  private allowedTab(key: string): string {
    const allowed = new Set(this.visibleTabs().map((t) => t.key));
    return allowed.has(key) ? key : (this.visibleTabs()[0]?.key ?? 'overview');
  }

  ngOnInit(): void {
    const pid = this.route.snapshot.paramMap.get('id') ?? '';
    this.id.set(pid);
    // Tab theo query ?tab= (đồng bộ với sidebar app); đổi query → đổi tab, không tải lại component.
    this.route.queryParamMap.subscribe((qp) => {
      this.tab.set(this.allowedTab(qp.get('tab') || 'overview'));
    });
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
