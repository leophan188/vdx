import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ProcessRow { processName: string; total: number; running: number; completed: number; cancelled: number; }
export interface StatusSummary { total: number; running: number; completed: number; cancelled: number; }
export interface PersonRow { user: string; processed: number; open: number; }
export interface TimeRow { date: string; started: number; }
export interface Report {
  from: string | null; to: string | null;
  byProcess: ProcessRow[]; byStatus: StatusSummary; byPerson: PersonRow[]; byTime: TimeRow[];
}

/** Báo cáo vận hành 4 lát cắt + xuất Excel (Story 4.6/4.8). */
@Injectable({ providedIn: 'root' })
export class ReportService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/reports';

  report(from?: string, to?: string): Observable<Report> {
    let p = new HttpParams();
    if (from) p = p.set('from', from);
    if (to) p = p.set('to', to);
    return this.http.get<Report>(this.base, { params: p, withCredentials: true });
  }

  /** URL tải file CSV (Excel) — mở trực tiếp với cookie phiên (same-origin). */
  exportUrl(from?: string, to?: string): string {
    const q: string[] = [];
    if (from) q.push('from=' + encodeURIComponent(from));
    if (to) q.push('to=' + encodeURIComponent(to));
    return `${this.base}/export.csv${q.length ? '?' + q.join('&') : ''}`;
  }
}
