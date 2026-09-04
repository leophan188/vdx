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

export interface ImportResult {
  rows: number;
  message: string;
}

export interface ValidateResponse {
  valid: boolean;
  dataRows: number;
  issues: { row: number; column: string | null; message: string }[];
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

  validateCustomer(file: File): Observable<ValidateResponse> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ValidateResponse>(`${this.base}/customer/validate`, fd);
  }

  importCustomer(period: string, file: File): Observable<ImportResult> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<ImportResult>(`${this.base}/customer/import?period=${period}`, fd);
  }

  customerRows(period: string): Observable<CustomerRow[]> {
    return this.http.get<CustomerRow[]>(`${this.base}/customer/rows?period=${period}`);
  }

  customerTemplateUrl(): string {
    return `${this.base}/customer/template`;
  }

  reconcile(period: string): Observable<ReconcileResponse> {
    return this.http.get<ReconcileResponse>(`${this.base}/reconcile?period=${period}`);
  }
}
