import { Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { ToastService } from '../shared/toast/toast.service';
import { WorkflowService, InstanceListItem, InstanceOverview } from '../core/workflow.service';

/** Theo dõi quy trình (Story 3.3) + tra cứu/tìm kiếm (Story 4.5) + dòng thời gian + hủy (Story 3.6). */
@Component({
  selector: 'app-tracking',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Modal, ConfirmDialog],
  templateUrl: './tracking.html'
})
export class Tracking implements OnInit, OnDestroy {
  private wf = inject(WorkflowService);
  private toast = inject(ToastService);

  readonly cols: GridColumn[] = [
    { key: 'title', header: 'Hồ sơ', sortable: true },
    { key: 'processName', header: 'Quy trình', sortable: true, width: '210px' },
    { key: 'status', header: 'Trạng thái', width: '130px' },
    { key: 'currentStep', header: 'Bước hiện tại' },
    { key: 'startedAt', header: 'Khởi tạo', width: '130px' },
    { key: 'actions', header: '', width: '160px' }
  ];

  readonly rows = signal<InstanceListItem[]>([]);
  readonly loading = signal(true);

  // ---- Tra cứu (Story 4.5) ----
  readonly search = signal('');
  readonly statusFilter = signal('');
  readonly filtered = computed(() => {
    const q = this.search().trim().toLowerCase();
    const st = this.statusFilter();
    return this.rows().filter((r) => {
      if (st && r.status !== st) return false;
      if (!q) return true;
      return [r.title, r.processName, r.startedBy, r.currentStep, r.searchText]
        .some((v) => (v || '').toLowerCase().includes(q));
    });
  });

  readonly tlOpen = signal(false);
  readonly overview = signal<InstanceOverview | null>(null);
  /** Các bước đang mở rộng (theo index). Bước đang xử lý mở sẵn, bước đã xong thu gọn. */
  readonly expanded = signal<Set<number>>(new Set());

  readonly confirmOpen = signal(false);
  readonly cancelTarget = signal<InstanceListItem | null>(null);

  private timer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.reload();
    this.timer = setInterval(() => this.reload(true), 15000); // realtime gần (Story 4.7)
  }
  ngOnDestroy(): void { if (this.timer) clearInterval(this.timer); }

  reload(silent = false): void {
    if (!silent) this.loading.set(true);
    this.wf.instances().subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => { if (!silent) this.toast.error('Không tải được danh sách phiên chạy'); this.loading.set(false); }
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
    return s === 'COMPLETED' ? 'Hoàn thành' : s === 'CANCELLED' ? 'Đã hủy' : 'Đang chạy';
  }

  openTimeline(i: InstanceListItem): void {
    this.overview.set(null);
    this.expanded.set(new Set());
    this.tlOpen.set(true);
    this.wf.overview(i.id).subscribe({
      next: (o) => {
        this.overview.set(o);
        // Mở sẵn các bước đang xử lý; các bước đã xong / chưa tới thu gọn.
        this.expanded.set(new Set(o.steps.filter((s) => s.status === 'ACTIVE').map((s) => s.index)));
      },
      error: () => { this.toast.error('Không tải được tổng quan quy trình'); this.tlOpen.set(false); }
    });
  }

  isExpanded(index: number): boolean {
    return this.expanded().has(index);
  }
  toggleStep(index: number): void {
    const next = new Set(this.expanded());
    next.has(index) ? next.delete(index) : next.add(index);
    this.expanded.set(next);
  }
  stepStatusLabel(s: string): string {
    return s === 'DONE' ? 'Đã xong' : s === 'ACTIVE' ? 'Đang xử lý' : 'Chưa tới';
  }

  askCancel(i: InstanceListItem): void {
    this.cancelTarget.set(i);
    this.confirmOpen.set(true);
  }

  doCancel(): void {
    const i = this.cancelTarget();
    this.confirmOpen.set(false);
    if (!i) return;
    this.wf.cancel(i.id, 'Hủy từ màn theo dõi').subscribe({
      next: () => { this.toast.success('Đã hủy phiên chạy', i.processName); this.reload(); },
      error: (e) => this.toast.error('Không hủy được', e?.error?.message || 'Phiên có thể đã kết thúc.')
    });
  }
}
