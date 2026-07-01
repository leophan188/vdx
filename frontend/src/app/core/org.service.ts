import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface OrgUnit {
  id: string;
  name: string;
  parentId: string | null;
}

/** Dịch vụ cây cơ cấu tổ chức (Story 1.2). */
@Injectable({ providedIn: 'root' })
export class OrgService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/org-units';

  all(): Observable<OrgUnit[]> {
    return this.http.get<OrgUnit[]>(this.base, { withCredentials: true });
  }

  create(name: string, parentId: string | null): Observable<OrgUnit> {
    return this.http.post<OrgUnit>(this.base, { name, parentId }, { withCredentials: true });
  }

  rename(id: string, name: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/name`, { name }, { withCredentials: true });
  }

  move(id: string, newParentId: string | null): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/parent`, { newParentId }, { withCredentials: true });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { withCredentials: true });
  }
}
