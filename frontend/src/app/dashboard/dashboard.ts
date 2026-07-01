import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DashboardService, PmHrDashboard } from '../core/dashboard.service';
import { StatCard } from '../shared/stat-card/stat-card';
import { PageHeader } from '../shared/page-header/page-header';
import { EmployeeChip } from '../shared/employee-chip/employee-chip';
import { formatThousands } from '../shared/format';

/** Bảng điều khiển — trọng tâm DỰ ÁN (mini-Jira) + NHÂN SỰ (HR). */
@Component({
  selector: 'app-dashboard',
  imports: [StatCard, PageHeader, EmployeeChip],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  private dash = inject(DashboardService);
  private router = inject(Router);

  readonly data = signal<PmHrDashboard | null>(null);
  readonly loading = signal(true);

  // ----- Khối DỰ ÁN: phân bổ theo trạng thái (cho thanh) -----
  readonly statusOrder = ['PLANNING', 'ACTIVE', 'ON_HOLD', 'DONE', 'CANCELLED'];
  readonly statusBars = computed(() => {
    const by = this.data()?.project.byStatus ?? {};
    const total = Object.values(by).reduce((a, b) => a + b, 0) || 1;
    return this.statusOrder.map((s) => ({
      status: s,
      label: this.statusLabel(s),
      count: by[s] ?? 0,
      pct: Math.round(((by[s] ?? 0) / total) * 100),
      badge: this.statusBadge(s)
    }));
  });

  // ----- Khối NHÂN SỰ: phân bổ theo bộ phận (thanh ngang) -----
  readonly deptBars = computed(() => {
    const by = this.data()?.hr.byDept ?? {};
    const entries = Object.entries(by);
    const max = Math.max(1, ...entries.map(([, v]) => v));
    return entries.map(([dept, count]) => ({ dept, count, pct: Math.round((count / max) * 100) }));
  });

  ngOnInit(): void {
    this.dash.pmHr().subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.loading.set(false); }
    });
  }

  fmt(n: number | null | undefined): string { return formatThousands(n); }

  openProject(id: string): void { this.router.navigate(['/projects', id]); }

  statusLabel(s: string): string {
    switch (s) {
      case 'PLANNING': return 'Lên kế hoạch';
      case 'ACTIVE': return 'Đang chạy';
      case 'ON_HOLD': return 'Tạm dừng';
      case 'DONE': return 'Hoàn thành';
      case 'CANCELLED': return 'Đã hủy';
      default: return s;
    }
  }
  statusBadge(s: string): string {
    switch (s) {
      case 'ACTIVE': return 'badge--active';
      case 'DONE': return 'badge--done';
      case 'CANCELLED': return 'badge--cancel';
      case 'ON_HOLD': return 'badge--pending';
      default: return 'badge--neutral';
    }
  }
}
