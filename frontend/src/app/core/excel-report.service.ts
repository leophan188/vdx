import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ReportColumn {
  header: string;
  type: 'TEXT' | 'NUMBER' | 'DATE';
}

export interface ReportTemplate {
  key: string;
  title: string;
  description: string;
  requiredColumns: ReportColumn[];
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

export interface ReportRunView {
  id: string;
  templateKey: string;
  runBy: string;
  runAt: string;
  inputFileName: string | null;
  status: 'SUCCESS' | 'FAILED';
  message: string | null;
  hasOutput: boolean;
}

/** Công cụ Import Excel → Báo cáo (Epic 4). Dùng phiên (cookie) → withCredentials. */
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
}
