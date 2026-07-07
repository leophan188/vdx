import { Component, HostListener, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { TypeFilter } from '../../shared/type-filter/type-filter';
import { memberPersonOptions } from '../../shared/person-options';
import { buildParentOptions } from '../work-stats';
import { BUG_DESCRIPTION_TEMPLATE, mergeBugFieldsIntoDescription } from '../../shared/bug-template';
import { forkJoin, of } from 'rxjs';
import { ToastService } from '../../shared/toast/toast.service';
import { AuthService } from '../../core/auth.service';
import { PrjTaskDetail } from '../task-detail/task-detail';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import {
  ProjectService, ProjectTask, TaskRequest, TaskType, TaskStatus, TaskPriority, BugSeverity, ProjectMember
} from '../../core/project.service';

/**
 * TAB Quản lý Bug/Issue. Bug/Issue gắn TRỰC TIẾP vào task cha (parentId).
 * Lọc listTasks lấy type ∈ {BUG, ISSUE}. Lưới + bộ lọc (status/type) + báo lỗi (modal).
 * Bấm 1 bug → mở <app-prj-task-detail> (Jira style).
 */
@Component({
  selector: 'app-prj-bugs',
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, SearchableSelect, PrjTaskDetail, EmployeeChip, TypeFilter],
  templateUrl: './bugs.html',
  styles: [`
    /* Thanh lọc dùng class chuẩn .filter-bar (ở _components.scss). */
    .bug-form { display: grid; gap: var(--space-3); width: 100%; }
    .bug-stats { display: flex; gap: var(--space-2); flex-wrap: wrap; margin-bottom: var(--space-3); }
    .bug-progress { display: flex; align-items: center; gap: 6px; }
    .bug-progress .bar { flex: 1; height: 6px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; min-width: 48px; }
    .bug-progress .bar > i { display: block; height: 100%; background: var(--color-primary); }
    .bug-progress .pct { font-size: .75rem; min-width: 34px; text-align: right; }
    .bug-open { background: none; border: none; padding: 0; color: var(--color-primary); cursor: pointer; font-weight: 600; text-align: left; }
    .field-hint { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
    /* Khu chọn ảnh khi báo lỗi */
    .bug-att { display: flex; flex-wrap: wrap; gap: var(--space-2); }
    .bug-att__item { position: relative; width: 76px; height: 76px; border-radius: var(--radius-md);
      overflow: hidden; border: 1px solid var(--color-border); }
    .bug-att__item img { width: 100%; height: 100%; object-fit: cover; }
    .bug-att__del { position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; border: none;
      border-radius: 50%; background: rgba(0,0,0,.6); color: #fff; cursor: pointer; line-height: 1; font-size: .75rem; }
    .bug-att__add { display: flex; align-items: center; justify-content: center; width: 76px; height: 76px;
      border: 1px dashed var(--color-border); border-radius: var(--radius-md); cursor: pointer;
      color: var(--color-text-muted); font-size: var(--text-sm); background: var(--color-surface-alt); }
    .bug-att__add:hover { border-color: var(--color-primary); color: var(--color-primary); }
    /* Badge mức độ nghiêm trọng (semantic màu cứng: đỏ/cam/xám) */
    .sev { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: .72rem; font-weight: 600; line-height: 18px; white-space: nowrap; }
    .sev--red { background: var(--overdue-bg); color: var(--overdue); }
    .sev--orange { background: var(--status-pending-bg); color: var(--status-pending); }
    .sev--gray { background: var(--color-surface-alt, #eef1f5); color: var(--color-text-muted, #64748b); }
    .bug-edit { background: none; border: none; cursor: pointer; color: var(--color-text-muted, #64748b); font-size: 1rem; padding: 2px 6px; border-radius: 6px; }
    .bug-edit:hover { background: var(--color-surface-alt, #eef1f5); color: var(--color-primary, #2563eb); }
  `]
})
export class PrjBugs implements OnInit {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  readonly projectId = input.required<string>();

  readonly tasks = signal<ProjectTask[]>([]);
  readonly loading = signal(true);
  /** CHỈ thành viên dự án (không phải toàn bộ nhân sự hệ thống) — gán bug đúng người trong dự án. */
  readonly members = signal<ProjectMember[]>([]);
  /** Ảnh đính kèm chờ tải lên (queue khi báo lỗi, upload sau khi tạo task). */
  readonly queuedFiles = signal<{ file: File; url: string }[]>([]);

