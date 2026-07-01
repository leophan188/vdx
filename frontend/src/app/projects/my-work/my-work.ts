import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PageHeader } from '../../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { Skeleton, EmptyState } from '../../shared/skeleton/skeleton';
import { ToastService } from '../../shared/toast/toast.service';
import { PrjTaskDetail } from '../task-detail/task-detail';
import { ProjectService, MyTask, ProjectTask, TaskStatus } from '../../core/project.service';

const STATUSES: { value: TaskStatus; label: string; badge: string }[] = [
  { value: 'BACKLOG', label: 'Backlog', badge: 'badge--neutral' },
  { value: 'TODO', label: 'Cần làm', badge: 'badge--pending' },
  { value: 'IN_PROGRESS', label: 'Đang làm', badge: 'badge--info' },
  { value: 'IN_REVIEW', label: 'Đang review', badge: 'badge--pending' },
  { value: 'DONE', label: 'Hoàn thành', badge: 'badge--done' }
];

/** Màn CÁ NHÂN: việc dự án được giao cho tôi (xuyên tất cả dự án) — tự cập nhật trạng thái. */
@Component({
  selector: 'app-my-project-work',
  imports: [RouterLink, PageHeader, DataGrid, GridCellDirective, SearchableSelect, Skeleton, EmptyState, PrjTaskDetail],
  templateUrl: './my-work.html',
  styles: [`
    .mw-stats { display: flex; gap: var(--space-2); flex-wrap: wrap; margin-bottom: var(--space-3); }
    .mw-bar { height: 7px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; min-width: 90px; }
    .mw-bar__fill { height: 100%; background: var(--color-primary); }
    .mw-link { background: none; border: none; padding: 0; cursor: pointer; color: var(--color-primary);
      font: inherit; text-align: left; }
    .mw-link:hover { text-decoration: underline; }
  `]
})
export class MyProjectWork implements OnInit {
  private api = inject(ProjectService);
  private toast = inject(ToastService);

  readonly all = signal<MyTask[]>([]);
  readonly loading = signal(true);
  /** Bộ lọc PHẢI là signal để computed rows chạy lại khi đổi. */
  readonly filterStatus = signal('');
  readonly filterProject = signal('');

  // ----- Chi tiết task (mở để cập nhật đầy đủ) -----
  readonly detailOpen = signal(false);
  readonly detailTask = signal<ProjectTask | null>(null);
  readonly detailProjectId = signal('');

  readonly statusSel: SelectOption[] = STATUSES.map((s) => ({ value: s.value, label: s.label }));
  readonly projectSel = computed<SelectOption[]>(() => {
    const seen = new Map<string, string>();
    for (const t of this.all()) seen.set(t.projectId, t.projectCode + ' · ' + t.projectName);
    return [...seen].map(([value, label]) => ({ value, label }));
  });

  readonly rows = computed<MyTask[]>(() => {
    const st = this.filterStatus();
    const pj = this.filterProject();
    return this.all().filter((t) => (!st || t.status === st) && (!pj || t.projectId === pj));
  });

  readonly stats = computed(() => {
    const a = this.all();
    const by: Record<string, number> = {};
    let est = 0, doneEst = 0;
    for (const t of a) {
      by[t.status] = (by[t.status] ?? 0) + 1;
      est += t.estimateHours || 0;
      if (t.status === 'DONE') doneEst += t.estimateHours || 0;
    }
    return { total: a.length, by, est, doneEst, pct: est > 0 ? Math.round((doneEst / est) * 1000) / 10 : 0 };
  });

  readonly cols: GridColumn[] = [
    { key: 'project', header: 'Dự án', width: '150px', sortable: true },
    { key: 'code', header: 'Mã', width: '90px' },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'priority', header: 'Ưu tiên', width: '100px' },
    { key: 'progress', header: '% Hoàn thành', width: '150px' },
    { key: 'dueDate', header: 'Hạn', width: '110px', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '170px' }
  ];

  readonly STATUSES = STATUSES;

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.api.myTasks().subscribe({
      next: (r) => { this.all.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được việc của tôi'); this.loading.set(false); }
    });
  }

  changeStatus(t: MyTask, status: string): void {
    if (status === t.status) return;
    this.api.updateTaskStatus(t.projectId, t.taskId, status as TaskStatus).subscribe({
      next: () => { this.toast.success('Đã cập nhật trạng thái', t.code); this.reload(); },
      error: () => this.toast.error('Không cập nhật được')
    });
  }

  /** Mở chi tiết task (Jira) — tải task đầy đủ từ dự án rồi mở modal để cập nhật mọi thứ. */
  openDetail(t: MyTask): void {
    this.api.listTasks(t.projectId).subscribe({
      next: (tasks) => {
        const full = tasks.find((x) => x.id === t.taskId);
        if (full) {
          this.detailProjectId.set(t.projectId);
          this.detailTask.set(full);
          this.detailOpen.set(true);
        } else {
          this.toast.error('Không tìm thấy công việc');
        }
      },
      error: () => this.toast.error('Không mở được chi tiết công việc')
    });
  }
  closeDetail(): void { this.detailOpen.set(false); this.detailTask.set(null); }

  badge(s: string): string { return STATUSES.find((x) => x.value === s)?.badge ?? 'badge--neutral'; }
  prioBadge(p: string): string {
    return p === 'URGENT' ? 'badge--cancel' : p === 'HIGH' ? 'badge--pending' : 'badge--neutral';
  }
}
