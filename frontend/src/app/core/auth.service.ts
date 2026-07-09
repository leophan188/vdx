import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, of, tap, catchError } from 'rxjs';

export interface LoginResult {
  username: string;
  fullName?: string;
  userId?: string;
  /** Vị trí công việc (từ hồ sơ nhân sự liên kết) — hiển thị dưới tên ở toolbar. */
  jobTitle?: string | null;
  /** Mã phòng ban (nếu có). */
  deptCode?: string | null;
  /** Id đơn vị (org unit) của người dùng — để điền sẵn trường "chọn đơn vị" theo user đăng nhập. */
  unitId?: string | null;
  authorities: { authority: string }[];
  /** Đang dùng mật khẩu mặc định và không phải admin → BUỘC đổi trước khi vào app. */
  mustChangePassword?: boolean;
  /** Đã có ảnh đại diện chưa — chưa có thì BUỘC tải lên trước khi vào app. */
  hasAvatar?: boolean;
  /** Bộ màu accent giao diện đã lưu theo tài khoản (orange/blue/green/purple/teal/rose). */
  themeAccent?: string;
  /** Chế độ giao diện đã lưu theo tài khoản. */
  themeMode?: 'light' | 'dark';
}

export interface UserAccount {
  id: string;
  username: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  status: string;
  role: string;
  roleCode: string | null;       // vai trò chức năng (phân quyền ma trận)
}

/**
 * Dịch vụ xác thực — gọi backend (AD-9). Dùng phiên (cookie) nên withCredentials.
 * Base URL trỏ tới backend Spring Boot.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly base = '/api/v1';
  readonly currentUser = signal<LoginResult | null>(null);

  constructor(private http: HttpClient) {}

  /** Tập authority của người đang đăng nhập (đọc signal → reactive trong computed/template). */
  private authoritySet(): Set<string> {
    return new Set((this.currentUser()?.authorities ?? []).map((a) => a.authority));
  }

  isAdmin(): boolean {
    return this.authoritySet().has('ROLE_ADMIN');
  }

  /** Admin toàn quyền (ROLE_ADMIN hoặc nhóm "Toàn quyền" — có quyền phân quyền). Dùng cho thao tác nhạy cảm như xoá hồ sơ. */
  isFullAdmin(): boolean {
    const s = this.authoritySet();
    return s.has('ROLE_ADMIN') || s.has('FEAT_PERMISSION');
  }

  /** Có quyền truy cập một CHỨC NĂNG (FEAT_*)? ADMIN luôn đủ. key vd "PROJECT", "HR". */
  hasFeature(key: string): boolean {
    const s = this.authoritySet();
    return s.has('ROLE_ADMIN') || s.has('FEAT_' + key);
  }

  /**
   * CÒN THIẾT LẬP BẮT BUỘC chưa xong? (đang dùng MK mặc định, HOẶC chưa có avatar).
   * Dùng để chặn điều hướng cho tới khi hoàn tất ở màn /account. Chưa đăng nhập → false (guard khác xử lý).
   */
  needsSetup(): boolean {
    const u = this.currentUser();
    if (!u) return false;
    if (this.authoritySet().has('ROLE_ADMIN')) return false; // ADMIN toàn quyền — không bị ép thiết lập
    return u.mustChangePassword === true || u.hasAvatar === false;
  }

  /** Cập nhật cờ thiết lập tại chỗ sau khi đổi MK / upload avatar (đồng bộ ngay, không chờ /me). */
  patchSetupFlags(patch: { mustChangePassword?: boolean; hasAvatar?: boolean }): void {
    const u = this.currentUser();
    if (u) {
      this.currentUser.set({ ...u, ...patch });
    }
  }

  login(username: string, password: string, rememberMe = false): Observable<LoginResult> {
    return this.http
      .post<LoginResult>(`${this.base}/auth/login`, { username, password, rememberMe }, { withCredentials: true })
      .pipe(tap((r) => this.currentUser.set(r)));
  }

  logout(): Observable<void> {
    return this.http
      .post<void>(`${this.base}/auth/logout`, {}, { withCredentials: true })
      .pipe(tap(() => this.currentUser.set(null)));
  }

  /** Lấy người dùng đang đăng nhập theo phiên (cookie). 401 nếu chưa đăng nhập. */
  me(): Observable<LoginResult> {
    return this.http
      .get<LoginResult>(`${this.base}/auth/me`, { withCredentials: true })
      .pipe(tap((r) => this.currentUser.set(r)));
  }

  /** Khôi phục phiên lúc khởi động app (sau F5). Nuốt lỗi 401 → coi như chưa đăng nhập. */
  restore(): Observable<LoginResult | null> {
    return this.me().pipe(
      catchError(() => {
        this.currentUser.set(null);
        return of(null);
      })
    );
  }

  listUsers(): Observable<UserAccount[]> {
    return this.http.get<UserAccount[]>(`${this.base}/users`, { withCredentials: true });
  }

  createUser(payload: {
    username: string; password: string; fullName: string; email?: string; phone?: string; role: string;
  }): Observable<UserAccount> {
    return this.http.post<UserAccount>(`${this.base}/users`, payload, { withCredentials: true });
  }

  updateUser(id: string, payload: { fullName: string; email?: string; phone?: string; role: string; roleCode?: string | null }): Observable<UserAccount> {
    return this.http.patch<UserAccount>(`${this.base}/users/${id}`, payload, { withCredentials: true });
  }

  deleteUser(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/users/${id}`, { withCredentials: true });
  }

  setLocked(id: string, locked: boolean): Observable<void> {
    return this.http.patch<void>(`${this.base}/users/${id}/lock?locked=${locked}`, {}, { withCredentials: true });
  }

  resetPassword(id: string, newPassword: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/users/${id}/password`, { newPassword }, { withCredentials: true });
  }
}
