import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { StatCard } from '../shared/stat-card/stat-card';
import { EmployeeChip } from '../shared/employee-chip/employee-chip';
import { WorkReportService, WorkReport, ReportRow } from '../core/work-report.service';

/** Màu 4 nhóm (token trạng thái). */
const GROUP_COLORS = {
  inProgress: 'var(--status-active)', // đang làm — xanh dương
  done: 'var(--status-done)',         // đã xong — xanh lá
  overdue: 'var(--overdue)',          // trễ — đỏ
  upcoming: 'var(--status-cancel)'    // sắp làm — xám
};

interface DonutSeg {
  key: string;
  label: string;
  color: string;
  est: number;
  pct: number;
  dash: string;      // stroke-dasharray "len rest"
  offset: number;    // stroke-dashoffset
}

/**
 * Bảng điều khiển — BÁO CÁO CÔNG VIỆC (snapshot hôm nay). Đo bằng EST GIỜ của task lá.
 * KPI 4 nhóm + donut phân bố + bảng Theo dự án + bảng Theo thành viên.
 */
@Component({
  selector: 'app-dashboard',
  imports: [PageHeader, StatCard, EmployeeChip],
  templateUrl: './dashboard.html'
})
export class Dashboard implements OnInit {
  private api = inject(WorkReportService);
  private router = inject(Router);

  readonly data = signal<WorkReport | null>(null);
  readonly loading = signal(true);

  readonly colors = GROUP_COLORS;

  // Donut (SVG thuần) — r = 54, chu vi = 2πr.
  readonly R = 54;
  readonly circ = 2 * Math.PI * 54;

  readonly donut = computed<DonutSeg[]>(() => {
    const o = this.data()?.overview;
    if (!o || o.totalEst <= 0) return [];
    // 3 nhóm partition (đang làm / đã xong / sắp làm) — trễ là cắt ngang nên không vẽ trong donut.
    const segs = [
      { key: 'inProgress', label: 'Đang làm', color: GROUP_COLORS.inProgress, g: o.inProgress },
      { key: 'done', label: 'Đã xong', color: GROUP_COLORS.done, g: o.done },
      { key: 'upcoming', label: 'Sắp làm', color: GROUP_COLORS.upcoming, g: o.upcoming }
    ];
    let acc = 0;
    const out: DonutSeg[] = [];
    for (const s of segs) {
      if (s.g.estimateHours <= 0) continue;
      const len = (s.g.estimateHours / o.totalEst) * this.circ;
      out.push({
        key: s.key,
        label: s.label,
        color: s.color,
        est: s.g.estimateHours,
        pct: s.g.pct,
        dash: `${len} ${this.circ - len}`,
        offset: -acc
      });
      acc += len;
    }
    return out;
  });

  ngOnInit(): void {
    this.api.dashboard().subscribe({
      next: (d) => { this.data.set(d); this.loading.set(false); },
      error: () => { this.loading.set(false); }
    });
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
