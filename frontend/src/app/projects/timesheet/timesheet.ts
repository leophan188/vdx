import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { Modal } from '../../shared/modal/modal';
import { PrjTaskDetail } from '../task-detail/task-detail';
import { TYPE_META, workActionLabel, workActionColor } from '../work-stats';
import { ProjectService, ProjectMember, ProjectTask, TaskType, WorkLog, WorkRole } from '../../core/project.service';

/** Một cột NGÀY CÔNG (T2–T6) trong khoảng đang xem. */
interface DayCol {
  key: string;    // yyyy-MM-dd (khoá so khớp)
  wd: string;     // thứ ("T2") — dòng trên, nhỏ
  dnum: string;   // số ngày ("03") — dòng dưới
}

/** Một hàng người: giờ theo từng ngày công + tổng + giờ chưa xếp lịch. */
interface Row {
  member: ProjectMember;
  days: number[];        // giờ thực tế theo từng ngày công
  dayTasks: number[];    // SỐ CÔNG VIỆC riêng biệt có ghi giờ trong ngày đó
  total: number;         // tổng giờ theo người trong khoảng (Σ days)
  totalTasks: number;    // tổng công việc riêng biệt trong khoảng
  unscheduled: number;   // giờ ghi vào T7/CN hoặc ngoài khoảng đang xem
}

const WDAY_VN = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];

