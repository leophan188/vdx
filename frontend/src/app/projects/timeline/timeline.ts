import { Component, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { ProjectService, ProjectTask, TaskStatus, TaskType, Project } from '../../core/project.service';
import { loadPref, savePref } from '../../shared/view-prefs';
import { TypeFilter, TypeChip } from '../../shared/type-filter/type-filter';

/** Đơn vị phóng (zoom) của trục thời gian. */
export type GanttZoom = 'year' | 'month' | 'week' | 'day';

/** Một ô nhãn trên header trục thời gian (vị trí + bề rộng tính bằng px). */
interface AxisTick {
  label: string;
  left: number;
  width: number;
  alt?: string; // dòng phụ (vd: khoảng ngày của tuần)
}

/** Một hàng Gantt: task + dữ liệu hiển thị + hình học của bar (nếu có lịch). */
interface GanttRow {
  task: ProjectTask;
  depth: number;            // cấp lồng cha-con (để thụt lề)
  hasBar: boolean;          // đủ ngày để vẽ bar?
  left: number;             // px từ mốc gốc tới startDate
  width: number;            // px độ dài bar
  start: Date | null;
  end: Date | null;
  progressPct: number;      // 0..100 — % hoàn thành (overlay tô đậm)
  tooltip: string;
}

const DAY_MS = 86_400_000;

/**
 * Tab Timeline (Gantt) cho module Quản lý dự án — vẽ thuần CSS (không thư viện ngoài, không CDK).
 * Cột trái: danh sách task (thụt lề theo cấp). Cột phải: trục thời gian + bar định vị theo ngày.
 * Phóng Năm/Tháng/Tuần/Ngày đổi px mỗi ngày → bar tính lại vị trí & độ dài.
 */
@Component({
  selector: 'app-prj-timeline',
  imports: [TypeFilter],
  templateUrl: './timeline.html',
  styles: [`
    .gantt { display: flex; flex-direction: column; gap: var(--space-3); font-size: var(--text-sm); color: var(--color-text); }

    .gantt__bar-toolbar { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .gantt__zoom { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
    .gantt__zoom button { border: 0; background: var(--color-surface); color: var(--color-text-muted);
      padding: 0 var(--space-3); height: var(--control-h-sm); font: inherit; cursor: pointer; }
    .gantt__zoom button + button { border-left: 1px solid var(--color-border); }
    .gantt__zoom button.is-active { background: var(--color-primary); color: var(--color-text-invert); font-weight: var(--weight-medium); }
    .gantt__legend { display: flex; gap: var(--space-3); flex-wrap: wrap; margin-left: auto; color: var(--color-text-muted); font-size: var(--text-xs); }
    .gantt__legend span { display: inline-flex; align-items: center; gap: var(--space-1); }
    .gantt__swatch { width: 12px; height: 12px; border-radius: var(--radius-sm); display: inline-block; }

    /* Thanh lọc khung thời gian + loại dùng class chuẩn .filter-bar (ở _components.scss). */
    .gantt__date { height: var(--control-h-sm); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
      background: var(--color-surface); color: var(--color-text); font: inherit; font-size: var(--text-xs);
      padding: 0 var(--space-2); }
    .gantt__filters { display: inline-flex; gap: var(--space-3); flex-wrap: wrap; }
    .gantt__check { display: inline-flex; align-items: center; gap: var(--space-1); font-size: var(--text-xs);
      color: var(--color-text); cursor: pointer; }
    .gantt__check input { accent-color: var(--color-primary); cursor: pointer; }
    .gantt__reset { border: 1px solid var(--color-border); background: var(--color-surface); color: var(--color-text-muted);
      height: var(--control-h-sm); padding: 0 var(--space-3); border-radius: var(--radius-sm); font: inherit;
      font-size: var(--text-xs); cursor: pointer; }
    .gantt__reset:hover { color: var(--color-primary); border-color: var(--color-primary); }

    .gantt__grid { display: flex; border: 1px solid var(--color-border); border-radius: var(--radius-md);
      overflow: hidden; background: var(--color-surface); }

    /* Cột trái: danh sách task (cuộn ngang khi chật) */
    .gantt__list { flex: 0 0 auto; border-right: 1px solid var(--color-border); overflow-x: auto; max-width: 560px; }
    .gantt__list-inner { min-width: 480px; }
    .gantt__list-head { height: 48px; display: flex; align-items: center;
      font-weight: var(--weight-semibold); border-bottom: 1px solid var(--color-border); background: var(--color-surface-alt); }
    .gantt__list-row { height: var(--row-h); display: flex; align-items: center;
      border-bottom: 1px solid var(--color-border); border-left: 3px solid transparent; white-space: nowrap; }
    .gantt__list-row:last-child { border-bottom: 0; }
    /* Tô màu nhận diện theo loại: EPIC (tím) · Story (xanh dương) · Task cha (xanh ngọc). */
    .gantt__list-row--epic { border-left-color: #7c3aed; background: color-mix(in srgb, #7c3aed 9%, transparent); }
    .gantt__list-row--epic .gantt__title { font-weight: 700; }
    .gantt__list-row--story { border-left-color: #2563eb; background: color-mix(in srgb, #2563eb 7%, transparent); }
    .gantt__list-row--story .gantt__title { font-weight: 600; }
    .gantt__list-row--parent { border-left-color: #0d9488; background: color-mix(in srgb, #0d9488 5%, transparent); }
    .gantt__legend-sep { width: 1px; height: 14px; background: var(--color-border); display: inline-block; }
    .gantt__swatch--epic { background: color-mix(in srgb, #7c3aed 30%, transparent); border-left: 3px solid #7c3aed; }
    .gantt__swatch--story { background: color-mix(in srgb, #2563eb 28%, transparent); border-left: 3px solid #2563eb; }
    .gantt__swatch--parent { background: color-mix(in srgb, #0d9488 24%, transparent); border-left: 3px solid #0d9488; }

    /* Các cột con của bảng trái */
    .gantt__c-task { flex: 1 1 240px; min-width: 200px; display: flex; align-items: center; gap: var(--space-2);
      padding: 0 var(--space-3); overflow: hidden; }
    .gantt__c-dates { flex: 0 0 132px; padding: 0 var(--space-2); border-left: 1px solid var(--color-border);
      font-size: var(--text-xs); color: var(--color-text-muted); white-space: nowrap;
      display: flex; align-items: center; height: 100%; box-sizing: border-box; }
    .gantt__c-pct { flex: 0 0 108px; padding: 0 var(--space-2); border-left: 1px solid var(--color-border);
      display: flex; align-items: center; gap: var(--space-2); height: 100%; box-sizing: border-box; }

    .gantt__code { font-family: var(--font-mono); font-size: var(--text-xs); color: var(--color-text-muted); flex: 0 0 auto; }
    .gantt__title { overflow: hidden; text-overflow: ellipsis; }

    /* Cột "% Hoàn thành": thanh nhỏ + số */
    .gantt__pct-track { flex: 1 1 auto; height: 6px; border-radius: var(--radius-pill, 999px);
      background: var(--color-border); overflow: hidden; }
    .gantt__pct-fill { height: 100%; background: var(--color-primary); border-radius: inherit; }
    .gantt__pct-num { flex: 0 0 auto; font-size: var(--text-xs); color: var(--color-text-muted);
      font-variant-numeric: tabular-nums; min-width: 30px; text-align: right; }

    /* Cột phải: vùng trục (cuộn ngang) */
    .gantt__scroll { flex: 1 1 auto; overflow-x: auto; }
    .gantt__canvas { position: relative; }
    .gantt__axis { height: 48px; position: relative; border-bottom: 1px solid var(--color-border); background: var(--color-surface-alt); }
    .gantt__tick { position: absolute; top: 0; bottom: 0; border-left: 1px solid var(--color-border);
      display: flex; flex-direction: column; justify-content: center; padding-left: var(--space-2);
      font-size: var(--text-xs); color: var(--color-text-muted); box-sizing: border-box; }
    .gantt__tick-alt { font-size: 10px; opacity: 0.7; }

    .gantt__lanes { position: relative; }
    .gantt__lane { height: var(--row-h); position: relative; border-bottom: 1px solid var(--color-border); }
    .gantt__lane:last-child { border-bottom: 0; }
    .gantt__gridline { position: absolute; top: 0; bottom: 0; width: 1px; background: var(--color-border); opacity: 0.5; }

    .gantt__bar { position: absolute; top: 8px; height: calc(var(--row-h) - 16px); min-width: 6px;
      border-radius: var(--radius-sm); display: flex; align-items: center; padding: 0 var(--space-2);
      color: var(--color-text-invert); font-size: var(--text-xs); overflow: hidden; white-space: nowrap; box-shadow: var(--shadow-sm); }
    .gantt__bar-label { position: relative; z-index: 1; overflow: hidden; text-overflow: ellipsis; }
    /* Overlay % hoàn thành: phần đã xong tô đậm hơn (chồng lên nền bar) */
    .gantt__bar-progress { position: absolute; top: 0; left: 0; bottom: 0; z-index: 0;
      background: rgba(0, 0, 0, 0.28); border-radius: var(--radius-sm) 0 0 var(--radius-sm); pointer-events: none; }
    .gantt__bar-pct { position: relative; z-index: 1; margin-left: auto; padding-left: var(--space-1);
      font-size: 10px; font-weight: var(--weight-semibold); opacity: 0.95; flex: 0 0 auto; }
    .gantt__nodate { position: absolute; top: 0; bottom: 0; left: var(--space-2); display: flex; align-items: center;
      font-size: var(--text-xs); font-style: italic; color: var(--color-text-muted); }

    /* Màu bar theo status */
    .gantt__bar--BACKLOG { background: var(--status-cancel); }
    .gantt__bar--TODO { background: var(--status-pending); }
    .gantt__bar--IN_PROGRESS { background: var(--status-active); }
    .gantt__bar--IN_REVIEW { background: var(--color-primary); }
    .gantt__bar--DONE { background: var(--status-done); }

    /* Đường "hôm nay" */
    .gantt__today { position: absolute; top: 0; bottom: 0; width: 2px; background: var(--overdue); z-index: 2; pointer-events: none; }
    .gantt__today::after { content: 'Hôm nay'; position: absolute; top: 2px; left: 4px; font-size: 10px;
      color: var(--overdue); background: var(--color-surface); padding: 0 2px; border-radius: var(--radius-sm); white-space: nowrap; }

    .gantt__empty { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
  `]
})
export class PrjTimeline implements OnInit {
  ngOnInit(): void { this.loadViewPrefs(); }

  readonly projectId = input.required<string>();

  private svc = inject(ProjectService);

  // ----- Trạng thái -----
  readonly tasks = signal<ProjectTask[]>([]);
  readonly loading = signal(true);
  readonly zoom = signal<GanttZoom>('month');

  readonly zoomOptions: { value: GanttZoom; label: string }[] = [
    { value: 'year', label: 'Năm' },
    { value: 'month', label: 'Tháng' },
    { value: 'week', label: 'Tuần' },
    { value: 'day', label: 'Ngày' }
  ];

  /** Chú giải màu theo status (đồng bộ với CSS .gantt__bar--*). */
  readonly statusLegend: { status: TaskStatus; label: string }[] = [
    { status: 'BACKLOG', label: 'Tồn đọng' },
    { status: 'TODO', label: 'Chờ làm' },
    { status: 'IN_PROGRESS', label: 'Đang làm' },
    { status: 'IN_REVIEW', label: 'Chờ duyệt' },
    { status: 'DONE', label: 'Hoàn thành' }
  ];

  // ----- Thanh tuỳ chỉnh -----
  /** Khung thời gian thủ công (yyyy-MM-dd từ <input type=date>); rỗng = auto theo dữ liệu. */
  readonly fromDate = signal<string>('');
  readonly toDate = signal<string>('');

  /** Bộ lọc loại task (level/loại) — lưu localStorage THEO DỰ ÁN cho lần sau. Mặc định bật hết. */
  readonly typeFilter = signal<Record<TaskType, boolean>>({
    EPIC: true, STORY: true, TASK: true, SUBTASK: true, BUG: true, ISSUE: true
  });
  private typeKey(): string { return 'bpm.timeline.typeFilter.' + (this.projectId() || 'x'); }
  private fromKey(): string { return 'bpm.timeline.from.' + (this.projectId() || 'x'); }
  private toKey(): string { return 'bpm.timeline.to.' + (this.projectId() || 'x'); }
  /** Khôi phục cấu hình lọc + khung thời gian đã lưu của dự án. */
  private loadViewPrefs(): void {
    const tf = loadPref<Record<TaskType, boolean> | null>(this.typeKey(), null);
    if (tf) this.typeFilter.set(tf);
    this.fromDate.set(loadPref<string>(this.fromKey(), ''));
    this.toDate.set(loadPref<string>(this.toKey(), ''));
  }

  /** Chip loại — ĐỒNG BỘ với Backlog (6 loại riêng, component chung app-type-filter). */
  readonly typeChips: TypeChip[] = [
    { value: 'EPIC', label: 'Epic' }, { value: 'STORY', label: 'Story' },
    { value: 'TASK', label: 'Task' }, { value: 'SUBTASK', label: 'Sub-task' },
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  /** Set loại đang bật (cho component chip). */
  readonly selectedTypes = computed<Set<string>>(() => {
    const f = this.typeFilter();
    return new Set(Object.keys(f).filter((k) => f[k as TaskType]));
  });
  /** Bấm 1 chip → đảo bật/tắt loại đó (không cho ẩn hết) + lưu. */
  onToggleType(type: string): void {
    this.typeFilter.update((f) => {
      const next = { ...f, [type]: !f[type as TaskType] } as Record<TaskType, boolean>;
      if (!Object.values(next).some((v) => v)) {
        next[type as TaskType] = true; // không cho ẩn toàn bộ
      }
      return next;
    });
    savePref(this.typeKey(), this.typeFilter());
  }
  setFromDate(v: string): void { this.fromDate.set(v); savePref(this.fromKey(), v); }
  setToDate(v: string): void { this.toDate.set(v); savePref(this.toKey(), v); }
  /** dd/MM/yyyy → yyyy-MM-dd (đổ vào input type=date). */
  private vnToIso(d: string | null): string {
    const m = d ? /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d) : null;
    return m ? `${m[3]}-${m[2]}-${m[1]}` : '';
  }
  resetCustom(): void {
    // "Đặt lại" → quay về ngày KHAI BÁO của dự án (không phải trống), khớp mặc định.
    const p = this.project();
    this.fromDate.set(p?.startDate ? this.vnToIso(p.startDate) : '');
    this.toDate.set(p?.dueDate ? this.vnToIso(p.dueDate) : '');
    this.typeFilter.set({ EPIC: true, STORY: true, TASK: true, SUBTASK: true, BUG: true, ISSUE: true });
    savePref(this.fromKey(), '');
    savePref(this.toKey(), '');
    savePref(this.typeKey(), this.typeFilter());
  }

  /** Task sau khi áp bộ lọc loại — nguồn cho range/rows. */
  private readonly visibleTasks = computed<ProjectTask[]>(() => {
    const f = this.typeFilter();
    return this.tasks().filter((t) => f[t.type]);
  });

  /** px mỗi ngày theo zoom — gốc của mọi phép định vị bar/trục. */
  private readonly pxPerDay = computed<number>(() => {
    switch (this.zoom()) {
      case 'year': return 1100 / 365;  // ~3px/ngày
      case 'month': return 240 / 30;   // ~8px/ngày
      case 'week': return 168 / 7;     // 24px/ngày
      case 'day': return 40;           // 40px/ngày
      default: return 8;
    }
  });

  constructor() {
    // Tải lại khi projectId đổi (effect đọc input signal).
    effect(() => {
      const pid = this.projectId();
      if (!pid) return;
      this.loading.set(true);
      this.svc.listTasks(pid).subscribe({
        next: (t) => { this.tasks.set(t); this.loading.set(false); },
        error: () => { this.tasks.set([]); this.loading.set(false); }
      });
      // Tải thông tin dự án → ĐIỀN SẴN ô Từ/Đến theo ngày bắt đầu/kết thúc khai báo của DỰ ÁN
      // (chỉ khi người dùng chưa tự đặt khung), để nhìn thấy + sửa được ngày vẽ.
      this.svc.get(pid).subscribe({
        next: (p) => {
          this.project.set(p);
          if (!this.fromDate() && p.startDate) this.fromDate.set(this.vnToIso(p.startDate));
          if (!this.toDate() && p.dueDate) this.toDate.set(this.vnToIso(p.dueDate));
        },
        error: () => this.project.set(null)
      });
    });
  }

  /** Dự án (để lấy khung thời gian mặc định). */
  readonly project = signal<Project | null>(null);

  setZoom(z: GanttZoom): void { this.zoom.set(z); }

  // ===== Phạm vi thời gian: [min start, max due] mở rộng đệm theo zoom =====
  private readonly range = computed(() => {
    // (1) Ưu tiên khung thời gian thủ công nếu nhập đủ/một phần.
    const manualFrom = parseIsoDate(this.fromDate());
    const manualTo = parseIsoDate(this.toDate());

    const dates: Date[] = [];
    for (const t of this.visibleTasks()) {
      const s = parseVnDate(t.startDate);
      const e = parseVnDate(t.dueDate);
      if (s) dates.push(s);
      if (e) dates.push(e);
    }
    const today = startOfDay(new Date());

    // Khung MẶC ĐỊNH = thời gian DỰ ÁN (startDate → dueDate) nếu có.
    const proj = this.project();
    const projStart = parseVnDate(proj?.startDate);
    const projDue = parseVnDate(proj?.dueDate);

    // Khung auto (nền) — ưu tiên ngày dự án; thiếu thì lấy theo dữ liệu task; cuối cùng quanh hôm nay.
    let autoMin: Date, autoMax: Date;
    if (projStart || projDue) {
      // Có ít nhất 1 mốc dự án → vẽ theo dự án (đệm nhẹ 2 ngày). Đầu thiếu thì suy từ task/hôm nay.
      let taskMin: Date | null = null, taskMax: Date | null = null;
      for (const d of dates) { if (!taskMin || d < taskMin) taskMin = d; if (!taskMax || d > taskMax) taskMax = d; }
      autoMin = addDays(startOfDay(projStart ?? taskMin ?? today), -2);
      autoMax = addDays(startOfDay(projDue ?? taskMax ?? addDays(today, 30)), 2);
    } else if (dates.length === 0) {
      autoMin = addDays(today, -7);
      autoMax = addDays(today, 30);
    } else {
      let min = dates[0], max = dates[0];
      for (const d of dates) { if (d < min) min = d; if (d > max) max = d; }
      if (today < min) min = today;
      if (today > max) max = today;
      autoMin = addDays(startOfDay(min), -2);
      autoMax = addDays(startOfDay(max), 2);
    }

    // (2) Khung thủ công ghi đè từng đầu nếu có nhập; ô để trống → giữ auto.
    let min = manualFrom ? startOfDay(manualFrom) : autoMin;
    let max = manualTo ? startOfDay(manualTo) : autoMax;
    if (max < min) { const tmp = min; min = max; max = tmp; }
    return { min, max };
  });

  /** Tổng bề rộng canvas (px). */
  readonly canvasWidth = computed<number>(() => {
    const { min, max } = this.range();
    const days = diffDays(min, max) + 1;
    return Math.max(days * this.pxPerDay(), 600);
  });

  /** Vị trí đường "hôm nay" (px) hoặc null nếu ngoài phạm vi. */
  readonly todayLeft = computed<number | null>(() => {
    const { min, max } = this.range();
    const today = startOfDay(new Date());
    if (today < min || today > max) return null;
    return diffDays(min, today) * this.pxPerDay();
  });

  // ===== Header trục: các ô nhãn theo zoom =====
  readonly ticks = computed<AxisTick[]>(() => {
    const { min, max } = this.range();
    const ppd = this.pxPerDay();
    const z = this.zoom();
    const out: AxisTick[] = [];
    const leftOf = (d: Date) => diffDays(min, d) * ppd;

    if (z === 'year') {
      let y = min.getFullYear();
      const endY = max.getFullYear();
      for (; y <= endY; y++) {
        const start = new Date(y, 0, 1);
        const next = new Date(y + 1, 0, 1);
        const segStart = start < min ? min : start;
        const segEnd = next > addDays(max, 1) ? addDays(max, 1) : next;
        out.push({ label: String(y), left: leftOf(segStart), width: diffDays(segStart, segEnd) * ppd });
      }
    } else if (z === 'month') {
      let cur = new Date(min.getFullYear(), min.getMonth(), 1);
      while (cur <= max) {
        const next = new Date(cur.getFullYear(), cur.getMonth() + 1, 1);
        const segStart = cur < min ? min : cur;
        const segEnd = next > addDays(max, 1) ? addDays(max, 1) : next;
        out.push({ label: pad2(cur.getMonth() + 1) + '/' + cur.getFullYear(), left: leftOf(segStart), width: diffDays(segStart, segEnd) * ppd });
        cur = next;
      }
    } else if (z === 'week') {
      // Tuần bắt đầu Thứ Hai.
      let cur = startOfWeek(min);
      let n = 1;
      while (cur <= max) {
        const next = addDays(cur, 7);
        const segStart = cur < min ? min : cur;
        const segEnd = next > addDays(max, 1) ? addDays(max, 1) : next;
        out.push({
          label: 'Tuần ' + n,
          alt: fmtShort(cur) + '–' + fmtShort(addDays(cur, 6)),
          left: leftOf(segStart), width: diffDays(segStart, segEnd) * ppd
        });
        cur = next; n++;
      }
    } else {
      // day
      let cur = startOfDay(min);
      while (cur <= max) {
        out.push({ label: fmtShort(cur), left: leftOf(cur), width: ppd });
        cur = addDays(cur, 1);
      }
    }
    return out;
  });

  // ===== Các hàng Gantt (cây phẳng theo parentId, giữ thứ tự orderIndex) =====
  /** Id các task LÀ CHA (được task khác trỏ parentId tới) — để nhận diện "Task cha". */
  private readonly parentIds = computed<Set<string>>(() => {
    const s = new Set<string>();
    for (const t of this.tasks()) { if (t.parentId) s.add(t.parentId); }
    return s;
  });

  /** Class tô màu DÒNG theo loại: EPIC / Story / Task cha (có con). */
  rowTypeClass(r: GanttRow): string {
    if (r.task.type === 'EPIC') return 'gantt__list-row--epic';
    if (r.task.type === 'STORY') return 'gantt__list-row--story';
    if (r.task.type === 'TASK' && this.parentIds().has(r.task.id)) return 'gantt__list-row--parent';
    return '';
  }

  readonly rows = computed<GanttRow[]>(() => {
    const all = this.visibleTasks();
    const { min } = this.range();
    const ppd = this.pxPerDay();

    // Dựng cây từ parentId rồi duyệt theo thứ tự để có depth thụt lề.
    const byParent = new Map<string | null, ProjectTask[]>();
    for (const t of all) {
      const key = t.parentId ?? null;
      if (!byParent.has(key)) byParent.set(key, []);
      byParent.get(key)!.push(t);
    }
    for (const list of byParent.values()) {
      list.sort((a, b) => (a.orderIndex - b.orderIndex) || (a.seq - b.seq));
    }

    const out: GanttRow[] = [];
    const known = new Set(all.map((t) => t.id));
    const walk = (parent: string | null, depth: number) => {
      for (const t of byParent.get(parent) ?? []) {
        out.push(this.toRow(t, depth, min, ppd));
        walk(t.id, depth + 1);
      }
    };
    walk(null, 0);
    // An toàn: task có parentId trỏ tới cha không tồn tại trong tập → đưa về gốc.
    for (const t of all) {
      if (t.parentId && !known.has(t.parentId) && !out.some((r) => r.task.id === t.id)) {
        out.push(this.toRow(t, 0, min, ppd));
      }
    }
    return out;
  });

  private toRow(t: ProjectTask, depth: number, min: Date, ppd: number): GanttRow {
    const start = parseVnDate(t.startDate);
    const end = parseVnDate(t.dueDate);
    const hasBar = !!(start && end);
    let left = 0, width = 0;
    let s = start, e = end;
    if (hasBar) {
      // Nếu lỡ dueDate < startDate, hoán đổi để bar vẫn hợp lệ.
      if (e! < s!) { const tmp = s; s = e; e = tmp; }
      left = diffDays(min, s!) * ppd;
      width = Math.max((diffDays(s!, e!) + 1) * ppd, 6); // +1 để bao trọn ngày kết thúc
    }
    const dateLabel = start || end
      ? (start ? fmtFull(start) : '?') + ' → ' + (end ? fmtFull(end) : '?')
      : 'chưa có lịch';
    const progressPct = Math.max(0, Math.min(100, Math.round(t.progressPct ?? 0)));
    const tooltip = `${t.title}\n${dateLabel}\nHoàn thành: ${progressPct}%`
      + (t.assigneeName ? `\nGiao: ${t.assigneeName}` : '');
    return { task: t, depth, hasBar, left, width, start: s, end: e, progressPct, tooltip };
  }

  indentPx(depth: number): string { return depth * 16 + 'px'; }

  /** Nhãn "dd/MM/yyyy" cho 1 mốc ngày; "—" nếu thiếu. */
  fmtDate(d: Date | null): string { return d ? fmtFull(d) : '—'; }

  /** Nhãn khoảng "Từ ngày → Đến ngày" cho cột bảng trái. */
  dateRangeLabel(r: GanttRow): string {
    return this.fmtDate(r.start) + ' → ' + this.fmtDate(r.end);
  }
}

// ===== Helpers ngày (thuần, không phụ thuộc) =====

/** Parse "dd/MM/yyyy" → Date (00:00 local). Trả null nếu rỗng/sai định dạng. */
function parseVnDate(s: string | null | undefined): Date | null {
  if (!s) return null;
  const m = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(s.trim());
  if (!m) return null;
  const d = +m[1], mo = +m[2], y = +m[3];
  if (mo < 1 || mo > 12 || d < 1 || d > 31) return null;
  const dt = new Date(y, mo - 1, d);
  return isNaN(dt.getTime()) ? null : dt;
}

/** Parse "yyyy-MM-dd" (từ <input type=date>) → Date (00:00 local). Trả null nếu rỗng/sai. */
function parseIsoDate(s: string | null | undefined): Date | null {
  if (!s) return null;
  const m = /^(\d{4})-(\d{1,2})-(\d{1,2})$/.exec(s.trim());
  if (!m) return null;
  const y = +m[1], mo = +m[2], d = +m[3];
  if (mo < 1 || mo > 12 || d < 1 || d > 31) return null;
  const dt = new Date(y, mo - 1, d);
  return isNaN(dt.getTime()) ? null : dt;
}

function startOfDay(d: Date): Date { return new Date(d.getFullYear(), d.getMonth(), d.getDate()); }
function addDays(d: Date, n: number): Date { return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n); }
/** Số ngày nguyên giữa 2 mốc (b - a), bỏ qua DST bằng cách làm tròn. */
function diffDays(a: Date, b: Date): number { return Math.round((startOfDay(b).getTime() - startOfDay(a).getTime()) / DAY_MS); }
/** Thứ Hai đầu tuần chứa d. */
function startOfWeek(d: Date): Date { const day = (d.getDay() + 6) % 7; return addDays(startOfDay(d), -day); }
function pad2(n: number): string { return n < 10 ? '0' + n : String(n); }
function fmtShort(d: Date): string { return pad2(d.getDate()) + '/' + pad2(d.getMonth() + 1); }
function fmtFull(d: Date): string { return pad2(d.getDate()) + '/' + pad2(d.getMonth() + 1) + '/' + d.getFullYear(); }
