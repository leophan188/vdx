import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ProjectService, ProjectMember, ProjectTask } from '../../core/project.service';

/** Một cột NGÀY CÔNG (T2–T6) trong khoảng đang xem. */
interface DayCol {
  key: string;    // yyyy-MM-dd (khoá so khớp)
  label: string;  // "T2 03/07"
}

/** Một hàng người: giờ theo từng ngày công + tổng + giờ chưa xếp lịch. */
interface Row {
  member: ProjectMember;
  days: number[];       // khớp thứ tự cột ngày
  total: number;        // tổng theo người trong khoảng (Σ days)
  unscheduled: number;  // est của task không có ngày nào
}

const WDAY_VN = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

/**
 * Tab TIMESHEET (selector app-prj-timesheet) — THUẦN FRONTEND, suy giờ từ estimateHours của task.
 * Lưới người × ngày công (T2–T6). Ô = phần est phân bổ vào ngày đó:
 * task có cả start & due → chia ĐỀU est cho các ngày công trong [start,due]; chỉ 1 ngày → dồn vào ngày đó;
 * không ngày → cột "Chưa xếp lịch". Chỉ tính task LÁ, assignee là thành viên, est > 0.
 */
@Component({
  selector: 'app-prj-timesheet',
  imports: [EmployeeChip],
  templateUrl: './timesheet.html',
  styles: [`
    .ts { display: grid; gap: var(--space-4); font-size: var(--text-sm); color: var(--color-text); }

    .ts__head { display: flex; align-items: flex-end; gap: var(--space-4); flex-wrap: wrap; }
    .ts__field { display: grid; gap: 4px; }
    .ts__field label { font-size: var(--text-xs); color: var(--color-text-muted); font-weight: var(--weight-medium); }
    .ts__field input { height: var(--control-h-sm); padding: 0 var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text); font: inherit; }
    .ts__hint { color: var(--color-text-muted); font-size: var(--text-xs); }

    .ts__wrap { overflow-x: auto; border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); }
    table.ts__grid { border-collapse: collapse; width: 100%; min-width: 640px; }
    .ts__grid th, .ts__grid td { padding: var(--space-2) var(--space-3); text-align: center; white-space: nowrap;
      border-bottom: 1px solid var(--color-border); }
    .ts__grid thead th { background: var(--color-surface-alt); font-weight: var(--weight-semibold);
      color: var(--color-text-muted); font-size: var(--text-xs); position: sticky; top: 0; }
    .ts__grid th.ts__who, .ts__grid td.ts__who { text-align: left; min-width: 220px; }
    .ts__grid td.ts__num { font-variant-numeric: tabular-nums; }
    .ts__zero { color: var(--color-text-muted); }
    .ts__total-col { font-weight: var(--weight-semibold); background: var(--color-surface-alt); }
    .ts__over { background: var(--overdue-bg); color: var(--overdue); font-weight: var(--weight-semibold); }
    .ts__grid tfoot td { font-weight: var(--weight-semibold); background: var(--color-surface-alt);
      border-top: 2px solid var(--color-border); border-bottom: 0; }
    .ts__grid tfoot td.ts__who { color: var(--color-text); }

    .ts__legend { display: flex; align-items: center; gap: var(--space-2); color: var(--color-text-muted);
      font-size: var(--text-xs); }
    .ts__swatch { width: 14px; height: 14px; border-radius: var(--radius-sm); background: var(--overdue-bg);
      border: 1px solid var(--overdue); display: inline-block; }

    .ts__empty, .ts__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
  `]
})
export class PrjTimesheet {
  readonly projectId = input.required<string>();

  private svc = inject(ProjectService);

  readonly loading = signal(true);
  readonly members = signal<ProjectMember[]>([]);
  readonly tasks = signal<ProjectTask[]>([]);

  // Tháng đang chọn (yyyy-MM cho input[type=month]) + khoảng ngày dẫn xuất (yyyy-MM-dd).
  readonly month = signal<string>('');
  readonly from = signal<string>('');
  readonly to = signal<string>('');

  constructor() {
    // Mặc định = THÁNG HIỆN TẠI (đầu → cuối tháng).
    const now = new Date();
    this.applyMonth(`${now.getFullYear()}-${this.pad(now.getMonth() + 1)}`);

    // Chỉ TẢI dữ liệu khi projectId đổi (không đọc from/to ở đây → tránh vòng lặp).
    effect(() => {
      const pid = this.projectId();
      if (!pid) return;
      this.loading.set(true);
      this.svc.listMembers(pid).subscribe({
        next: (m) => this.members.set(m ?? []),
        error: () => this.members.set([])
      });
      this.svc.listTasks(pid).subscribe({
        next: (t) => { this.tasks.set(t ?? []); this.loading.set(false); },
        error: () => { this.tasks.set([]); this.loading.set(false); }
      });
    });
  }

  // ----- Cột ngày công trong khoảng -----
  readonly cols = computed<DayCol[]>(() => {
    const lo = this.parseIso(this.from());
    const hi = this.parseIso(this.to());
    if (!lo || !hi || lo.getTime() > hi.getTime()) return [];
    const out: DayCol[] = [];
    const cur = new Date(lo);
    let guard = 0;
    while (cur.getTime() <= hi.getTime() && guard++ < 400) {
      const d = cur.getDay();
      if (d >= 1 && d <= 5) {
        out.push({ key: this.toIso(cur), label: `${WDAY_VN[d]} ${this.dm(cur)}` });
      }
      cur.setDate(cur.getDate() + 1);
    }
    return out;
  });

