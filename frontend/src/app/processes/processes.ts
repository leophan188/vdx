import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ProcessService, ProcessSummary } from '../core/process.service';
import { WorkflowService } from '../core/workflow.service';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { StatusBadge, TaskStatus } from '../shared/status-badge/status-badge';
import { PageHeader } from '../shared/page-header/page-header';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-processes',
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, ConfirmDialog, StatusBadge, PageHeader],
  templateUrl: './processes.html'
})
export class Processes implements OnInit {
  private svc = inject(ProcessService);
  private wf = inject(WorkflowService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly rows = signal<ProcessSummary[]>([]);
  readonly error = signal<string | null>(null);

  readonly cols: GridColumn[] = [
    { key: 'processKey', header: 'Mã quy trình', sortable: true, width: '170px' },
    { key: 'name', header: 'Tên quy trình', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '150px' },
    { key: 'publishedVersion', header: 'Phiên bản', align: 'center', width: '110px' },
    { key: 'actions', header: 'Thao tác', align: 'right', width: '360px' }
  ];

  readonly createOpen = signal(false);
  newKey = '';
  newName = '';
  newCopyFrom = '';

  readonly editOpen = signal(false);
  readonly editTarget = signal<ProcessSummary | null>(null);
  editName = '';

  readonly delOpen = signal(false);
  readonly delTarget = signal<ProcessSummary | null>(null);

  readonly publishOpen = signal(false);
  readonly publishTarget = signal<ProcessSummary | null>(null);
  readonly retireOpen = signal(false);
  readonly retireTarget = signal<ProcessSummary | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.svc.list().subscribe({
      next: (r) => this.rows.set(r),
      error: () => this.error.set('Không tải được danh sách quy trình (cần quyền quản trị).')
    });
  }

  statusKind(s: string): TaskStatus {
    return s === 'PUBLISHED' ? 'done' : s === 'RETIRED' ? 'cancel' : 'pending';
  }
  statusLabel(s: string): string {
    return s === 'PUBLISHED' ? 'Đã ban hành' : s === 'RETIRED' ? 'Ngừng dùng' : 'Nháp';
  }

  openCreate(): void {
    this.newKey = this.newName = this.newCopyFrom = '';
    this.createOpen.set(true);
  }
  create(): void {
    if (!this.newKey || !this.newName) return;
    this.svc.create(this.newKey, this.newName, this.newCopyFrom).subscribe({
      next: (p) => {
        this.toast.success('Đã tạo quy trình', this.newCopyFrom ? this.newName + ' (đã sao chép)' : this.newName);
        this.createOpen.set(false);
        this.reload();
        this.router.navigate(['/processes', p.id]);
      },
      error: () => this.toast.error('Không tạo được quy trình', 'Mã có thể đã tồn tại.')
    });
  }

  design(p: ProcessSummary): void {
    this.router.navigate(['/processes', p.id]);
  }

  /** Khởi tạo một phiên chạy từ quy trình đã ban hành (Story 3.1) → việc về "Việc của tôi". */
  startInstance(p: ProcessSummary): void {
    this.wf.start(p.id, {}).subscribe({
      next: () => this.toast.success('Đã khởi tạo nhiệm vụ', `${p.name} — xem "Việc của tôi"`),
      error: () => this.toast.error('Không khởi tạo được', 'Quy trình cần được ban hành và có người thực hiện.')
    });
  }

  openEdit(p: ProcessSummary): void {
    this.editTarget.set(p);
    this.editName = p.name;
    this.editOpen.set(true);
  }
  saveEdit(): void {
    const p = this.editTarget();
    if (!p || !this.editName.trim()) return;
    this.svc.rename(p.id, this.editName.trim()).subscribe({
      next: () => {
        this.toast.success('Đã đổi tên quy trình', this.editName);
        this.editOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không đổi được tên quy trình')
    });
  }

  askPublish(p: ProcessSummary): void {
    this.publishTarget.set(p);
    this.publishOpen.set(true);
  }
  confirmPublish(): void {
    const p = this.publishTarget();
    if (!p) return;
    this.publishOpen.set(false);
    this.svc.publish(p.id).subscribe({
      next: (v) => {
        this.toast.success('Đã ban hành quy trình', p.name + ' — Phiên bản v' + v.version);
        this.reload();
      },
      error: () => this.toast.error('Không ban hành được', 'Quy trình cần có sơ đồ trước khi ban hành.')
    });
  }

  askRetire(p: ProcessSummary): void {
    this.retireTarget.set(p);
    this.retireOpen.set(true);
  }
  confirmRetire(): void {
    const p = this.retireTarget();
    if (!p) return;
    this.retireOpen.set(false);
    this.svc.retire(p.id).subscribe({
      next: () => {
        this.toast.success('Đã ngừng dùng quy trình', p.name);
        this.reload();
      },
      error: () => this.toast.error('Không ngừng dùng được quy trình')
    });
  }

  askDelete(p: ProcessSummary): void {
    this.delTarget.set(p);
    this.delOpen.set(true);
  }
  confirmDelete(): void {
    const p = this.delTarget();
    if (!p) return;
    this.delOpen.set(false);
    this.svc.remove(p.id).subscribe({
      next: () => {
        this.toast.success('Đã xóa quy trình', p.name);
        this.reload();
      },
      error: () => this.toast.error('Không xóa được quy trình')
    });
  }
}
