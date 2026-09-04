import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Kết nối chung tới ERP — API key không bao giờ đi ra khỏi backend. */
export interface ErpConnection {
  baseUrl: string | null;
  dbName: string | null;
  username: string | null;
  apiKeySet: boolean;
  /** Đơn vị trên cây tổ chức ERP mà hệ thống lấy dữ liệu — mọi luồng chỉ lấy trong phạm vi này. */
  orgUnitName: string | null;
  orgUnitErpId: number | null;
  lastCheckAt: string | null;
  lastCheckStatus: string | null;
  updatedBy: string | null;
}

/** Một luồng dữ liệu lấy từ ERP. */
export interface ErpIntegration {
  key: string;
  label: string;
  description: string;
  /** Model gợi ý sẵn trong code; link người dùng dán vào mới là căn cứ cuối cùng. */
  suggestedModel: string;
  linkUrl: string | null;
  modelName: string | null;
  enabled: boolean;
  lastCheckAt: string | null;
  lastCheckStatus: string | null;
  lastCount: number | null;
  updatedBy: string | null;
}

/** Một dự án bên ERP để người dùng tick chọn. */
export interface ErpProjectCandidate {
  erpId: number;
  name: string;
  code: string | null;
  state: string | null;
  startDate: string | null;
  endDate: string | null;
  customer: string | null;
  unit: string | null;
  /** Đã đồng bộ về PlanX chưa. */
  linked: boolean;
  localCode: string | null;
}

export interface ErpOverview {
  connection: ErpConnection;
  integrations: ErpIntegration[];
}

/** Cấu hình tích hợp ERP: kết nối chung + link của từng luồng dữ liệu. */
@Injectable({ providedIn: 'root' })
export class ErpIntegrationService {
  private http = inject(HttpClient);
  private base = '/api/v1/erp-integrations';

  overview(): Observable<ErpOverview> {
    return this.http.get<ErpOverview>(this.base);
  }

  saveConnection(body: { baseUrl: string; dbName: string; username: string; apiKey: string }): Observable<ErpConnection> {
    return this.http.put<ErpConnection>(`${this.base}/connection`, body);
  }

  testConnection(body: { baseUrl: string; dbName: string; username: string; apiKey: string }): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/connection/test`, body);
  }

  save(key: string, body: { linkUrl: string; modelName: string; enabled: boolean }): Observable<ErpIntegration> {
    return this.http.put<ErpIntegration>(`${this.base}/${key}`, body);
  }

  test(key: string): Observable<ErpIntegration> {
    return this.http.post<ErpIntegration>(`${this.base}/${key}/test`, {});
  }

  /** Tra đơn vị theo tên; trả về danh sách khớp (một dòng nghĩa là đã chốt và lưu). */
  resolveOrgUnit(name: string): Observable<{ matches: string[] }> {
    return this.http.post<{ matches: string[] }>(`${this.base}/org-unit`, { name });
  }

  projects(): Observable<ErpProjectCandidate[]> {
    return this.http.get<ErpProjectCandidate[]>(`${this.base}/projects`);
  }

  syncProjects(erpIds: number[]): Observable<{ count: number; message: string }> {
    return this.http.post<{ count: number; message: string }>(`${this.base}/projects/sync`, { erpIds });
  }
}
