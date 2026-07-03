import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { ProjectService, Project, ProjectTask } from '../core/project.service';
import { PrjOverview } from './overview/overview';
import { PrjMembers } from './members/members';
import { PrjBacklog } from './backlog/backlog';
import { PrjKanban } from './kanban/kanban';
import { PrjTimeline } from './timeline/timeline';
import { PrjBugs } from './bugs/bugs';
import { PrjReportsPeriod } from './reports-period/reports-period';
import { PrjTimesheet } from './timesheet/timesheet';
import { PrjLog } from './log/log';
import { PrjTaskDetail } from './task-detail/task-detail';

interface WsTab { key: string; label: string; icon: string; }

/** Không gian làm việc của một dự án: tab Tổng quan (gộp thống kê + burndown) / Thành viên / Backlog / Kanban / Timeline / Bug / Báo cáo ngày-tuần. */
@Component({
  selector: 'app-project-workspace',
  imports: [PageHeader, PrjOverview, PrjMembers, PrjBacklog, PrjKanban, PrjTimeline, PrjBugs,
    PrjReportsPeriod, PrjTimesheet, PrjLog, PrjTaskDetail],
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

  readonly id = signal<string>('');
  readonly project = signal<Project | null>(null);
  readonly tab = signal('overview');
  readonly refresh = signal(0); // tăng để buộc tab tải lại sau khi đổi từ chi tiết

  // Chi tiết task (mở từ Backlog/Kanban)
  readonly detailTask = signal<ProjectTask | null>(null);
  readonly detailOpen = signal(false);

  readonly tabs: WsTab[] = [
    { key: 'overview', label: 'Tổng quan', icon: '📊' },
    { key: 'members', label: 'Thành viên', icon: '👥' },
    { key: 'backlog', label: 'Backlog', icon: '🗂️' },
    { key: 'kanban', label: 'Kanban', icon: '📋' },
    { key: 'timeline', label: 'Timeline', icon: '📅' },
    { key: 'bugs', label: 'Bug / Issue', icon: '🐞' },
    { key: 'reports-period', label: 'Báo cáo ngày/tuần', icon: '🗓️' },
    { key: 'timesheet', label: 'Timesheet', icon: '⏱️' },
    { key: 'log', label: 'Log', icon: '📜' }
  ];

  ngOnInit(): void {
    const pid = this.route.snapshot.paramMap.get('id') ?? '';
    this.id.set(pid);
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
