import { Component, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { ToastService } from '../../shared/toast/toast.service';
import {
  ProjectService, ProjectTask, TaskRequest, TaskType, TaskStatus, TaskPriority, ProjectMember
} from '../../core/project.service';
import { memberPersonOptions } from '../../shared/person-options';
import { buildTree, hasChildren, subtreeLeafEstimate } from '../../shared/task-tree';
import { loadPref, savePref } from '../../shared/view-prefs';
import { TypeFilter } from '../../shared/type-filter/type-filter';

/** Một dòng cây (task phẳng + cấp thụt lề, giữ thứ tự DFS). */
interface TreeRow {
  task: ProjectTask;
  level: number;
  hasChildren: boolean;
}

/**
 * TAB Backlog — cây đa cấp cha-con của task trong dự án.
 * Dựng cây từ listTasks phẳng theo parentId, thụt lề theo cấp, gập/mở.
 * Thao tác mỗi dòng: thêm con, sửa, xoá (chỉ khi không con), đổi status, gán người.
 */
@Component({
  selector: 'app-prj-backlog',
  imports: [FormsModule, Modal, SearchableSelect, TypeFilter],
  templateUrl: './backlog.html',
  styles: [`
    .bl-summary { display: flex; gap: var(--space-2); flex-wrap: wrap; align-items: center; margin-bottom: var(--space-3); }
    /* Thanh lọc dùng class chuẩn .filter-bar (ở _components.scss). */
    .bl-toolbar__spacer { flex: 1; } /* còn dùng trong footer modal */
    .bl-chip { padding: 3px var(--space-2); border: 1px solid var(--color-border); border-radius: var(--radius-full);
      background: var(--color-surface); color: var(--color-text-muted); cursor: pointer; font-size: var(--font-size-xs); }
    .bl-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .bl-chip--on { background: var(--color-primary-soft); border-color: var(--color-primary);
      color: var(--color-primary); font-weight: 600; }
    .bl-tree { border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow-x: auto; background: var(--color-surface); }
    .bl-head, .bl-row {
      display: grid;
      grid-template-columns: minmax(220px, 1.6fr) 92px 118px 150px 62px 110px 96px 96px 84px 96px 116px;
      align-items: center; gap: var(--space-2);
      padding: var(--space-2) var(--space-3); min-width: 1180px;
    }
    .bl-head { font-weight: 600; font-size: var(--font-size-sm); color: var(--color-text-muted);
      background: var(--color-surface-alt); border-bottom: 1px solid var(--color-border); }
    .bl-row { border-bottom: 1px solid var(--color-border); }
    .bl-row:last-child { border-bottom: 0; }
    .bl-row:hover { background: var(--color-surface-alt); }
    .bl-title { display: flex; align-items: center; gap: 2px; min-width: 0; }
    .bl-toggle { background: none; border: 0; cursor: pointer; width: 18px; font-size: var(--font-size-sm);
      color: var(--color-text-muted); padding: 0; flex: 0 0 auto; }
    .bl-leaf { display: inline-block; width: 18px; text-align: center; color: var(--color-border); flex: 0 0 auto; }
    .bl-code { font-family: var(--font-mono, monospace); font-size: var(--font-size-xs); color: var(--color-text-muted);
      flex: 0 0 auto; margin-right: 2px; }
    .bl-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
    .bl-name:hover { color: var(--color-primary); text-decoration: underline; }
    .bl-actions { display: flex; gap: 2px; justify-content: flex-end; }
    .bl-empty { padding: var(--space-4); text-align: center; color: var(--color-text-muted); }
    .bl-num { text-align: right; font-variant-numeric: tabular-nums; }
    .bl-num--muted { color: var(--color-text-muted); }
    .bl-date { font-size: var(--text-sm); font-variant-numeric: tabular-nums; white-space: nowrap; }
    /* Nhập nhanh est tại chỗ trên lưới (task lá) */
    .bl-est {
      width: 100%; max-width: 70px; text-align: right; font-variant-numeric: tabular-nums;
      padding: 2px var(--space-1); border: 1px solid var(--color-border); border-radius: var(--radius-sm);
      background: var(--color-surface); color: var(--color-text); font-size: var(--font-size-sm);
    }
    .bl-est:hover { border-color: var(--color-primary-soft); }
    .bl-est:focus { outline: none; border-color: var(--color-primary);
      box-shadow: 0 0 0 2px var(--color-primary-soft); }
    .field-hint { font-size: var(--font-size-xs); color: var(--color-text-muted); margin-top: 2px; }
    .bl-form { display: grid; gap: var(--space-3); width: 100%; }
    .bl-keep { display: inline-flex; align-items: center; gap: var(--space-2);
      font-size: var(--font-size-sm); color: var(--color-text-muted); cursor: pointer; user-select: none; }
    .bl-keep input { width: 16px; height: 16px; cursor: pointer; }

    /* Thanh tiến độ % hoàn thành */
    .bl-progress { display: flex; align-items: center; gap: var(--space-2); min-width: 0; }
    .bl-progress__track {
      flex: 1; min-width: 40px; height: 8px; border-radius: 999px;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); overflow: hidden;
    }
    .bl-progress__bar { height: 100%; border-radius: 999px; background: var(--color-primary); transition: width .2s ease; }
    .bl-progress__bar--done { background: var(--color-success, var(--color-primary)); }
    .bl-progress__label { font-size: var(--font-size-xs); color: var(--color-text-muted);
      font-variant-numeric: tabular-nums; flex: 0 0 auto; width: 34px; text-align: right; }

    /* Thanh tiến độ chung trong khu tổng */
    .bl-summary__overall { display: flex; align-items: center; gap: var(--space-2); min-width: 180px; flex: 1; max-width: 280px; }
    .bl-summary__track { flex: 1; height: 10px; border-radius: 999px;
      background: var(--color-surface-alt); border: 1px solid var(--color-border); overflow: hidden; }
    .bl-summary__bar { height: 100%; border-radius: 999px; background: var(--color-primary); transition: width .2s ease; }
  `]
})
export class PrjBacklog implements OnInit {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();

  /** Phát khi bấm tiêu đề một task → cha mở chi tiết task. */
  readonly openTask = output<ProjectTask>();

  readonly tasks = signal<ProjectTask[]>([]);
  readonly loading = signal(true);
  /** CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống). */
  readonly members = signal<ProjectMember[]>([]);
  readonly collapsed = signal<Set<string>>(new Set());

  // ----- Modal tạo/sửa -----
  readonly modalOpen = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly saving = signal(false);
  f: TaskRequest = this.emptyForm();
  // ngày dạng yyyy-MM-dd cho <input type=date>; convert khi gửi.
  startIso = '';
  dueIso = '';
  /** Nhập nhanh: tích chọn → sau khi Lưu KHÔNG đóng popup, giữ form để thêm task tiếp. */
  keepAdding = false;

  readonly typeOptions: { value: TaskType; label: string }[] = [
    { value: 'EPIC', label: 'Epic' }, { value: 'STORY', label: 'Story' },
    { value: 'TASK', label: 'Task' }, { value: 'SUBTASK', label: 'Sub-task' },
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'Cần làm' },
    { value: 'IN_PROGRESS', label: 'Đang làm' }, { value: 'IN_REVIEW', label: 'Đang review' },
    { value: 'DONE', label: 'Hoàn thành' }
  ];
  readonly priorityOptions: { value: TaskPriority; label: string }[] = [
    { value: 'LOW', label: 'Thấp' }, { value: 'MEDIUM', label: 'Trung bình' },
    { value: 'HIGH', label: 'Cao' }, { value: 'URGENT', label: 'Khẩn cấp' }
  ];

  readonly typeSel: SelectOption[] = this.typeOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly statusSel: SelectOption[] = this.statusOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly prioritySel: SelectOption[] = this.priorityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members()));

  /** Cây task (byId + childrenOf) — dùng cho rollup est cha / phát hiện có con. */
  readonly tree = computed(() => buildTree(this.tasks()));

  /** Lọc theo LOẠI (Epic/Story/Task/Sub-task/Bug/Issue) — lưu localStorage THEO DỰ ÁN cho lần sau. */
  readonly allTypes: TaskType[] = ['EPIC', 'STORY', 'TASK', 'SUBTASK', 'BUG', 'ISSUE'];
  readonly typeFilter = signal<Set<TaskType>>(new Set(this.allTypes));
  /** Lọc theo NGƯỜI THỰC HIỆN (rỗng = tất cả) — lưu theo dự án. */
  readonly filterAssignee = signal('');
  private typeKey(): string { return 'bpm.backlog.typeFilter.' + (this.projectId() || 'x'); }
  private asgKey(): string { return 'bpm.backlog.assignee.' + (this.projectId() || 'x'); }
  /** Nạp cấu hình lọc đã lưu của dự án (gọi khi có projectId). */
  private loadFilterPrefs(): void {
    const saved = loadPref<TaskType[] | null>(this.typeKey(), null);
    if (saved && saved.length) this.typeFilter.set(new Set(saved));
    this.filterAssignee.set(loadPref<string>(this.asgKey(), ''));
  }
  isTypeOn(t: TaskType): boolean { return this.typeFilter().has(t); }
  toggleType(t: TaskType): void {
    const s = new Set(this.typeFilter());
    s.has(t) ? s.delete(t) : s.add(t);
    if (s.size === 0) { this.allTypes.forEach((x) => s.add(x)); } // không cho ẩn hết
    this.typeFilter.set(s);
    savePref(this.typeKey(), [...s]);
  }
  setAssignee(uid: string): void {
    this.filterAssignee.set(uid || '');
    savePref(this.asgKey(), uid || '');
  }
  /** Danh sách người để lọc — suy từ assignee của các task. */
  readonly assigneeSel = computed<SelectOption[]>(() => {
    const map = new Map<string, string>();
    for (const t of this.tasks()) {
      if (t.assigneeUserId) map.set(t.assigneeUserId, t.assigneeName || t.assigneeUserId);
    }
    return [...map.entries()].map(([value, label]) => ({ value, label }))
      .sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  });
  /** Làm tròn % về số nguyên (không lấy thập phân). */
  round(n: number | null | undefined): number { return Math.round(n || 0); }

  /** Danh sách phẳng DFS theo parentId + orderIndex, kèm cấp thụt lề. */
  readonly rows = computed<TreeRow[]>(() => {
    const byParent = new Map<string | null, ProjectTask[]>();
    for (const t of this.tasks()) {
      const k = t.parentId ?? null;
      (byParent.get(k) ?? byParent.set(k, []).get(k)!).push(t);
    }
    for (const list of byParent.values()) {
      list.sort((a, b) => (a.orderIndex - b.orderIndex) || (a.seq - b.seq));
    }
    const out: TreeRow[] = [];
    const walk = (parentId: string | null, level: number) => {
      for (const t of byParent.get(parentId) ?? []) {
        const kids = byParent.get(t.id) ?? [];
        out.push({ task: t, level, hasChildren: kids.length > 0 });
        walk(t.id, level + 1);
      }
    };
    walk(null, 0);
    return out;
  });

  /** Ẩn dòng nếu có tổ tiên đang gập, hoặc loại không nằm trong bộ lọc, hoặc không khớp người (vẫn giữ task CHA). */
  readonly visible = computed<TreeRow[]>(() => {
    const col = this.collapsed();
    const types = this.typeFilter();
    const asg = this.filterAssignee();
    const byId = new Map(this.tasks().map((t) => [t.id, t]));

    // Lọc NGƯỜI: giữ task khớp người + MỌI tổ tiên của chúng (để vẫn thấy Epic/Story/task cha).
    let assigneeKeep: Set<string> | null = null;
    if (asg) {
      assigneeKeep = new Set<string>();
      for (const t of this.tasks()) {
        if (t.assigneeUserId === asg) {
          assigneeKeep.add(t.id);
          let p = t.parentId;
          while (p) { assigneeKeep.add(p); p = byId.get(p)?.parentId ?? null; }
        }
      }
    }

    return this.rows().filter((r) => {
      if (!types.has(r.task.type)) return false;
      if (assigneeKeep && !assigneeKeep.has(r.task.id)) return false;
      let p = r.task.parentId;
      while (p) {
        if (col.has(p)) return false;
        p = byId.get(p)?.parentId ?? null;
      }
      return true;
    });
  });

  /**
   * Tổng est (giờ) + % hoàn thành chung.
   * % chung: rollup theo est của các task GỐC (parentId=null) dùng progressPct mỗi gốc theo est.
   * progressPct của gốc đã là rollup theo est của cây con → đây là trọng số est chuẩn nhất.
   * Fallback: nếu tổng est = 0, lấy trung bình progressPct của task lá.
   */
  readonly summary = computed(() => {
    const ts = this.tasks();
    const tr = this.tree();
    // Tổng est CHỈ tính task LÁ (tránh double-count cha + con).
    const totalEst = ts
      .filter((t) => !hasChildren(t.id, tr))
      .reduce((s, t) => s + (t.estimateHours || 0), 0);
    const leaves = ts.filter((t) => t.leaf);
    const leafDone = leaves.filter((t) => t.status === 'DONE').length;

    const roots = ts.filter((t) => !t.parentId);
    const rootEst = roots.reduce((s, t) => s + (t.estimateHours || 0), 0);
    let pct: number;
    if (rootEst > 0) {
      // trung bình có trọng số theo est của các gốc
      const weighted = roots.reduce((s, t) => s + (t.estimateHours || 0) * (t.progressPct || 0), 0);
      pct = Math.round(weighted / rootEst);
    } else if (roots.length) {
      // không có est → trung bình đơn giản progressPct các gốc
      pct = Math.round(roots.reduce((s, t) => s + (t.progressPct || 0), 0) / roots.length);
    } else {
      pct = 0;
    }
    return { total: ts.length, totalEst, leaves: leaves.length, leafDone, pct };
  });

  constructor() {
    // Tải lại khi projectId đổi.
    effect(() => {
      const id = this.projectId();
      if (id) this.reload();
    });
  }

  ngOnInit(): void {
    this.loadFilterPrefs(); // khôi phục bộ lọc đã lưu của dự án (loại + người)
    // CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống).
    this.svc.listMembers(this.projectId()).subscribe({
      next: (m) => this.members.set(m),
      error: () => { /* không có thành viên — bỏ qua */ }
    });
  }

  reload(): void {
    this.loading.set(true);
    this.svc.listTasks(this.projectId()).subscribe({
      next: (r) => { this.tasks.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách công việc.'); this.loading.set(false); }
    });
  }

  /** Tải lại danh sách KHÔNG bật cờ loading (tránh nhấp nháy) — dùng sau thao tác inline để rollup %/est của cha cập nhật ngay. */
  private silentReload(): void {
    this.svc.listTasks(this.projectId()).subscribe({
      next: (r) => this.tasks.set(r),
      error: () => { /* giữ nguyên dữ liệu hiện tại nếu lỗi */ }
    });
  }

  // ----- Cây gập/mở -----
  isCollapsed(id: string): boolean { return this.collapsed().has(id); }
  toggle(id: string): void {
    const s = new Set(this.collapsed());
    s.has(id) ? s.delete(id) : s.add(id);
    this.collapsed.set(s);
  }
  expandAll(): void { this.collapsed.set(new Set()); }
  collapseAll(): void {
    this.collapsed.set(new Set(this.rows().filter((r) => r.hasChildren).map((r) => r.task.id)));
  }

  // ----- Modal -----
  openCreate(parentId: string | null): void {
    this.editingId.set(null);
    this.f = this.emptyForm();
    this.f.parentId = parentId;
    // task con của BUG/ISSUE kế thừa screen từ cha (gợi ý) — để trống mặc định.
    // Mặc định Ngày bắt đầu = Ngày kết thúc = HÔM NAY (vẫn cho sửa).
    this.startIso = this.dueIso = this.todayIso();
    this.modalOpen.set(true);
  }

  openEdit(t: ProjectTask): void {
    this.editingId.set(t.id);
    this.f = {
      parentId: t.parentId,
      title: t.title,
      description: t.description ?? '',
      type: t.type,
      status: t.status,
      priority: t.priority,
      assigneeUserId: t.assigneeUserId,
      estimateHours: t.estimateHours,
      screen: t.screen ?? '',
      startDate: t.startDate,
      dueDate: t.dueDate
    };
    this.startIso = this.toIso(t.startDate);
    this.dueIso = this.toIso(t.dueDate);
    this.modalOpen.set(true);
  }

  save(): void {
    if (!this.f.title?.trim()) { this.toast.warning('Thiếu tiêu đề công việc'); return; }
    this.saving.set(true);
    const body: TaskRequest = {
      ...this.f,
      title: this.f.title.trim(),
      estimateHours: this.f.estimateHours ? Number(this.f.estimateHours) : 0,
      startDate: this.fromIso(this.startIso),
      dueDate: this.fromIso(this.dueIso),
      screen: this.isBugLike(this.f.type) ? (this.f.screen || null) : null
    };
    const id = this.editingId();
    const call = id
      ? this.svc.updateTask(this.projectId(), id, body)
      : this.svc.createTask(this.projectId(), body);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(id ? 'Đã cập nhật công việc' : 'Đã tạo công việc');
        this.reload();
        if (!id && this.keepAdding) {
          // Nhập nhanh: giữ popup + giữ cấp cha/loại/ưu tiên/người; xoá tiêu đề + est + ngày + mô tả.
          const { parentId, type, priority, assigneeUserId } = this.f;
          this.f = { ...this.emptyForm(), parentId, type, priority, assigneeUserId };
          this.startIso = this.dueIso = '';
          setTimeout(() =>
            document.querySelector<HTMLInputElement>('#bl-form input[name=title]')?.focus(), 0);
        } else {
          this.modalOpen.set(false);
        }
      },
      error: (e) => { this.saving.set(false); this.toast.error('Không lưu được', e?.error?.message ?? ''); }
    });
  }

  // ----- Thao tác nhanh trên dòng -----
  changeStatus(t: ProjectTask, status: string): void {
    if (!status || status === t.status) return;
    this.svc.updateTaskStatus(this.projectId(), t.id, status as TaskStatus).subscribe({
      next: (u) => {
        this.patchLocal(u);
        this.toast.success('Đã đổi trạng thái', u.code);
        this.silentReload(); // tính lại % rollup của cha + tổng ngay (không cần reload tay)
      },
      error: (e) => this.toast.error('Không đổi được trạng thái', e?.error?.message ?? '')
    });
  }

  changeAssignee(t: ProjectTask, userId: string): void {
    const val = userId || null;
    if (val === t.assigneeUserId) return;
    this.svc.assignTask(this.projectId(), t.id, val).subscribe({
      next: (u) => { this.patchLocal(u); this.toast.success('Đã gán người thực hiện', u.assigneeName ?? '— bỏ gán —'); },
      error: (e) => this.toast.error('Không gán được', e?.error?.message ?? '')
    });
  }

  /**
   * Nhập nhanh est (giờ) tại chỗ trên lưới — CHỈ với task LÁ.
   * Gửi updateTask với đầy đủ field hiện có của task (title/type/status/priority/assignee/dates/screen)
   * + estimateHours mới → không xoá dữ liệu nào. Sau đó cập nhật local.
   */
  saveEstInline(t: ProjectTask, raw: string): void {
    if (hasChildren(t.id, this.tree())) return; // cha = rollup, không nhập
    const val = Math.max(0, Number(raw) || 0);
    if (val === (t.estimateHours || 0)) return;
    const body: TaskRequest = {
      parentId: t.parentId,
      title: t.title,
      description: t.description ?? '',
      type: t.type,
      status: t.status,
      priority: t.priority,
      assigneeUserId: t.assigneeUserId,
      estimateHours: val,
      screen: t.screen,
      startDate: t.startDate,
      dueDate: t.dueDate
    };
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => {
        this.patchLocal(u);
        this.toast.success('Đã cập nhật est', `${u.code}: ${val} giờ`);
        this.silentReload(); // est đổi → % rollup cha + tổng est tính lại ngay
      },
      error: (e) => this.toast.error('Không cập nhật được est', e?.error?.message ?? '')
    });
  }

  /** Có task con không (dùng cho lưới / modal). */
  hasKids(taskId: string): boolean { return hasChildren(taskId, this.tree()); }

  /** Est hiển thị cho task CHA = Σ est lá trong cây con (rollup, read-only). */
  rollupEst(taskId: string): number { return subtreeLeafEstimate(taskId, this.tree()); }

  /** Task có con? (dùng trong template cho cột ngày rollup). */
  hasChildrenRow(taskId: string): boolean { return hasChildren(taskId, this.tree()); }

  // ===== Ngày (rollup từ task con) + Duration =====
  private parseDmy(s: string | null | undefined): Date | null {
    if (!s) return null;
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(s);
    return m ? new Date(+m[3], +m[2] - 1, +m[1]) : null;
  }
  private fmtDmy(d: Date | null): string | null {
    if (!d) return null;
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()}`;
  }
  /** Mọi ngày bắt đầu/kết thúc của task LÁ trong cây con (gồm chính task nếu là lá). */
  private leafDates(taskId: string, kind: 'start' | 'due'): Date[] {
    const tr = this.tree();
    const kids = tr.childrenOf.get(taskId) ?? [];
    if (kids.length === 0) {
      const t = tr.byId.get(taskId);
      const d = this.parseDmy(kind === 'start' ? t?.startDate : t?.dueDate);
      return d ? [d] : [];
    }
    return kids.flatMap((k) => this.leafDates(k.id, kind));
  }
  /** Ngày bắt đầu hiển thị: lá → của nó; cha → MIN ngày bắt đầu các lá. */
  startOf(task: ProjectTask): string | null {
    if (!hasChildren(task.id, this.tree())) return task.startDate;
    const ds = this.leafDates(task.id, 'start');
    return ds.length ? this.fmtDmy(new Date(Math.min(...ds.map((d) => d.getTime())))) : null;
  }
  /** Ngày kết thúc hiển thị: lá → của nó; cha → MAX ngày kết thúc các lá. */
  dueOf(task: ProjectTask): string | null {
    if (!hasChildren(task.id, this.tree())) return task.dueDate;
    const ds = this.leafDates(task.id, 'due');
    return ds.length ? this.fmtDmy(new Date(Math.max(...ds.map((d) => d.getTime())))) : null;
  }
  /** Duration (số ngày) từ ngày bắt đầu → kết thúc (gồm 2 đầu); '—' nếu thiếu. */
  durationOf(task: ProjectTask): string {
    const s = this.parseDmy(this.startOf(task));
    const e = this.parseDmy(this.dueOf(task));
    if (!s || !e || e < s) return '—';
    const days = Math.round((e.getTime() - s.getTime()) / 86400000) + 1;
    return days + ' ngày';
  }

  /** Đang sửa 1 task ĐÃ CÓ con → disable est/người trong modal. */
  readonly editingHasChildren = computed(() => {
    const id = this.editingId();
    return id ? hasChildren(id, this.tree()) : false;
  });

  remove(r: TreeRow): void {
    if (r.hasChildren) { this.toast.warning('Không thể xoá', 'Công việc còn có task con.'); return; }
    if (!confirm(`Xoá công việc ${r.task.code} — ${r.task.title}?`)) return;
    this.svc.deleteTask(this.projectId(), r.task.id).subscribe({
      next: () => { this.toast.success('Đã xoá công việc', r.task.code); this.reload(); },
      error: (e) => this.toast.error('Không xoá được', e?.error?.message ?? '')
    });
  }

  // ----- nhãn / badge -----
  typeLabel(t: TaskType): string { return this.typeOptions.find((o) => o.value === t)?.label ?? t; }
  statusLabel(s: TaskStatus): string { return this.statusOptions.find((o) => o.value === s)?.label ?? s; }
  priorityLabel(p: TaskPriority): string { return this.priorityOptions.find((o) => o.value === p)?.label ?? p; }

  typeBadge(t: TaskType): string {
    switch (t) {
      case 'EPIC': return 'badge--pending';
      case 'STORY': return 'badge--active';
      case 'BUG': return 'badge--cancel';
      case 'ISSUE': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }
  statusBadge(s: TaskStatus): string {
    switch (s) {
      case 'DONE': return 'badge--active';
      case 'IN_PROGRESS': return 'badge--pending';
      case 'IN_REVIEW': return 'badge--pending';
      case 'TODO': return 'badge--neutral';
      default: return 'badge--neutral';
    }
  }
  priorityBadge(p: TaskPriority): string {
    switch (p) {
      case 'URGENT': return 'badge--cancel';
      case 'HIGH': return 'badge--pending';
      case 'MEDIUM': return 'badge--neutral';
      default: return 'badge--neutral';
    }
  }

  isBugLike(t: TaskType | null | undefined): boolean { return t === 'BUG' || t === 'ISSUE'; }

  /** Bấm tiêu đề task → báo cha mở chi tiết. */
  openDetail(t: ProjectTask): void { this.openTask.emit(t); }

  // ----- helpers -----
  private patchLocal(u: ProjectTask): void {
    this.tasks.update((ts) => ts.map((t) => (t.id === u.id ? u : t)));
  }

  private emptyForm(): TaskRequest {
    return {
      parentId: null, title: '', description: '', type: 'TASK', status: 'BACKLOG',
      priority: 'MEDIUM', assigneeUserId: null, estimateHours: 0, screen: '',
      startDate: null, dueDate: null
    };
  }

  /** Hôm nay dạng yyyy-MM-dd (local) cho <input type=date>. */
  private todayIso(): string {
    const d = new Date();
    const mm = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${mm}-${dd}`;
  }

  /** dd/MM/yyyy → yyyy-MM-dd cho input date. */
  private toIso(d: string | null): string {
    if (!d) return '';
    const m = d.match(/^(\d{2})\/(\d{2})\/(\d{4})$/);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : '';
  }
  /** yyyy-MM-dd → dd/MM/yyyy cho API. */
  private fromIso(d: string): string | null {
    if (!d) return null;
    const m = d.match(/^(\d{4})-(\d{2})-(\d{2})$/);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : null;
  }
}
