import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AppNotification {
  id: string;
  type: string;
  title: string;
  body: string;
  link: string | null;
  read: boolean;
  createdAt: string;
}

/** Thông báo in-app (Story 4.9). */
@Injectable({ providedIn: 'root' })
export class NotificationService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/notifications';

  list(): Observable<AppNotification[]> {
    return this.http.get<AppNotification[]>(this.base, { withCredentials: true });
  }
  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.base}/unread-count`, { withCredentials: true });
  }
  markRead(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/read`, {}, { withCredentials: true });
  }
  markAllRead(): Observable<void> {
    return this.http.post<void>(`${this.base}/read-all`, {}, { withCredentials: true });
  }
}
