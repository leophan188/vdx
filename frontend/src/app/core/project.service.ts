import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * API client module Quản lý dự án (mini-Jira). HỢP ĐỒNG khớp CHÍNH XÁC DTO backend
 * (com.bpm.api.dto.ProjectDto). Ngày nghiệp vụ dạng dd/MM/yyyy (chuỗi); metadata ISO Instant (chuỗi).
 */

// ===== Enums (string union — khớp tên enum backend) =====
export type ProjectStatus = 'PLANNING' | 'ACTIVE' | 'ON_HOLD' | 'DONE' | 'CANCELLED';
export type ProjectRole = 'PM' | 'LEAD' | 'MEMBER';
export type TaskType = 'EPIC' | 'STORY' | 'TASK' | 'SUBTASK' | 'BUG' | 'ISSUE';
export type TaskStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'IN_REVIEW' | 'DONE' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT';
/** Mức độ nghiêm trọng của lỗi (BUG/ISSUE) — kiểu Jira. */
export type BugSeverity = 'BLOCKER' | 'CRITICAL' | 'MAJOR' | 'MINOR' | 'TRIVIAL';

// ===== Project =====
export interface Project {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: ProjectStatus;
  startDate: string | null;     // dd/MM/yyyy
  dueDate: string | null;       // dd/MM/yyyy
  ownerUserId: string | null;
  ownerName: string | null;
  budget: number | null;        // ngân sách (VND), nullable
  plannedEffortMm: number | null; // nỗ lực KẾ HOẠCH (man-month, nhập tay) — để so với thực tế
  completionPct: number;        // 0..100
  memberCount: number;
  taskCount: number;
  totalEffortMM: number;        // nỗ lực THỰC TẾ = Σ(member.manday)/22
  createdAt: string | null;     // ISO Instant
  updatedAt: string | null;     // ISO Instant
}

/** Tạo/sửa dự án. code chỉ dùng khi tạo (PUT bỏ qua). status để trống khi tạo → PLANNING. */
export interface ProjectRequest {
  code?: string | null;
  name: string;
  description?: string | null;
  status?: ProjectStatus | null;
  startDate?: string | null;    // dd/MM/yyyy
  dueDate?: string | null;      // dd/MM/yyyy
  ownerUserId?: string | null;
  budget?: number | null;       // ngân sách (VND), nullable
  plannedEffortMm?: number | null; // nỗ lực kế hoạch (man-month, nhập tay)
}

// ===== Member =====
export interface ProjectMember {
  id: string;
  projectId: string;
  userId: string;
  name: string;                 // denorm từ UserAccount
  empCode: string | null;
  jobPosition: string | null;   // denorm từ Employee (theo userId)
  title: string | null;         // chức danh — denorm từ Employee (theo userId)
  deptCode: string | null;      // denorm từ Employee (theo userId)
  roleInProject: ProjectRole;
  startDate: string | null;     // dd/MM/yyyy
  endDate: string | null;       // dd/MM/yyyy
  effortPct: number;            // % effort dành cho dự án (1–100)
  workdays: number;             // số ngày làm việc (T2–T6) trong khoảng
  manday: number;               // man-day thực tế = workdays × effort%
  joinedAt: string | null;      // ISO Instant
}

export interface AddMemberRequest {
  userId: string;
  role?: ProjectRole | null;    // để trống → MEMBER
  startDate?: string | null;    // dd/MM/yyyy
  endDate?: string | null;      // dd/MM/yyyy
  effortPct?: number | null;    // % effort (1–100), trống → 100
}

/** Sửa thành viên — KHÔNG đổi người. */
export interface UpdateMemberRequest {
  role?: ProjectRole | null;
  startDate?: string | null;    // dd/MM/yyyy
  endDate?: string | null;      // dd/MM/yyyy
  effortPct?: number | null;
}

/** Một mắt xích cha của task (Epic/Story/Task cha). */
export interface ParentRef {
  type: TaskType;
  code: string;
  title: string;
}

