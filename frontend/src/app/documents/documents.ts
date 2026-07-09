import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { ToastService } from '../shared/toast/toast.service';
import { DocumentService, DocSummary } from '../core/document.service';

/** Danh sách tài liệu dự thảo (Story 3.10) — tạo + mở soạn thảo OnlyOffice. */
@Component({
  selector: 'app-documents',
  imports: [PageHeader, DataGrid, GridCellDirective],
  templateUrl: './documents.html'
})
export class Documents implements OnInit {
  private svc = inject(DocumentService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly cols: GridColumn[] = [
    { key: 'name', header: 'Tên tài liệu', sortable: true },
    { key: 'version', header: 'Phiên bản', align: 'center', width: '110px' },
    { key: 'updatedAt', header: 'Cập nhật', width: '150px' },
    { key: 'updatedBy', header: 'Bởi', width: '140px' },
    { key: 'actions', header: '', width: '140px' }
  ];

  readonly rows = signal<DocSummary[]>([]);
  readonly loading = signal(true);

  ngOnInit(): void { this.reload(); }

  reload(): void {
    this.loading.set(true);
    this.svc.list().subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách tài liệu'); this.loading.set(false); }
    });
  }

  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  create(): void {
    const name = window.prompt('Tên tài liệu mới:', 'Dự thảo công văn');
    if (name === null) return;
    this.svc.create(name || 'Tài liệu mới').subscribe({
      next: (d) => { this.toast.success('Đã tạo tài liệu', d.name); this.open(d); },
      error: () => this.toast.error('Không tạo được tài liệu')
    });
  }

  open(d: DocSummary): void {
    this.router.navigate(['/documents', d.id]);
  }

  remove(d: DocSummary, ev: Event): void {
    ev.stopPropagation();
    if (!window.confirm(`Xoá hẳn tài liệu "${d.name}"? Không khôi phục được.`)) return;
    this.svc.remove(d.id).subscribe({
      next: () => { this.toast.success('Đã xoá tài liệu', d.name); this.reload(); },
      error: () => this.toast.error('Không xoá được tài liệu')
    });
  }
}
