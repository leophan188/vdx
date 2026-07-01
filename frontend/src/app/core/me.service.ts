import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Hồ sơ "Tài khoản của tôi" — fullName ưu tiên QLNS; linkedEmployee → khoá ô Họ tên. */
export interface MyProfile {
  username: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  role: string;
  roleCode: string | null;
  linkedEmployee: boolean;
  hasAvatar: boolean;
}

export interface AvatarResult {
  mediaId: string;
  url: string;
}

/** Self-service tài khoản của tôi (/api/v1/me). Dùng phiên (cookie) nên withCredentials. */
@Injectable({ providedIn: 'root' })
export class MeService {
  private readonly base = '/api/v1/me';
  private http = inject(HttpClient);

  getProfile(): Observable<MyProfile> {
    return this.http.get<MyProfile>(`${this.base}/profile`, { withCredentials: true });
  }

  updateProfile(body: { email?: string | null; phone?: string | null; fullName?: string | null }): Observable<MyProfile> {
    return this.http.put<MyProfile>(`${this.base}/profile`, body, { withCredentials: true });
  }

  changePassword(oldPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>(`${this.base}/password`, { oldPassword, newPassword }, { withCredentials: true });
  }

  uploadAvatar(file: File): Observable<AvatarResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AvatarResult>(`${this.base}/avatar`, form, { withCredentials: true });
  }

  /** URL ảnh đại diện của chính mình (kèm cache-bust để hiển thị ngay sau khi đổi). */
  avatarUrl(bust?: string | number): string {
    const q = bust != null ? `?v=${encodeURIComponent(String(bust))}` : '';
    return `${this.base}/avatar${q}`;
  }

  /** URL ảnh đại diện của một người (topbar / danh sách). */
  avatarUrlOf(userId: string, bust?: string | number): string {
    const q = bust != null ? `?v=${encodeURIComponent(String(bust))}` : '';
    return `${this.base}/avatar/${userId}${q}`;
  }
}