// ===== Task =====
export interface ProjectTask {
  id: string;
  projectId: string;
  parentId: string | null;      // null = task gốc; đa cấp
  seq: number;
  code: string;                 // "PRJ-12"
  title: string;
  description: string | null;
  type: TaskType;
  status: TaskStatus;
  priority: TaskPriority;
  assigneeUserId: string | null;
  assigneeName: string | null;     // denorm từ UserAccount
  assigneeCode: string | null;     // mã NV (empCode), hoặc username nếu không có Employee
  assigneePosition: string | null; // vị trí (Employee.jobPosition)
  assigneeTitle: string | null;    // chức danh (Employee.title)
  assigneeDept: string | null;     // mã bộ phận (Employee.deptCode)
  // ===== Người kiểm thử (tester) + người log (reporter) — denorm từ UserAccount =====
  testerUserId?: string | null;
  testerName?: string | null;
  reporterUserId?: string | null;
  reporterName?: string | null;
  estimateHours: number;
  spentHours: number;           // thời gian thực tế đã log (giờ); cộng dồn qua log-work
  startDate: string | null;     // dd/MM/yyyy
  dueDate: string | null;       // dd/MM/yyyy
  orderIndex: number;
  screen: string | null;        // màn hình liên quan (BUG/ISSUE) — optional, không bắt buộc
  // ===== Chi tiết lỗi (BUG/ISSUE) — đều nullable =====
  severity: BugSeverity | null;     // mức độ nghiêm trọng
  stepsToReproduce: string | null;  // các bước tái hiện
  expectedResult: string | null;    // kết quả mong đợi
  actualResult: string | null;      // kết quả thực tế
  environment: string | null;       // môi trường (trình duyệt/OS/phiên bản)
  leaf: boolean;                // không có con (dùng cho completion)
  progressPct: number;          // 0..100; lá DONE=100/khác=0; cha = rollup theo est lá DONE trong cây con
  parentChain: ParentRef[];     // chuỗi cha gốc→cha trực tiếp (Epic › Story › Task cha)
  createdAt: string | null;     // ISO Instant
  updatedAt: string | null;     // ISO Instant
  createdBy: string | null;
}

/** Tạo/sửa task. parentId nullable; enum để trống dùng mặc định (type=TASK, status=BACKLOG, priority=MEDIUM). */
export interface TaskRequest {
  parentId?: string | null;
  title: string;
  description?: string | null;
  type?: TaskType | null;
  status?: TaskStatus | null;
  priority?: TaskPriority | null;
  assigneeUserId?: string | null;
  estimateHours?: number | null;
  startDate?: string | null;    // dd/MM/yyyy
  dueDate?: string | null;      // dd/MM/yyyy
  orderIndex?: number | null;
  screen?: string | null;
  testerUserId?: string | null; // người kiểm thử (bàn giao khi chuyển Kiểm thử)
  // ===== Chi tiết lỗi (BUG/ISSUE) — đều optional =====
  severity?: BugSeverity | null;
  stepsToReproduce?: string | null;
  expectedResult?: string | null;
  actualResult?: string | null;
  environment?: string | null;
}

export interface ReorderItem {
  taskId: string;
  parentId: string | null;
  orderIndex: number;
}

// ===== Report =====
export interface AssigneeStat {
  userId: string;
  name: string;
  total: number;
  done: number;
  backlog: number;
  todo: number;
  doing: number;
  review: number;
  cancel: number;
  estimate: number;
}

export interface ProjectReport {
  completionPct: number;        // 0..100
  totalTasks: number;
  doneTasks: number;
  leafTasks: number;
  leafDoneTasks: number;
  totalEstimate: number;
  doneEstimate: number;
  totalSpent: number;           // tổng giờ thực tế đã log (so sánh với totalEstimate)
  bugCount: number;
  overdue: number;              // task quá dueDate chưa DONE
  byStatus: Record<TaskStatus, number>;
  byType: Record<TaskType, number>;
  byAssignee: AssigneeStat[];
}

// ===== People =====
export interface Person {
  userId: string;
  name: string;
  empCode: string | null;
  jobPosition: string | null;
  title: string | null;
  deptCode: string | null;
}

/** Một việc của tôi (xuyên dự án) — màn cá nhân. */
export interface MyTask {
  taskId: string;
  projectId: string;
  projectCode: string;
  projectName: string;
  code: string;
  title: string;
  type: TaskType;
  status: TaskStatus;
  priority: TaskPriority;
  estimateHours: number;
  dueDate: string | null;
  progressPct: number;
  assigneeCode: string | null;   // mã NV của tôi (nếu có Employee liên kết)
}

