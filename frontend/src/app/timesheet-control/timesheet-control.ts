import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Tabs, TabItem } from '../shared/tabs/tabs';
import { ToastService } from '../shared/toast/toast.service';
import {
  ErpTimesheetService, ErpConfig, ErpPersonRow, CustomerRow, ReconcileRow, ReconcileSummary
} from '../core/erp-timesheet.service';

/**
 * Kiểm soát giờ công: đọc chấm công từ ERP, nhận công khách hàng ghi nhận từ file Excel, rồi đối soát.
 *
 * Mọi thứ xoay quanh KỲ (tháng) chọn ở đầu màn — cả ba tab dùng chung kỳ đó, vì câu hỏi nghiệp vụ
 * luôn là "tháng này hai bên có khớp không". Ba tab là ba góc nhìn của cùng một tháng nên đổi tháng
 * là tải lại cả ba, tránh cảnh đang so tháng 8 mà bảng bên cạnh hiện tháng 9.
 */
@Component({
  selector: 'app-timesheet-control',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Tabs],
  templateUrl: './timesheet-control.html',
  styles: [`
    .tsc-bar { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap;
      margin-bottom: var(--space-3); }
    .tsc-bar__label { font-size: var(--text-sm); color: var(--color-text-muted); }
    .tsc-month { height: var(--control-h-sm); padding: 0 var(--space-2);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); font: inherit; }
    .tsc-card { border: 1px solid var(--color-border); border-radius: var(--radius-md);
      padding: var(--space-4); background: var(--color-surface); display: grid;
      gap: var(--space-3); margin-bottom: var(--space-4); }
    .tsc-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: var(--space-3); }
    .tsc-hint { font-size: var(--text-sm); color: var(--color-text-muted); margin: 0; }
    .tsc-metrics { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .tsc-metric { border: 1px solid var(--color-border); border-radius: var(--radius-sm);
      padding: var(--space-2) var(--space-3); min-width: 132px; }
    .tsc-metric b { display: block; font-size: var(--text-lg); }
    .tsc-metric span { font-size: var(--text-xs); color: var(--color-text-muted); }
    /* Lệch dương và lệch âm là hai câu chuyện khác nhau — công chưa được ghi nhận, hay ghi nhận
       thừa — nên phải phân biệt được ngay bằng màu, không bắt người đọc dò dấu trừ. */
    .tsc-diff--over { color: var(--status-pending, #d97706); font-weight: var(--weight-semibold); }
    .tsc-diff--under { color: var(--overdue, #e5484d); font-weight: var(--weight-semibold); }
    .tsc-diff--zero { color: var(--color-text-muted); }
    .tsc-file { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
  `]
})
export class TimesheetControl {
  private svc = inject(ErpTimesheetService);
  private http = inject(HttpClient);
  private toast = inject(ToastService);

  readonly tabs: TabItem[] = [
    { key: 'reconcile', label: 'Đối soát', icon: '⚖️' },
    { key: 'erp', label: 'Công ERP', icon: '🏢' },
    { key: 'customer', label: 'Công khách hàng', icon: '📄' }
  ];
  readonly tab = signal<string>('reconcile');

  /** Kỳ đang xem, dạng "yyyy-MM" — mặc định tháng trước, vì đối soát luôn làm cho tháng đã đóng. */
  readonly period = signal<string>(defaultPeriod());
  readonly loading = signal(false);
  readonly busy = signal(false);

  readonly config = signal<ErpConfig | null>(null);
  readonly configOpen = signal(false);
  readonly form = { baseUrl: '', dbName: '', username: '', apiKey: '' };

  readonly erpRows = signal<ErpPersonRow[]>([]);
  readonly customerRows = signal<CustomerRow[]>([]);
  readonly reconcileRows = signal<ReconcileRow[]>([]);
  readonly summary = signal<ReconcileSummary | null>(null);

  /** Nguồn dữ liệu khách hàng của kỳ — hiện file nào, ai import, lúc nào. */
  readonly customerSource = computed(() => {
    const first = this.customerRows()[0];
    if (!first) return '';
    const when = first.importedAt ? new Date(first.importedAt).toLocaleString('vi-VN') : '';
    return `${first.sourceFile ?? 'file không rõ tên'}${when ? ' · ' + when : ''}`
      + (first.importedBy ? ' · ' + first.importedBy : '');
  });

  readonly erpCols: GridColumn[] = [
    { key: 'name', header: 'Nhân sự', sortable: true },
    { key: 'dayCount', header: 'Ngày chấm', width: '110px', align: 'center', sortable: true },
    { key: 'hours', header: 'Tổng giờ', width: '110px', align: 'right', sortable: true },
    { key: 'days', header: 'Quy ra công', width: '124px', align: 'right', sortable: true }
  ];

  readonly customerCols: GridColumn[] = [
    { key: 'name', header: 'Nhân sự', sortable: true },
    { key: 'empCode', header: 'Mã NV', width: '110px' },
    { key: 'days', header: 'Số công', width: '110px', align: 'right', sortable: true },
    { key: 'note', header: 'Ghi chú', width: '260px' }
  ];

