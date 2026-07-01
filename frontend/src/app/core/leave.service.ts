import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export type LeaveType = 'ANNUAL' | 'UNPAID';

export interface LeaveEntry {
  id: string;
  fromDate: string;            // YYYY-MM-DD
  toDate: string;              // YYYY-MM-DD
  type: LeaveType;
  typeLabel: string;
  days: number;                // server tự tính (T2–T6)
  reason: string | null;
  userName: string;
}

export interface LeaveEntryRequest {
  fromDate: string;
  toDate: string;
  type: LeaveType;
  reason?: string | null;
}

export interface LeaveEmployeeRow {
  userId: string;
  userName: string;
  orgUnitId: string | null;
  orgUnitName: string;
  annualDays: number;
  unpaidDays: number;
  totalDays: number;
  entryCount: number;
}

export interface LeaveSummary {
  from: string;
  to: string;
  byEmployee: LeaveEmployeeRow[];
  totals: {
    annualDays: number;
    unpaidDays: number;
    totalDays: number;
    people: number;
  };
}

/** Đăng ký nghỉ (ghi nhận, không phê duyệt). /api/v1/leave. */
@Injectable({ providedIn: 'root' })
export class LeaveService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/leave';

  // --- Cá nhân (FEAT_LEAVE) ---
  myEntries(): Observable<LeaveEntry[]> {
    return this.http.get<LeaveEntry[]>(`${this.base}/entries`, { withCredentials: true });
  }
  register(req: LeaveEntryRequest): Observable<LeaveEntry> {
    return this.http.post<LeaveEntry>(`${this.base}/entries`, req, { withCredentials: true });
  }
  update(id: string, req: LeaveEntryRequest): Observable<LeaveEntry> {
    return this.http.put<LeaveEntry>(`${this.base}/entries/${id}`, req, { withCredentials: true });
  }
  remove(id: string): Observable<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(`${this.base}/entries/${id}`, { withCredentials: true });
  }

  // --- Tổng hợp (FEAT_LEAVE_MANAGE) ---
  summary(from: string, to: string, orgUnitId?: string): Observable<LeaveSummary> {
    const q = orgUnitId ? `&orgUnitId=${encodeURIComponent(orgUnitId)}` : '';
    return this.http.get<LeaveSummary>(
      `${this.base}/summary?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${q}`,
      { withCredentials: true });
  }
  /** URL tải file .xlsx — mở trực tiếp với cookie phiên (same-origin). */
  exportUrl(from: string, to: string, orgUnitId?: string): string {
    const q = orgUnitId ? `&orgUnitId=${encodeURIComponent(orgUnitId)}` : '';
    return `${this.base}/export?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}${q}`;
  }
}

/** Đếm số ngày làm việc (T2–T6) trong [fromISO, toISO] inclusive — feedback xem trước client-side. */
export function workdays(fromISO: string, toISO: string): number {
  if (!fromISO || !toISO) return 0;
  const start = new Date(fromISO + 'T00:00:00');
  const end = new Date(toISO + 'T00:00:00');
  if (isNaN(start.getTime()) || isNaN(end.getTime()) || start > end) return 0;
  let count = 0;
  const d = new Date(start);
  while (d <= end) {
    const day = d.getDay(); // 0=CN, 6=T7
    if (day !== 0 && day !== 6) count++;
    d.setDate(d.getDate() + 1);
  }
  return count;
}