// ===== Bình luận task (kiểu Jira) =====
export interface TaskComment {
  id: string;
  taskId: string;
  authorId: string;
  authorName: string;            // denorm từ UserAccount
  body: string;
  edited: boolean;
  mine: boolean;                 // bình luận của chính user hiện tại
  createdAt: string | null;      // ISO Instant
  updatedAt: string | null;      // ISO Instant
}

export interface CommentRequest {
  body: string;
}

// ===== Ảnh đính kèm task (kiểu Jira) =====
export interface TaskAttachment {
  id: string;
  taskId: string;
  fileName: string;
  contentType: string;
  size: number;                  // bytes
  uploadedBy: string | null;
  uploadedAt: string | null;     // ISO Instant
  url: string;                   // đường dẫn nội dung ảnh (có phiên)
  commentId: string | null;      // != null → ảnh thuộc bình luận, hiện dưới bình luận đó
}

// ===== Lịch sử / hoạt động task (kiểu Jira) =====
export type TaskActivityAction =
  'CREATED' | 'STATUS' | 'ASSIGN' | 'EDIT' | 'COMMENT' | 'ATTACH' | 'SPENT';

export interface TaskActivity {
  id: string;
  taskId: string;
  actorName: string;             // tên người thao tác (denorm)
  action: TaskActivityAction;
  detail: string | null;         // mô tả ngắn (vd "TODO → DONE", "+2.5h")
  createdAt: string | null;      // ISO Instant
}

// ===== Nhật ký hoạt động cấp DỰ ÁN (kiểu Jira "Activity stream") =====
export interface ProjectActivityItem {
  id: string;
  taskId: string;
  taskCode: string | null;       // null nếu task đã xoá
  taskTitle: string | null;      // null nếu task đã xoá
  actorName: string;
  action: TaskActivityAction;
  detail: string | null;
  createdAt: string | null;      // ISO Instant
}

// ===== Báo cáo ngày / tuần =====
export interface ReportTaskItem {
  taskId: string;
  code: string;                  // "PRJ-12"
  title: string;
  type: TaskType;
  status: TaskStatus;
  assigneeName: string | null;
  estimateHours: number;
  startDate: string | null;      // dd/MM/yyyy (ngày bắt đầu)
  dueDate: string | null;        // dd/MM/yyyy
  progressPct: number;           // 0..100
  priority: TaskPriority | null; // ưu tiên (thống kê bug/issue theo mức ưu tiên)
  severity: BugSeverity | null;  // mức độ nghiêm trọng (bug)
  assigneeUserId: string | null; // gộp theo nhân sự
  parentPath: string | null;     // "Epic: … › Story: …" (ngữ cảnh cha)
  reporterUserId: string | null; // người LOG (tester tạo bug/việc)
  reporterName: string | null;
}

export interface ReportOverview {
  completionPct: number;         // 0..100
  totalTasks: number;
  doneTasks: number;
  totalEstimate: number;
  doneEstimate: number;
  overdueCount: number;
  bugCount: number;
}

/** Tỷ lệ hoàn thành theo nhân sự trong kỳ (khớp DTO PersonProgress backend). */
export interface PersonProgress {
  userId: string | null;
  name: string;
  total: number;
  done: number;
  doing: number;
  todo: number;
  overdue: number;
  pct: number;                   // 0..100
}

export interface PeriodReport {
  periodLabel: string;           // vd "Hôm nay 27/06/2026" / "Tuần 22/06–28/06"
  done: ReportTaskItem[];        // DONE, cập nhật trong kỳ (updatedAt trong khoảng)
  inProgress: ReportTaskItem[];  // IN_PROGRESS + IN_REVIEW
  upcoming: ReportTaskItem[];    // TODO/BACKLOG có startDate trong cửa sổ tới, hoặc chưa lên lịch
  overdue: ReportTaskItem[];     // dueDate < hôm nay & chưa DONE
  overview: ReportOverview;
  epicStory: ReportTaskItem[];   // (chỉ báo cáo TUẦN) tiến độ % EPIC/Story của dự án
  byPerson: PersonProgress[];    // tỷ lệ hoàn thành theo nhân sự (việc lá)
  bugsLogged: ReportTaskItem[];  // Bug/Issue được LOG (tạo) TRONG KỲ — cho thống kê tester/dev theo kỳ
}

