import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { memberPersonOptions } from '../../shared/person-options';
import { ToastService } from '../../shared/toast/toast.service';
import {
  ProjectService, ProjectTask, TaskComment, TaskAttachment, TaskActivity, TaskActivityAction,
  TaskType, TaskStatus, TaskPriority, BugSeverity, TaskRequest, ProjectMember
} from '../../core/project.service';

type SubTab = 'info' | 'comments' | 'activity';

/**
 * Chi tiết Task kiểu Jira (modal rộng <app-modal [xwide]>).
 * - Header: code + title + badge type/priority + thanh % hoàn thành (progressPct).
 * - Hàng segmented 5 trạng thái (Backlog/Cần làm/Đang làm/Đang review/Hoàn thành) → updateTaskStatus.
 * - Thông tin: assignee (searchable-select → assignTask), estimate, ngày, mô tả.
 * - Bình luận (list TaskComment + thêm/sửa/xoá, Enter gửi).
 * - Ảnh đính kèm (lưới thumbnail, lightbox, upload, xoá).
 *
 * Dùng: <app-prj-task-detail [projectId]="pid" [task]="t" [open]="o"
 *          (closed)="…" (changed)="reload()" />
 */
@Component({
  selector: 'app-prj-task-detail',
  imports: [Modal, SearchableSelect],
  templateUrl: './task-detail.html',
  styles: [`
    .td { display: grid; gap: var(--space-4); width: 100%; }
    .td__head { display: grid; gap: var(--space-2); }
    .td__path { display: flex; align-items: center; gap: 6px; flex-wrap: wrap;
      padding-bottom: var(--space-2); border-bottom: 1px dashed var(--color-border); }
    .td__path-label { font-size: var(--text-xs); color: var(--color-text-muted); }
    .td__path-badge { font-size: 10px; padding: 0 6px; line-height: 17px; }
    .td__path-name { font-size: var(--text-sm); color: var(--color-text); max-width: 280px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .td__path-sep { color: var(--color-text-muted); }
    .td__titlerow { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
    .td__code { font-weight: 700; color: var(--color-primary); }
    .td__title { font-size: 1.05rem; font-weight: 700; }
    .td__progress { display: flex; align-items: center; gap: var(--space-2); }
    .td__bar { flex: 1; height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .td__bar > i { display: block; height: 100%; background: var(--color-primary); transition: width .2s; }
    .td__pct { font-size: .8rem; font-weight: 600; min-width: 38px; text-align: right; }

    .td__seg { display: flex; flex-wrap: wrap; gap: 6px; }
    .td__seg button {
      border: 1px solid var(--color-border); background: var(--color-surface);
      padding: 6px 12px; border-radius: 999px; font-size: .82rem; cursor: pointer; color: var(--color-text);
    }
    .td__seg button:hover { background: var(--color-surface-alt); }
    .td__seg button.is-active { background: var(--color-primary); border-color: var(--color-primary); color: #fff; font-weight: 600; }
    .td__seg button:disabled { opacity: .6; cursor: default; }

    .td__grid { display: grid; grid-template-columns: 1.2fr 1fr; gap: var(--space-4); }
    @media (max-width: 720px) { .td__grid { grid-template-columns: 1fr; } }
    .td__info { display: grid; gap: var(--space-3); }
    .td__row { display: grid; grid-template-columns: 120px 1fr; align-items: center; gap: var(--space-2); }
    .td__row > label { font-size: .82rem; color: var(--color-text-muted); }
    .td__desc { white-space: pre-wrap; line-height: 1.5; }
    .td__muted { color: var(--color-text-muted); }

    .td__sec h4 { margin: 0 0 var(--space-2); font-size: .9rem; }

    /* Chi tiết lỗi (BUG/ISSUE) */
    .bug { display: grid; gap: var(--space-3); padding: 12px; border-radius: 10px;
           background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .bug__head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2); }
    .bug__head h4 { margin: 0; font-size: .9rem; }
    .bug__field { display: grid; gap: 4px; }
    .bug__field > label { font-size: .78rem; color: var(--color-text-muted); font-weight: 600; }
    .bug__val { font-size: .88rem; white-space: pre-wrap; line-height: 1.45; color: var(--color-text); }
    .bug__val.is-empty { color: var(--color-text-muted); font-style: italic; }
    .bug__edit { display: grid; gap: var(--space-3); }
    .bug__edit textarea, .bug__edit input {
      width: 100%; padding: 7px 9px; border: 1px solid var(--color-border); border-radius: 8px;
      background: var(--color-surface); color: var(--color-text); font: inherit; box-sizing: border-box;
    }
    .bug__acts { display: flex; gap: 8px; }
    .sev { display: inline-block; padding: 1px 8px; border-radius: 999px; font-size: .72rem; font-weight: 600; line-height: 18px; }
    .sev--red { background: #fee2e2; color: #b91c1c; }
    .sev--orange { background: #ffedd5; color: #c2410c; }
    .sev--gray { background: var(--color-surface); color: var(--color-text-muted); }

    /* Sub-tabs (Thông tin | Bình luận | Hoạt động) */
    .td__tabs { display: flex; gap: 4px; border-bottom: 1px solid var(--color-border); }
    .td__tab {
      border: none; background: none; padding: 8px 14px; cursor: pointer; font-size: .85rem;
      color: var(--color-text-muted); border-bottom: 2px solid transparent; margin-bottom: -1px;
    }
    .td__tab:hover { color: var(--color-text); }
    .td__tab.is-active { color: var(--color-primary); border-bottom-color: var(--color-primary); font-weight: 600; }
    .td__tab small { font-weight: 400; opacity: .75; }

    /* Theo dõi thời gian (Spent vs Est) */
    .td__time { display: grid; gap: var(--space-2); padding: 12px; border-radius: 10px; background: var(--color-surface-alt); }
    .td__timehead { display: flex; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; font-size: .85rem; }
    .td__timehead b { font-size: 1rem; }
    .td__timehead .td__muted { font-size: .82rem; }
    .td__hbar { height: 10px; border-radius: 999px; background: var(--color-surface); overflow: hidden; }
    .td__hbar > i { display: block; height: 100%; background: var(--color-primary); transition: width .25s; }
    .td__hbar.is-over > i { background: var(--danger, #dc2626); }
    .td__over { font-size: .78rem; color: var(--danger, #dc2626); font-weight: 600; }
    .td__logwork { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .td__logwork input { width: 110px; padding: 6px 8px; border: 1px solid var(--color-border); border-radius: 8px; }
    .td__logwork .td__muted { font-size: .8rem; }

    /* Hoạt động (timeline kiểu Jira) */
    .td__acts { display: grid; gap: 0; max-height: 420px; overflow: auto; }
    .act { display: grid; grid-template-columns: 32px 1fr; gap: 10px; padding: 8px 0; position: relative; }
    .act:not(:last-child)::before {
      content: ''; position: absolute; left: 15px; top: 34px; bottom: -8px; width: 2px; background: var(--color-border);
    }
    .act__icon {
      width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
      background: var(--color-surface-alt); font-size: .95rem; z-index: 1;
    }
    .act__icon.is-status { background: #dbeafe; } .act__icon.is-assign { background: #ede9fe; }
    .act__icon.is-comment { background: #dcfce7; } .act__icon.is-attach { background: #fef3c7; }
    .act__icon.is-spent { background: #cffafe; } .act__icon.is-created { background: #e2e8f0; }
    .act__icon.is-edit { background: #fce7f3; }
    .act__body { display: grid; gap: 2px; align-content: start; }
    .act__line { font-size: .86rem; }
    .act__label { font-weight: 600; }
    .act__detail { color: var(--color-text); }
    .act__meta { font-size: .74rem; color: var(--color-text-muted); }

    /* Bình luận */
    .td__comments { display: grid; gap: var(--space-2); max-height: 320px; overflow: auto; }
    .cmt { display: grid; gap: 4px; padding: 8px 10px; border-radius: 10px; background: var(--color-surface-alt); }
    .cmt__top { display: flex; align-items: baseline; gap: 6px; flex-wrap: wrap; }
    .cmt__author { font-weight: 600; font-size: .85rem; }
    .cmt__time { font-size: .72rem; color: var(--color-text-muted); }
    .cmt__edited { font-size: .72rem; font-style: italic; color: var(--color-text-muted); }
    .cmt__body { font-size: .88rem; white-space: pre-wrap; line-height: 1.4; }
    .cmt__acts { display: flex; gap: 8px; }
    .linkbtn { background: none; border: none; padding: 0; cursor: pointer; color: var(--color-primary); font-size: .75rem; }
    .linkbtn--danger { color: var(--danger, #dc2626); }
    .cmt__edit { display: grid; gap: 6px; }
    .cmt__editacts { display: flex; gap: 8px; }
    .td__newcmt { display: flex; gap: 8px; align-items: flex-start; margin-top: var(--space-2); }
    .td__newcmt textarea { flex: 1; }

    /* Ảnh */
    .td__atts { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 8px; }
    .att { position: relative; border-radius: 8px; overflow: hidden; aspect-ratio: 1; background: var(--color-surface-alt); }
    .att img { width: 100%; height: 100%; object-fit: cover; cursor: pointer; display: block; }
    .att__del {
      position: absolute; top: 4px; right: 4px; width: 22px; height: 22px; border-radius: 50%;
      border: none; background: rgba(0,0,0,.55); color: #fff; cursor: pointer; line-height: 1; font-size: .8rem;
    }
    .td__upload { margin-top: var(--space-2); }

    /* Lightbox */
    .lb { position: fixed; inset: 0; z-index: 2000; background: rgba(0,0,0,.82);
          display: flex; align-items: center; justify-content: center; }
    .lb img { max-width: 92vw; max-height: 88vh; object-fit: contain; }
    .lb__close { position: absolute; top: 18px; right: 22px; background: none; border: none; color: #fff; font-size: 1.8rem; cursor: pointer; }
    .lb__nav { position: absolute; top: 50%; transform: translateY(-50%); background: rgba(255,255,255,.15); color: #fff;
               border: none; width: 44px; height: 64px; font-size: 2rem; cursor: pointer; }
    .lb__nav--prev { left: 12px; } .lb__nav--next { right: 12px; }
    .lb__counter { position: absolute; bottom: 18px; left: 50%; transform: translateX(-50%); color: #fff; font-size: .85rem; }
  `]
})
export class PrjTaskDetail {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();
  readonly task = input<ProjectTask | null>(null);
  readonly open = input(false);

