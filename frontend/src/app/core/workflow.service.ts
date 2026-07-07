import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface InboxItem {
  taskId: string;
  stepName: string;
  processName: string;
  instanceId: string | null;
  createdAt: string | null;
  formId: string | null;
  slaHours: number | null;
  dueAt: string | null;
  overdue: boolean;
  claimable: boolean;
  /** Vị trí bước hiện tại trong tổng thể quy trình (vd Bước 6/10). */
  stepIndex: number;
  stepTotal: number;
}

export interface TaskDetail {
  taskId: string;
  stepName: string;
  processName: string;
  instanceId: string | null;
  formId: string | null;
  fieldPerms: Record<string, 'EDIT' | 'READONLY' | 'HIDDEN'> | null;
  actions: string[];
  formData: Record<string, unknown>;
  /** Dữ liệu các bước TRƯỚC đã hoàn thành (xem lại ngữ cảnh). */
  priorSteps: StepView[];
}

export interface StartableProcess {
  id: string;
  processKey: string;
  name: string;
}

export interface InstanceResponse {
  id: string;
  processId: string;
  processVersion: number;
  flowableInstanceId: string;
  status: string;
  startedAt: string;
  firstTaskId: string | null;
}

export interface InstanceListItem {
  id: string;
  processName: string;
  title: string;
  processVersion: number;
  status: string;
  startedBy: string;
  startedAt: string;
  currentStep: string;
  currentAssignee: string;
  currentOverdue: boolean;
  searchText: string;
}

export interface StepHistory {
  stepName: string;
  assignee: string;
  startedAt: string | null;
  endedAt: string | null;
  status: 'DONE' | 'ACTIVE';
}

export interface InstanceTimeline {
  id: string;
  processName: string;
  status: string;
  steps: StepHistory[];
}

export interface FieldValue {
  label: string;
  value: string;
}

export interface StepView {
  index: number;
  stepKey: string;
  stepName: string;
  assignee: string | null;
  startedAt: string | null;
  endedAt: string | null;
  status: 'DONE' | 'ACTIVE' | 'PENDING';
  data: FieldValue[];
}

/** Tổng quan quy trình theo toàn bộ các bước (trạng thái + người + dữ liệu từng bước). */
export interface InstanceOverview {
  id: string;
  processName: string;
  title: string;
  status: string;
  total: number;
  currentIndex: number;
  steps: StepView[];
}

/** Vận hành nhiệm vụ runtime (Story 3.1/3.2/3.4): khởi tạo, hộp thư, chi tiết, hoàn thành. */
@Injectable({ providedIn: 'root' })
export class WorkflowService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1';

  /** Quy trình đã ban hành mà người dùng có thể tạo yêu cầu. */
  startable(): Observable<StartableProcess[]> {
    return this.http.get<StartableProcess[]>(`${this.base}/instances/startable`, { withCredentials: true });
  }

  /** Khởi tạo phiên chạy từ quy trình đã publish. */
  start(processId: string, formData: Record<string, unknown>): Observable<InstanceResponse> {
    return this.http.post<InstanceResponse>(`${this.base}/instances`, { processId, formData }, { withCredentials: true });
  }

  /** Việc của tôi. */
  inbox(): Observable<InboxItem[]> {
    return this.http.get<InboxItem[]>(`${this.base}/inbox`, { withCredentials: true });
  }

  detail(taskId: string): Observable<TaskDetail> {
    return this.http.get<TaskDetail>(`${this.base}/tasks/${taskId}`, { withCredentials: true });
  }

  complete(taskId: string, action: string, formData: Record<string, unknown>): Observable<void> {
    return this.http.post<void>(`${this.base}/tasks/${taskId}/complete`, { action, formData }, { withCredentials: true });
  }

  /** Nhận việc theo vai trò (Story 3.x). */
  claim(taskId: string): Observable<void> {
    return this.http.post<void>(`${this.base}/tasks/${taskId}/claim`, {}, { withCredentials: true });
  }

  /** Xin gia hạn xử lý việc (Story 3.8). */
  extend(taskId: string, hours: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/tasks/${taskId}/extend`, { hours, reason }, { withCredentials: true });
  }

  /** Theo dõi: danh sách phiên chạy (Story 3.3). */
  instances(): Observable<InstanceListItem[]> {
    return this.http.get<InstanceListItem[]>(`${this.base}/instances/all`, { withCredentials: true });
  }

  /** Hồ sơ do tôi tạo (Story 4.4). */
  myInstances(): Observable<InstanceListItem[]> {
    return this.http.get<InstanceListItem[]>(`${this.base}/instances/mine`, { withCredentials: true });
  }

  timeline(id: string): Observable<InstanceTimeline> {
    return this.http.get<InstanceTimeline>(`${this.base}/instances/${id}/timeline`, { withCredentials: true });
  }

  /** Tổng quan quy trình theo toàn bộ các bước + dữ liệu từng bước. */
  overview(id: string): Observable<InstanceOverview> {
    return this.http.get<InstanceOverview>(`${this.base}/instances/${id}/overview`, { withCredentials: true });
  }

  /** Hủy phiên chạy (Story 3.6). */
  cancel(id: string, reason: string): Observable<void> {
    return this.http.post<void>(`${this.base}/instances/${id}/cancel`, { reason }, { withCredentials: true });
  }
}
