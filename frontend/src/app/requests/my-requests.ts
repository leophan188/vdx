import { Component, OnInit, inject, signal } from '@angular/core';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ToastService } from '../shared/toast/toast.service';
import { WorkflowService, InstanceListItem, InstanceTimeline } from '../core/workflow.service';

/** "Hồ sơ của tôi" (Story 4.4): theo dõi các hồ sơ do chính người dùng tạo + tiến trình. */
@Component({
  selector: 'app-my-requests',
  imports: [PageHeader, DataGrid, GridCellDirective, Modal],
  templateUrl: './my-requests.html'
})
export class MyRequests implements OnInit {
  private wf = inject(WorkflowService);
  private toast = inject(ToastService);

  readonly cols: GridColumn[] = [
    { key: 'title', header: 'Hồ sơ', sortable: true },
    { key: 'processName', header: 'Quy trình', sortable: true, width: '210px' },
    { key: 'processVersion', header: 'PB', align: 'center', width: '64px', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '130px' },
    { key: 'currentStep', header: 'Đang ở bước' },
    { key: 'startedAt', header: 'Tạo lúc', width: '130px' },
    { key: 'actions', header: '', width: '120px' }
  ];

  readonly rows = signal<InstanceListItem[]>([]);
  readonly loading = signal(true);
  readonly tlOpen = signal(false);
  readonly timeline = signal<InstanceTimeline | null>(null);

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.wf.myInstances().subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được hồ sơ của tôi'); this.loading.set(false); }
    });
  }

  fmt(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
  statusClass(s: string): string {
    return s === 'COMPLETED' ? 'badge--success' : s === 'CANCELLED' ? 'badge--cancel' : 'badge--info';
  }
  statusLabel(s: string): string {
    return s === 'COMPLETED' ? 'Hoàn thành' : s === 'CANCELLED' ? 'Đã hủy' : 'Đang xử lý';
  }

  openTimeline(i: InstanceListItem): void {
    this.timeline.set(null);
    this.tlOpen.set(true);
    this.wf.timeline(i.id).subscribe({
      next: (t) => this.timeline.set(t),
      error: () => { this.toast.error('Không tải được tiến trình'); this.tlOpen.set(false); }
    });
  }
}
