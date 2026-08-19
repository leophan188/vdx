import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ReportColumn {
  header: string;
  type: 'TEXT' | 'NUMBER' | 'DATE';
  required: boolean;
}

/** Một loại tool (mẫu xử lý) khai báo sẵn ở backend. */
export interface ReportTemplate {
  key: string;
  title: string;
  description: string;
  /** Chỉ các cột bắt buộc. */
  requiredColumns: ReportColumn[];
  /** Toàn bộ cột kể cả cột tuỳ chọn. */
  columns: ReportColumn[];
}

export interface ValidationIssue {
  row: number;
  column: string | null;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  dataRows: number;
  issues: ValidationIssue[];
}

/** Ô số nổi bật trên đầu khối kết quả. */
export interface ResultMetric {
  label: string;
  value: string;
}

/** Một bảng kết quả — tương ứng một sheet trong file .xlsx tải về. */
export interface ResultTable {
  key: string;
  title: string;
  columns: string[];
  /** Song song với columns: TEXT | NUMBER | MONEY (dùng để canh phải + định dạng số). */
  types: ('TEXT' | 'NUMBER' | 'MONEY')[];
  rows: (string | number)[][];
}

/** Kết quả một lần chạy, dạng trung lập → mọi loại tool dùng chung một giao diện hiển thị. */
export interface ReportResult {
  metrics: ResultMetric[];
  tables: ResultTable[];
  warnings: string[];
}

export interface ReportRunView {
  id: string;
  templateKey: string;
  runBy: string;
  runAt: string;
  inputFileName: string | null;
  status: 'SUCCESS' | 'FAILED';
  message: string | null;
  hasOutput: boolean;
  hasResult: boolean;
  /** Chỉ có trong phản hồi của run(); danh sách lịch sử không kèm để nhẹ. */
  result: ReportResult | null;
}

/** Công cụ Import Excel → Kết quả (Epic 4). Dùng phiên (cookie) → withCredentials. */
@Injectable({ providedIn: 'root' })
export class ExcelReportService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/excel-reports';

  templates(): Observable<ReportTemplate[]> {
    return this.http.get<ReportTemplate[]>(`${this.base}/templates`, { withCredentials: true });
  }

  validate(templateKey: string, file: File): Observable<ValidationResult> {
    const fd = new FormData();
    fd.append('templateKey', templateKey);
    fd.append('file', file);
    return this.http.post<ValidationResult>(`${this.base}/validate`, fd, { withCredentials: true });
  }

  run(templateKey: string, file: File): Observable<ReportRunView> {
    const fd = new FormData();
    fd.append('templateKey', templateKey);
    fd.append('file', file);
    return this.http.post<ReportRunView>(`${this.base}/run`, fd, { withCredentials: true });
  }

  history(): Observable<ReportRunView[]> {
    return this.http.get<ReportRunView[]>(`${this.base}/history`, { withCredentials: true });
  }

  download(id: string): Observable<Blob> {
    return this.http.get(`${this.base}/${id}/download`, { withCredentials: true, responseType: 'blob' });
  }

  /** Kết quả của một lần chạy cũ (để xem lại trên màn hình từ bảng lịch sử). */
  result(id: string): Observable<ReportResult> {
    return this.http.get<ReportResult>(`${this.base}/${id}/result`, { withCredentials: true });
  }

  /** Biểu mẫu Excel trống của loại tool, để người dùng điền rồi import lại. */
  sample(templateKey: string): Observable<Blob> {
    return this.http.get(`${this.base}/templates/${templateKey}/sample`,
      { withCredentials: true, responseType: 'blob' });
  }
}
