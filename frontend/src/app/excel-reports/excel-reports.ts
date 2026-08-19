import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { ToastService } from '../shared/toast/toast.service';
import {
  ExcelReportService,
  ReportResult,
  ReportTemplate,
  ResultTable,
  ValidationResult,
  ReportRunView
} from '../core/excel-report.service';

/** Một dòng kết quả đã đổi sang dạng object để <data-grid> render (c0, c1, … theo thứ tự cột). */
type ResultRow = Record<string, string | number>;

/**
 * Màn Công cụ (Epic 4, UX-DR7): chọn LOẠI TOOL → tải biểu mẫu mẫu → import file → hiển thị lỗi định dạng →
 * chạy → xem kết quả ngay trên màn hình (nhiều bảng) → xuất .xlsx → bảng lịch sử lần chạy.
 * Khối kết quả bám model trung lập của backend nên thêm loại tool mới KHÔNG phải sửa màn này.
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
    .xlrep-actions { display: flex; gap: var(--space-2); align-items: center; flex-wrap: wrap; }
    .xlrep-metrics { display: flex; flex-wrap: wrap; gap: var(--space-3); }
    .xlrep-metric { border: 1px solid var(--color-border); border-radius: var(--radius-sm);
      padding: var(--space-2) var(--space-3); min-width: 150px; background: var(--color-bg); }
    .xlrep-metric b { display: block; font-size: var(--font-size-lg); }
    .xlrep-tabs { display: flex; gap: var(--space-1); flex-wrap: wrap; }
    .xlrep-warnings { max-height: 180px; overflow: auto; margin: 0; padding-left: var(--space-4);
      font-size: var(--font-size-sm); }
    /* Ô lưới giữ ĐÚNG MỘT dòng: chuỗi dài (tên tool, tên file) cắt bằng … và xem đủ ở tooltip,
       thay vì xuống 5 dòng làm hàng cao ngất và khó dò ngang. */
    .xlrep-cell { display: block; max-width: 100%; overflow: hidden;
      text-overflow: ellipsis; white-space: nowrap; }
    /* Nút hành động trong lưới nằm NGANG, sát mép phải cột. */
    .xlrep-rowacts { display: flex; gap: var(--space-1); justify-content: flex-end; align-items: center; }
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

  /** Kết quả đang xem (của lần chạy vừa xong hoặc mở lại từ lịch sử). */
  readonly result = signal<ReportResult | null>(null);
  readonly resultRunId = signal<string | null>(null);
  readonly activeTable = signal(0);

  /**
   * DESIGN-SYSTEM §4c: đúng MỘT cột nội dung chính không khai báo width (ở đây là "File vào" —
   * chuỗi dài nhất và đáng đọc nhất), đặt sớm; mọi cột còn lại có width sát nội dung thật.
   */
  readonly historyCols: GridColumn[] = [
    { key: 'runAt', header: 'Thời điểm', width: '128px' },
    { key: 'templateKey', header: 'Loại tool', width: '210px' },
    { key: 'inputFileName', header: 'File vào' },
    { key: 'runBy', header: 'Người chạy', width: '128px' },
    { key: 'status', header: 'Trạng thái', align: 'center', width: '112px' },
    { key: 'actions', header: '', align: 'right', width: '84px' }
  ];

  /** Bảng kết quả đang mở. */
  readonly table = computed<ResultTable | null>(() => {
    const r = this.result();
    return r && r.tables.length ? r.tables[Math.min(this.activeTable(), r.tables.length - 1)] : null;
  });

  /**
   * Cột của bảng đang mở, quy về key c0…cN. Theo DESIGN-SYSTEM §4c: bề rộng bám nội dung THẬT
   * (số hẹp, tiền vừa, email/khoảng ngày rộng), và chỉ cột chữ ĐẦU TIÊN chưa có bề rộng riêng
   * mới được co giãn — các cột chữ còn lại phải có width, nếu không chúng tranh chỗ khó đoán.
   */
  readonly tableCols = computed<GridColumn[]>(() => {
    const t = this.table();
    if (!t) return [];
    let flexTaken = false;
    return t.columns.map((header, i) => {
      const type = t.types[i];
      let width = this.fixedWidth(header, type);
      if (!width) {
        if (flexTaken) {
          width = '180px';
        } else {
          flexTaken = true; // cột co giãn duy nhất
        }
      }
      const col: GridColumn = { key: `c${i}`, header, sortable: true };
      if (type !== 'TEXT') col.align = 'right';
      if (width) col.width = width;
      return col;
    });
  });

  /** Bề rộng cố định theo loại/tên cột; trả về rỗng nghĩa là "ứng viên cột co giãn". */
  private fixedWidth(header: string, type: string): string | null {
    if (type === 'MONEY') return '148px';
    if (type === 'NUMBER') return '104px';
    switch (header.trim().toLowerCase()) {
      case 'email': return '230px';
      case 'date': return '190px';       // chứa khoảng ngày "01/07/2026 – 31/07/2026"
      case 'position':
      case 'level':
      case 'vendor': return '112px';
      default: return null;
    }
  }

  readonly tableRows = computed<ResultRow[]>(() => {
    const t = this.table();
    if (!t) return [];
    return t.rows.map((row) => {
      const obj: ResultRow = {};
      row.forEach((v, i) => (obj[`c${i}`] = v));
      return obj;
    });
  });

  ngOnInit(): void {
    this.svc.templates().subscribe({
      next: (t) => {
        this.templates.set(t);
        if (t.length) this.selectedKey.set(t[0].key);
      },
      error: () => this.toast.error('Không tải được danh sách loại tool')
    });
    this.reloadHistory();
  }

  get selectedTemplate(): ReportTemplate | undefined {
    return this.templates().find((t) => t.key === this.selectedKey());
  }

  onTemplateChange(key: string): void {
    this.selectedKey.set(key);
    this.validation.set(null);
    this.clearResult();
  }

  onFile(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    const f = input.files && input.files.length ? input.files[0] : null;
    this.file.set(f);
    this.validation.set(null);
    this.clearResult();
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
      this.toast.warning('Hãy chọn loại tool và tải file lên');
      return;
    }
    this.running.set(true);
    this.svc.run(this.selectedKey(), f).subscribe({
      next: (r) => {
        this.running.set(false);
        this.showResult(r.id, r.result);
        this.toast.success('Đã tính xong', 'Xem kết quả bên dưới hoặc xuất ra .xlsx');
        this.reloadHistory();
      },
      error: (e: HttpErrorResponse) => {
        this.running.set(false);
        this.clearResult();
        this.toast.error('Chạy tool thất bại', e.error?.message ?? 'Kiểm tra lại định dạng file');
        this.reloadHistory();
      }
    });
  }

  /** Tải biểu mẫu Excel trống của loại tool đang chọn. */
  downloadSample(): void {
    const key = this.selectedKey();
    if (!key) return;
    this.svc.sample(key).subscribe({
      next: (blob) => this.saveBlob(blob, `bieu-mau-${key.toLowerCase()}.xlsx`),
      error: () => this.toast.error('Không tải được biểu mẫu')
    });
  }

  /** Mở lại kết quả của một lần chạy trong lịch sử. */
  viewResult(r: ReportRunView): void {
    if (!r.hasResult) {
      this.toast.warning('Lần chạy này không lưu kết quả', 'Hãy tải file .xlsx để xem.');
      return;
    }
    this.svc.result(r.id).subscribe({
      next: (res) => {
        this.showResult(r.id, res);
        document.querySelector('.xlrep-result')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      },
      error: (e: HttpErrorResponse) => this.toast.error('Không mở được kết quả lần chạy này', this.denyMsg(e))
    });
  }

  private showResult(runId: string, res: ReportResult | null): void {
    this.result.set(res);
    this.resultRunId.set(res ? runId : null);
    this.activeTable.set(0);
  }

  private clearResult(): void {
    this.result.set(null);
    this.resultRunId.set(null);
    this.activeTable.set(0);
  }

  /** Xuất file .xlsx đầy đủ (mọi sheet) của kết quả đang xem. */
  exportResult(): void {
    const id = this.resultRunId();
    if (!id) return;
    const run = this.history().find((h) => h.id === id);
    this.svc.download(id).subscribe({
      next: (blob) => this.saveBlob(blob, `ket-qua-${(run?.templateKey ?? 'tool').toLowerCase()}.xlsx`),
      error: (e: HttpErrorResponse) => this.toast.error('Không tải được file kết quả', this.denyMsg(e))
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
      next: (blob) => this.saveBlob(blob, `ket-qua-${r.templateKey.toLowerCase()}.xlsx`),
      error: (e: HttpErrorResponse) => this.toast.error('Không tải được file kết quả', this.denyMsg(e))
    });
  }

  /** Tên loại tool hiển thị trong bảng lịch sử (thay vì khoá kỹ thuật). */
  templateTitle(key: string): string {
    return this.templates().find((t) => t.key === key)?.title ?? key;
  }

  /** 403 = file của người khác; nói thẳng lý do thay vì để người dùng tưởng hệ thống lỗi. */
  private denyMsg(e: HttpErrorResponse): string {
    return e?.status === 403 ? 'Bạn chỉ xem được file do chính mình import.' : (e?.error?.message ?? '');
  }

  private saveBlob(blob: Blob, name: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    a.click();
    URL.revokeObjectURL(url);
  }

  /** Số trong ô kết quả hiển thị kiểu VN (1.234,56); chuỗi giữ nguyên. */
  cell(value: string | number | undefined): string {
    if (value == null || value === '') return '';
    if (typeof value !== 'number') return String(value);
    return value.toLocaleString('vi-VN', { maximumFractionDigits: 2 });
  }

  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }
}
