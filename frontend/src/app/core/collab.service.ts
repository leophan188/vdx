import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface CommentItem {
  id: string; author: string; authorName: string; body: string;
  createdAt: string; editable: boolean; edited: boolean;
}
export interface OpinionItem {
  id: string; author: string; authorName: string; stance: string; body: string;
  resolution: string | null; resolutionNote: string | null;
  createdAt: string; editable: boolean; edited: boolean; roundId: string | null;
}
export interface RoundItem {
  id: string; requester: string; participants: string; deadline: string; status: string;
}
export interface RoundStatus {
  roundId: string; status: string; participants: string[]; respondedCount: number;
  pending: string[]; overdue: boolean; complete: boolean; deadline: string;
}

/** Cộng tác trên tài liệu/hồ sơ (Story 3.11–3.15). */
@Injectable({ providedIn: 'root' })
export class CollabService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/collab';
  private opts = { withCredentials: true };

  comments(type: string, id: string): Observable<CommentItem[]> {
    return this.http.get<CommentItem[]>(`${this.base}/${type}/${id}/comments`, this.opts);
  }
  addComment(type: string, id: string, body: string): Observable<CommentItem> {
    return this.http.post<CommentItem>(`${this.base}/${type}/${id}/comments`, { body }, this.opts);
  }
  editComment(cid: string, body: string): Observable<CommentItem> {
    return this.http.patch<CommentItem>(`${this.base}/comments/${cid}`, { body }, this.opts);
  }

  opinions(type: string, id: string): Observable<OpinionItem[]> {
    return this.http.get<OpinionItem[]>(`${this.base}/${type}/${id}/opinions`, this.opts);
  }
  giveOpinion(type: string, id: string, stance: string, body: string, roundId?: string): Observable<OpinionItem> {
    return this.http.post<OpinionItem>(`${this.base}/${type}/${id}/opinions`, { stance, body, roundId: roundId ?? null }, this.opts);
  }
  editOpinion(oid: string, stance: string, body: string): Observable<OpinionItem> {
    return this.http.patch<OpinionItem>(`${this.base}/opinions/${oid}`, { stance, body }, this.opts);
  }
  resolveOpinion(oid: string, resolution: string, note: string): Observable<OpinionItem> {
    return this.http.post<OpinionItem>(`${this.base}/opinions/${oid}/resolve`, { resolution, note }, this.opts);
  }

  requestCoordination(type: string, id: string, participants: string[], deadlineHours: number): Observable<RoundItem> {
    return this.http.post<RoundItem>(`${this.base}/${type}/${id}/coordination`, { participants, deadlineHours }, this.opts);
  }
  rounds(type: string, id: string): Observable<RoundItem[]> {
    return this.http.get<RoundItem[]>(`${this.base}/${type}/${id}/rounds`, this.opts);
  }
  roundStatus(rid: string): Observable<RoundStatus> {
    return this.http.get<RoundStatus>(`${this.base}/rounds/${rid}/status`, this.opts);
  }
  closeRound(rid: string): Observable<void> {
    return this.http.post<void>(`${this.base}/rounds/${rid}/close`, {}, this.opts);
  }
}