  /** Có task nào không xếp lịch (để hiện cột phụ "Chưa xếp lịch"). */
  readonly hasUnscheduled = computed(() => this.rows().some((r) => r.unscheduled > 0));

  // ----- Lưới người × ngày -----
  readonly rows = computed<Row[]>(() => {
    const cols = this.cols();
    const members = this.members();
    const tasks = this.tasks();
    const colIndex = new Map<string, number>();
    cols.forEach((c, i) => colIndex.set(c.key, i));

    // Chỉ giữ thành viên hợp lệ; map userId → hàng.
    const rowByUser = new Map<string, Row>();
    const rows: Row[] = members.map((m) => {
      const r: Row = { member: m, days: new Array(cols.length).fill(0), total: 0, unscheduled: 0 };
      rowByUser.set(m.userId, r);
      return r;
    });

    // Task LÁ = id không xuất hiện trong parentId của task khác.
    const parentIds = new Set<string>();
    for (const t of tasks) if (t.parentId) parentIds.add(t.parentId);

    for (const t of tasks) {
      if (parentIds.has(t.id)) continue;              // task cha (rollup) — bỏ qua
      if (!t.assigneeUserId) continue;
      const row = rowByUser.get(t.assigneeUserId);
      if (!row) continue;                             // assignee không phải thành viên
      const est = t.estimateHours ?? 0;
      if (est <= 0) continue;

      const start = this.parseDmy(t.startDate);
      const due = this.parseDmy(t.dueDate);

      if (start && due) {
        // Chia đều est cho các NGÀY CÔNG trong [start,due] (theo min/max để an toàn thứ tự).
        const lo = start.getTime() <= due.getTime() ? start : due;
        const hi = start.getTime() <= due.getTime() ? due : start;
        const workdays: string[] = [];
        const cur = new Date(lo);
        let guard = 0;
        while (cur.getTime() <= hi.getTime() && guard++ < 800) {
          const d = cur.getDay();
          if (d >= 1 && d <= 5) workdays.push(this.toIso(cur));
          cur.setDate(cur.getDate() + 1);
        }
        if (workdays.length === 0) {
          row.unscheduled += est;                     // khoảng chỉ có T7/CN → coi như chưa xếp lịch
        } else {
          const per = est / workdays.length;
          for (const key of workdays) {
            const ci = colIndex.get(key);
            if (ci !== undefined) row.days[ci] += per; // ngày ngoài khoảng xem vẫn tiêu est nhưng không hiện
          }
        }
      } else if (start || due) {
        // Chỉ 1 ngày → dồn est vào ngày đó (nếu là ngày công & nằm trong khoảng xem).
        const only = (start ?? due)!;
        const ci = colIndex.get(this.toIso(only));
        if (ci !== undefined) row.days[ci] += est;
        else row.unscheduled += est;                  // ngày rơi ngoài khoảng/T7-CN → cột phụ
      } else {
        row.unscheduled += est;                       // không có ngày nào
      }
    }

    for (const r of rows) r.total = r.days.reduce((a, b) => a + b, 0);
    return rows;
  });

  /** Tổng theo từng ngày (mọi người) — hàng cuối lưới. */
  readonly dayTotals = computed<number[]>(() => {
    const cols = this.cols();
    const totals = new Array(cols.length).fill(0);
    for (const r of this.rows()) r.days.forEach((v, i) => totals[i] += v);
    return totals;
  });
  readonly grandTotal = computed(() => this.rows().reduce((a, r) => a + r.total, 0));
  readonly unscheduledTotal = computed(() => this.rows().reduce((a, r) => a + r.unscheduled, 0));

  // ----- Hiển thị -----
  fmt(v: number): string {
    if (!v) return '';
    return String(Math.round(v * 10) / 10);
  }
  isOver(v: number): boolean { return v > 8; }

  // ----- Helpers ngày -----
  /** Chọn THÁNG (yyyy-MM từ input[type=month]) → đặt khoảng = đầu → cuối tháng đó. */
  setMonth(ym: string): void { if (ym) this.applyMonth(ym); }
  private applyMonth(ym: string): void {
    const [y, m] = ym.split('-').map(Number);
    if (!y || !m) return;
    this.month.set(ym);
    this.from.set(this.toIso(new Date(y, m - 1, 1)));   // ngày 1 của tháng
    this.to.set(this.toIso(new Date(y, m, 0)));         // ngày 0 của tháng kế = ngày cuối tháng này
  }
  private toIso(d: Date): string {
    return `${d.getFullYear()}-${this.pad(d.getMonth() + 1)}-${this.pad(d.getDate())}`;
  }
  private dm(d: Date): string { return `${this.pad(d.getDate())}/${this.pad(d.getMonth() + 1)}`; }
  private parseIso(s: string): Date | null {
    if (!s) return null;
    const [y, m, d] = s.split('-').map(Number);
    if (!y || !m || !d) return null;
    return new Date(y, m - 1, d);
  }
  /** "dd/MM/yyyy" → Date (nửa đêm địa phương); null nếu rỗng/sai. */
  private parseDmy(s: string | null): Date | null {
    if (!s) return null;
    const [d, m, y] = s.split('/').map(Number);
    if (!d || !m || !y) return null;
    return new Date(y, m - 1, d);
  }
  private pad(n: number): string { return n < 10 ? '0' + n : String(n); }
}
