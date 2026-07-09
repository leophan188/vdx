import { Component, OnInit, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { TaskProcessor } from '../shared/task-processor/task-processor';
import { Modal } from '../shared/modal/modal';
import { ToastService } from '../shared/toast/toast.service';
import { WorkflowService, InboxItem, StartableProcess } from '../core/workflow.service';

/** Hộp thư "Việc của tôi" (Story 3.2) + tạo yêu cầu (chọn quy trình → nhập liệu) + realtime (Story 4.7). */
@Component({
  selector: 'app-inbox',
  imports: [PageHeader, DataGrid, GridCellDirective, TaskProcessor, Modal],
  templateUrl: './inbox.html'
})
export class Inbox implements OnInit, OnDestroy {
  private wf = inject(WorkflowService);
  private toast = inject(ToastService);
  private timer?: ReturnType<typeof setInterval>;

  readonly proc = viewChild.required(TaskProcessor);

  readonly cols: GridColumn[] = [
    { key: 'stepName', header: 'Việc / Bước', sortable: true },
    { key: 'processName', header: 'Quy trình', sortable: true },
    { key: 'createdAt', header: 'Nhận lúc', width: '150px' },
    { key: 'sla', header: 'SLA', align: 'center', width: '90px' },
    { key: 'actions', header: '', width: '120px' }
  ];

  readonly items = signal<InboxItem[]>([]);
  readonly loading = signal(true);

  // ----- Tạo yêu cầu: chọn quy trình → nhập liệu (gộp vào Việc của tôi) -----
  readonly startables = signal<StartableProcess[]>([]);
  readonly pickerOpen = signal(false);
  readonly pickerLoading = signal(false);
  readonly busyId = signal<string | null>(null);

  openPicker(): void {
    this.pickerOpen.set(true);
    if (this.startables().length === 0) {
      this.pickerLoading.set(true);
      this.wf.startable().subscribe({
        next: (r) => { this.startables.set(r); this.pickerLoading.set(false); },
        error: () => { this.pickerLoading.set(false); this.toast.error('Không tải được danh sách quy trình'); }
      });
    }
  }

  /**
   * Chọn 1 quy trình → mở form nhập liệu bước đầu ở chế độ NHÁP (CHƯA tạo hồ sơ). Hồ sơ chỉ được tạo khi
   * người dùng bấm Gửi hoặc mở tab Soạn thảo tài liệu → tránh sinh hồ sơ rác khi chỉ chọn/lướt quy trình.
   */
  createFromProcess(p: StartableProcess): void {
    this.pickerOpen.set(false);
    this.proc().openStartForm({ id: p.id, name: p.name });
  }

  initials(name: string): string {
    const p = name.replace(/^Quy trình\s+/i, '').trim().split(/\s+/);
    return ((p[0]?.[0] ?? '') + (p[1]?.[0] ?? '')).toUpperCase();
  }

  ngOnInit(): void {
    this.reload();
    // Cập nhật gần realtime (Story 4.7): làm mới nền mỗi 15s (không hiện trạng thái tải).
    this.timer = setInterval(() => this.reload(true), 15000);
  }
  ngOnDestroy(): void { if (this.timer) clearInterval(this.timer); }

  reload(silent = false): void {
    if (!silent) this.loading.set(true);
    this.wf.inbox().subscribe({
      next: (r) => { this.items.set(this.newestFirst(r)); this.loading.set(false); },
      error: () => { if (!silent) this.toast.error('Không tải được hộp thư việc'); this.loading.set(false); }
    });
  }

  /** Sắp xếp việc mới nhận nhất lên đầu (theo thời điểm nhận, giảm dần). */
  private newestFirst(r: InboxItem[]): InboxItem[] {
    return [...r].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''));
  }

  fmt(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  /** Mở việc để xử lý (modal dùng chung). */
  process(item: InboxItem): void {
    this.proc().openTask(item.taskId);
  }

  /** Nhận một việc theo vai trò (claim). */
  claim(item: InboxItem): void {
    this.wf.claim(item.taskId).subscribe({
      next: () => { this.toast.success('Đã nhận việc', item.stepName); this.reload(); },
      error: (e) => this.toast.error('Không nhận được việc', e?.error?.message || 'Việc có thể đã có người nhận.')
    });
  }

  /** Xin gia hạn xử lý việc. */
  askExtend(item: InboxItem): void {
    const raw = window.prompt(`Xin gia hạn việc "${item.stepName}" — số giờ thêm:`, '24');
    if (raw === null) return;
    const hours = Number(raw);
    if (!Number.isFinite(hours) || hours <= 0) { this.toast.error('Số giờ không hợp lệ'); return; }
    this.wf.extend(item.taskId, hours, 'Xin gia hạn từ hộp thư').subscribe({
      next: () => { this.toast.success('Đã gia hạn', `+${hours}h cho "${item.stepName}"`); this.reload(); },
      error: () => this.toast.error('Không gia hạn được')
    });
  }
}
