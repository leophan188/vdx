import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { EmployeeChip } from '../shared/employee-chip/employee-chip';
import { ToastService } from '../shared/toast/toast.service';
import { WorkReportService, WorkReport, ReportRow } from '../core/work-report.service';

type Period = 'DAILY' | 'WEEKLY';

/** Màu 4 nhóm (token trạng thái). */
const GROUP_COLORS = {
  inProgress: 'var(--status-active)',
  done: 'var(--status-done)',
  overdue: 'var(--overdue)',
  upcoming: 'var(--status-cancel)'
};

/**
 * Báo cáo CÔNG VIỆC — Ngày / Tuần (snapshot live). Đo theo est giờ của task lá.
 * 2 bảng (theo Dự án, theo Thành viên) + nút Xuất Excel.
 */
@Component({
  selector: 'app-reports',
  imports: [PageHeader, EmployeeChip],
  templateUrl: './reports.html'
})
export class Reports implements OnInit {
  private api = inject(WorkReportService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly data = signal<WorkReport | null>(null);
  readonly loading = signal(true);
  readonly period = signal<Period>('DAILY');
  /** Ngày chọn (yyyy-MM-dd) — với TUẦN là ngày bất kỳ trong tuần. */
  readonly date = signal<string>(this.today());

  readonly colors = GROUP_COLORS;

  readonly exportHref = computed(() => this.api.exportUrl(this.period(), this.date() || undefined));

  ngOnInit(): void { this.load(); }

  private today(): string {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }

  setPeriod(p: Period): void {
    if (this.period() === p) return;
    this.period.set(p);
    this.load();
  }

  onDate(value: string): void {
    this.date.set(value);
    this.load();
  }

  load(): void {
    this.loading.set(true);
    const d = this.date() || undefined;
    const obs = this.period() === 'WEEKLY' ? this.api.weekly(d) : this.api.daily(d);
    obs.subscribe({
      next: (r) => { this.data.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được báo cáo'); this.loading.set(false); }
    });
  }

  exportExcel(): void {
    window.open(this.exportHref(), '_blank');
  }

  openProject(id: string): void {
    if (id && id !== 'ALL' && id !== 'UNASSIGNED') this.router.navigate(['/projects', id]);
  }

  fmt(n: number | null | undefined): string {
    if (n == null || isNaN(n)) return '0';
    return Number.isInteger(n) ? String(n) : n.toFixed(1);
  }

  trackRow = (_: number, r: ReportRow) => r.id;
}
