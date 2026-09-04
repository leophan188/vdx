import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Kết nối ERP — API key KHÔNG bao giờ đi ra khỏi backend, chỉ biết đã đặt hay chưa. */
export interface ErpConfig {
  baseUrl: string | null;
  dbName: string | null;
  username: string | null;
  apiKeySet: boolean;
  lastCheckAt: string | null;
  lastCheckStatus: string | null;
  updatedBy: string | null;
}

/** Tổng chấm công ERP của một người trong kỳ. */
export interface ErpPersonRow {
  name: string;
  /** Mã nhân viên tách từ tên hiển thị bên ERP ("Nguyễn Văn A - 4021"). */
  empCode: string | null;
  hours: number;
  days: number;
  /** Số NGÀY có chấm công — khác số công: một ngày chấm 4h vẫn là 1 ngày. */
  dayCount: number;
}

/** Một dòng công khách hàng ghi nhận đã lưu theo kỳ. */
export interface CustomerRow {
  name: string;
  empCode: string | null;
  days: number;
  note: string | null;
  sourceFile: string | null;
  importedAt: string | null;
  importedBy: string | null;
}

export type ReconcileStatus = 'MATCHED' | 'DIFF' | 'ERP_ONLY' | 'CUSTOMER_ONLY';

export interface ReconcileRow {
  name: string;
  empCode: string | null;
  erpHours: number;
  erpDays: number;
  erpDayCount: number;
  customerDays: number;
  /** ERP − khách hàng; dương nghĩa là ERP nhiều hơn. */
  diffDays: number;
  status: ReconcileStatus;
  statusLabel: string;
}

export interface ReconcileSummary {
  total: number;
  matched: number;
  diff: number;
  erpOnly: number;
  customerOnly: number;
  erpDays: number;
  customerDays: number;
  diffDays: number;
}

export interface ReconcileResponse {
  period: string;
  rows: ReconcileRow[];
  summary: ReconcileSummary;
}

/** Kết quả dò tên database Odoo. */
export interface DbProbe {
  database: string | null;
  options: string[];
  message: string;
}

/** Một dòng của bảng công theo ngày. */
export interface PivotRow {
  name: string;
  empCode: string | null;
  /** Hạng mục / dự án khách hàng ghi cho người này (chỉ có ở nguồn khách hàng). */
  note: string | null;
  /** NGÀY CÔNG theo ngày trong tháng (1 hoặc 0,5); ngày vắng mặt trong map = nghỉ. */
  daysByDay: Record<string, number>;
  /** Số giờ tương ứng — chỉ nguồn ERP mới có, dùng cho tooltip. */
  hoursByDay: Record<string, number>;
  totalDays: number;
  dayCount: number;
}

export interface PivotResult {
  period: string;
  daysInMonth: number;
  /** Các ngày rơi vào Thứ Bảy / Chủ nhật — để tô khác màu. */
  weekendDays: number[];
  rows: PivotRow[];
  /** Dữ liệu kỳ này tải bằng phiên bản cũ (thiếu ngày công) — cần tải lại từ ERP. */
  stale: boolean;
}

/** Số công hai bên và chênh lệch của một người trong MỘT tháng. */
export interface RangeCell {
  erpDays: number;
  customerDays: number;
  diffDays: number;
}

export interface RangeRow {
  name: string;
  empCode: string | null;
  /** Khoá là kỳ "yyyy-MM"; tháng không có dữ liệu thì không có khoá. */
  byPeriod: Record<string, RangeCell>;
  totalErp: number;
  totalCustomer: number;
  totalDiff: number;
  monthsWithDiff: number;
}

export interface PeriodTotal {
  period: string;
  erpDays: number;
  customerDays: number;
  diffDays: number;
  peopleWithDiff: number;
}

export interface RangeReport {
  periods: string[];
  rows: RangeRow[];
  months: PeriodTotal[];
  totalErp: number;
  totalCustomer: number;
  totalDiff: number;
  peopleWithDiff: number;
  monthsWithDiff: number;
  /** Dòng chỉ có ở file khách hàng, không có bên ERP — đã bị loại khỏi bảng. */
  droppedCustomerOnlyRows: number;
}

export interface ImportResult {
  rows: number;
  message: string;
}

/** Kiểm soát giờ công: chấm công ERP · công khách hàng · đối soát, tất cả theo kỳ "yyyy-MM". */
@Injectable({ providedIn: 'root' })
export class ErpTimesheetService {
  private http = inject(HttpClient);
  private base = '/api/v1/erp-timesheet';

  config(): Observable<ErpConfig> {
    return this.http.get<ErpConfig>(`${this.base}/config`);
  }

  /** apiKey để trống = giữ nguyên khoá đang lưu. */
  saveConfig(body: { baseUrl: string; dbName: string; username: string; apiKey: string }): Observable<ErpConfig> {
    return this.http.put<ErpConfig>(`${this.base}/config`, body);
  }

  /** Dò tên database từ thông tin đang gõ trên form (chưa cần lưu). */
  detectDb(body: { baseUrl: string; dbName: string; username: string; apiKey: string }): Observable<DbProbe> {
    return this.http.post<DbProbe>(`${this.base}/config/detect-db`, body);
  }

  /** Kiểm tra bằng thông tin đang gõ trên form — ô trống thì backend dùng giá trị đã lưu. */
  testConnection(body: { baseUrl: string; dbName: string; username: string; apiKey: string }): Observable<ImportResult> {
    return this.http.post<ImportResult>(`${this.base}/config/test`, body);
  }

  periods(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/periods`);
  }

  syncErp(period: string): Observable<ImportResult> {
    return this.http.post<ImportResult>(`${this.base}/erp/sync?period=${period}`, {});
  }

  erpRows(period: string): Observable<ErpPersonRow[]> {
    return this.http.get<ErpPersonRow[]>(`${this.base}/erp/rows?period=${period}`);
  }

  /** Bảng ngang nguồn ERP. */
  erpPivot(period: string): Observable<PivotResult> {
    return this.http.get<PivotResult>(`${this.base}/erp/pivot?period=${period}`);
  }

  /** Bảng ngang nguồn khách hàng — cùng khuôn để so bằng mắt. */
  customerPivot(period: string): Observable<PivotResult> {
    return this.http.get<PivotResult>(`${this.base}/customer/pivot?period=${period}`);
  }

  importCustomer(period: string, file: File): Observable<ImportResult> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/customer/import?period=${period}`, fd);
  }

  customerRows(period: string): Observable<CustomerRow[]> {
    return this.http.get<CustomerRow[]>(`${this.base}/customer/rows?period=${period}`);
  }

  customerTemplateUrl(period: string): string {
    return `${this.base}/customer/template?period=${period}`;
  }

  /** Đối soát nhiều tháng — chỉ gọi khi người dùng bấm nút. */
  reconcileRange(from: string, to: string): Observable<RangeReport> {
    return this.http.get<RangeReport>(`${this.base}/reconcile-range?from=${from}&to=${to}`);
  }

  /** Xuất một kỳ (kèm hai bảng công theo ngày). */
  exportUrl(period: string): string {
    return `${this.base}/export?period=${period}`;
  }

  /** Xuất báo cáo đối soát nhiều tháng — đúng bảng đang xem. */
  exportRangeUrl(from: string, to: string): string {
    return `${this.base}/export?from=${from}&to=${to}`;
  }

  reconcile(period: string): Observable<ReconcileResponse> {
    return this.http.get<ReconcileResponse>(`${this.base}/reconcile?period=${period}`);
  }
}
