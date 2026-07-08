import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { FormService, FormSummary } from '../core/form.service';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { StatusBadge, TaskStatus } from '../shared/status-badge/status-badge';
import { PageHeader } from '../shared/page-header/page-header';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-forms',
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, ConfirmDialog, StatusBadge, PageHeader],
  templateUrl: './forms.html'
})
export class Forms implements OnInit {
  private svc = inject(FormService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly rows = signal<FormSummary[]>([]);
  readonly error = signal<string | null>(null);

  readonly cols: GridColumn[] = [
    { key: 'formKey', header: 'Mã biểu mẫu', sortable: true, width: '170px' },
    { key: 'name', header: 'Tên biểu mẫu', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '150px' },
    { key: 'publishedVersion', header: 'Phiên bản', align: 'center', width: '110px' },
    { key: 'actions', header: 'Thao tác', align: 'right', width: '360px' }
  ];

  readonly createOpen = signal(false);
  newKey = '';
  newName = '';
  newCopyFrom = '';

  readonly editOpen = signal(false);
  readonly editTarget = signal<FormSummary | null>(null);
  editName = '';

  readonly delOpen = signal(false);
  readonly delTarget = signal<FormSummary | null>(null);
  readonly publishOpen = signal(false);
  readonly publishTarget = signal<FormSummary | null>(null);
  readonly retireOpen = signal(false);
  readonly retireTarget = signal<FormSummary | null>(null);

  ngOnInit(): void {
    this.reload();
  }
  reload(): void {
    this.svc.list().subscribe({
      next: (r) => this.rows.set(r),
      error: () => this.error.set('Không tải được danh sách biểu mẫu (cần quyền quản trị).')
    });
  }

  statusKind(s: string): TaskStatus {
    return s === 'PUBLISHED' ? 'done' : s === 'RETIRED' ? 'cancel' : 'pending';
  }
  statusLabel(s: string): string {
    return s === 'PUBLISHED' ? 'Đã ban hành' : s === 'RETIRED' ? 'Ngừng dùng' : 'Nháp';
  }

  askPublish(f: FormSummary): void { this.publishTarget.set(f); this.publishOpen.set(true); }
  confirmPublish(): void {
    const f = this.publishTarget();
    if (!f) return;
    this.publishOpen.set(false);
    this.svc.publish(f.id).subscribe({
      next: (v) => { this.toast.success('Đã ban hành biểu mẫu', f.name + ' — v' + v.version); this.reload(); },
      error: () => this.toast.error('Không ban hành được', 'Biểu mẫu cần có ít nhất một trường.')
    });
  }
  askRetire(f: FormSummary): void { this.retireTarget.set(f); this.retireOpen.set(true); }
  confirmRetire(): void {
    const f = this.retireTarget();
    if (!f) return;
    this.retireOpen.set(false);
    this.svc.retire(f.id).subscribe({
      next: () => { this.toast.success('Đã ngừng dùng biểu mẫu', f.name); this.reload(); },
      error: () => this.toast.error('Không ngừng dùng được biểu mẫu')
    });
  }

  openCreate(): void { this.newKey = this.newName = this.newCopyFrom = ''; this.createOpen.set(true); }
  create(): void {
    if (!this.newKey || !this.newName) return;
    this.svc.create(this.newKey, this.newName, this.newCopyFrom).subscribe({
      next: (f) => {
        this.toast.success('Đã tạo biểu mẫu', this.newCopyFrom ? this.newName + ' (đã sao chép)' : this.newName);
        this.createOpen.set(false);
        this.router.navigate(['/forms', f.id]);
      },
      error: () => this.toast.error('Không tạo được biểu mẫu', 'Mã có thể đã tồn tại.')
    });
  }

  design(f: FormSummary): void { this.router.navigate(['/forms', f.id]); }

  openEdit(f: FormSummary): void { this.editTarget.set(f); this.editName = f.name; this.editOpen.set(true); }
  saveEdit(): void {
    const f = this.editTarget();
    if (!f || !this.editName.trim()) return;
    this.svc.rename(f.id, this.editName.trim()).subscribe({
      next: () => { this.toast.success('Đã đổi tên biểu mẫu', this.editName); this.editOpen.set(false); this.reload(); },
      error: () => this.toast.error('Không đổi được tên biểu mẫu')
    });
  }

  askDelete(f: FormSummary): void { this.delTarget.set(f); this.delOpen.set(true); }
  confirmDelete(): void {
    const f = this.delTarget();
    if (!f) return;
    this.delOpen.set(false);
    this.svc.remove(f.id).subscribe({
      next: () => { this.toast.success('Đã xóa biểu mẫu', f.name); this.reload(); },
      error: () => this.toast.error('Không xóa được biểu mẫu')
    });
  }
}
