import { Component, OnInit, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { ToastService } from '../shared/toast/toast.service';
import {
  ExcelReportService,
  ReportTemplate,
  ValidationResult,
  ReportRunView
} from '../core/excel-report.service';

/**
 * Màn Import Excel → Báo cáo (Epic 4, UX-DR7): chọn mẫu → tải file → hiển thị lỗi định dạng →
 * chạy → tải kết quả .xlsx → bảng lịch sử lần chạy. Theo design-system GĐ1.
 */
@Component({
  selector: 'app-excel-reports',
  imports: [PageHeader, DataGrid, GridCellDirective],
  templateUrl: './excel-reports.html',
  styles: [`
    .xlrep-card { border: 1px solid var(--color-border); border-radius: var(--radius-md);
      padding: var(--space-4); background: var(--color-surface); display: grid;
      gap: var(--space-3); margin-bottom: var(--space-4); }
    .xlrep-cols { display: flex; flex-wrap: wrap; gap: var(--space-1); }
    .xlrep-issues { margin-top: var(--space-2); border: 1px solid var(--color-border);
      border-radius: var(--radius-sm); overflow: hidden; }
    .xlrep-issues table { width: 100%; border-collapse: collapse; font-size: var(--font-size-sm); }
    .xlrep-issues th, .xlrep-issues td { padding: var(--space-1) var(--space-2);
      text-align: left; border-bottom: 1px solid var(--color-border); }
    .xlrep-actions { display: flex; gap: var(--space-2); align-items: center; }
  `]
})
export class ExcelReports implements OnInit {
  private svc = inject(ExcelReportService);
  private toast = inject(ToastService);

  readonly templates = signal<ReportTemplate[]>([]);
  readonly selectedKey = signal<string>('');
  readonly file = signal<File | null>(null);
  readonly validation = signal<ValidationResult | null>(null);
  readonly running = signal(false);
  readonly history = signal<ReportRunView[]>([]);
  readonly loadingHistory = signal(true);

  readonly historyCols: GridColumn[] = [
    { key: 'runAt', header: 'Thời điểm', width: '160px' },
    { key: 'templateKey', header: 'Mẫu', width: '160px' },
    { key: 'runBy', header: 'Người chạy', width: '140px' },
    { key: 'inputFileName', header: 'File vào' },
    { key: 'status', header: 'Trạng thái', align: 'center', width: '120px' },
    { key: 'actions', header: '', width: '130px' }
  ];

  ngOnInit(): void {
    this.svc.templates().subscribe({
      next: (t) => {
        this.templates.set(t);
        if (t.length) this.selectedKey.set(t[0].key);
      },
      error: () => this.toast.error('Không tải được danh sách mẫu báo cáo')
    });
    this.reloadHistory();
  }

  get selectedTemplate(): ReportTemplate | undefined {
    return this.templates().find((t) => t.key === this.selectedKey());
  }

  onTemplateChange(key: string): void {
    this.selectedKey.set(key);
    this.validation.set(null);
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files && input.files.length ? input.files[0] : null;
    this.file.set(f);
    this.validation.set(null);
    if (f) this.validate();
  }

  validate(): void {
    const f = this.file();
    if (!f || !this.selectedKey()) return;
    this.svc.validate(this.selectedKey(), f).subscribe({
      next: (r) => {
        this.validation.set(r);
        if (r.valid) this.toast.success('File hợp lệ', `${r.dataRows} dòng dữ liệu`);
      },
      error: () => this.toast.error('Không kiểm tra được file')
    });
  }

  run(): void {
    const f = this.file();
    if (!f || !this.selectedKey()) {
      this.toast.warning('Hãy chọn mẫu và tải file lên');
      return;
    }
    this.running.set(true);
    this.svc.run(this.selectedKey(), f).subscribe({
      next: (r) => {
        this.running.set(false);
        this.toast.success('Đã tạo báo cáo', 'Bạn có thể tải kết quả .xlsx');
        this.reloadHistory();
        this.downloadRun(r);
      },
      error: (e: HttpErrorResponse) => {
        this.running.set(false);
        this.toast.error('Chạy báo cáo thất bại', e.error?.message ?? 'Kiểm tra lại định dạng file');
        this.reloadHistory();
      }
    });
  }

  reloadHistory(): void {
    this.loadingHistory.set(true);
    this.svc.history().subscribe({
      next: (h) => { this.history.set(h); this.loadingHistory.set(false); },
      error: () => { this.toast.error('Không tải được lịch sử'); this.loadingHistory.set(false); }
    });
  }

  downloadRun(r: ReportRunView): void {
    if (!r.hasOutput) {
      this.toast.warning('Lần chạy này không có file kết quả');
      return;
    }
    this.svc.download(r.id).subscribe({
      next: (blob) => this.saveBlob(blob, `bao-cao-${r.templateKey.toLowerCase()}.xlsx`),
      error: () => this.toast.error('Không tải được file kết quả')
    });
  }

  private saveBlob(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.click();
    URL.revokeObjectURL(url);
  }

  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
}
