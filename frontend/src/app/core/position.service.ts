import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface Position {
  id: string;
  title: string;
  orgUnitId: string;
  currentHolderUserId: string | null;
}

/** Dịch vụ vị trí/chức danh (Story 1.3). */
@Injectable({ providedIn: 'root' })
export class PositionService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/positions';

  byOrgUnit(orgUnitId: string): Observable<Position[]> {
    return this.http.get<Position[]>(`${this.base}?orgUnitId=${orgUnitId}`, { withCredentials: true });
  }

  all(): Observable<Position[]> {
    return this.http.get<Position[]>(`${this.base}/all`, { withCredentials: true });
  }

  create(title: string, orgUnitId: string): Observable<Position> {
    return this.http.post<Position>(this.base, { title, orgUnitId }, { withCredentials: true });
  }

  assign(id: string, userId: string): Observable<void> {
    return this.http.patch<void>(`${this.base}/${id}/holder`, { userId }, { withCredentials: true });
  }

  update(id: string, title: string): Observable<Position> {
    return this.http.patch<Position>(`${this.base}/${id}`, { title }, { withCredentials: true });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { withCredentials: true });
  }
}
