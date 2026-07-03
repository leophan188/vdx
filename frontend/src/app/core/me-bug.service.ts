import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ProjectOption } from './me-task.service';
import { TaskStatus, TaskPriority, BugSeverity } from './project.service';

/**
 * API client cho BUG/ISSUE CÁ NHÂN (xuyên dự án) — màn "Bug/Issue của tôi".
 * HỢP ĐỒNG khớp backend:
 *  - GET  /api/v1/me/bugs           → MyBug[]
 *  - POST /api/v1/me/bugs           → CreateResult
 *  - GET  /api/v1/me/tasks/projects → ProjectOption[] (dự án của tôi)
 * Ngày (deadline) dùng ISO yyyy-MM-dd khi TẠO; dueDate trả về là dd/MM/yyyy (chuỗi) hoặc null.
 */

export type MyBugType = 'BUG' | 'ISSUE';
/** Vai trò của tôi trên bug: người log / người xử lý / cả hai. */
export type MyBugRole = 'REPORTER' | 'ASSIGNEE' | 'BOTH';

/** Một bug/issue của tôi (xuyên dự án) — tôi log HOẶC tôi là PIC. */
export interface MyBug {
  id: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  code: string;
  title: string;
  type: MyBugType;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeUserId: string | null;
  assigneeName: string | null;
  reporterUserId: string | null;
  reporterName: string | null;
  dueDate: string | null;   // dd/MM/yyyy | null
  role: MyBugRole;
}

/** Body tạo bug/issue cá nhân. */
export interface LogBugRequest {
  projectId: string;
  type: MyBugType;
  title: string;
  description?: string | null;
  priority?: TaskPriority | null;
  assigneeUserId?: string | null;
  estimateHours?: number | null;
  dueDate?: string | null;   // ISO yyyy-MM-dd | null
  // Chi tiết lỗi (BUG/ISSUE)
  severity?: BugSeverity | null;
  screen?: string | null;
  environment?: string | null;
  stepsToReproduce?: string | null;
  expectedResult?: string | null;
  actualResult?: string | null;
}

/**
 * Kết quả tạo (khớp backend MeTaskDto.CreateResult):
 *  - source="PROJECT" → projectTaskId + projectId (task vào backlog chung)
 *  - source="PERSONAL" → personal có giá trị (task riêng)
 */
export interface CreateResult {
  source?: 'PROJECT' | 'PERSONAL';
  projectTaskId?: string;   // id task dự án vừa tạo (dùng để đính kèm ảnh)
  projectId?: string;
  personal?: { id?: string } | null;
}

/** Loại cho TẠO NHANH (toolbar): đủ 6 loại như backlog. */
export type QuickCreateType = 'EPIC' | 'STORY' | 'TASK' | 'SUBTASK' | 'BUG' | 'ISSUE';

/** Body TẠO NHANH (POST /api/v1/me/bugs) — reporter = tôi; tạo trong dự án tôi là thành viên. */
export interface QuickCreateRequest {
  projectId: string;
  type: QuickCreateType;
  title: string;
  description?: string | null;
  priority?: TaskPriority | null;
  assigneeUserId?: string | null;  // PIC / người thực hiện
  testerUserId?: string | null;    // người kiểm thử (tuỳ chọn)
  estimateHours?: number | null;
  dueDate?: string | null;         // ISO yyyy-MM-dd | null
  parentId?: string | null;        // task cha (theo phân cấp loại); EPIC = null
  // Chi tiết lỗi (chỉ BUG/ISSUE)
  severity?: BugSeverity | null;
  screen?: string | null;
  environment?: string | null;
  stepsToReproduce?: string | null;
  expectedResult?: string | null;
  actualResult?: string | null;
}

@Injectable({ providedIn: 'root' })
export class MeBugService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/me/bugs';
  private readonly opts = { withCredentials: true };

  /** Danh sách bug/issue của tôi (log hoặc xử lý), xuyên dự án. */
  list(): Observable<MyBug[]> {
    return this.http.get<MyBug[]>(this.base, this.opts);
  }

  /** Log một bug/issue mới vào dự án. */
  log(req: LogBugRequest): Observable<CreateResult> {
    return this.http.post<CreateResult>(this.base, req, this.opts);
  }

  /** TẠO NHANH (toolbar): Task/Bug/Issue trong dự án tôi là thành viên (reporter = tôi). */
  quickCreate(req: QuickCreateRequest): Observable<CreateResult> {
    return this.http.post<CreateResult>(this.base, req, this.opts);
  }

  /** Dropdown dự án của tôi (tái dùng endpoint me/tasks/projects). */
  myProjects(): Observable<ProjectOption[]> {
    return this.http.get<ProjectOption[]>('/api/v1/me/tasks/projects', this.opts);
  }
}
