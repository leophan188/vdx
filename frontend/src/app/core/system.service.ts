import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Một nhóm dữ liệu có thể xoá (Cấu hình hệ thống → Xoá dữ liệu hệ thống). */
export interface SystemCategory {
  key: string;
  label: string;
  description: string;
  /** Ước lượng số bản ghi hiện có; -1 nếu không đếm được. */
  count: number;
}

/** Kết quả xoá theo nhóm: số bản ghi đã xoá (number) hoặc chuỗi lỗi "Lỗi: …". */
export type WipeResult = Record<string, number | string>;

/** Kết quả seed dữ liệu demo quy trình nhân sự. */
export interface HrDemoSeedResult {
  seeded: boolean;
  processes: number;
  forms: number;
  instances: number;
  message: string;
}

/** Kết quả seed dữ liệu demo module Quản lý dự án. */
export interface ProjectDemoSeedResult {
  seeded: boolean;
  projects: number;
  members: number;
  tasks: number;
  bugs: number;
  message: string;
}

/**
 * Xoá dữ liệu hệ thống theo nhóm — CHỈ ADMIN. KHÔNG xoá tài khoản admin.
 * Backend: /api/v1/system (categories, wipe). Yêu cầu confirm = "XOA".
 */
@Injectable({ providedIn: 'root' })
export class SystemService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/system';

  categories(): Observable<SystemCategory[]> {
    return this.http.get<SystemCategory[]>(`${this.base}/categories`, { withCredentials: true });
  }

  wipe(categories: string[], confirm: string): Observable<WipeResult> {
    return this.http.post<WipeResult>(
      `${this.base}/wipe`,
      { categories, confirm },
      { withCredentials: true }
    );
  }

  /** Tạo dữ liệu demo quy trình nhân sự (CHỈ THÊM, idempotent). */
  seedHrDemo(): Observable<HrDemoSeedResult> {
    return this.http.post<HrDemoSeedResult>(
      `${this.base}/seed-hr-demo`,
      {},
      { withCredentials: true }
    );
  }

  /** Tạo dữ liệu demo module Quản lý dự án (CHỈ THÊM, idempotent). */
  seedProjectDemo(): Observable<ProjectDemoSeedResult> {
    return this.http.post<ProjectDemoSeedResult>(
      `${this.base}/seed-project-demo`,
      {},
      { withCredentials: true }
    );
  }

  /** Chuẩn hoá & sửa dữ liệu nhân sự: giữ số 0 đầu mã NV/SĐT, reset mật khẩu, gán quyền vai trò. */
  fixHrData(password?: string): Observable<Record<string, number | string>> {
    return this.http.post<Record<string, number | string>>(
      `${this.base}/fix-hr-data`,
      { password: password || '' },
      { withCredentials: true }
    );
  }
}
