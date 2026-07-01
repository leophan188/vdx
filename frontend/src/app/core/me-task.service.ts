import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * API client cho BACKLOG CÁ NHÂN (task riêng của tôi) + đồng bộ task dự án.
 * HỢP ĐỒNG khớp backend:
 *  - GET    /api/v1/me/tasks            → PersonalTask[]
 *  - POST   /api/v1/me/tasks            → CreateResult (PERSONAL | PROJECT)
 *  - PUT    /api/v1/me/tasks/{id}       → PersonalTask (chỉ task riêng)
 *  - PATCH  /api/v1/me/tasks/{id}/status→ PersonalTask
 *  - DELETE /api/v1/me/tasks/{id}       → { ok: true }
 *  - GET    /api/v1/me/tasks/projects   → ProjectOption[]
 * Ngày (deadline) dùng ISO yyyy-MM-dd (chuỗi) hoặc null.
 */

/** Một task RIÊNG cá nhân (không thuộc dự án). */
export interface PersonalTask {
  id: string;
  title: string;
  estimateHours: number;
  deadline: string | null;   // ISO yyyy-MM-dd
  status: string;            // BACKLOG/TODO/IN_PROGRESS/IN_REVIEW/DONE
  note: string | null;
  createdAt: string;         // ISO Instant
}

/** Dự án của tôi (để chọn khi thêm task → đồng bộ backlog chung). */
export interface ProjectOption {
  id: string;
  code: string;
  name: string;
}

/** Body tạo task riêng/dự án. projectId có → tạo trong backlog dự án; null → task riêng. */
export interface CreateMeTaskRequest {
  title: string;
  estimateHours?: number | null;
  deadline?: string | null;         // ISO yyyy-MM-dd | null
  projectId?: string | null;
}

/** Body sửa task RIÊNG. */
export interface UpdatePersonalRequest {
  title: string;
  estimateHours?: number | null;
  deadline?: string | null;         // ISO yyyy-MM-dd | null
  status?: string | null;
}

/** Kết quả tạo: task riêng (PERSONAL) hoặc task dự án (PROJECT). */
export interface CreateResult {
  source: 'PERSONAL' | 'PROJECT';
  personal?: PersonalTask;
  projectTaskId?: string;
  projectId?: string;
}

@Injectable({ providedIn: 'root' })
export class MeTaskService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/me/tasks';
  private readonly opts = { withCredentials: true };

  /** Danh sách task RIÊNG cá nhân. */
  listPersonal(): Observable<PersonalTask[]> {
    return this.http.get<PersonalTask[]>(this.base, this.opts);
  }

  /** Tạo task: có projectId → đồng bộ backlog dự án; không → task riêng. */
  create(req: CreateMeTaskRequest): Observable<CreateResult> {
    return this.http.post<CreateResult>(this.base, req, this.opts);
  }

  /** Sửa task RIÊNG (chỉ chủ sở hữu). */
  updatePersonal(id: string, req: UpdatePersonalRequest): Observable<PersonalTask> {
    return this.http.put<PersonalTask>(`${this.base}/${id}`, req, this.opts);
  }

  /** Đổi trạng thái task RIÊNG. */
  setStatus(id: string, status: string): Observable<PersonalTask> {
    return this.http.patch<PersonalTask>(`${this.base}/${id}/status`, { status }, this.opts);
  }

  /** Xoá task RIÊNG. */
  remove(id: string): Observable<{ ok: boolean }> {
    return this.http.delete<{ ok: boolean }>(`${this.base}/${id}`, this.opts);
  }

  /** Dropdown dự án của tôi (để gán task vào backlog dự án). */
  myProjects(): Observable<ProjectOption[]> {
    return this.http.get<ProjectOption[]>(`${this.base}/projects`, this.opts);
  }
}
