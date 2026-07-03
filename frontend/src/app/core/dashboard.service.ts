import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface ProcessStat {
  processName: string;
  running: number;
  completed: number;
}

export interface DashboardSummary {
  totalInstances: number;
  running: number;
  completed: number;
  cancelled: number;
  openTasks: number;
  overdueTasks: number;
  byProcess: ProcessStat[];
}

export interface WorkloadItem {
  user: string;
  openTasks: number;
  overdueTasks: number;
}

// ===== Tổng hợp DỰ ÁN + NHÂN SỰ (mini-Jira + HR) =====

export interface ProjectStats {
  totalProjects: number;
  byStatus: Record<string, number>;
  totalTasks: number;
  doneTasks: number;
  avgCompletionPct: number;
  overdueTasks: number;
  openBugs: number;
  totalBudget: number;
  totalPlannedMM: number;
  totalActualMM: number;
}

export interface ProjectRow {
  id: string;
  code: string;
  name: string;
  status: string;
  completionPct: number;
  memberCount: number;
  plannedMm: number;
  actualMm: number;
  budget: number | null;
  overdue: number;
  bugOpen: number;
}

export interface Overloaded {
  empCode: string | null;
  name: string;
  totalEffort: number;
  projects: string[];
}

export interface PersonRef {
  empCode: string | null;
  name: string;
  deptCode: string | null;
  jobPosition: string | null;
  title: string | null;
}

export interface HrStats {
  totalEmployees: number;
  active: number;
  inactive: number;
  external: number;
  byDept: Record<string, number>;
  byLevel: Record<string, number>;
  overloaded: Overloaded[];
  unassigned: number;
  availableSample: PersonRef[];
}

export interface EffortByPerson {
  empCode: string | null;
  name: string;
  deptCode: string | null;
  projectsCount: number;
  totalEffort: number;
  totalManday: number;
  totalMM: number;
}

export interface PmHrDashboard {
  project: ProjectStats;
  projects: ProjectRow[];
  hr: HrStats;
  effortByPerson: EffortByPerson[];
}

/** Số liệu bảng điều khiển — vận hành (Epic 4, giữ) + tổng hợp Dự án/Nhân sự (mới). */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/dashboard';

  summary(): Observable<DashboardSummary> {
    return this.http.get<DashboardSummary>(`${this.base}/summary`, { withCredentials: true });
  }

  workload(): Observable<WorkloadItem[]> {
    return this.http.get<WorkloadItem[]>(`${this.base}/workload`, { withCredentials: true });
  }

  /** Tổng hợp DỰ ÁN + NHÂN SỰ cho Bảng điều khiển + Báo cáo. */
  pmHr(): Observable<PmHrDashboard> {
    return this.http.get<PmHrDashboard>(`${this.base}/pm-hr`, { withCredentials: true });
  }
}