// ===== Burndown (biểu đồ cháy việc) =====
/** Một mốc trên đường burndown (giờ): ideal = kế hoạch, actual = thực tế còn lại. */
export interface BurndownPoint {
  date: string;                  // dd/MM/yyyy
  ideal: number;                 // giờ còn lại theo kế hoạch (giảm tuyến tính → 0)
  actual: number;                // giờ còn lại thực tế (totalEstimate − est lá DONE hoàn thành ≤ ngày)
}

export interface Burndown {
  startDate: string;             // dd/MM/yyyy (mốc đầu trục thời gian)
  dueDate: string;               // dd/MM/yyyy (mốc cuối trục thời gian)
  totalEstimate: number;         // Σ estimateHours task LÁ
  totalSpent: number;            // Σ spentHours task LÁ
  teamManday: number;            // Σ manday các thành viên dự án
  unit: 'day' | 'week';          // lấy mẫu theo ngày; > 60 ngày → theo tuần
  points: BurndownPoint[];
}

// ===== Nhật ký dự án (ghi tay buổi làm việc với khách hàng) =====
/** Một bản ghi nhật ký (khớp DTO ProjectDto.DiaryEntry). */
export interface DiaryEntry {
  id: string;
  workDate: string | null;        // dd/MM/yyyy
  category: string | null;        // Demo/Khảo sát/UAT/…
  teamUserIds: string[];          // userId nhân sự team tham gia
  teamNames: string[];            // tên đã resolve từ teamUserIds
  team: DiaryPerson[];            // tên + vai trò lấy từ hệ thống (in vào biên bản họp)
  clientContacts: string | null;  // người phía khách hàng (text tự do)
  content: string | null;
  conclusion: string | null;
  location: string | null;        // địa điểm / hình thức (Online…)
  startTime: string | null;       // HH:mm
  endTime: string | null;         // HH:mm
  nextActions: DiaryAction[];     // việc cần làm tiếp
  createdBy: string | null;
  createdByName: string | null;
  createdAt: string | null;       // ISO Instant
  canEdit: boolean;               // creator/admin/PM/owner
}

/** Người tham dự trong biên bản họp: họ tên + vai trò. */
export interface DiaryPerson {
  name: string | null;
  role: string | null;
}

/** Trạng thái một việc cần làm tiếp. */
export type DiaryActionStatus = 'NEW' | 'DOING' | 'DONE';

/** Một việc cần làm tiếp (next action) — in thành bảng trong biên bản họp. */
export interface DiaryAction {
  content: string | null;
  owner: string | null;           // text tự do (có thể là người phía khách hàng)
  dueDate: string | null;         // dd/MM/yyyy
  status: DiaryActionStatus | null;
}

/** Tạo/sửa nhật ký. workDate chấp nhận dd/MM/yyyy hoặc yyyy-MM-dd. */
export interface DiaryRequest {
  workDate?: string | null;
  category?: string | null;
  teamUserIds?: string[];
  clientContacts?: string | null;
  content?: string | null;
  conclusion?: string | null;
  location?: string | null;
  startTime?: string | null;
  endTime?: string | null;
  nextActions?: DiaryAction[];
}

