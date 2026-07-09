import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface DocSummary {
  id: string;
  name: string;
  version: number;
  instanceId: string | null;
  updatedAt: string;
  updatedBy: string;
  status: string;
  signedNumber: string | null;
  signedAt: string | null;
  signedBy: string | null;
  archiveNo: string | null;
  classification: string | null;
  retentionYears: number | null;
  archivedAt: string | null;
}

export interface DocVersion { version: number; savedAt: string; savedBy: string; }

export interface EditorConfig {
  documentServerUrl: string;
  config: Record<string, unknown>;
  name: string;
  version: number;
}

/** Tài liệu dự thảo OnlyOffice (Story 3.10). */
@Injectable({ providedIn: 'root' })
export class DocumentService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/documents';

  list(): Observable<DocSummary[]> {
    return this.http.get<DocSummary[]>(this.base, { withCredentials: true });
  }
  create(name: string, instanceId?: string): Observable<DocSummary> {
    return this.http.post<DocSummary>(this.base, { name, instanceId: instanceId ?? null }, { withCredentials: true });
  }
  get(id: string): Observable<DocSummary> {
    return this.http.get<DocSummary>(`${this.base}/${id}`, { withCredentials: true });
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { withCredentials: true });
  }
  versions(id: string): Observable<DocVersion[]> {
    return this.http.get<DocVersion[]>(`${this.base}/${id}/versions`, { withCredentials: true });
  }
  editorConfig(id: string): Observable<EditorConfig> {
    return this.http.get<EditorConfig>(`${this.base}/${id}/editor-config`, { withCredentials: true });
  }
  /** Yêu cầu OnlyOffice lưu ngay tài liệu đang mở (gọi khi rời editor). */
  forceSave(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/forcesave`, {}, { withCredentials: true });
  }
  sign(id: string, number: string): Observable<DocSummary> {
    return this.http.post<DocSummary>(`${this.base}/${id}/sign`, { number }, { withCredentials: true });
  }
  archive(id: string, archiveNo: string, classification: string, retentionYears: number): Observable<DocSummary> {
    return this.http.post<DocSummary>(`${this.base}/${id}/archive`, { archiveNo, classification, retentionYears }, { withCredentials: true });
  }
}
