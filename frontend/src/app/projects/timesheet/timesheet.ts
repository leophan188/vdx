import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { Modal } from '../../shared/modal/modal';
import { ProjectService, ProjectMember, WorkLog, WorkRole } from '../../core/project.service';

/** Một cột NGÀY CÔNG (T2–T6) trong khoảng đang xem. */
interface DayCol {
  key: string;    // yyyy-MM-dd (khoá so khớp)
  wd: string;     // thứ ("T2") — dòng trên, nhỏ
  dnum: string;   // số ngày ("03") — dòng dưới
}

/** Một hàng người: giờ theo từng ngày công + tổng + giờ chưa xếp lịch. */
interface Row {
  member: ProjectMember;
  days: number[];       // khớp thứ tự cột ngày
  total: number;        // tổng theo người trong khoảng (Σ days)
  unscheduled: number;  // est của task không có ngày nào
}

const WDAY_VN = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

/** Dòng cho người ĐÃ RỜI dự án nhưng vẫn còn giờ đã ghi — giữ lại để tổng lưới không hụt. */
function ghostMember(userId: string, name: string | null): ProjectMember {
  return {
    id: 'ghost-' + userId, projectId: '', userId, name: (name ?? '') + ' (ngoài dự án)',
    empCode: null, jobPosition: null, title: null, deptCode: null,
    roleInProject: 'MEMBER' as ProjectMember['roleInProject'],
    startDate: null, endDate: null, effortPct: 0, workdays: 0, manday: 0, joinedAt: null
  };
}

/**
 * Tab TIMESHEET (selector app-prj-timesheet) — GIỜ LÀM VIỆC THỰC TẾ đã ghi.
 * Lưới người × ngày công (T2–T6); ô = tổng giờ người đó ghi vào ngày đó, bấm để xem ghi vào task nào.
 * Nguồn: bảng project_task_work_log (ghi lúc bàn giao Kiểm thử/Hoàn thành hoặc ghi tay hằng ngày).
 * Lọc được theo vai (lập trình / kiểm thử). Giờ ghi vào T7/CN hoặc ngoài khoảng xem dồn vào cột
 * "Ngoài khoảng" để tổng lưới luôn khớp tổng giờ đã ghi, không giấu mất giờ của ai.
 *
 * TRƯỚC ĐÂY tab này suy giờ từ estimateHours chia đều theo ngày — đó là giờ KẾ HOẠCH, không dùng
 * chấm công được; đã thay hẳn theo yêu cầu.
 */