@Injectable({ providedIn: 'root' })
export class ProjectService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/projects';
  private readonly opts = { withCredentials: true };

  // ===== Project =====
  list(): Observable<Project[]> {
    return this.http.get<Project[]>(this.base, this.opts);
  }
  /** Việc của tôi xuyên dự án (màn cá nhân). */
  myTasks(): Observable<MyTask[]> {
    return this.http.get<MyTask[]>(`${this.base}/my-tasks`, this.opts);
  }
  get(id: string): Observable<Project> {
    return this.http.get<Project>(`${this.base}/${id}`, this.opts);
  }
  create(body: ProjectRequest): Observable<Project> {
    return this.http.post<Project>(this.base, body, this.opts);
  }
  update(id: string, body: ProjectRequest): Observable<Project> {
    return this.http.put<Project>(`${this.base}/${id}`, body, this.opts);
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`, this.opts);
  }

  // ===== Members =====
  listMembers(projectId: string): Observable<ProjectMember[]> {
    return this.http.get<ProjectMember[]>(`${this.base}/${projectId}/members`, this.opts);
  }
  addMember(projectId: string, body: AddMemberRequest): Observable<ProjectMember> {
    return this.http.post<ProjectMember>(`${this.base}/${projectId}/members`, body, this.opts);
  }
  updateMember(projectId: string, memberId: string, body: UpdateMemberRequest): Observable<ProjectMember> {
    return this.http.put<ProjectMember>(`${this.base}/${projectId}/members/${memberId}`, body, this.opts);
  }
  removeMember(projectId: string, memberId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${projectId}/members/${memberId}`, this.opts);
  }

  // ===== Tasks (phẳng — FE tự dựng cây từ parentId) =====
  listTasks(projectId: string): Observable<ProjectTask[]> {
    return this.http.get<ProjectTask[]>(`${this.base}/${projectId}/tasks`, this.opts);
  }

  /** Xuất Excel backlog theo BỘ LỌC (taskIds = danh sách task đang khớp lọc; rỗng = toàn bộ). */
  exportBacklog(projectId: string, taskIds?: string[]): Observable<Blob> {
    return this.http.post(`${this.base}/${projectId}/backlog/export`, { taskIds: taskIds ?? null },
      { responseType: 'blob', withCredentials: true });
  }
  exportTimeline(projectId: string, taskIds?: string[], fromDate?: string, toDate?: string): Observable<Blob> {
    return this.http.post(`${this.base}/${projectId}/timeline/export`,
      { taskIds: taskIds ?? null, fromDate: fromDate || null, toDate: toDate || null },
      { responseType: 'blob', withCredentials: true });
  }
  /** Xuất Excel TỔNG QUAN dự án (báo cáo khách — không có người thực hiện & trễ hạn). */
  exportOverview(projectId: string): Observable<Blob> {
    return this.http.get(`${this.base}/${projectId}/overview/export`,
      { responseType: 'blob', withCredentials: true });
  }
  /** Kích hoạt tải Blob về máy với tên file cho trước. */
  static downloadBlob(blob: Blob, filename: string): void {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  createTask(projectId: string, body: TaskRequest): Observable<ProjectTask> {
    return this.http.post<ProjectTask>(`${this.base}/${projectId}/tasks`, body, this.opts);
  }
  updateTask(projectId: string, taskId: string, body: TaskRequest): Observable<ProjectTask> {
    return this.http.put<ProjectTask>(`${this.base}/${projectId}/tasks/${taskId}`, body, this.opts);
  }
  updateTaskStatus(projectId: string, taskId: string, status: TaskStatus): Observable<ProjectTask> {
    return this.http.patch<ProjectTask>(`${this.base}/${projectId}/tasks/${taskId}/status`, { status }, this.opts);
  }
  assignTask(projectId: string, taskId: string, assigneeUserId: string | null): Observable<ProjectTask> {
    return this.http.patch<ProjectTask>(`${this.base}/${projectId}/tasks/${taskId}/assignee`, { assigneeUserId }, this.opts);
  }
  reorderTasks(projectId: string, items: ReorderItem[]): Observable<void> {
    return this.http.post<void>(`${this.base}/${projectId}/tasks/reorder`, { items }, this.opts);
  }
  deleteTask(projectId: string, taskId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${projectId}/tasks/${taskId}`, this.opts);
  }
  /** Lịch sử / hoạt động của một công việc (mới → cũ). */
  listActivity(projectId: string, taskId: string): Observable<TaskActivity[]> {
    return this.http.get<TaskActivity[]>(`${this.base}/${projectId}/tasks/${taskId}/activity`, this.opts);
  }
  /** Log work: cộng thêm giờ thực tế (hours > 0) vào spentHours của công việc; trả task đã cập nhật. */
  logWork(projectId: string, taskId: string, hours: number): Observable<ProjectTask> {
    return this.http.post<ProjectTask>(`${this.base}/${projectId}/tasks/${taskId}/log-work`, { hours }, this.opts);
  }
  /** Nhật ký hoạt động toàn dự án (mới → cũ). */
  projectActivity(projectId: string): Observable<ProjectActivityItem[]> {
    return this.http.get<ProjectActivityItem[]>(`${this.base}/${projectId}/activity`, this.opts);
  }

  // ===== Bình luận task (kiểu Jira) =====
  listComments(projectId: string, taskId: string): Observable<TaskComment[]> {
    return this.http.get<TaskComment[]>(`${this.base}/${projectId}/tasks/${taskId}/comments`, this.opts);
  }
  addComment(projectId: string, taskId: string, body: string): Observable<TaskComment> {
    return this.http.post<TaskComment>(`${this.base}/${projectId}/tasks/${taskId}/comments`, { body }, this.opts);
  }
  editComment(projectId: string, taskId: string, commentId: string, body: string): Observable<TaskComment> {
    return this.http.put<TaskComment>(`${this.base}/${projectId}/tasks/${taskId}/comments/${commentId}`, { body }, this.opts);
  }
  deleteComment(projectId: string, taskId: string, commentId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${projectId}/tasks/${taskId}/comments/${commentId}`, this.opts);
  }

  // ===== Ảnh đính kèm task (kiểu Jira) =====
  listAttachments(projectId: string, taskId: string): Observable<TaskAttachment[]> {
    return this.http.get<TaskAttachment[]>(`${this.base}/${projectId}/tasks/${taskId}/attachments`, this.opts);
  }
  /** Upload 1 ảnh (multipart field "file"; chỉ ảnh, ≤10MB — kiểm ở backend). */
  /** commentId != null → ảnh gắn vào bình luận đó (hiện dưới bình luận, không vào khối ảnh chung). */
  uploadAttachment(projectId: string, taskId: string, file: File, commentId?: string): Observable<TaskAttachment> {
    const form = new FormData();
    form.append('file', file);
    if (commentId) form.append('commentId', commentId);
    return this.http.post<TaskAttachment>(`${this.base}/${projectId}/tasks/${taskId}/attachments`, form, this.opts);
  }
  deleteAttachment(projectId: string, taskId: string, attId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${projectId}/tasks/${taskId}/attachments/${attId}`, this.opts);
  }
  /** URL nội dung ảnh (dùng trong <img [src]> — cần phiên). Cũng có sẵn trong TaskAttachment.url. */
  attachmentUrl(projectId: string, taskId: string, attId: string): string {
    return `${this.base}/${projectId}/tasks/${taskId}/attachments/${attId}/content`;
  }

  // ===== Report =====
  report(projectId: string): Observable<ProjectReport> {
    return this.http.get<ProjectReport>(`${this.base}/${projectId}/report`, this.opts);
  }
  reportDaily(projectId: string, date?: string): Observable<PeriodReport> {
    const q = date ? `?date=${date}` : '';
    return this.http.get<PeriodReport>(`${this.base}/${projectId}/report/daily${q}`, this.opts);
  }
  reportWeekly(projectId: string, date?: string): Observable<PeriodReport> {
    const q = date ? `?date=${date}` : '';
    return this.http.get<PeriodReport>(`${this.base}/${projectId}/report/weekly${q}`, this.opts);
  }
  /** URL tải file báo cáo (xlsx/docx) cho kỳ + ngày chọn — dùng anchor để trình duyệt tự tải (cookie phiên). */
  reportExportUrl(projectId: string, period: 'daily' | 'weekly', format: 'xlsx' | 'docx', date?: string): string {
    const q = `?format=${format}` + (date ? `&date=${date}` : '');
    return `${this.base}/${projectId}/report/${period}/export${q}`;
  }
  /** Biểu đồ burndown: giờ ước lượng còn lại theo thời gian (ideal vs actual). */
  burndown(projectId: string): Observable<Burndown> {
    return this.http.get<Burndown>(`${this.base}/${projectId}/burndown`, this.opts);
  }

  // ===== Nhật ký dự án (ghi tay buổi làm việc với khách hàng) =====
  listDiary(projectId: string): Observable<DiaryEntry[]> {
    return this.http.get<DiaryEntry[]>(`${this.base}/${projectId}/diary`, this.opts);
  }
  createDiary(projectId: string, body: DiaryRequest): Observable<DiaryEntry> {
    return this.http.post<DiaryEntry>(`${this.base}/${projectId}/diary`, body, this.opts);
  }
  updateDiary(projectId: string, id: string, body: DiaryRequest): Observable<DiaryEntry> {
    return this.http.put<DiaryEntry>(`${this.base}/${projectId}/diary/${id}`, body, this.opts);
  }
  deleteDiary(projectId: string, id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${projectId}/diary/${id}`, this.opts);
  }
  /** URL tải BIÊN BẢN HỌP (.docx) của một bản ghi nhật ký — mở bằng anchor để dùng cookie phiên. */
  diaryMinutesUrl(projectId: string, id: string): string {
    return `${this.base}/${projectId}/diary/${id}/minutes`;
  }

  // ===== People (chọn thành viên / assignee) =====
  people(): Observable<Person[]> {
    return this.http.get<Person[]>(`${this.base}/people`, this.opts);
  }
}
