import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface FormSummary {
  id: string;
  formKey: string;
  name: string;
  status: string;
  publishedVersion: number;
  updatedAt: string;
}

export interface FormDetail extends FormSummary {
  schemaJson: string | null;
}

export interface FormVersion {
  id: string;
  version: number;
  status: string;
  publishedAt: string;
  publishedBy: string;
}

/** Tệp đính kèm đã upload (trường "Tải file"). Lưu mảng các ref này (JSON) vào giá trị trường. */
export interface AttachmentRef {
  id: string;
  name: string;
  size: number;
  contentType: string;
  url: string;
}

/** Dịch vụ biểu mẫu động (Story 2.6). */
@Injectable({ providedIn: 'root' })
export class FormService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/forms';

  list(): Observable<FormSummary[]> {
    return this.http.get<FormSummary[]>(this.base, { withCredentials: true });
  }
  get(id: string): Observable<FormDetail> {
    return this.http.get<FormDetail>(`${this.base}/${id}`, { withCredentials: true });
  }
  create(formKey: string, name: string, copyFromId?: string): Observable<FormSummary> {
    return this.http.post<FormSummary>(this.base, { formKey, name, copyFromId: copyFromId || null }, { withCredentials: true });
  }
  rename(id: string, name: string): Observable<FormSummary> {
    return this.http.patch<FormSummary>(`${this.base}/${id}`, { name }, { withCredentials: true });
  }
  saveSchema(id: string, schemaJson: string): Observable<FormDetail> {
    return this.http.put<FormDetail>(`${this.base}/${id}/schema`, { schemaJson }, { withCredentials: true });
  }
  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { withCredentials: true });
  }
  publish(id: string): Observable<FormVersion> {
    return this.http.post<FormVersion>(`${this.base}/${id}/publish`, {}, { withCredentials: true });
  }
  retire(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/retire`, {}, { withCredentials: true });
  }
  versions(id: string): Observable<FormVersion[]> {
    return this.http.get<FormVersion[]>(`${this.base}/${id}/versions`, { withCredentials: true });
  }

  /** Upload một tệp đính kèm cho trường "Tải file". Trả metadata để lưu vào giá trị trường. */
  uploadAttachment(file: File): Observable<AttachmentRef> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<AttachmentRef>('/api/v1/attachments', form, { withCredentials: true });
  }
}
