import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Số liệu MỘT nhóm (est giờ + số task + % so với tổng est của dòng). */
export interface GroupStat {
  key: string;
  estimateHours: number;
  taskCount: number;
  pct: number;
}

/** Một dòng báo cáo (tổng quan / theo dự án / theo thành viên). */
export interface ReportRow {
  id: string;
  code: string | null;
  name: string;
  extra: string | null; // bộ phận (thành viên) hoặc mã dự án
  totalEst: number;
  inProgress: GroupStat;
  done: GroupStat;
  upcoming: GroupStat;
  overdue: GroupStat;
  completionPct: number;
}

/** Báo cáo công việc hoàn chỉnh (snapshot live tại mốc). */
export interface WorkReport {
  periodType: 'DAILY' | 'WEEKLY';
  periodLabel: string;
  snapshotDate: string; // dd/MM/yyyy
  overview: ReportRow;
  byProject: ReportRow[];
  byMember: ReportRow[];
}

/**
 * Cụm BÁO CÁO CÔNG VIỆC — Dashboard + Report Ngày + Report Tuần.
 * Đo bằng EST GIỜ của TASK LÁ; 4 nhóm: đang làm / đã xong / trễ (cắt ngang) / sắp làm.
 * Phạm vi dữ liệu do backend quyết theo quyền (admin/FEAT_REPORTS xem tất cả).
 */
@Injectable({ providedIn: 'root' })
export class WorkReportService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/work-reports';

  dashboard(): Observable<WorkReport> {
    return this.http.get<WorkReport>(`${this.base}/dashboard`, { withCredentials: true });
  }

  daily(date?: string): Observable<WorkReport> {
    const q = date ? `?date=${date}` : '';
    return this.http.get<WorkReport>(`${this.base}/daily${q}`, { withCredentials: true });
  }

  weekly(date?: string): Observable<WorkReport> {
    const q = date ? `?date=${date}` : '';
    return this.http.get<WorkReport>(`${this.base}/weekly${q}`, { withCredentials: true });
  }

  /** URL tải Excel (mở trong tab/anchor để kèm phiên cookie). */
  exportUrl(period: 'DAILY' | 'WEEKLY', date?: string): string {
    const seg = period === 'WEEKLY' ? 'weekly' : 'daily';
    const q = date ? `?date=${date}` : '';
    return `${this.base}/${seg}/export.xlsx${q}`;
  }
}
