import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Nhân sự đầy đủ 14 trường + metadata (Epic 1 GĐ2). Ngày dạng dd/MM/yyyy (chuỗi). */
export interface Employee {
  id: string;
  empCode: string;
  status: string | null;
  fullName: string;
  jobPosition: string | null;
  title: string | null;
  deptCode: string | null;
  unit: string | null;
  joinDate: string | null;
  birthDate: string | null;
  leaveDate: string | null;    // dd/MM/yyyy — ngày rời công ty, null khi còn đang làm
  phone: string | null;
  contractType: string | null;
  bankAccount: string | null;
  bankName: string | null;
  level: string | null;
  external: boolean;          // nhân sự thuê ngoài/mượn (tạo & cập nhật thủ công)
  userAccountId: string | null;
  // Liên thông cơ cấu / vị trí / vai trò (Epic 1 GĐ2). orgUnitName/positionTitle/roleNames chỉ điền ở chi tiết.
  orgUnitId: string | null;
  orgUnitName: string | null;
  positionId: string | null;
  positionTitle: string | null;
  roleNames: string[];
  updatedAt: string | null;
  updatedBy: string | null;
  projects: string[];         // mã các dự án đang join (từ ProjectMember)
  totalEffort: number;        // tổng % effort qua các dự án
}

/** Sửa tay — empCode KHÔNG sửa được. */
export interface EmployeeUpdate {
  status: string | null;
  fullName: string;
  jobPosition: string | null;
  title: string | null;
  deptCode: string | null;
  unit: string | null;
  joinDate: string | null;
  birthDate: string | null;
  phone: string | null;
  contractType: string | null;
  bankAccount: string | null;
  bankName: string | null;
  level: string | null;
}

/** Tạo mới nhân sự thủ công (thuê ngoài/mượn) — empCode bắt buộc & duy nhất; external=true do BE đặt. */
export interface EmployeeCreate {
  empCode: string;
  status: string | null;
  fullName: string;
  jobPosition: string | null;
  title: string | null;
  deptCode: string | null;
  unit: string | null;
  joinDate: string | null;
  birthDate: string | null;
  phone: string | null;
  contractType: string | null;
  bankAccount: string | null;
  bankName: string | null;
  level: string | null;
}

export interface EmployeeFilters {
  status?: string;
  deptCode?: string;
  level?: string;
  q?: string;
}

export type PreviewAction = 'ADD' | 'UPDATE' | 'LOCK' | 'HANDOVER' | 'ERROR';

export interface PreviewRow {
  action: PreviewAction;
  empCode: string | null;
  status: string | null;
  fullName: string | null;
  jobPosition: string | null;
  title: string | null;
  deptCode: string | null;
  unit: string | null;
  joinDate: string | null;
  birthDate: string | null;
  phone: string | null;
  contractType: string | null;
  bankAccount: string | null;
  bankName: string | null;
  level: string | null;
  message: string | null;
}

export interface PreviewResponse {
  add: PreviewRow[];
  update: PreviewRow[];
  lock: PreviewRow[];
  handover: PreviewRow[];
  errors: PreviewRow[];
  totalRead: number;
  // Số đơn vị / vị trí / vai trò sẽ tạo mới khi áp dụng (liên thông — Epic 1 GĐ2).
  newOrgUnits: number;
  newPositions: number;
  newRoles: number;
}

export interface ApplyResponse {
  added: number;
  updated: number;
  locked: number;
  handover: number;
  errors: number;
  handoverDetail: PreviewRow[];
}

export interface ImportLog {
  id: string;
  runAt: string;
  runBy: string;
  fileName: string;
  added: number;
  updated: number;
  locked: number;
  handover: number;
  errors: number;
  note: string;
}

export interface SheetConfig {
  sheetUrl: string | null;
  fullSync: boolean;
  autoSync: boolean;
  syncTime: string;        // "HH:mm" — giờ đồng bộ hàng ngày
  lastSyncAt: string | null;
  lastSyncStatus: string | null;
}
export interface SheetConfigSave {
  url: string;
  fullSync: boolean;
  autoSync: boolean;
  syncTime: string;
}

/** Quản lý nhân sự + Import từ file (Epic 1 GĐ2 — chỉ admin). */
@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/employees';

  list(filters: EmployeeFilters = {}): Observable<Employee[]> {
    let params = new HttpParams();
    if (filters.status) params = params.set('status', filters.status);
    if (filters.deptCode) params = params.set('deptCode', filters.deptCode);
    if (filters.level) params = params.set('level', filters.level);
    if (filters.q) params = params.set('q', filters.q);
    return this.http.get<Employee[]>(this.base, { params, withCredentials: true });
  }

  get(id: string): Observable<Employee> {
    return this.http.get<Employee>(`${this.base}/${id}`, { withCredentials: true });
  }

  update(id: string, body: EmployeeUpdate): Observable<Employee> {
    return this.http.put<Employee>(`${this.base}/${id}`, body, { withCredentials: true });
  }

  /** Tạo mới nhân sự thủ công (thuê ngoài/mượn). */
  create(body: EmployeeCreate): Observable<Employee> {
    return this.http.post<Employee>(this.base, body, { withCredentials: true });
  }

  /** Xoá nhân sự THUÊ NGOÀI (chỉ external). */
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, { withCredentials: true });
  }

  preview(file: File, fullSync = false): Observable<PreviewResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('fullSync', String(fullSync));
    return this.http.post<PreviewResponse>(`${this.base}/import/preview`, form, { withCredentials: true });
  }

  apply(file: File, fullSync = false): Observable<ApplyResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('fullSync', String(fullSync));
    return this.http.post<ApplyResponse>(`${this.base}/import/apply`, form, { withCredentials: true });
  }

  // ===== Tuỳ chọn: đồng bộ qua link Google Sheet (sheet phải chia sẻ công khai / xuất bản CSV) =====
  previewSheet(url: string, fullSync = false): Observable<PreviewResponse> {
    return this.http.post<PreviewResponse>(`${this.base}/import/sheet/preview`, { url, fullSync }, { withCredentials: true });
  }
  applySheet(url: string, fullSync = false): Observable<ApplyResponse> {
    return this.http.post<ApplyResponse>(`${this.base}/import/sheet/apply`, { url, fullSync }, { withCredentials: true });
  }

  // Link đã lưu — chủ động tự đồng bộ
  getSheetConfig(): Observable<SheetConfig> {
    return this.http.get<SheetConfig>(`${this.base}/sheet-config`, { withCredentials: true });
  }
  saveSheetConfig(cfg: SheetConfigSave): Observable<SheetConfig> {
    return this.http.put<SheetConfig>(`${this.base}/sheet-config`, cfg, { withCredentials: true });
  }
  syncNow(): Observable<ApplyResponse> {
    return this.http.post<ApplyResponse>(`${this.base}/sheet-config/sync-now`, {}, { withCredentials: true });
  }

  logs(): Observable<ImportLog[]> {
    return this.http.get<ImportLog[]>(`${this.base}/import/logs`, { withCredentials: true });
  }
}
