import { Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { EmployeeChip } from '../shared/employee-chip/employee-chip';
import { ToastService } from '../shared/toast/toast.service';
import { DashboardService, PmHrDashboard } from '../core/dashboard.service';
import { formatThousands } from '../shared/format';

/** Báo cáo CHI TIẾT dự án + phân bổ nỗ lực nhân sự (mini-Jira + HR). */
@Component({
  selector: 'app-reports',
  imports: [PageHeader, EmployeeChip],
  templateUrl: './reports.html'
})
export class Reports implements OnInit {
  private dash = inject(DashboardService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly data = signal<PmHrDashboard | null>(null);
  readonly loading = signal(true);

  ngOnInit(): void { this.load(); }

  load(): void {
    this.loading.set(true);
    this.dash.pmHr().subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được báo cáo'); this.loading.set(false); }
    });
  }

  printReport(): void { window.print(); }

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