  readonly closed = output<void>();
  readonly changed = output<void>();

  // Bản sao cục bộ của task để cập nhật ngay khi đổi status/assignee.
  readonly model = signal<ProjectTask | null>(null);
  readonly current = computed(() => this.model() ?? this.task());

  /** CHỈ thành viên dự án (không lấy toàn bộ nhân sự hệ thống). */
  readonly members = signal<ProjectMember[]>([]);
  readonly comments = signal<TaskComment[]>([]);
  readonly attachments = signal<TaskAttachment[]>([]);
  readonly activities = signal<TaskActivity[]>([]);

  readonly draft = signal('');
  readonly editingId = signal<string | null>(null);
  readonly editDraft = signal('');
  readonly busyStatus = signal(false);

  // Sub-tab đang xem.
  readonly tab = signal<SubTab>('info');

  // Theo dõi thời gian.
  readonly logHours = signal<number | null>(null);
  readonly busyLog = signal(false);

  // % giờ đã làm so với ước lượng (có thể > 100 nếu vượt).
  readonly hoursPct = computed(() => {
    const t = this.current();
    const est = t?.estimateHours || 0;
    const spent = t?.spentHours || 0;
    if (est <= 0) return spent > 0 ? 100 : 0;
    return Math.round((spent / est) * 100);
  });
  readonly hoursOver = computed(() => {
    const t = this.current();
    return !!t && (t.estimateHours || 0) > 0 && (t.spentHours || 0) > t.estimateHours;
  });

