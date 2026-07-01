import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AuditEvent {
  id: string;
  action: string;
  objectType: string;
  objectId: string;
  actor: string;
  detail: string;
  createdAt: string;
}

/** Truy vết kiểm toán append-only (Story 1.8). Chỉ đọc — ghi đi qua AuditPort ở backend. */
@Injectable({ providedIn: 'root' })
export class AuditService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/audit';

  trail(objectType: string, objectId: string): Observable<AuditEvent[]> {
    const params = new HttpParams().set('objectType', objectType).set('objectId', objectId);
    return this.http.get<AuditEvent[]>(this.base, { params, withCredentials: true });
  }

  /** 200 sự kiện kiểm toán gần nhất toàn hệ thống (duyệt nhanh). */
  recent(): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${this.base}/recent`, { withCredentials: true });
  }

  trailForTask(taskId: string): Observable<AuditEvent[]> {
    return this.http.get<AuditEvent[]>(`${this.base}/task/${encodeURIComponent(taskId)}`, {
      withCredentials: true
    });
  }
}
