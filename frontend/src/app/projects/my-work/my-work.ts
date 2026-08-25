import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { PageHeader } from '../../shared/page-header/page-header';
import { SelectOption } from '../../shared/searchable-select/searchable-select';
import { AuthService } from '../../core/auth.service';
import { MeTaskService, ProjectOption } from '../../core/me-task.service';
import { ProjectService, ProjectTask } from '../../core/project.service';
import { PrjBacklog } from '../backlog/backlog';
import { PrjTaskDetail } from '../task-detail/task-detail';

/**
 * BACKLOG CỦA TÔI — chọn dự án → hiển thị NGUYÊN backlog dự án (EPIC · Story · task cha/con)
 * như màn Quản lý dự án, mặc định LỌC theo việc của tôi (vẫn hiện task cha). Thêm việc như QLDA.
 */
@Component({
  selector: 'app-my-project-work',
  imports: [PageHeader, PrjBacklog, PrjTaskDetail],
  templateUrl: './my-work.html',
  styles: [`
    .mb-empty { padding: var(--space-8); text-align: center; color: var(--color-text-muted); }
  `]
})
export class MyProjectWork implements OnInit {
  private auth = inject(AuthService);
  private meApi = inject(MeTaskService);
  private projectApi = inject(ProjectService);

  /** Tham chiếu backlog con để reload sau khi sửa task ở chi tiết. */
  private readonly backlog = viewChild(PrjBacklog);

  readonly projects = signal<ProjectOption[]>([]);
  readonly selectedId = signal<string>('');
  readonly loading = signal(true);

  /** userId để backlog mặc định lọc theo việc của tôi. */
  readonly myUserId = computed(() => this.auth.currentUser()?.userId ?? '');
  readonly projectSel = computed<SelectOption[]>(() =>
    this.projects().map((p) => ({ value: p.id, label: p.code + ' · ' + p.name })));

  // Chi tiết task (mở từ backlog).
  readonly detailOpen = signal(false);
  readonly detailTask = signal<ProjectTask | null>(null);

  ngOnInit(): void {
    // Ưu tiên dự án TÔI là thành viên; nếu không có (vd admin) → hiện mọi dự án để giám sát.
    this.meApi.myProjects().subscribe({
      next: (ps) => (ps.length ? this.apply(ps) : this.loadAllFallback()),
      error: () => this.loadAllFallback()
    });
  }
  private apply(ps: ProjectOption[]): void {
    this.projects.set(ps);
    if (ps.length) this.selectedId.set(ps[0].id);
    this.loading.set(false);
  }
  private loadAllFallback(): void {
    this.projectApi.list().subscribe({
      next: (all) => this.apply(all.map((p) => ({ id: p.id, code: p.code, name: p.name }))),
      error: () => this.loading.set(false)
    });
  }

  openDetail(t: ProjectTask): void { this.detailTask.set(t); this.detailOpen.set(true); }
  closeDetail(): void { this.detailOpen.set(false); this.detailTask.set(null); }
  /** Sửa task ở popup → chỉ làm mới dữ liệu, không đụng bộ lọc/nhóm đang gập/vị trí cuộn. */
  onTaskChanged(): void { this.backlog()?.refreshSilently(); }
}
