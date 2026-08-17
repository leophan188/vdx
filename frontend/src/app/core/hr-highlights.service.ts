import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Nhân sự mừng sinh nhật (ngày/tháng trùng hôm nay hoặc trong tuần). */
export interface BirthdayView {
  empCode: string;
  fullName: string;
  deptCode: string | null;
  jobPosition: string | null;
  title: string | null;
  userId: string | null;
  day: number;
  month: number;
  inDays: number;
}

/** Nhân sự sắp onboard (chưa tới ngày vào) — "còn X ngày Onboard". */
export interface OnboardingView {
  empCode: string;
  fullName: string;
  deptCode: string | null;
  jobPosition: string | null;
  title: string | null;
  userId: string | null;
  joinDate: string;
  daysUntil: number;
}

/**
 * TRI ÂN THÂM NIÊN — kỷ niệm ngày vào làm, chỉ từ TRÒN 1 NĂM trở lên.
 * `years` là số năm đạt được tại lần kỷ niệm này; `inDays` = 0 nghĩa là đúng hôm nay.
 */
export interface AnniversaryView {
  empCode: string;
  fullName: string;
  deptCode: string | null;
  jobPosition: string | null;
  title: string | null;
  userId: string | null;
  joinDate: string;
  years: number;
  inDays: number;
}

export interface HrHighlights {
  birthdaysToday: BirthdayView[];
  birthdaysThisWeek: BirthdayView[];
  onboardingSoon: OnboardingView[];
  anniversariesToday: AnniversaryView[];
  anniversariesThisWeek: AnniversaryView[];
}

/**
 * Điểm nhấn nhân sự cho trang chủ (Việc B + C): sinh nhật hôm nay + sắp onboard.
 * Gọi /api/v1/hr/highlights (mọi user đăng nhập). Dùng phiên (cookie) → withCredentials.
 */
@Injectable({ providedIn: 'root' })
export class HrHighlightsService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/hr';

  highlights(): Observable<HrHighlights> {
    return this.http.get<HrHighlights>(`${this.base}/highlights`, { withCredentials: true });
  }
}
