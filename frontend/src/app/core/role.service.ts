import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Role {
  code: string;
  name: string;
  permissions: string[];
}

/** Dịch vụ vai trò & phân quyền (Story 1.4). */
@Injectable({ providedIn: 'root' })
export class RoleService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/roles';

  list(): Observable<Role[]> {
    return this.http.get<Role[]>(this.base, { withCredentials: true });
  }

  create(code: string, name: string, permissions: string[]): Observable<Role> {
    return this.http.post<Role>(this.base, { code, name, permissions }, { withCredentials: true });
  }

  assign(positionId: string, roleCode: string): Observable<void> {
    return this.http.post<void>(`${this.base}/assign`, { positionId, roleCode }, { withCredentials: true });
  }

  update(code: string, name: string, permissions: string[]): Observable<Role> {
    return this.http.patch<Role>(`${this.base}/${code}`, { name, permissions }, { withCredentials: true });
  }

  remove(code: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${code}`, { withCredentials: true });
  }
}