@Component({
  selector: 'app-prj-timesheet',
  imports: [EmployeeChip, Modal],
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
    table.ts__grid { border-collapse: collapse; width: 100%; }
    .ts__grid th, .ts__grid td { padding: var(--space-2) var(--space-3); text-align: center; white-space: nowrap;
      border-bottom: 1px solid var(--color-border); }
    .ts__grid thead th { background: var(--color-surface-alt); font-weight: var(--weight-semibold);
      color: var(--color-text-muted); font-size: var(--text-xs); position: sticky; top: 0; }
    .ts__grid th.ts__who, .ts__grid td.ts__who { text-align: left; min-width: 200px; }
    /* Cột NGÀY thu gọn: hẹp, padding nhỏ, nhãn 2 dòng (thứ nhỏ + số ngày) */
    .ts__grid th.ts__day, .ts__grid td.ts__num { min-width: 32px; width: 32px; padding: 4px 3px; }
    .ts__grid td.ts__num { font-variant-numeric: tabular-nums; font-size: var(--text-sm); }
    .ts__day { line-height: 1.1; }
    .ts__wd { display: block; font-size: 10px; font-weight: 500; color: var(--color-text-muted); }
    .ts__dnum { display: block; font-size: var(--text-sm); }
    .ts__zero { color: var(--color-text-muted); }
    .ts__total-col { font-weight: var(--weight-semibold); background: var(--color-surface-alt); }
    .ts__over { background: var(--status-done-bg); color: var(--status-done); font-weight: var(--weight-semibold); }
    .ts__under { background: var(--overdue-bg); color: var(--overdue); font-weight: var(--weight-semibold); }
    .ts__grid tfoot td { font-weight: var(--weight-semibold); background: var(--color-surface-alt);
      border-top: 2px solid var(--color-border); border-bottom: 0; }
    .ts__grid tfoot td.ts__who { color: var(--color-text); }

    .ts__legend { display: flex; align-items: center; gap: var(--space-2); color: var(--color-text-muted);
      font-size: var(--text-xs); }
    .ts__swatch { width: 14px; height: 14px; border-radius: var(--radius-sm); display: inline-block; vertical-align: middle; }
    .ts__swatch--under { background: var(--overdue-bg); border: 1px solid var(--overdue); }
    .ts__swatch--over { background: var(--status-done-bg); border: 1px solid var(--status-done); }

    .ts__empty, .ts__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }

    /* Lọc theo vai */
    .ts__roles { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
    .ts__roles button { border: 0; background: var(--color-surface); color: var(--color-text-muted);
      padding: 0 var(--space-3); height: var(--control-h-sm); font: inherit; font-size: var(--text-xs); cursor: pointer; }
    .ts__roles button + button { border-left: 1px solid var(--color-border); }
    .ts__roles button.is-active { background: var(--color-primary); color: var(--color-text-invert); font-weight: var(--weight-medium); }

    /* Ô có giờ → bấm xem chi tiết */
    .ts__grid td.ts__click { cursor: pointer; }
    .ts__grid td.ts__click:hover { outline: 2px solid var(--color-primary); outline-offset: -2px; }

    /* Popup chi tiết ô */
    .ts__detail { display: grid; gap: 4px; min-width: 380px; }
    .ts__drow { display: grid; grid-template-columns: 74px 70px 1fr 52px; align-items: center; gap: 8px;
      padding: 5px 8px; border-radius: 6px; background: var(--color-surface-alt); font-size: var(--text-sm); }
    .ts__drole { font-size: 10px; font-weight: 700; padding: 1px 6px; border-radius: 999px; text-align: center;
      color: var(--status-active); background: color-mix(in srgb, var(--status-active) 14%, transparent); }
    .ts__drole.is-test { color: var(--status-done); background: color-mix(in srgb, var(--status-done) 14%, transparent); }
    .ts__dcode { font-size: var(--text-xs); color: var(--color-text-muted); font-weight: 600; }
    .ts__dtitle { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ts__dh { text-align: right; font-variant-numeric: tabular-nums; }
    .ts__dnote { padding: 0 8px 4px 90px; font-size: var(--text-xs); color: var(--color-text-muted); }
  `]
})
export class PrjTimesheet {
  readonly projectId = input.required<string>();

  private svc = inject(ProjectService);

  readonly loading = signal(true);
  readonly members = signal<ProjectMember[]>([]);
  /** Giờ THỰC TẾ đã ghi trong khoảng đang xem (thay cho suy từ estimate). */
  readonly logs = signal<WorkLog[]>([]);

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
      const from = this.from();
      const to = this.to();
      this.loading.set(true);
      this.svc.listMembers(pid).subscribe({
        next: (m) => this.members.set(m ?? []),
        error: () => this.members.set([])
      });
      // GIỜ THỰC TẾ đã ghi (không còn suy từ estimate).
      this.svc.listProjectWorkLogs(pid, from, to).subscribe({
        next: (w) => { this.logs.set(w ?? []); this.loading.set(false); },
        error: () => { this.logs.set([]); this.loading.set(false); }
      });
    });
  }

  /** Lọc theo vai: tất cả / chỉ giờ lập trình / chỉ giờ kiểm thử. */
  readonly roleFilter = signal<WorkRole | 'ALL'>('ALL');
  readonly scopedLogs = computed(() => {
    const r = this.roleFilter();
    return r === 'ALL' ? this.logs() : this.logs().filter((w) => w.role === r);
  });
  readonly devTotal = computed(() => this.sum(this.logs().filter((w) => w.role === 'DEV')));
  readonly testTotal = computed(() => this.sum(this.logs().filter((w) => w.role === 'TEST')));
  private sum(ws: WorkLog[]): number {
    return Math.round(ws.reduce((a, w) => a + (w.hours || 0), 0) * 10) / 10;
  }

  /** Chi tiết một ô (người × ngày) — bấm để xem đã ghi giờ vào task nào. */
  readonly cellDetail = signal<{ title: string; logs: WorkLog[] } | null>(null);
  openCell(r: Row, colKey: string, label: string): void {
    const uid = r.member.userId;
    const list = this.scopedLogs().filter((w) => w.userId === uid && w.workDate === colKey);
    if (list.length) this.cellDetail.set({ title: `${r.member.name || ''} · ${label}`, logs: list });
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
        out.push({ key: this.toIso(cur), wd: WDAY_VN[d], dnum: this.pad(cur.getDate()) });
      }
      cur.setDate(cur.getDate() + 1);
    }
    return out;
  });

  /** Có task nào không xếp lịch (để hiện cột phụ "Chưa xếp lịch"). */
  readonly hasUnscheduled = computed(() => this.rows().some((r) => r.unscheduled > 0));

  /**
   * Lưới người × ngày từ GIỜ THỰC TẾ đã ghi.
   * Người không phải thành viên dự án (đã rời nhóm) vẫn hiện thành dòng riêng — nếu bỏ đi thì
   * tổng giờ của lưới sẽ nhỏ hơn giờ thực đã ghi, chấm công bị thiếu mà không ai biết.
   */
  readonly rows = computed<Row[]>(() => {
    const cols = this.cols();
    const colIndex = new Map<string, number>();
    cols.forEach((c, i) => colIndex.set(c.key, i));

    const rowByUser = new Map<string, Row>();
    const rows: Row[] = this.members().map((m) => {
      const r: Row = { member: m, days: new Array(cols.length).fill(0), total: 0, unscheduled: 0 };
      rowByUser.set(m.userId, r);
      return r;
    });

    for (const w of this.scopedLogs()) {
      let row = rowByUser.get(w.userId);
      if (!row) {
        // Không còn trong danh sách thành viên → vẫn dựng dòng để không mất giờ.
        row = {
          member: ghostMember(w.userId, w.userName),
          days: new Array(cols.length).fill(0), total: 0, unscheduled: 0
        };
        rowByUser.set(w.userId, row);
        rows.push(row);
      }
      const ci = colIndex.get(w.workDate);
      if (ci !== undefined) row.days[ci] += w.hours || 0;
      else row.unscheduled += w.hours || 0;   // ghi vào T7/CN hoặc ngoài khoảng đang xem
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
  /** > 8h/ngày → XANH (dư giờ). */
  isOver(v: number): boolean { return v > 8; }
  /** > 0 và < 8h/ngày → ĐỎ (thiếu giờ). = 8h hoặc = 0 → bình thường. */
  isUnder(v: number): boolean { return v > 0 && v < 8; }

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