/** Dòng cho người ĐÃ RỜI dự án nhưng vẫn còn giờ đã ghi — giữ lại để tổng lưới không hụt. */
function ghostMember(userId: string, name: string | null): ProjectMember {
  return {
    id: 'ghost-' + userId, projectId: '', userId, name: (name ?? '') + ' (ngoài dự án)',
    empCode: null, jobPosition: null, title: null, deptCode: null,
    roleInProject: 'MEMBER' as ProjectMember['roleInProject'],
    startDate: null, endDate: null, effortPct: 0, workdays: 0, manday: 0, joinedAt: null,
    active: false            // đã rời dự án — chỉ còn xuất hiện vì có giờ đã ghi
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
  imports: [EmployeeChip, Modal, PrjTaskDetail],
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
    /* Ô rộng hơn vì hiển thị 2 số liệu: giờ (to) + số công việc (nhỏ, mờ). */
    .ts__grid th.ts__day, .ts__grid td.ts__num { min-width: 52px; width: 52px; padding: 4px 3px; }
    .ts__grid td.ts__num { font-variant-numeric: tabular-nums; font-size: var(--text-sm); line-height: 1.15; }
    .ts__h { display: block; font-weight: var(--weight-semibold); }
    .ts__t { display: block; font-size: 10px; color: var(--color-text-muted); white-space: nowrap; }
    .ts__over .ts__t, .ts__under .ts__t { color: inherit; opacity: .8; }
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

    /* ===== Popup chi tiết ô (người × ngày) ===== */
    .ts__detail { display: grid; gap: 3px; }
    .ts__dsum { display: flex; flex-wrap: wrap; gap: var(--space-4); padding: 8px 10px; margin-bottom: 6px;
      border-radius: 8px; background: var(--color-surface-alt); font-size: var(--text-sm);
      color: var(--color-text-muted); }
    .ts__dsum b { color: var(--color-text); font-variant-numeric: tabular-nums; }
    .ts__dsum-dev b { color: var(--status-active); }
    .ts__dsum-test b { color: var(--status-done); }
    .ts__dhead, .ts__drow { display: grid; grid-template-columns: 118px 72px 1fr 62px 66px;
      align-items: start; gap: 10px; padding: 7px 10px; }
    .ts__dhead { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--color-text-muted);
      text-transform: uppercase; letter-spacing: .02em; padding-bottom: 2px; }
    .ts__dhead > :nth-child(4), .ts__dhead > :nth-child(5) { text-align: right; }
    .ts__drow { border-radius: 8px; background: var(--color-surface-alt); font-size: var(--text-sm); }
    /* Nhãn HÀNH ĐỘNG — màu theo loại hành động, không phải theo vai. */
    .ts__drole { font-size: 10px; font-weight: 700; padding: 2px 6px; border-radius: 999px; text-align: center;
      white-space: nowrap; color: var(--act, var(--status-active));
      background: color-mix(in srgb, var(--act, var(--status-active)) 14%, transparent); }
    .ts__dcode { border: 0; background: none; padding: 0; cursor: pointer; text-align: left;
      font: inherit; font-size: var(--text-xs); font-weight: 700; color: var(--color-primary); }
    .ts__dcode:hover { text-decoration: underline; }
    .ts__dmain { display: grid; gap: 2px; min-width: 0; }
    .ts__dtitle { border: 0; background: none; padding: 0; cursor: pointer; text-align: left; font: inherit;
      color: var(--color-text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ts__dtitle:hover { color: var(--color-primary); text-decoration: underline; }
    .ts__dtype { font-style: normal; font-size: 10px; font-weight: 700; padding: 1px 6px; border-radius: 999px;
      margin-right: 6px; color: var(--color-text-muted); background: var(--color-surface); }
    .ts__dpath, .ts__dnote { font-size: var(--text-xs); color: var(--color-text-muted);
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ts__dest { text-align: right; font-variant-numeric: tabular-nums; color: var(--color-text-muted);
      font-size: var(--text-sm); }
    .ts__dh { text-align: right; font-variant-numeric: tabular-nums; font-size: 1rem; }
  `]
})
export class PrjTimesheet {
  readonly projectId = input.required<string>();
  /** Tăng khi task được sửa ở popup chi tiết → tải lại DỮ LIỆU mà không dựng lại component,
   *  nhờ vậy bộ lọc / nhóm đang gập / trang / vị trí cuộn giữ nguyên như trước khi mở popup. */
  readonly refreshKey = input(0);

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
      this.refreshKey();
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
  /** Tổng giờ + tách theo vai của ô đang mở — hiện ngay đầu popup. */
  readonly cellTotal = computed(() => this.sum(this.cellDetail()?.logs ?? []));
  readonly cellDevTotal = computed(() =>
    this.sum((this.cellDetail()?.logs ?? []).filter((w) => w.role === 'DEV')));
  readonly cellTestTotal = computed(() =>
    this.sum((this.cellDetail()?.logs ?? []).filter((w) => w.role === 'TEST')));

  typeLabel(t: TaskType): string { return TYPE_META[t]?.short ?? t; }
  /** Nhãn HÀNH ĐỘNG (Log lỗi / Bàn giao KT / Duyệt xong / Trả về sửa / Ghi tay). */
  actLabel(w: WorkLog): string { return workActionLabel(w.action, w.role); }
  actColor(w: WorkLog): string { return workActionColor(w.action, w.role); }

  // ===== Chi tiết công việc (mở chồng lên popup giờ) =====
  readonly taskDetail = signal<ProjectTask | null>(null);
  readonly taskDetailOpen = signal(false);
  /** Bấm mã/tên việc trong popup giờ → mở chi tiết công việc đầy đủ như các màn khác. */
  openTask(w: WorkLog): void {
    this.svc.listTasks(this.projectId()).subscribe({
      next: (ts) => {
        const t = (ts ?? []).find((x) => x.id === w.taskId);
        if (!t) return;
        this.taskDetail.set(t);
        this.taskDetailOpen.set(true);
      }
    });
  }
  closeTask(): void { this.taskDetailOpen.set(false); this.taskDetail.set(null); }
  /** Sửa task xong → nạp lại giờ để ô trong lưới cập nhật ngay. */
  reloadLogs(): void {
    this.svc.listProjectWorkLogs(this.projectId(), this.from(), this.to()).subscribe({
      next: (w) => this.logs.set(w ?? [])
    });
  }

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
    const mk = (m: ProjectMember): Row => ({
      member: m, days: new Array(cols.length).fill(0), dayTasks: new Array(cols.length).fill(0),
      total: 0, totalTasks: 0, unscheduled: 0
    });
    // Người đã TẠM NGƯNG trong dự án không dựng sẵn dòng: họ không còn làm nên để một hàng 0 giờ
    // chỉ tổ làm loãng bảng. Nếu trong kỳ họ VẪN có giờ đã ghi thì vòng lặp bên dưới tự dựng dòng —
    // bỏ hẳn sẽ làm tổng giờ của dự án hụt đi.
    const memberById = new Map(this.members().map((m) => [m.userId, m]));
    const rows: Row[] = this.members().filter((m) => m.active).map((m) => {
      const r = mk(m);
      rowByUser.set(m.userId, r);
      return r;
    });
    // Khử trùng công việc theo (người × ngày) và theo (người × cả khoảng): một task ghi giờ
    // nhiều lần trong ngày vẫn chỉ tính LÀ MỘT việc, nếu không con số sẽ đếm số lần ghi.
    const seenDay = new Set<string>();
    const seenAll = new Set<string>();

    for (const w of this.scopedLogs()) {
      let row = rowByUser.get(w.userId);
      if (!row) {
        // Đã tạm ngưng (vẫn là thành viên) hoặc đã rời hẳn dự án → vẫn dựng dòng để không mất giờ.
        const paused = memberById.get(w.userId);
        row = mk(paused
          ? { ...paused, name: paused.name + ' (đã ngưng)' }
          : ghostMember(w.userId, w.userName));
        rowByUser.set(w.userId, row);
        rows.push(row);
      }
      const ci = colIndex.get(w.workDate);
      if (ci !== undefined) {
        row.days[ci] += w.hours || 0;
        const k = w.userId + '|' + w.workDate + '|' + w.taskId;
        if (!seenDay.has(k)) { seenDay.add(k); row.dayTasks[ci]++; }
      } else {
        row.unscheduled += w.hours || 0;   // ghi vào T7/CN hoặc ngoài khoảng đang xem
      }
      const ka = w.userId + '|' + w.taskId;
      if (!seenAll.has(ka)) { seenAll.add(ka); row.totalTasks++; }
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
  /** Số công việc riêng biệt có ghi giờ theo từng ngày (mọi người) — hàng cuối lưới. */
  readonly dayTaskTotals = computed<number[]>(() => {
    const cols = this.cols();
    const out = new Array(cols.length).fill(0);
    cols.forEach((c, i) => {
      const set = new Set(this.scopedLogs().filter((w) => w.workDate === c.key).map((w) => w.taskId));
      out[i] = set.size;
    });
    return out;
  });
  readonly grandTaskTotal = computed(() => new Set(this.scopedLogs().map((w) => w.taskId)).size);
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
