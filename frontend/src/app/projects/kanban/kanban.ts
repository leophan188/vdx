import { Component, Renderer2, computed, effect, inject, input, output, signal } from '@angular/core';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { TypeFilter } from '../../shared/type-filter/type-filter';
import { ToastService } from '../../shared/toast/toast.service';
import { ProjectService, ProjectTask, TaskStatus, TaskPriority, TaskType } from '../../core/project.service';

interface Column {
  status: TaskStatus;
  label: string;
}

/**
 * Bảng Kanban dự án — 5 cột theo TaskStatus. KÉO-THẢ là cơ chế CHÍNH để đổi trạng thái
 * (HTML5 draggable → updateTaskStatus, cập nhật lạc quan, hoàn tác khi lỗi). Thả vào cùng cột = no-op.
 * Bấm thẻ (không phải khi kéo) → phát output openTask để cha mở chi tiết.
 * Mỗi thẻ có thanh % hoàn thành. Lọc theo assignee. Đếm số thẻ mỗi cột.
 */
@Component({
  selector: 'app-prj-kanban',
  imports: [EmployeeChip, SearchableSelect, TypeFilter],
  templateUrl: './kanban.html',
  styles: [`
    /* Thanh lọc dùng class chuẩn .filter-bar (ở _components.scss). */
    .filter-bar__field { min-width: 240px; }
    .kb-datechips { display: inline-flex; gap: var(--space-1); flex-wrap: wrap; }
    .kb-chip {
      padding: 5px var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-full);
      background: var(--color-surface); color: var(--color-text-muted); cursor: pointer; font-size: var(--text-sm);
    }
    .kb-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .kb-chip--on { background: var(--color-primary-soft, var(--brand-blue-soft)); border-color: var(--color-primary);
      color: var(--color-primary); font-weight: var(--weight-semibold); }
    .kb-card__due { margin-top: 2px; }
    .kb-due--over { background: var(--overdue-bg); color: var(--overdue); font-weight: var(--weight-semibold); }
    .kb-card.is-overdue { border-color: var(--overdue); box-shadow: 0 0 0 1px var(--overdue) inset, var(--shadow-sm); }
    .kb-board {
      display: grid; grid-auto-flow: column; grid-auto-columns: minmax(240px, 1fr);
      gap: var(--space-3); align-items: stretch; overflow-x: auto; padding-bottom: var(--space-2);
      -webkit-overflow-scrolling: touch;
    }
    /* Mobile: cột hẹp hơn để thấy được nhiều cột khi cuộn ngang. */
    @media (max-width: 860px) { .kb-board { grid-auto-columns: minmax(82vw, 1fr); } }
    /* Cột cao tối thiểu để TOÀN cột là vùng thả (kéo vào chỗ trống bên dưới vẫn nhận). */
    .kb-col {
      display: flex; flex-direction: column; min-height: 62vh; max-height: 78vh;
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface-alt);
    }
    .kb-col__head {
      display: flex; align-items: center; justify-content: space-between; gap: var(--space-2);
      padding: var(--space-3); border-bottom: 1px solid var(--color-border);
      font-size: var(--text-sm); font-weight: var(--weight-semibold); color: var(--color-text-muted);
      position: sticky; top: 0; background: var(--color-surface-alt); border-radius: var(--radius-lg) var(--radius-lg) 0 0;
    }
    .kb-col__count {
      display: inline-flex; align-items: center; justify-content: center; min-width: 22px; height: 20px;
      padding: 0 var(--space-2); border-radius: var(--radius-full);
      background: var(--color-surface); color: var(--color-text-muted); font-size: var(--text-xs);
    }
    .kb-col__body { display: flex; flex-direction: column; gap: var(--space-2); padding: var(--space-3); overflow-y: auto; flex: 1; min-height: 60px; transition: background .15s ease; }
    .kb-col.is-over { outline: 2px dashed var(--color-primary); outline-offset: -4px; }
    .kb-col.is-over .kb-col__body { background: var(--status-active-bg, var(--color-surface)); }
    .kb-card {
      display: grid; gap: var(--space-2); padding: var(--space-3);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); box-shadow: var(--shadow-sm); cursor: grab;
      transition: box-shadow .15s ease, opacity .15s ease, transform .05s ease;
      user-select: none; -webkit-user-select: none;  /* không bôi đen chữ khi kéo */
    }
    .kb-card:hover { box-shadow: var(--shadow-md, var(--shadow-sm)); }
    .kb-card:active { cursor: grabbing; }
    .kb-card.is-dragging { opacity: 0.4; cursor: grabbing; }
    /* Thẻ bóng ma bám con trỏ khi kéo */
    .kb-ghost {
      position: fixed; z-index: 3000; pointer-events: none; transform: translate(14px, 14px);
      max-width: 260px; padding: var(--space-2) var(--space-3);
      background: var(--color-primary, #2563eb); color: #fff;
      border-radius: var(--radius-md); box-shadow: var(--shadow-pop);
      font-size: var(--text-xs); font-weight: var(--weight-semibold);
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .kb-card__top { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
    .kb-card__code { font-size: var(--text-xs); font-weight: var(--weight-semibold); color: var(--color-text-muted); }
    /* Nút "⋮" + menu chuyển cột — phương án dự phòng khi không kéo-thả được (cảm ứng). */
    .kb-card__topright { display: inline-flex; align-items: center; gap: var(--space-1); }
    .kb-card__menu-wrap { position: relative; }
    .kb-card__menu-btn {
      height: 24px; width: 24px; padding: 0; border: none; background: transparent; cursor: pointer;
      color: var(--color-text-muted); font-size: var(--text-lg); line-height: 1; border-radius: var(--radius-sm);
    }
    .kb-card__menu-btn:hover { background: var(--color-surface-alt); color: var(--color-text); }
    .kb-card__menu {
      position: absolute; top: calc(100% + 4px); right: 0; z-index: 20; min-width: 168px;
      background: var(--color-surface); border: 1px solid var(--color-border); border-radius: var(--radius-md);
      box-shadow: var(--shadow-pop); padding: var(--space-1); display: flex; flex-direction: column;
    }
    .kb-card__menu-title {
      font-size: var(--text-xs); color: var(--color-text-muted); padding: var(--space-1) var(--space-2);
      text-transform: uppercase; letter-spacing: .04em;
    }
    .kb-card__menu-item {
      display: flex; align-items: center; gap: var(--space-2); text-align: left; width: 100%;
      background: transparent; border: none; cursor: pointer; color: var(--color-text);
      padding: var(--space-2) var(--space-2); border-radius: var(--radius-sm); font-size: var(--text-sm);
    }
    .kb-card__menu-item:hover:not(:disabled) { background: var(--color-primary-soft); color: var(--color-primary); }
    .kb-card__menu-item:disabled { color: var(--color-text-muted); cursor: default; }
    .kb-card__menu-item.is-current { font-weight: var(--weight-semibold); }
    .kb-card__menu-check { width: 14px; text-align: center; color: var(--color-primary); }
    .kb-card__menu-backdrop { position: fixed; inset: 0; z-index: 19; }
    .kb-card__title { font-size: var(--text-sm); color: var(--color-text); line-height: 1.35; font-weight: var(--weight-medium); }
    /* Ngữ cảnh cha (Epic › Story › Task cha) */
    .kb-card__path { display: flex; flex-wrap: wrap; align-items: center; gap: 4px;
      padding-bottom: 2px; border-bottom: 1px dashed var(--color-border); margin-bottom: 2px; }
    .kb-card__path-item { display: inline-flex; align-items: center; gap: 3px; min-width: 0; max-width: 100%; }
    .kb-card__path-name { font-size: var(--text-xs); color: var(--color-text-muted);
      max-width: 130px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .kb-card__path-sep { color: var(--color-text-muted); font-size: var(--text-xs); }
    .kb-badge--xs { font-size: 10px; padding: 0 5px; line-height: 16px; height: 16px; }
    .kb-card__meta { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); flex-wrap: wrap; }
    .kb-card__est { font-size: var(--text-xs); color: var(--color-text-muted); }
    .kb-card__progress { display: grid; gap: 2px; }
    .kb-card__bar { height: 6px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .kb-card__bar > i { display: block; height: 100%; border-radius: var(--radius-full); background: var(--color-primary); transition: width .2s ease; }
    .kb-card__bar.is-done > i { background: var(--status-active, var(--color-primary)); }
    .kb-card__pct { font-size: var(--text-xs); color: var(--color-text-muted); text-align: right; }
    .kb-empty { font-size: var(--text-sm); color: var(--color-text-muted); padding: var(--space-2); text-align: center; }
    .badge--urgent { background: var(--overdue-bg); color: var(--overdue); }
    .badge--high   { background: var(--status-pending-bg); color: var(--status-pending); }
    .badge--medium { background: var(--status-active-bg); color: var(--status-active); }
    .badge--low    { background: var(--color-surface-alt); color: var(--color-text-muted); }
  `]
})
export class PrjKanban {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);
  private renderer = inject(Renderer2);
  /** Hàm gỡ listener document khi kết thúc kéo. */
  private dragCleanup: Array<() => void> = [];

  readonly projectId = input.required<string>();

  /** Phát khi bấm thẻ (không phải khi kéo) — để cha mở chi tiết công việc. */
  readonly openTask = output<ProjectTask>();

  readonly tasks = signal<ProjectTask[]>([]);
  readonly loading = signal(true);
  readonly draggingId = signal<string | null>(null);
  readonly overStatus = signal<TaskStatus | null>(null);
  /** ID thẻ đang mở menu "chuyển cột" (phương án thay thế kéo-thả, tiện cho cảm ứng). */
  readonly menuId = signal<string | null>(null);
  /** Thẻ "bóng ma" bám theo con trỏ khi đang kéo (phản hồi trực quan). */
  readonly ghost = signal<{ x: number; y: number; label: string } | null>(null);

  /** Lọc theo assignee (rỗng = tất cả) — PHẢI là signal để computed lọc chạy lại. */
  readonly filterAssignee = signal('');
  /** Lọc nhanh theo hạn: ALL/TODAY/TOMORROW/OVERDUE. */
  readonly dateFilter = signal<'ALL' | 'TODAY' | 'TOMORROW' | 'OVERDUE'>('ALL');
  readonly dateFilters: { key: 'ALL' | 'TODAY' | 'TOMORROW' | 'OVERDUE'; label: string }[] = [
    { key: 'ALL', label: 'Tất cả' },
    { key: 'TODAY', label: 'Hôm nay' },
    { key: 'TOMORROW', label: 'Ngày mai' },
    { key: 'OVERDUE', label: 'Trễ hạn' }
  ];

  readonly columns: Column[] = [
    { status: 'BACKLOG', label: 'Backlog' },
    { status: 'TODO', label: 'Cần làm' },
    { status: 'IN_PROGRESS', label: 'Đang làm' },
    { status: 'IN_REVIEW', label: 'Đang review' },
    { status: 'DONE', label: 'Hoàn thành' }
  ];

  /** Lọc ƯU TIÊN — chip dùng chung. Mặc định hiện hết. */
  readonly priorityChips = [
    { value: 'URGENT', label: 'Khẩn' }, { value: 'HIGH', label: 'Cao' },
    { value: 'MEDIUM', label: 'Trung bình' }, { value: 'LOW', label: 'Thấp' }
  ];
  readonly priorityFilter = signal<Set<string>>(new Set(['URGENT', 'HIGH', 'MEDIUM', 'LOW']));
  togglePriority(p: string): void {
    const set = new Set(this.priorityFilter());
    set.has(p) ? set.delete(p) : set.add(p);
    if (set.size === 0) ['URGENT', 'HIGH', 'MEDIUM', 'LOW'].forEach((x) => set.add(x));
    this.priorityFilter.set(set);
  }

  /** Danh sách assignee (suy ra từ task) cho bộ lọc. */
  readonly assigneeSel = computed<SelectOption[]>(() => {
    const map = new Map<string, string>();
    for (const t of this.tasks()) {
      if (t.assigneeUserId) map.set(t.assigneeUserId, t.assigneeName || t.assigneeUserId);
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  });

  /** Task sau lọc — Kanban CHỈ hiện hạng mục công việc (ẩn Epic/Story là cấp nhóm) + lọc người + lọc hạn. */
  readonly visible = computed(() => {
    const a = this.filterAssignee();
    const df = this.dateFilter();
    const prio = this.priorityFilter();
    let ts = this.tasks().filter((t) => t.type !== 'EPIC' && t.type !== 'STORY');
    if (a) ts = ts.filter((t) => t.assigneeUserId === a);
    if (prio.size < 4) ts = ts.filter((t) => prio.has(t.priority));
    if (df !== 'ALL') ts = ts.filter((t) => this.matchDate(t, df));
    return ts;
  });

  /** Hôm nay 00:00 (mốc so sánh hạn). */
  private today0(): number {
    const d = new Date(); d.setHours(0, 0, 0, 0); return d.getTime();
  }
  /** dueDate dd/MM/yyyy → mốc 00:00 (ms) hoặc null. */
  private dueMs(t: ProjectTask): number | null {
    if (!t.dueDate) return null;
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(t.dueDate);
    if (!m) return null;
    return new Date(+m[3], +m[2] - 1, +m[1]).getTime();
  }
  /** Quá hạn = có hạn, hạn < hôm nay, CHƯA hoàn thành. */
  isOverdue(t: ProjectTask): boolean {
    const d = this.dueMs(t);
    return d != null && d < this.today0() && t.status !== 'DONE';
  }
  /** Khớp bộ lọc hạn. OVERDUE/TODAY chỉ tính task CHƯA xong (trễ hạn nằm trong "Hôm nay" đến khi xong). */
  private matchDate(t: ProjectTask, df: 'TODAY' | 'TOMORROW' | 'OVERDUE'): boolean {
    const d = this.dueMs(t);
    if (d == null) return false;
    const today = this.today0();
    const day = 86400000;
    if (df === 'TOMORROW') return d === today + day;
    if (df === 'OVERDUE') return d < today && t.status !== 'DONE';
    // TODAY: đúng hôm nay HOẶC trễ hạn (chưa xong) → vẫn nằm trong hôm nay đến khi xong.
    return t.status !== 'DONE' && d <= today;
  }

  typeLabel(t: TaskType): string {
    switch (t) {
      case 'EPIC': return 'Epic';
      case 'STORY': return 'Story';
      case 'TASK': return 'Task';
      case 'SUBTASK': return 'Sub-task';
      case 'BUG': return 'Bug';
      case 'ISSUE': return 'Issue';
      default: return t;
    }
  }
  typeBadge(t: TaskType): string {
    switch (t) {
      case 'EPIC': return 'badge--pending';
      case 'STORY': return 'badge--active';
      case 'BUG': case 'ISSUE': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }

  constructor() {
    // Tự tải lại khi projectId đổi.
    effect(() => {
      const id = this.projectId();
      if (id) this.reload(id);
    });
  }

  reload(id = this.projectId()): void {
    this.loading.set(true);
    this.svc.listTasks(id).subscribe({
      next: (rows) => { this.tasks.set(rows); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách công việc'); this.loading.set(false); }
    });
  }

  /** Thẻ thuộc 1 cột (sau lọc). */
  cardsOf(status: TaskStatus): ProjectTask[] {
    return this.visible().filter((t) => t.status === status);
  }
  countOf(status: TaskStatus): number {
    return this.cardsOf(status).length;
  }

  // ----- Kéo-thả bằng MOUSE + Renderer2 lắng nghe DOCUMENT (zone-aware → CD chạy, bắt chuột mọi nơi) -----
  /** Nhấn chuột xuống thẻ → bắt đầu theo dõi kéo trên toàn document. */
  onCardMouseDown(ev: MouseEvent, task: ProjectTask): void {
    if (ev.button !== 0) return;                                  // chỉ chuột trái
    if ((ev.target as HTMLElement).closest('.kb-card__menu-wrap')) return;  // nút ⋮ → bỏ qua
    ev.preventDefault();                                          // chặn bôi đen chữ khi kéo
    const startX = ev.clientX, startY = ev.clientY;
    const label = `${task.code} · ${task.title}`;
    let started = false;

    const move = (e: MouseEvent) => {
      if (!started) {
        if (Math.hypot(e.clientX - startX, e.clientY - startY) < 5) return;  // ngưỡng để phân biệt click
        started = true;
        this.draggingId.set(task.id);
      }
      this.ghost.set({ x: e.clientX, y: e.clientY, label });
      const el = document.elementFromPoint(e.clientX, e.clientY) as HTMLElement | null;
      const col = el?.closest('.kb-col') as HTMLElement | null;
      const st = (col?.getAttribute('data-status') as TaskStatus | null) ?? null;
      if (this.overStatus() !== st) this.overStatus.set(st);
    };

    const up = () => {
      this.endDrag();
      const dragId = this.draggingId();
      const over = this.overStatus();
      this.draggingId.set(null);
      this.overStatus.set(null);
      this.ghost.set(null);
      if (started && dragId && over) {
        this.changeStatus(dragId, over);                         // thả vào cột → đổi trạng thái (cùng cột = no-op)
      } else if (!started) {
        this.openTask.emit(task);                                // chỉ click → mở chi tiết
      }
    };

    // Renderer2.listen('document',…) chạy TRONG Angular zone → change-detection cập nhật ghost/highlight.
    this.dragCleanup = [
      this.renderer.listen('document', 'mousemove', move),
      this.renderer.listen('document', 'mouseup', up)
    ];
  }

  private endDrag(): void {
    this.dragCleanup.forEach((fn) => fn());
    this.dragCleanup = [];
  }

  // ----- Menu "chuyển cột" (dự phòng cảm ứng) -----
  /** Mở/đóng menu chọn cột cho 1 thẻ. */
  toggleMenu(taskId: string): void {
    this.menuId.update((cur) => (cur === taskId ? null : taskId));
  }
  closeMenu(): void {
    this.menuId.set(null);
  }
  /** Chọn cột từ menu → đổi trạng thái rồi đóng menu. */
  pickStatus(taskId: string, status: TaskStatus): void {
    this.closeMenu();
    this.changeStatus(taskId, status);
  }

  /** Đổi trạng thái (kéo-thả hoặc dropdown) — cập nhật lạc quan rồi reload. */
  changeStatus(taskId: string, status: TaskStatus): void {
    const cur = this.tasks();
    const target = cur.find((t) => t.id === taskId);
    if (!target || target.status === status) return;
    const prev = target.status;
    // Lạc quan: cập nhật ngay UI.
    this.tasks.set(cur.map((t) => (t.id === taskId ? { ...t, status } : t)));
    this.svc.updateTaskStatus(this.projectId(), taskId, status).subscribe({
      next: () => this.reload(),
      error: () => {
        // Hoàn tác khi lỗi.
        this.tasks.set(this.tasks().map((t) => (t.id === taskId ? { ...t, status: prev } : t)));
        this.toast.error('Không cập nhật được trạng thái công việc');
      }
    });
  }

  /** Kẹp progressPct về 0..100 (an toàn dữ liệu). */
  pct(t: ProjectTask): number {
    const v = Math.round(t.progressPct ?? 0);
    return v < 0 ? 0 : v > 100 ? 100 : v;
  }

  // ----- nhãn/màu -----
  priorityBadge(p: TaskPriority): string {
    switch (p) {
      case 'URGENT': return 'badge--urgent';
      case 'HIGH': return 'badge--high';
      case 'MEDIUM': return 'badge--medium';
      default: return 'badge--low';
    }
  }
  priorityLabel(p: TaskPriority): string {
    switch (p) {
      case 'URGENT': return 'Khẩn';
      case 'HIGH': return 'Cao';
      case 'MEDIUM': return 'Trung bình';
      default: return 'Thấp';
    }
  }
}
