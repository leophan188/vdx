import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Định nghĩa một CHỨC NĂNG (feature) trong hệ thống phân quyền. */
export interface FeatureDef {
  key: string;
  label: string;
  group: string;
  staffDefault: boolean;
}

/** Một VAI TRÒ PHÂN QUYỀN (thực thể riêng) + chức năng đang bật + số nhân sự. */
export interface PermRole {
  code: string;
  name: string;
  description: string;
  features: string[];
  memberCount: number;
}

/** Tham chiếu tài khoản dùng cho gán thành viên vai trò. */
export interface UserRef {
  userId: string;
  username: string;
  fullName: string;
  jobPosition: string | null;
  deptCode: string | null;
  roleCode: string | null;
  admin: boolean;
}

/**
 * Dịch vụ Phân quyền chức năng.
 * Vai trò phân quyền là THỰC THỂ RIÊNG (tách khỏi vai trò chức danh).
 * Backend đã sẵn sàng — service chỉ gọi API. Dùng phiên (cookie) nên withCredentials.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/permissions';

  /** Danh mục chức năng (gom theo nhóm). */
  features(): Observable<FeatureDef[]> {
    return this.http.get<FeatureDef[]>(`${this.base}/features`, { withCredentials: true });
  }

  /** Danh sách vai trò phân quyền (KHÔNG có chức danh). */
  roles(): Observable<PermRole[]> {
    return this.http.get<PermRole[]>(`${this.base}/roles`, { withCredentials: true });
  }

  /** Tạo vai trò mới (code sinh tự động). */
  createRole(name: string, description: string): Observable<PermRole> {
    return this.http.post<PermRole>(
      `${this.base}/roles`,
      { name, description },
      { withCredentials: true }
    );
  }

  /** Cập nhật vai trò: tên, mô tả và danh sách chức năng. */
  updateRole(
    code: string,
    body: { name: string; description: string; features: string[] }
  ): Observable<PermRole> {
    return this.http.put<PermRole>(`${this.base}/roles/${code}`, body, { withCredentials: true });
  }

  /** Xoá một vai trò phân quyền. */
  deleteRole(code: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/roles/${code}`, { withCredentials: true });
  }

  /** Mọi tài khoản (để chọn gán thành viên). */
  users(): Observable<UserRef[]> {
    return this.http.get<UserRef[]>(`${this.base}/users`, { withCredentials: true });
  }

  /** Thành viên hiện tại của một vai trò. */
  members(code: string): Observable<UserRef[]> {
    return this.http.get<UserRef[]>(`${this.base}/roles/${code}/members`, { withCredentials: true });
  }

  /** Gán đúng danh sách thành viên cho vai trò (ai bỏ ra → về mặc định). */
  setMembers(code: string, userIds: string[]): Observable<UserRef[]> {
    return this.http.put<UserRef[]>(
      `${this.base}/roles/${code}/members`,
      { userIds },
      { withCredentials: true }
    );
  }
}
