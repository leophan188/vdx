import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OtEntry {
  id: string;
  userId: string;
  userName: string;
  orgUnitId: string | null;
  workDate: string;            // YYYY-MM-DD
  startTime: string | null;    // HH:mm:ss
  endTime: string | null;
  hours: number;
  reason: string | null;
  note: string | null;
  periodKey: string;           // YYYY-MM
  closed: boolean;
  createdAt: string;
}

export interface OtEntryRequest {
  workDate: string;
  startTime?: string | null;
  endTime?: string | null;
  hours?: number | null;
  reason?: string | null;
  note?: string | null;
}

export interface OtEmployeeRow {
  userId: string;
  userName: string;
  orgUnitId: string | null;
  orgUnitName: string;
  totalHours: number;
  entryCount: number;
}

export interface OtDeptRow {
  orgUnitId: string | null;
  orgUnitName: string;
  totalHours: number;
  entryCount: number;
}

export interface OtSummary {
  periodKey: string;
  closed: boolean;
  totalHours: number;
  entryCount: number;
  byEmployee: OtEmployeeRow[];
  byDept: OtDeptRow[];
}

/** Tổng hợp OT theo khoảng ngày (FEAT_OT_MANAGE). */
export interface OtRangeSummary {
  from: string;
  to: string;
  byEmployee: OtEmployeeRow[];
  totals: { totalHours: number; people: number };
}

/** Công cụ Đăng ký OT (Epic 3). /api/v1/ot. */
@Injectable({ providedIn: 'root' })
export class OtService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/ot';

  // --- OT của tôi ---
  myEntries(): Observable<OtEntry[]> {
    return this.http.get<OtEntry[]>(`${this.base}/entries`, { withCredentials: true });
  }
  register(req: OtEntryRequest): Observable<OtEntry> {
    return this.http.post<OtEntry>(`${this.base}/entries`, req, { withCredentials: true });
  }
  update(id: string, req: OtEntryRequest): Observable<OtEntry> {
    return this.http.put<OtEntry>(`${this.base}/entries/${id}`, req, { withCredentials: true });
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/entries/${id}`, { withCredentials: true });
  }

  // --- Tổng hợp (ADMIN) ---
  periods(): Observable<string[]> {
    return this.http.get<string[]>(`${this.base}/periods`, { withCredentials: true });
  }
  summary(period: string, orgUnitId?: string): Observable<OtSummary> {
    const q = orgUnitId ? `&orgUnitId=${encodeURIComponent(orgUnitId)}` : '';
    return this.http.get<OtSummary>(`${this.base}/summary?period=${encodeURIComponent(period)}${q}`, { withCredentials: true });
  }
  closePeriod(period: string): Observable<{ periodKey: string; closed: boolean; closedAt: string; closedBy: string }> {
    return this.http.post<{ periodKey: string; closed: boolean; closedAt: string; closedBy: string }>(
      `${this.base}/periods/${encodeURIComponent(period)}/close`, {}, { withCredentials: true });
  }
  /** URL tải file .xlsx — mở trực tiếp với cookie phiên (same-origin). */
  exportUrl(period: string): string {
    return `${this.base}/export?period=${encodeURIComponent(period)}`;
  }

  // --- Tổng hợp theo KHOẢNG NGÀY (FEAT_OT_MANAGE) ---
  summaryRange(from: string, to: string, orgUnitId?: string): Observable<OtRangeSummary> {
    const q = orgUnitId ? `&orgUnitId=${encodeURIComponent(orgUnitId)}` : '';
    return this.http.get<OtRangeSummary>(
      `${this.base}/summary-range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${q}`,
      { withCredentials: true });
  }
  exportRangeUrl(from: string, to: string, orgUnitId?: string): string {
    const q = orgUnitId ? `&orgUnitId=${encodeURIComponent(orgUnitId)}` : '';
    return `${this.base}/export-range?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${q}`;
  }
}
