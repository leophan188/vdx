import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ProcessSummary {
  id: string;
  processKey: string;
  name: string;
  status: string;
  publishedVersion: number;
  updatedAt: string;
}

export interface ProcessDetail extends ProcessSummary {
  bpmnXml: string | null;
  stepsMetaJson: string | null;
}

export interface ProcessVersion {
  id: string;
  version: number;
  status: string;
  publishedAt: string;
  publishedBy: string;
}

export interface ProcessVersionStep {
  stepKey: string;
  stepName: string;
  assigneeType: string;
  assignee: string;
}

/** Dịch vụ định nghĩa quy trình (Story 2.1). */
@Injectable({ providedIn: 'root' })
export class ProcessService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/processes';

  list(): Observable<ProcessSummary[]> {
    return this.http.get<ProcessSummary[]>(this.base, { withCredentials: true });
  }

  get(id: string): Observable<ProcessDetail> {
    return this.http.get<ProcessDetail>(`${this.base}/${id}`, { withCredentials: true });
  }

  create(processKey: string, name: string, copyFromId?: string): Observable<ProcessSummary> {
    return this.http.post<ProcessSummary>(this.base, { processKey, name, copyFromId: copyFromId || null }, { withCredentials: true });
  }

  rename(id: string, name: string): Observable<ProcessSummary> {
    return this.http.patch<ProcessSummary>(`${this.base}/${id}`, { name }, { withCredentials: true });
  }

  saveDesign(id: string, bpmnXml: string, stepsMetaJson: string): Observable<ProcessDetail> {
    return this.http.put<ProcessDetail>(`${this.base}/${id}/design`, { bpmnXml, stepsMetaJson }, { withCredentials: true });
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { withCredentials: true });
  }

  publish(id: string): Observable<ProcessVersion> {
    return this.http.post<ProcessVersion>(`${this.base}/${id}/publish`, {}, { withCredentials: true });
  }

  retire(id: string): Observable<void> {
    return this.http.post<void>(`${this.base}/${id}/retire`, {}, { withCredentials: true });
  }

  versions(id: string): Observable<ProcessVersion[]> {
    return this.http.get<ProcessVersion[]>(`${this.base}/${id}/versions`, { withCredentials: true });
  }
  versionSteps(id: string, version: number): Observable<ProcessVersionStep[]> {
    return this.http.get<ProcessVersionStep[]>(`${this.base}/${id}/versions/${version}/steps`, { withCredentials: true });
  }
}