  readonly lightboxIndex = signal<number | null>(null);
  readonly lightboxItem = computed(() => {
    const i = this.lightboxIndex();
    return i === null ? null : (this.attachments()[i] ?? null);
  });

  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'Cần làm' },
    { value: 'IN_PROGRESS', label: 'Đang làm' }, { value: 'IN_REVIEW', label: 'Đang review' },
    { value: 'DONE', label: 'Hoàn thành' }
  ];
  readonly typeLabels: Record<TaskType, string> = {
    EPIC: 'Epic', STORY: 'Story', TASK: 'Task', SUBTASK: 'Subtask', BUG: 'Bug', ISSUE: 'Issue'
  };
  readonly priorityLabels: Record<TaskPriority, string> = {
    LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao', URGENT: 'Khẩn cấp'
  };
  readonly severityLabels: Record<BugSeverity, string> = {
    BLOCKER: 'Chặn', CRITICAL: 'Nguy kịch', MAJOR: 'Lớn', MINOR: 'Nhỏ', TRIVIAL: 'Không đáng kể'
  };
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Chặn' }, { value: 'CRITICAL', label: 'Nguy kịch' },
    { value: 'MAJOR', label: 'Lớn' }, { value: 'MINOR', label: 'Nhỏ' },
    { value: 'TRIVIAL', label: 'Không đáng kể' }
  ];
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));

  /** Task hiện tại có phải BUG/ISSUE không (hiển thị khối Chi tiết lỗi). */
  readonly isBug = computed(() => {
    const t = this.current();
    return !!t && (t.type === 'BUG' || t.type === 'ISSUE');
  });

  // ===== Sửa inline khối Chi tiết lỗi =====
  readonly editingBug = signal(false);
  readonly busyBug = signal(false);
  bugForm: {
    severity: BugSeverity | null; stepsToReproduce: string;
    expectedResult: string; actualResult: string; environment: string;
  } = { severity: null, stepsToReproduce: '', expectedResult: '', actualResult: '', environment: '' };

  readonly activityLabels: Record<TaskActivityAction, string> = {
    CREATED: 'Tạo task', STATUS: 'Đổi trạng thái', ASSIGN: 'Gán người',
    EDIT: 'Sửa', COMMENT: 'Bình luận', ATTACH: 'Đính kèm ảnh', SPENT: 'Ghi giờ làm'
  };
  readonly activityIcons: Record<TaskActivityAction, string> = {
    CREATED: '✨', STATUS: '🔄', ASSIGN: '👤', EDIT: '✏️', COMMENT: '💬', ATTACH: '📎', SPENT: '⏱️'
  };

  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members()));

  constructor() {
    // Khi task đầu vào đổi → reset bản sao cục bộ.
    effect(() => {
      const t = this.task();
      this.model.set(t);
    });

    // Khi mở (open=true & có task) → nạp comments + attachments + hoạt động; về tab Thông tin.
    effect(() => {
      const t = this.task();
      if (this.open() && t) { this.tab.set('info'); this.logHours.set(null); this.loadDetails(t.id); }
    });
  }

  private loadDetails(taskId: string): void {
    const pid = this.projectId();
    // Người thực hiện chỉ trong thành viên dự án.
    this.svc.listMembers(pid).subscribe({
      next: (m) => this.members.set(m),
      error: () => { /* bỏ qua */ }
    });
    this.svc.listComments(pid, taskId).subscribe({
      next: (c) => this.comments.set(c),
      error: () => this.comments.set([])
    });
    this.svc.listAttachments(pid, taskId).subscribe({
      next: (a) => this.attachments.set(a),
      error: () => this.attachments.set([])
    });
    this.loadActivity(taskId);
  }

  private loadActivity(taskId: string): void {
    this.svc.listActivity(this.projectId(), taskId).subscribe({
      next: (a) => this.activities.set(a),
      error: () => this.activities.set([])
    });
  }

  // ===== Trạng thái (segmented) =====
  setStatus(s: TaskStatus): void {
    const t = this.current();
    if (!t || t.status === s || this.busyStatus()) return;
    this.busyStatus.set(true);
    this.svc.updateTaskStatus(this.projectId(), t.id, s).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyStatus.set(false);
        this.toast.success('Đã đổi trạng thái', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyStatus.set(false); this.toast.error('Không đổi được trạng thái', e?.error?.message ?? ''); }
    });
  }

  // ===== Assignee =====
  assign(userId: string | null): void {
    const t = this.current();
    if (!t) return;
    this.svc.assignTask(this.projectId(), t.id, userId || null).subscribe({
      next: (u) => { this.model.set(u); this.toast.success('Đã gán người thực hiện', u.code); this.changed.emit(); this.loadActivity(u.id); },
      error: (e) => this.toast.error('Không gán được', e?.error?.message ?? '')
    });
  }

  // ===== Chi tiết lỗi (BUG/ISSUE) — sửa inline =====
  startEditBug(): void {
    const t = this.current();
    if (!t) return;
    this.bugForm = {
      severity: t.severity ?? null,
      stepsToReproduce: t.stepsToReproduce ?? '',
      expectedResult: t.expectedResult ?? '',
      actualResult: t.actualResult ?? '',
      environment: t.environment ?? ''
    };
    this.editingBug.set(true);
  }
  cancelEditBug(): void { this.editingBug.set(false); }

  saveBug(): void {
    const t = this.current();
    if (!t || this.busyBug()) return;
    this.busyBug.set(true);
    // PUT đầy đủ — giữ nguyên các trường hiện có của task, chỉ đổi 5 trường chi tiết lỗi.
    const body: TaskRequest = {
      parentId: t.parentId, title: t.title, description: t.description,
      type: t.type, status: t.status, priority: t.priority,
      assigneeUserId: t.assigneeUserId, estimateHours: t.estimateHours,
      startDate: t.startDate, dueDate: t.dueDate, orderIndex: t.orderIndex, screen: t.screen,
      severity: this.bugForm.severity,
      stepsToReproduce: this.bugForm.stepsToReproduce.trim() || null,
      expectedResult: this.bugForm.expectedResult.trim() || null,
      actualResult: this.bugForm.actualResult.trim() || null,
      environment: this.bugForm.environment.trim() || null
    };
    this.svc.updateTask(this.projectId(), t.id, body).subscribe({
      next: (u) => {
        this.model.set(u);
        this.busyBug.set(false);
        this.editingBug.set(false);
        this.toast.success('Đã cập nhật chi tiết lỗi', u.code);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyBug.set(false); this.toast.error('Không cập nhật được', e?.error?.message ?? ''); }
    });
  }

  /** Badge mức độ: Chặn/Nguy kịch = đỏ; Lớn = cam; Nhỏ/Không đáng kể = xám. */
  severityBadge(s: BugSeverity | null): string {
    switch (s) {
      case 'BLOCKER':
      case 'CRITICAL': return 'sev sev--red';
      case 'MAJOR': return 'sev sev--orange';
      default: return 'sev sev--gray';
    }
  }

  // ===== Theo dõi thời gian (log work) =====
  setTab(t: SubTab): void { this.tab.set(t); }

  onLogHoursInput(e: Event): void {
    const v = (e.target as HTMLInputElement).valueAsNumber;
    this.logHours.set(Number.isNaN(v) ? null : v);
  }

  submitLogWork(): void {
    const t = this.current();
    const h = this.logHours();
    if (!t || this.busyLog() || h === null || !(h > 0)) {
      if (h !== null && !(h > 0)) this.toast.error('Số giờ phải lớn hơn 0');
      return;
    }
    this.busyLog.set(true);
    this.svc.logWork(this.projectId(), t.id, h).subscribe({
      next: (u) => {
        this.model.set(u);
        this.logHours.set(null);
        this.busyLog.set(false);
        this.toast.success('Đã ghi nhận giờ làm', `+${h}h`);
        this.changed.emit();
        this.loadActivity(u.id);
      },
      error: (e) => { this.busyLog.set(false); this.toast.error('Không ghi được giờ làm', e?.error?.message ?? ''); }
    });
  }

  // ===== Bình luận =====
  submitComment(): void {
    const t = this.current();
    const body = this.draft().trim();
    if (!t || !body) return;
    this.svc.addComment(this.projectId(), t.id, body).subscribe({
      next: (c) => { this.comments.update((cs) => [...cs, c]); this.draft.set(''); this.loadActivity(t.id); },
      error: () => this.toast.error('Không gửi được bình luận')
    });
  }
  onCommentKey(e: KeyboardEvent): void {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); this.submitComment(); }
  }
  startEdit(c: TaskComment): void { this.editingId.set(c.id); this.editDraft.set(c.body); }
  cancelEdit(): void { this.editingId.set(null); this.editDraft.set(''); }
  saveEdit(c: TaskComment): void {
    const t = this.current();
    const body = this.editDraft().trim();
    if (!t || !body) return;
    this.svc.editComment(this.projectId(), t.id, c.id, body).subscribe({
      next: (u) => { this.comments.update((cs) => cs.map((x) => (x.id === c.id ? u : x))); this.cancelEdit(); },
      error: (e) => this.toast.error('Không sửa được', e?.error?.message ?? '')
    });
  }
  deleteComment(c: TaskComment): void {
    const t = this.current();
    if (!t || !window.confirm('Xoá bình luận này?')) return;
    this.svc.deleteComment(this.projectId(), t.id, c.id).subscribe({
      next: () => this.comments.update((cs) => cs.filter((x) => x.id !== c.id)),
      error: (e) => this.toast.error('Không xoá được', e?.error?.message ?? '')
    });
  }

  // ===== Ảnh đính kèm =====
  attUrl(a: TaskAttachment): string {
    const t = this.current();
    return t ? this.svc.attachmentUrl(this.projectId(), t.id, a.id) : a.url;
  }
  onUpload(e: Event): void {
    const t = this.current();
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!t || !file) return;
    this.svc.uploadAttachment(this.projectId(), t.id, file).subscribe({
      next: (a) => { this.attachments.update((xs) => [...xs, a]); input.value = ''; this.toast.success('Đã tải ảnh lên'); this.loadActivity(t.id); },
      error: (err) => { input.value = ''; this.toast.error('Không tải được ảnh', err?.error?.message ?? ''); }
    });
  }
  deleteAttachment(a: TaskAttachment): void {
    const t = this.current();
    if (!t || !window.confirm('Xoá ảnh này?')) return;
    this.svc.deleteAttachment(this.projectId(), t.id, a.id).subscribe({
      next: () => this.attachments.update((xs) => xs.filter((x) => x.id !== a.id)),
      error: (e) => this.toast.error('Không xoá được ảnh', e?.error?.message ?? '')
    });
  }

  // ===== Lightbox =====
  openLightbox(i: number): void { this.lightboxIndex.set(i); }
  closeLightbox(): void { this.lightboxIndex.set(null); }
  prevImg(): void {
    const n = this.attachments().length;
    this.lightboxIndex.update((i) => (i === null ? null : (i - 1 + n) % n));
  }
  nextImg(): void {
    const n = this.attachments().length;
    this.lightboxIndex.update((i) => (i === null ? null : (i + 1) % n));
  }

  // ===== Helpers =====
  typeBadge(t: TaskType): string {
    switch (t) {
      case 'BUG': return 'badge--cancel';
      case 'ISSUE': return 'badge--pending';
      case 'EPIC': return 'badge--active';
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
  fmt(iso: string | null): string {
    if (!iso) return '';
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
}
