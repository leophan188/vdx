import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NgTemplateOutlet } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { PageHeader } from '../shared/page-header/page-header';
import { Tabs, TabItem } from '../shared/tabs/tabs';
import { ToastService } from '../shared/toast/toast.service';
import {
  ErpTimesheetService, ErpConfig, CustomerRow, PivotResult, RangeReport, RangeRow
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
  imports: [FormsModule, NgTemplateOutlet, PageHeader, Tabs],
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
    .tsc-card--warn { border-color: var(--status-pending, #d97706); }
    .tsc-card--warn b { font-size: var(--text-lg); }
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
    .tsc-check { display: inline-flex; align-items: center; gap: var(--space-2);
      font-size: var(--text-sm); color: var(--color-text-muted); }
    .tsc-pivot--gap { margin-top: var(--space-3); }
    .tsc-search { height: var(--control-h-sm); min-width: 240px; padding: 0 var(--space-3);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); font: inherit; }
    /* Bảng công theo ngày: 31 cột nên chắc chắn phải cuộn ngang, mà cuộn ngang thì cột tên trôi mất
       và người đọc không còn biết mình đang xem dòng của ai — ghim cột tên lại. */
    .tsc-pivot { overflow-x: auto; border: 1px solid var(--color-border); border-radius: var(--radius-md); }
    .tsc-pivot table { border-collapse: separate; border-spacing: 0; font-size: var(--text-sm);
      font-variant-numeric: tabular-nums; }
    .tsc-pivot th, .tsc-pivot td { padding: 3px var(--space-2); border-bottom: 1px solid var(--color-border);
      white-space: nowrap; text-align: right; }
    .tsc-pivot thead th { position: sticky; top: 0; z-index: 2; background: var(--color-surface-alt);
      color: var(--color-text-muted); font-weight: var(--weight-semibold); }
    .tsc-pivot .tsc-pivot__name { position: sticky; left: 0; z-index: 3; text-align: left;
      background: var(--color-surface); min-width: 220px; border-right: 1px solid var(--color-border); }
    .tsc-pivot thead .tsc-pivot__name { z-index: 4; background: var(--color-surface-alt); }
    .tsc-pivot tbody tr:nth-child(even) td { background: var(--color-surface-zebra); }
    .tsc-pivot tbody tr:nth-child(even) .tsc-pivot__name { background: var(--color-surface-zebra); }
    .tsc-pivot__code { font-family: var(--font-mono, monospace); font-size: var(--text-xs);
      color: var(--color-text-muted); margin-right: var(--space-2); }
    /* Cột Thứ Bảy / Chủ nhật. Phải đặc hiệu hơn quy tắc kẻ sọc ở trên, nếu không dòng chẵn bị nền
       zebra đè mất và cột cuối tuần chỉ hiện màu ở nửa số dòng. Trộn với màu nền thay vì dùng
       transparent để đúng cả nền sáng lẫn nền tối. */
    .tsc-pivot th.tsc-pivot__we,
    .tsc-pivot td.tsc-pivot__we,
    .tsc-pivot tbody tr:nth-child(even) td.tsc-pivot__we {
      background: color-mix(in srgb, var(--color-primary) 16%, var(--color-surface));
    }
    .tsc-pivot thead th.tsc-pivot__we { color: var(--color-text); }
    .tsc-pivot__total { font-weight: var(--weight-semibold);
      border-left: 1px solid var(--color-border); }
    .tsc-pivot__empty { color: var(--color-text-muted); }
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
  /** Danh sách database máy chủ công bố (nếu có) — để chọn thay vì gõ tay. */
  readonly dbOptions = signal<string[]>([]);
  readonly dbHint = signal<string>('');

  /** Lọc chung cho MỌI bảng: gõ tên hoặc mã nhân sự. Bốn bảng là bốn góc nhìn của cùng một tháng
      nên lọc riêng từng bảng chỉ tạo ra cảnh mỗi nơi hiện một tập người khác nhau. */
  readonly keyword = signal('');

  readonly erpPivot = signal<PivotResult | null>(null);
  readonly custPivot = signal<PivotResult | null>(null);

  /**
   * Khoảng kỳ cho báo cáo đối soát. Hai bảng nguồn vẫn xem theo TỪNG tháng (chúng là ảnh chụp dữ liệu
   * của một tháng), còn đối soát thì câu hỏi thường là "cả quý này lệch bao nhiêu, tháng nào lệch".
   */
  readonly fromPeriod = signal<string>(defaultPeriod());
  readonly toPeriod = signal<string>(defaultPeriod());
  readonly report = signal<RangeReport | null>(null);
  readonly reporting = signal(false);
  readonly customerRowsAll = signal<CustomerRow[]>([]);

  readonly erpPivotRows = computed(() =>
    (this.erpPivot()?.rows ?? []).filter((r) => hit(this.keyword(), r.name, r.empCode)));
  readonly custPivotRows = computed(() =>
    (this.custPivot()?.rows ?? []).filter((r) => hit(this.keyword(), r.name, r.empCode)));
  readonly reportRows = computed(() =>
    (this.report()?.rows ?? []).filter((r) => hit(this.keyword(), r.name, r.empCode)));
  /** Chỉ những người có tháng lệch — dùng cho chế độ "chỉ xem dòng lệch". */
  readonly onlyDiff = signal(false);
  readonly visibleReportRows = computed(() =>
    this.onlyDiff() ? this.reportRows().filter((r) => r.monthsWithDiff > 0) : this.reportRows());
  /** Các ngày trong tháng — dựng sẵn để template khỏi tính lại mỗi lần vẽ. */
  readonly pivotDays = computed(() => {
    const n = this.erpPivot()?.daysInMonth ?? this.custPivot()?.daysInMonth ?? 0;
    return Array.from({ length: n }, (_, i) => i + 1);
  });
  private readonly weekendSet = computed(() =>
    new Set(this.erpPivot()?.weekendDays ?? this.custPivot()?.weekendDays ?? []));

  /** Nguồn dữ liệu khách hàng của kỳ — hiện file nào, ai import, lúc nào. */
  readonly customerSource = computed(() => {
    const first = this.customerRowsAll()[0];
    if (!first) return '';
    const when = first.importedAt ? new Date(first.importedAt).toLocaleString('vi-VN') : '';
    return `${first.sourceFile ?? 'file không rõ tên'}${when ? ' · ' + when : ''}`
      + (first.importedBy ? ' · ' + first.importedBy : '');
  });

  constructor() {
    this.svc.config().subscribe({
      next: (c) => {
        this.config.set(c);
        this.form.baseUrl = c.baseUrl ?? '';
        this.form.dbName = c.dbName ?? '';
        this.form.username = c.username ?? '';
        // CHƯA có khoá thì mở sẵn form, không bắt bấm thêm một nút mới thấy được chỗ khai. Đã khai
        // rồi thì gập lại, vì từ đó trở đi màn này dùng để đối soát chứ không phải để sửa kết nối.
        if (!c.apiKeySet) {
          this.configOpen.set(true);
        }
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
    // Kéo khoảng đối soát theo kỳ đang xem: phần lớn lần dùng là đối soát đúng tháng vừa nạp dữ liệu.
    this.fromPeriod.set(value);
    this.toPeriod.set(value);
    this.reload();
  }

  /** Tải lại CẢ BA bảng của kỳ — chúng là ba góc nhìn của cùng một tháng. */
  reload(): void {
    const p = this.period();
    this.loading.set(true);
    this.svc.customerRows(p).subscribe({
      next: (r) => this.customerRowsAll.set(r),
      error: () => this.customerRowsAll.set([])
    });
    this.svc.erpPivot(p).subscribe({ next: (r) => this.erpPivot.set(r), error: () => this.erpPivot.set(null) });
    this.svc.customerPivot(p).subscribe({
      next: (r) => this.custPivot.set(r),
      error: () => this.custPivot.set(null)
    });
    this.loading.set(false);
    // KHÔNG tự chạy đối soát ở đây: người dùng còn phải tải ERP và import file khách hàng xong xuôi
    // rồi mới chốt số, chạy sẵn chỉ bày ra một kết quả nửa vời rồi bị hiểu là kết quả thật.
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

  /**
   * Dò tên database. Người dùng thường chỉ có đường link và tài khoản đăng nhập; tên database là thứ
   * của người quản trị ERP, không hiện ở đâu trong giao diện thường ngày.
   */
  detectDb(): void {
    this.busy.set(true);
    this.svc.detectDb({ ...this.form }).subscribe({
      next: (p) => {
        this.busy.set(false);
        this.dbOptions.set(p.options ?? []);
        this.dbHint.set(p.message ?? '');
        if (p.database) {
          this.form.dbName = p.database;
          this.toast.success('Tìm thấy database: ' + p.database);
        } else {
          this.toast.info(p.message || 'Chưa dò được tên database.');
        }
      },
      error: (e) => {
        this.busy.set(false);
        this.toast.error(msg(e, 'Không dò được database — kiểm tra lại URL.'));
      }
    });
  }

  testConnection(): void {
    this.busy.set(true);
    this.svc.testConnection({ ...this.form }).subscribe({
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

  /** Chạy đối soát cho khoảng kỳ đang chọn — chỉ khi người dùng bấm. */
  runReconcile(): void {
    this.reporting.set(true);
    this.svc.reconcileRange(this.fromPeriod(), this.toPeriod()).subscribe({
      next: (r) => {
        this.report.set(r);
        this.reporting.set(false);
        this.tab.set('reconcile');
      },
      error: (e) => {
        this.reporting.set(false);
        this.report.set(null);
        this.toast.error(msg(e, 'Không đối soát được.'));
      }
    });
  }

  /** Số công của một người trong một tháng — rỗng khi tháng đó không có dữ liệu. */
  cellDiff(row: RangeRow, period: string): string {
    const c = row.byPeriod[period];
    return c === undefined ? '' : this.num(c.diffDays);
  }

  cellDiffClass(row: RangeRow, period: string): string {
    const c = row.byPeriod[period];
    return c === undefined ? '' : this.diffClass(c.diffDays);
  }

  /** Chi tiết ERP/KH của ô, hiện khi rê chuột — bảng chỉ hiện chênh lệch cho đỡ rối. */
  cellTitle(row: RangeRow, period: string): string {
    const c = row.byPeriod[period];
    if (c === undefined) return 'Tháng này không có dữ liệu';
    return `ERP ${this.num(c.erpDays)} · KH ${this.num(c.customerDays)} · lệch ${this.num(c.diffDays)}`;
  }

  /** "2026-01" → "T01/26" cho tiêu đề cột khỏi chiếm chỗ. */
  shortPeriod(p: string): string {
    const [y, m] = p.split('-');
    return `T${m}/${y.slice(2)}`;
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

  /** Tải kết quả đối soát của kỳ — để gửi kèm biên bản mà người nhận không phải mở hệ thống. */
  /** Xuất kỳ đang chọn: đối soát tháng đó + hai bảng công theo ngày. */
  exportExcel(): void {
    this.saveFile(this.svc.exportUrl(this.period()), `doi-soat-cong-${this.period()}.xlsx`);
  }

  /** Xuất đúng báo cáo nhiều tháng đang hiển thị ở tab Đối soát. */
  exportRange(): void {
    const from = this.fromPeriod();
    const to = this.toPeriod();
    this.saveFile(this.svc.exportRangeUrl(from, to), `doi-soat-cong-${from}_${to}.xlsx`);
  }

  setFrom(v: string): void {
    if (v) {
      this.fromPeriod.set(v);
    }
  }

  setTo(v: string): void {
    if (v) {
      this.toPeriod.set(v);
    }
  }

  downloadTemplate(): void {
    this.saveFile(this.svc.customerTemplateUrl(this.period()), `mau-cong-khach-hang-${this.period()}.xlsx`);
  }

  private saveFile(url: string, name: string): void {
    this.http.get(url, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = objectUrl;
        a.download = name;
        a.click();
        URL.revokeObjectURL(objectUrl);
      },
      error: () => this.toast.error('Không tải được file.')
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

  /** NGÀY CÔNG của một người trong một ngày; rỗng nghĩa là ngày đó nghỉ. */
  cell(row: { daysByDay: Record<string, number> }, day: number): string {
    const v = row.daysByDay[String(day)];
    return v === undefined ? '' : this.num(v);
  }

  /** Số giờ tương ứng, cho tooltip — chỉ nguồn ERP mới có. */
  cellHint(row: { hoursByDay?: Record<string, number> }, day: number): string {
    const h = row.hoursByDay?.[String(day)];
    return h === undefined ? '' : this.num(h) + ' giờ';
  }

  isWeekend(day: number): boolean {
    return this.weekendSet().has(day);
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

/** Khớp từ khoá với tên hoặc mã nhân sự, bỏ dấu để gõ "duc" vẫn ra "Đức". */
function hit(keyword: string, name: string | null, code: string | null): boolean {
  const q = norm(keyword);
  if (!q) return true;
  return norm(name).includes(q) || norm(code).includes(q);
}

function norm(s: string | null): string {
  return (s ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd').replace(/Đ/g, 'D').toLowerCase().trim();
}

function msg(e: unknown, fallback: string): string {
  const err = e as { error?: { message?: string; error?: string } };
  return err?.error?.message || err?.error?.error || fallback;
}