  // ----- Bộ lọc (chip đa chọn — signal để computed lọc CHẠY LẠI khi đổi; đồng bộ với Kanban/Log) -----
  readonly STATUS_KEYS = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CANCELLED'];
  readonly TYPE_KEYS = ['BUG', 'ISSUE'];
  readonly statusFilter = signal<Set<string>>(new Set(this.STATUS_KEYS));
  readonly typeFilter = signal<Set<string>>(new Set(this.TYPE_KEYS));
  toggleStatus(v: string): void {
    const s = new Set(this.statusFilter());
    s.has(v) ? s.delete(v) : s.add(v);
    if (s.size === 0) this.STATUS_KEYS.forEach((x) => s.add(x));
    this.statusFilter.set(s);
  }
  toggleType(v: string): void {
    const s = new Set(this.typeFilter());
    s.has(v) ? s.delete(v) : s.add(v);
    if (s.size === 0) this.TYPE_KEYS.forEach((x) => s.add(x));
    this.typeFilter.set(s);
  }

  readonly typeOptions: { value: TaskType; label: string }[] = [
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'Cần làm' },
    { value: 'IN_PROGRESS', label: 'Đang làm' }, { value: 'IN_REVIEW', label: 'Kiểm thử' },
    { value: 'DONE', label: 'Hoàn thành' }, { value: 'CANCELLED', label: 'Huỷ' }
  ];
  readonly priorityOptions: { value: TaskPriority; label: string }[] = [
    { value: 'LOW', label: 'Thấp' }, { value: 'MEDIUM', label: 'Trung bình' },
    { value: 'HIGH', label: 'Cao' }, { value: 'URGENT', label: 'Khẩn cấp' }
  ];
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Chặn' }, { value: 'CRITICAL', label: 'Nguy kịch' },
    { value: 'MAJOR', label: 'Lớn' }, { value: 'MINOR', label: 'Nhỏ' },
    { value: 'TRIVIAL', label: 'Không đáng kể' }
  ];

  readonly typeSel: SelectOption[] = this.typeOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly statusSel: SelectOption[] = this.statusOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly prioritySel: SelectOption[] = this.priorityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members()));

  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '90px', sortable: true },
    { key: 'type', header: 'Loại', width: '90px' },
    { key: 'title', header: 'Tiêu đề', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '150px' },
    { key: 'priority', header: 'Ưu tiên', width: '120px' },
    { key: 'severity', header: 'Mức độ', width: '120px' },
    { key: 'assignee', header: 'Người thực hiện', width: '160px' },
    { key: 'progress', header: '%', width: '120px' },
    { key: 'est', header: 'Est', width: '70px', align: 'right' },
    { key: 'act', header: '', width: '70px', align: 'center' }
  ];

  /** Toàn bộ bug/issue (chưa lọc). */
  readonly bugs = computed<ProjectTask[]>(() =>
    this.tasks().filter((t) => t.type === 'BUG' || t.type === 'ISSUE'));

  /** Mọi task — để gắn bug vào task cha (parentId). Badge loại + chuỗi cha (đồng bộ Backlog/Tạo nhanh). */
  readonly parentSel = computed<SelectOption[]>(() => buildParentOptions(this.tasks(), this.tasks()));

  readonly filtered = computed<ProjectTask[]>(() => {
    const st = this.statusFilter();
    const ty = this.typeFilter();
    return this.bugs().filter((t) =>
      (st.size >= this.STATUS_KEYS.length || st.has(t.status)) &&
      (ty.size >= this.TYPE_KEYS.length || ty.has(t.type)));
  });

  readonly stats = computed(() => {
    const b = this.bugs();
    const open = b.filter((t) => t.status !== 'DONE').length;
    return { total: b.length, open, done: b.length - open };
  });

  // ----- Modal báo lỗi / sửa lỗi -----
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  /** id bug đang sửa; null = đang tạo mới. */
  readonly editingId = signal<string | null>(null);
  readonly modalTitle = computed(() => (this.editingId() ? 'Sửa lỗi' : 'Báo lỗi'));
  f: TaskRequest = this.emptyForm();

  // ----- Chi tiết task (Jira) -----
  readonly detailOpen = signal(false);
  readonly detailTask = signal<ProjectTask | null>(null);

  constructor() {
    effect(() => {
      const id = this.projectId();
      if (id) this.reload();
    });
  }

  ngOnInit(): void {
    // CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống).
    this.svc.listMembers(this.projectId()).subscribe({
      next: (m) => this.members.set(m),
      error: () => { /* bỏ qua */ }
    });
  }

  // ----- Ảnh đính kèm (chọn ngay khi báo lỗi) -----
  onFilesSelected(ev: Event): void {
    const input = ev.target as HTMLInputElement;
    if (!input.files) return;
    const add = Array.from(input.files)
      .filter((f) => f.type.startsWith('image/'))
      .map((file) => ({ file, url: URL.createObjectURL(file) }));
    this.queuedFiles.update((q) => [...q, ...add]);
    input.value = ''; // cho phép chọn lại cùng file
  }

  /**
   * Dán ảnh từ clipboard (Ctrl/Cmd+V) khi form Báo lỗi/Sửa lỗi đang mở → tự thêm vào hàng chờ,
   * upload cùng lúc lưu (không cần bấm đính kèm). Tester chỉ cần chụp màn hình rồi dán.
   */
  @HostListener('document:paste', ['$event'])
  onPaste(ev: ClipboardEvent): void {
    if (!this.modalOpen()) return; // chỉ khi form báo/sửa lỗi đang mở
    const items = ev.clipboardData?.items;
    if (!items) return;
    const add: { file: File; url: string }[] = [];
    for (const it of Array.from(items)) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const raw = it.getAsFile();
        if (!raw) continue;
        const ext = (it.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        // Ảnh clipboard thường không có tên → đặt tên gợi nhớ theo thời điểm.
        const file = raw.name && raw.name !== 'image.png'
          ? raw
          : new File([raw], `screenshot-${Date.now()}.${ext}`, { type: it.type });
        add.push({ file, url: URL.createObjectURL(file) });
      }
    }
    if (add.length) {
      ev.preventDefault();
      this.queuedFiles.update((q) => [...q, ...add]);
      this.toast.success('Đã dán ảnh', `${add.length} ảnh — sẽ tải lên khi lưu.`);
    }
  }
  removeQueued(i: number): void {
    this.queuedFiles.update((q) => {
      const item = q[i];
      if (item) URL.revokeObjectURL(item.url);
      return q.filter((_, idx) => idx !== i);
    });
  }
  private clearQueued(): void {
    for (const q of this.queuedFiles()) URL.revokeObjectURL(q.url);
    this.queuedFiles.set([]);
  }

  reload(): void {
    this.loading.set(true);
    this.svc.listTasks(this.projectId()).subscribe({
      next: (r) => {
        this.tasks.set(r);
        this.loading.set(false);
        // đồng bộ task đang mở chi tiết (nếu có) với dữ liệu mới
        const d = this.detailTask();
        if (d) {
          const fresh = r.find((x) => x.id === d.id);
          if (fresh) this.detailTask.set(fresh);
        }
      },
      error: () => { this.toast.error('Không tải được danh sách lỗi.'); this.loading.set(false); }
    });
  }

  resetFilter(): void {
    this.statusFilter.set(new Set(this.STATUS_KEYS));
    this.typeFilter.set(new Set(this.TYPE_KEYS));
  }

  // ----- Modal -----
  openReport(): void {
    this.editingId.set(null);
    this.f = this.emptyForm();
    this.clearQueued();
    this.modalOpen.set(true);
  }

  /** Mở modal ở chế độ SỬA — đổ dữ liệu bug vào form. */
  openEdit(t: ProjectTask): void {
    this.editingId.set(t.id);
    this.f = {
      parentId: t.parentId, title: t.title,
      // Gộp Bước/Kết quả cũ (nếu có) vào Mô tả để không mất dữ liệu.
      description: mergeBugFieldsIntoDescription(t),
      type: t.type, status: t.status, priority: t.priority,
      assigneeUserId: t.assigneeUserId, testerUserId: t.testerUserId, estimateHours: t.estimateHours,
      startDate: t.startDate, dueDate: t.dueDate,
      severity: t.severity, stepsToReproduce: '', expectedResult: '', actualResult: '',
      environment: t.environment ?? ''
    };
    this.modalOpen.set(true);
  }

  save(): void {
    if (!this.f.title?.trim()) { this.toast.warning('Thiếu tiêu đề lỗi'); return; }
    if (!this.f.parentId) { this.toast.warning('Bắt buộc chọn task cha'); return; }
    this.saving.set(true);
    const body: TaskRequest = {
      ...this.f,
      title: this.f.title.trim(),
      parentId: this.f.parentId,
      // Đã gộp vào Mô tả → không gửi 3 trường tách nữa.
      stepsToReproduce: null, expectedResult: null, actualResult: null
    };
    const id = this.editingId();
    if (id) {
      // ----- SỬA (cho phép đính kèm THÊM ảnh) -----
      this.svc.updateTask(this.projectId(), id, body).subscribe({
        next: (t) => {
          const files = this.queuedFiles();
          const uploads = files.length
            ? forkJoin(files.map((q) => this.svc.uploadAttachment(this.projectId(), t.id, q.file)))
            : of([]);
          uploads.subscribe({
            next: () => {
              this.saving.set(false);
              this.clearQueued();
              this.toast.success('Đã cập nhật lỗi', `${t.code} · ${t.title}`
                + (files.length ? ` · +${files.length} ảnh` : ''));
              this.modalOpen.set(false);
              this.reload();
            },
            error: () => {
              this.saving.set(false); this.clearQueued();
              this.toast.warning('Đã lưu lỗi nhưng tải ảnh thất bại — thử lại ở chi tiết.');
              this.modalOpen.set(false); this.reload();
            }
          });
        },
        error: (e) => { this.saving.set(false); this.toast.error('Không cập nhật được', e?.error?.message ?? ''); }
      });
      return;
    }
    // ----- TẠO MỚI → upload ảnh đã chọn (nếu có) → mở task-detail -----
    this.svc.createTask(this.projectId(), body).subscribe({
      next: (t) => {
        const files = this.queuedFiles();
        const uploads = files.length
          ? forkJoin(files.map((q) => this.svc.uploadAttachment(this.projectId(), t.id, q.file)))
          : of([]);
        uploads.subscribe({
          next: () => {
            this.saving.set(false);
            this.clearQueued();
            this.toast.success('Đã báo lỗi', `${t.code} · ${t.title}`
              + (files.length ? ` · ${files.length} ảnh` : ''));
            this.modalOpen.set(false);
            this.reload();
            this.openDetail(t); // mở chi tiết để xem/đính kèm thêm
          },
          error: () => {
            // Task đã tạo nhưng ảnh lỗi → vẫn mở chi tiết để thử lại.
            this.saving.set(false);
            this.toast.warning('Đã báo lỗi nhưng tải ảnh chưa xong', 'Hãy đính kèm lại trong chi tiết.');
            this.modalOpen.set(false);
            this.reload();
            this.openDetail(t);
          }
        });
      },
      error: (e) => { this.saving.set(false); this.toast.error('Không tạo được', e?.error?.message ?? ''); }
    });
  }

  // ----- Chi tiết -----
  openDetail(t: ProjectTask): void {
    this.detailTask.set(t);
    this.detailOpen.set(true);
  }
  closeDetail(): void {
    this.detailOpen.set(false);
    this.detailTask.set(null);
  }

  // ----- Đổi status nhanh trên lưới -----
  changeStatus(t: ProjectTask, status: string): void {
    if (!status || status === t.status) return;
    this.svc.updateTaskStatus(this.projectId(), t.id, status as TaskStatus).subscribe({
      next: (u) => {
        this.tasks.update((ts) => ts.map((x) => (x.id === u.id ? u : x)));
        this.toast.success('Đã đổi trạng thái', u.code);
      },
      error: (e) => this.toast.error('Không đổi được trạng thái', e?.error?.message ?? '')
    });
  }

  // ----- nhãn / badge -----
  typeLabel(t: TaskType): string { return this.typeOptions.find((o) => o.value === t)?.label ?? t; }
  statusLabel(s: TaskStatus): string { return this.statusOptions.find((o) => o.value === s)?.label ?? s; }
  priorityLabel(p: TaskPriority): string { return this.priorityOptions.find((o) => o.value === p)?.label ?? p; }

  typeBadge(t: TaskType): string { return t === 'ISSUE' ? 'badge--pending' : 'badge--cancel'; }
  statusBadge(s: TaskStatus): string {
    switch (s) {
      case 'DONE': return 'badge--active';
      case 'IN_PROGRESS': return 'badge--pending';
      case 'IN_REVIEW': return 'badge--pending';
      case 'CANCELLED': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }
  priorityBadge(p: TaskPriority): string {
    switch (p) {
      case 'URGENT': return 'badge--cancel';
      case 'HIGH': return 'badge--pending';
      default: return 'badge--neutral';
    }
  }
  severityLabel(s: BugSeverity | null): string {
    return s ? (this.severityOptions.find((o) => o.value === s)?.label ?? s) : '';
  }
  /** Badge mức độ: Chặn/Nguy kịch = đỏ; Lớn = cam; Nhỏ/Không đáng kể = xám. */
  severityBadge(s: BugSeverity | null): string {
    switch (s) {
      case 'BLOCKER':
      case 'CRITICAL': return 'sev sev--red';
      case 'MAJOR': return 'sev sev--orange';
      case 'MINOR':
      case 'TRIVIAL': return 'sev sev--gray';
      default: return 'sev sev--gray';
    }
  }

  private emptyForm(): TaskRequest {
    return {
      parentId: null, title: '', description: BUG_DESCRIPTION_TEMPLATE, type: 'BUG', status: 'BACKLOG',
      priority: 'MEDIUM', assigneeUserId: null, estimateHours: 0,
      startDate: null, dueDate: null,
      // Người kiểm thử MẶC ĐỊNH = người log/tạo (chính mình) — vẫn cho chỉnh lại.
      testerUserId: this.auth.currentUser()?.userId ?? null,
      severity: null, stepsToReproduce: '', expectedResult: '', actualResult: '', environment: ''
    };
  }
}
