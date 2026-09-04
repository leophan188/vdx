import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Kết nối chung tới ERP — API key không bao giờ đi ra khỏi backend. */
export interface ErpConnection {
  baseUrl: string | null;
  dbName: string | null;
  username: string | null;
  apiKeySet: boolean;
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
}