  readonly reconcileCols: GridColumn[] = [
    { key: 'name', header: 'Nhân sự', sortable: true },
    { key: 'status', header: 'Tình trạng', width: '150px' },
    { key: 'erpDayCount', header: 'Ngày chấm', width: '108px', align: 'center', sortable: true },
    { key: 'erpHours', header: 'Giờ ERP', width: '104px', align: 'right', sortable: true },
    { key: 'erpDays', header: 'Công ERP', width: '112px', align: 'right', sortable: true },
    { key: 'customerDays', header: 'Công KH', width: '112px', align: 'right', sortable: true },
    { key: 'diffDays', header: 'Lệch', width: '104px', align: 'right', sortable: true }
  ];

  constructor() {
    this.svc.config().subscribe({
      next: (c) => {
        this.config.set(c);
        this.form.baseUrl = c.baseUrl ?? '';
        this.form.dbName = c.dbName ?? '';
        this.form.username = c.username ?? '';
      },
      error: () => this.config.set(null)
    });
    this.reload();
  }

  setTab(key: string): void {
    this.tab.set(key);
  }

  setPeriod(value: string): void {
    if (!value) return;
    this.period.set(value);
    this.reload();
  }

  /** Tải lại CẢ BA bảng của kỳ — chúng là ba góc nhìn của cùng một tháng. */
  reload(): void {
    const p = this.period();
    this.loading.set(true);
    this.svc.erpRows(p).subscribe({ next: (r) => this.erpRows.set(r), error: () => this.erpRows.set([]) });
    this.svc.customerRows(p).subscribe({
      next: (r) => this.customerRows.set(r),
      error: () => this.customerRows.set([])
    });
    this.svc.reconcile(p).subscribe({
      next: (r) => { this.reconcileRows.set(r.rows); this.summary.set(r.summary); this.loading.set(false); },
      error: () => { this.reconcileRows.set([]); this.summary.set(null); this.loading.set(false); }
    });
  }

  // ===== Cấu hình kết nối =====

  saveConfig(): void {
    this.busy.set(true);
    this.svc.saveConfig({ ...this.form }).subscribe({
      next: (c) => {
        this.config.set(c);
        this.form.apiKey = '';
        this.busy.set(false);
        this.toast.success('Đã lưu kết nối ERP.');
      },
      error: (e) => { this.busy.set(false); this.toast.error(msg(e, 'Không lưu được kết nối.')); }
    });
  }

  testConnection(): void {
    this.busy.set(true);
    this.svc.testConnection().subscribe({
      next: (r) => { this.busy.set(false); this.toast.success(r.message); this.refreshConfig(); },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(msg(e, 'Không kết nối được ERP.'));
        this.refreshConfig();
      }
    });
  }

  private refreshConfig(): void {
    this.svc.config().subscribe({ next: (c) => this.config.set(c) });
  }

  // ===== Nguồn 1: ERP =====

  syncErp(): void {
    this.busy.set(true);
    this.svc.syncErp(this.period()).subscribe({
      next: (r) => { this.busy.set(false); this.toast.success(r.message); this.reload(); },
      error: (e) => { this.busy.set(false); this.toast.error(msg(e, 'Không tải được chấm công từ ERP.')); }
    });
  }

  // ===== Nguồn 2: khách hàng =====

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.busy.set(true);
    this.svc.importCustomer(this.period(), file).subscribe({
      next: (r) => {
        this.busy.set(false);
        input.value = '';   // chọn lại đúng file đó lần nữa vẫn phải kích hoạt được
        this.toast.success(r.message);
        this.reload();
      },
      error: (e) => {
        this.busy.set(false);
        input.value = '';
        this.toast.error(msg(e, 'File không hợp lệ — dữ liệu kỳ cũ giữ nguyên.'));
      }
    });
  }

  downloadTemplate(): void {
    this.http.get(this.svc.customerTemplateUrl(), { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'mau-cong-khach-hang.xlsx';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.toast.error('Không tải được biểu mẫu.')
    });
  }

  // ===== hiển thị =====

  diffClass(v: number): string {
    if (Math.abs(v) < 0.01) return 'tsc-diff--zero';
    return v > 0 ? 'tsc-diff--over' : 'tsc-diff--under';
  }

  statusBadge(s: string): string {
    switch (s) {
      case 'MATCHED': return 'badge--done';
      case 'DIFF': return 'badge--pending';
      case 'ERP_ONLY': return 'badge--active';
      default: return 'badge--neutral';
    }
  }

  num(v: number): string {
    return (Math.round(v * 100) / 100).toLocaleString('vi-VN');
  }
}

/** Tháng TRƯỚC tháng hiện tại, dạng "yyyy-MM" — đối soát luôn làm cho kỳ đã đóng. */
function defaultPeriod(): string {
  const d = new Date();
  d.setDate(1);
  d.setMonth(d.getMonth() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function msg(e: unknown, fallback: string): string {
  const err = e as { error?: { message?: string; error?: string } };
  return err?.error?.message || err?.error?.error || fallback;
}
