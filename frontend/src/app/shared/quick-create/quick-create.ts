import { Component, HostListener, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Modal } from '../modal/modal';
import { BUG_DESCRIPTION_TEMPLATE } from '../bug-template';
import { SearchableSelect, SelectOption } from '../searchable-select/searchable-select';
import { buildParentOptions } from '../../projects/work-stats';
import { memberPersonOptions } from '../person-options';
import { ToastService } from '../toast/toast.service';
import { AuthService } from '../../core/auth.service';
import { MeBugService, QuickCreateType, QuickCreateRequest } from '../../core/me-bug.service';
import { QuickCreatePrefsService } from '../../core/quick-create-prefs';
import { ProjectService, ProjectMember, ProjectTask, TaskType, TaskPriority, BugSeverity } from '../../core/project.service';

/** Ảnh chờ đính kèm: file gốc + URL preview (blob) để hiển thị + thu hồi. */
interface PendingImage { file: File; url: string; name: string; }

/**
 * TẠO NHANH (từ toolbar) — form ĐẦY ĐỦ giống modal thêm task của backlog.
 * Chọn Dự án → nạp task (listTasks) để dựng CHA theo phân cấp + members (listMembers) cho người thực hiện / kiểm thử.
 * Đủ 6 loại (Epic/Story/Task/Sub-task/Bug/Issue) + chọn cha theo loại + est/deadline/assignee/tester/mô tả.
 * Submit → POST /api/v1/me/bugs (quickCreate). Toast + đóng.
 * Dùng: <app-quick-create [open]="o()" [presetType]="'BUG'" (closed)="…" (created)="…" />
 */
@Component({
  selector: 'app-quick-create',
  imports: [Modal, SearchableSelect],
  templateUrl: './quick-create.html',
  styles: [`
    .qc { display: grid; gap: var(--space-3); width: 100%; }
    .qc__seg { display: flex; gap: 6px; flex-wrap: wrap; }
    .qc__seg button {
      border: 1px solid var(--color-border); background: var(--color-surface);
      padding: 6px 14px; border-radius: 999px; font-size: .85rem; cursor: pointer; color: var(--color-text);
    }
    .qc__seg button.is-active { background: var(--color-primary); border-color: var(--color-primary); color: var(--color-text-invert); font-weight: 600; }
    .qc__row2 { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }
    @media (max-width: 620px) { .qc__row2 { grid-template-columns: 1fr; } }
    .field > label { display: block; font-size: .82rem; color: var(--color-text-muted); margin-bottom: 4px; }
    .field input, .field textarea {
      width: 100%; padding: 7px 9px; border: 1px solid var(--color-border); border-radius: 8px;
      background: var(--color-surface); color: var(--color-text); font: inherit; box-sizing: border-box;
    }
    .qc__hint { font-size: var(--font-size-xs, .75rem); color: var(--color-text-muted); margin-top: 4px; }
    .qc__atts { display: flex; flex-wrap: wrap; gap: 8px; }
    .qc__att { position: relative; width: 72px; height: 72px; border-radius: 8px; overflow: hidden; border: 1px solid var(--color-border); }
    .qc__att img { width: 100%; height: 100%; object-fit: cover; display: block; }
    .qc__att-del {
      position: absolute; top: 2px; right: 2px; width: 20px; height: 20px; padding: 0;
      border: none; border-radius: 50%; background: rgba(0,0,0,.6); color: #fff; font-size: .7rem;
      line-height: 20px; cursor: pointer; display: flex; align-items: center; justify-content: center;
    }
    .qc__att-add {
      width: 72px; height: 72px; border: 1px dashed var(--color-border); border-radius: 8px;
      display: flex; align-items: center; justify-content: center; font-size: 1.5rem;
      color: var(--color-text-muted); cursor: pointer; background: var(--color-surface);
    }
    .qc__att-add:hover { border-color: var(--color-primary); color: var(--color-primary); }
  `]
})
export class QuickCreate {
  private meBug = inject(MeBugService);
  private projectApi = inject(ProjectService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private prefs = inject(QuickCreatePrefsService);

  /** Cha muốn khôi phục từ lựa chọn đã nhớ — áp sau khi listTasks trả về (chỉ khi còn hợp lệ). */
  private pendingParentId: string | null = null;

  readonly open = input(false);
  /** Loại preset khi mở (nút ＋Task / ＋Bug). Vẫn cho đổi sang loại bất kỳ. */
  readonly presetType = input<QuickCreateType>('TASK');

  readonly closed = output<void>();
  readonly created = output<void>();

  readonly projects = signal<SelectOption[]>([]);
  readonly members = signal<ProjectMember[]>([]);
  /** Danh sách task của dự án đã chọn — nguồn dựng CHA theo phân cấp. */
  readonly tasks = signal<ProjectTask[]>([]);
  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members()));

  readonly type = signal<QuickCreateType>('TASK');
  readonly projectId = signal('');
  readonly parentId = signal('');
  readonly title = signal('');
  readonly description = signal('');
  readonly priority = signal<TaskPriority>('MEDIUM');
  readonly assigneeUserId = signal('');
  readonly testerUserId = signal('');
  readonly estimateHours = signal('');
  readonly startIso = signal('');
  readonly dueIso = signal('');
  // ===== Chi tiết lỗi (chỉ BUG/ISSUE) =====
  readonly severity = signal<BugSeverity | ''>('');
  readonly screen = signal('');
  readonly environment = signal('');
  readonly stepsToReproduce = signal('');
  readonly expectedResult = signal('');
  readonly actualResult = signal('');
  readonly saving = signal(false);
  /** Ảnh chờ đính kèm — upload sau khi tạo task thành công (nhận được id). */
  readonly previews = signal<PendingImage[]>([]);

  readonly typeOptions: { value: QuickCreateType; label: string }[] = [
    { value: 'EPIC', label: 'Epic' }, { value: 'STORY', label: 'Story' },
    { value: 'TASK', label: 'Task' }, { value: 'SUBTASK', label: 'Sub-task' },
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  readonly priorityOptions: { value: TaskPriority; label: string }[] = [
    { value: 'LOW', label: 'Thấp' }, { value: 'MEDIUM', label: 'Trung bình' },
    { value: 'HIGH', label: 'Cao' }, { value: 'URGENT', label: 'Khẩn cấp' }
  ];
  readonly prioritySel: SelectOption[] = this.priorityOptions.map((o) => ({ value: o.value, label: o.label }));
  /** Mức độ nghiêm trọng (BUG/ISSUE) — đồng bộ task-detail. */
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Chặn' }, { value: 'CRITICAL', label: 'Nguy kịch' },
    { value: 'MAJOR', label: 'Lớn' }, { value: 'MINOR', label: 'Nhỏ' },
    { value: 'TRIVIAL', label: 'Không đáng kể' }
  ];
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));
  /** Loại đang chọn là BUG/ISSUE → hiện khối "Chi tiết lỗi". */
  readonly isBugLike = computed<boolean>(() => this.type() === 'BUG' || this.type() === 'ISSUE');

  // ===== Phân cấp cha-con theo LOẠI (bám backlog) =====
  /** Loại cha HỢP LỆ cho một loại con (null = không có cha — chỉ EPIC). */
  private parentTypeOf(type: QuickCreateType | null | undefined): TaskType[] | null {
    switch (type) {
      case 'EPIC': return null;
      case 'STORY': return ['EPIC'];
      case 'TASK': return ['STORY', 'EPIC'];   // Task gắn dưới Story HOẶC thẳng lên Epic
      case 'SUBTASK': return ['TASK'];
      case 'BUG':
      case 'ISSUE': return ['TASK', 'SUBTASK'];
      default: return null;
    }
  }
  private typeLabel(t: QuickCreateType | TaskType): string {
    return this.typeOptions.find((o) => o.value === t)?.label ?? t;
  }
  /** Nhãn gọn loại cha yêu cầu (toast/hint). */
  parentTypeLabel(): string {
    const pt = this.parentTypeOf(this.type());
    return pt ? pt.map((t) => this.typeLabel(t)).join(' hoặc ') : '';
  }
  /** Loại đang chọn có cần cha không (khác EPIC). */
  needsParent = computed<boolean>(() => this.parentTypeOf(this.type()) !== null);

  /** Danh sách cha hợp lệ theo loại + dự án đang chọn. */
  parentOptions = computed<SelectOption[]>(() => {
    const allow = this.parentTypeOf(this.type());
    if (!allow) return [];
    return buildParentOptions(this.tasks(), this.tasks().filter((t) => allow.includes(t.type)));
  });

  /** Loại đang chọn là SUB-TASK → chặn est > 4h (UX sớm; BE cũng chặn). */
  readonly isSubtask = computed<boolean>(() => this.type() === 'SUBTASK');
  /** Epic/Story là cấp NHÓM — est của chúng tổng hợp từ con nên không áp trần 4h. */
  readonly isGroupType = computed<boolean>(() => this.type() === 'EPIC' || this.type() === 'STORY');

  constructor() {
    // Mỗi lần mở → reset form + nạp danh sách dự án; áp preset loại.
    // CHỈ phụ thuộc open()/presetType(); reset() có đọc+ghi previews() nên PHẢI bọc
    // untracked() để tránh effect tự phụ thuộc rồi lặp vô hạn (treo tab).
    effect(() => {
      const isOpen = this.open();
      const preset = this.presetType();
      if (!isOpen) return;
      untracked(() => {
        this.reset();
        this.type.set(preset);
        if (preset === 'BUG' || preset === 'ISSUE') this.description.set(BUG_DESCRIPTION_TEMPLATE);
        this.loadProjects();
        this.restorePrefs(); // nhớ dự án + task cha lần trước → user không phải chọn lại
      });
    });
  }

  /** Dán ảnh (Ctrl/Cmd+V) khi form Tạo nhanh đang mở → tự thêm vào ảnh chờ, upload sau khi tạo. */
  @HostListener('document:paste', ['$event'])
  onPaste(ev: ClipboardEvent): void {
    if (!this.open()) return;
    const items = ev.clipboardData?.items;
    if (!items) return;
    const add: PendingImage[] = [];
    for (const it of Array.from(items)) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const raw = it.getAsFile();
        if (!raw) continue;
        const ext = (it.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        const name = raw.name && raw.name !== 'image.png' ? raw.name : `screenshot-${Date.now()}.${ext}`;
        const file = raw.name && raw.name !== 'image.png' ? raw : new File([raw], name, { type: it.type });
        add.push({ file, url: URL.createObjectURL(file), name });
      }
    }
    if (add.length) {
      ev.preventDefault();
      this.previews.update((xs) => [...xs, ...add]);
      this.toast.success('Đã dán ảnh', `${add.length} ảnh`);
    }
  }

  /** Hôm nay dạng yyyy-MM-dd (local) cho <input type=date>. */
  private todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  private reset(): void {
    this.projectId.set('');
    this.parentId.set('');
    this.members.set([]);
    this.tasks.set([]);
    this.title.set('');
    this.description.set('');
    this.priority.set('MEDIUM');
    this.assigneeUserId.set('');
    this.testerUserId.set(this.auth.currentUser()?.userId ?? '');
    // Tạo nhanh: KHÔNG bắt buộc — mặc định est = 4 giờ, từ ngày & đến ngày = hôm nay (vẫn cho sửa).
    this.estimateHours.set('4');
    this.startIso.set(this.todayIso());
    this.dueIso.set(this.todayIso());
    this.severity.set('');
    this.screen.set('');
    this.environment.set('');
    this.stepsToReproduce.set('');
    this.expectedResult.set('');
    this.actualResult.set('');
    this.saving.set(false);
    this.clearImages();
  }

  /** Thu hồi mọi blob URL đang xem trước rồi xoá danh sách ảnh. */
  private clearImages(): void {
    for (const p of this.previews()) { URL.revokeObjectURL(p.url); }
    this.previews.set([]);
  }

  /** Chọn thêm ảnh (nhiều) — chỉ nhận image/*, dựng blob URL xem trước. */
  onFilesPicked(e: Event): void {
    const input = e.target as HTMLInputElement;
    const picked = Array.from(input.files ?? []).filter((f) => f.type.startsWith('image/'));
    if (picked.length) {
      const added = picked.map((file) => ({ file, url: URL.createObjectURL(file), name: file.name }));
      this.previews.update((xs) => [...xs, ...added]);
    }
    input.value = ''; // cho phép chọn lại cùng file
  }

  /** Bỏ 1 ảnh khỏi danh sách chờ + thu hồi blob URL. */
  removeFile(i: number): void {
    const list = this.previews();
    const p = list[i];
    if (p) { URL.revokeObjectURL(p.url); }
    this.previews.set(list.filter((_, idx) => idx !== i));
  }

  private loadProjects(): void {
    this.meBug.myProjects().subscribe({
      next: (ps) => this.projects.set(ps.map((p) => ({ value: p.id, label: p.code + ' · ' + p.name }))),
      error: () => this.projects.set([])
    });
  }

  /** Khôi phục dự án + task cha đã nhớ của user đăng nhập (bỏ qua nếu chưa từng tạo). */
  private restorePrefs(): void {
    const saved = this.prefs.load();
    if (!saved?.projectId) return;
    this.selectProject(saved.projectId, saved.parentByType[this.type()] ?? null);
  }

  /** Áp cha đã nhớ SAU khi có danh sách task — bỏ qua nếu task đã xoá hoặc sai loại cha. */
  private applyPendingParent(): void {
    const want = this.pendingParentId;
    this.pendingParentId = null;
    if (!want) return;
    const allow = this.parentTypeOf(this.type());
    if (!allow) return;
    if (this.tasks().some((t) => t.id === want && allow.includes(t.type))) this.parentId.set(want);
  }

  onProject(id: string): void { this.selectProject(id, null); }

  /** Chọn dự án + nạp members/tasks. wantParent != null → thử khôi phục cha đã nhớ. */
  private selectProject(id: string, wantParent: string | null): void {
    this.pendingParentId = wantParent;
    this.projectId.set(id || '');
    this.parentId.set('');
    this.assigneeUserId.set('');
    this.testerUserId.set(this.auth.currentUser()?.userId ?? '');
    this.members.set([]);
    this.tasks.set([]);
    if (!id) return;
    this.projectApi.listMembers(id).subscribe({
      next: (m) => this.members.set(m),
      error: () => this.members.set([])
    });
    this.projectApi.listTasks(id).subscribe({
      next: (r) => { this.tasks.set(r); this.applyPendingParent(); },
      error: () => { this.tasks.set([]); this.pendingParentId = null; }
    });
  }

  /** Đổi loại → EPIC bỏ cha; loại khác → reset cha nếu không còn hợp lệ. */
  setType(t: QuickCreateType): void {
    this.type.set(t);
    // Bug/Issue: điền sẵn khung Mô tả nếu đang trống; rời Bug mà Mô tả vẫn là khung mặc định → xoá.
    const nowBug = t === 'BUG' || t === 'ISSUE';
    if (nowBug && !this.description().trim()) this.description.set(BUG_DESCRIPTION_TEMPLATE);
    if (!nowBug && this.description() === BUG_DESCRIPTION_TEMPLATE) this.description.set('');
    const allow = this.parentTypeOf(t);
    if (!allow) { this.parentId.set(''); return; }
    const ok = this.tasks().some((tk) => tk.id === this.parentId() && allow.includes(tk.type));
    if (!ok) {
      // Cha hiện tại sai loại → thử cha đã nhớ RIÊNG cho loại vừa chọn (cùng dự án).
      this.parentId.set('');
      const saved = this.prefs.load();
      if (saved && saved.projectId === this.projectId()) {
        this.pendingParentId = saved.parentByType[t] ?? null;
        this.applyPendingParent();
      }
    }
    // Task tạo mới luôn là LÁ → est trần 4h cho mọi loại trừ Epic/Story (cấp nhóm).
    if (t !== 'EPIC' && t !== 'STORY' && Number(this.estimateHours()) > 4) this.estimateHours.set('4');
  }

  onParent(id: string): void { this.parentId.set(id || ''); }

  /** yyyy-MM-dd (từ input date) — dùng thẳng cho ISO dueDate. */
  onDue(e: Event): void { this.dueIso.set((e.target as HTMLInputElement).value); }

  submit(): void {
    if (this.saving()) return;
    if (!this.projectId()) { this.toast.warning('Chọn dự án'); return; }
    if (!this.title().trim()) { this.toast.warning('Nhập tiêu đề'); return; }

    // Validate CHA theo phân cấp: mọi loại KHÁC EPIC bắt buộc chọn cha đúng loại.
    const allow = this.parentTypeOf(this.type());
    if (allow) {
      const ok = this.parentId() && this.tasks().some((t) => t.id === this.parentId() && allow.includes(t.type));
      if (!ok) { this.toast.error(`Vui lòng chọn ${this.parentTypeLabel()} cha`); return; }
    }

    // Chặn est ≤ 4h — task tạo mới luôn là LÁ; Epic/Story là cấp nhóm nên bỏ qua.
    const est = this.estimateHours() ? Number(this.estimateHours()) : 0;
    if (this.type() !== 'EPIC' && this.type() !== 'STORY' && est > 4) {
      this.toast.error('Ước lượng không được quá 4 giờ', 'Hãy tách nhỏ công việc');
      return;
    }

    this.saving.set(true);
    const bug = this.isBugLike();
    const body: QuickCreateRequest = {
      projectId: this.projectId(),
      type: this.type(),
      title: this.title().trim(),
      description: this.description().trim() || null,
      priority: this.priority(),
      assigneeUserId: this.assigneeUserId() || null,
      testerUserId: this.testerUserId() || null,
      estimateHours: est || null,
      startDate: this.startIso() || null,
      dueDate: this.dueIso() || null,
      parentId: allow ? (this.parentId() || null) : null,
      // Chi tiết lỗi — chỉ gửi khi BUG/ISSUE (loại khác gửi null).
      severity: bug ? (this.severity() || null) : null,
      screen: bug ? (this.screen().trim() || null) : null,
      environment: bug ? (this.environment().trim() || null) : null,
      // Gộp Bước/Kết quả vào Mô tả → 3 trường này không dùng nữa.
      stepsToReproduce: null,
      expectedResult: null,
      actualResult: null
    };
    this.meBug.quickCreate(body).subscribe({
      next: (r) => {
        // Tạo OK → nhớ dự án + cha cho lần sau (theo user đăng nhập).
        this.prefs.save(this.type(), this.projectId(), body.parentId ?? null);
        const imgs = this.previews();
        const taskId = r.projectTaskId; // backend trả projectTaskId (id task dự án)
        // Không có ảnh → xong ngay.
        if (!imgs.length || !taskId || !r.projectId) {
          this.finishOk(imgs.length ? 'Đã tạo nhưng thiếu id để đính kèm ảnh' : undefined);
          return;
        }
        // Có ảnh → tạo xong mới upload từng ảnh vào task vừa tạo (projectTaskId + projectId).
        const uploads = imgs.map((p) =>
          this.projectApi.uploadAttachment(r.projectId!, taskId, p.file).pipe(
            catchError(() => of(null)) // 1 ảnh lỗi không chặn các ảnh khác
          )
        );
        forkJoin(uploads).subscribe((res) => {
          const failed = res.filter((x) => x === null).length;
          this.finishOk(failed ? `Tạo xong; ${failed}/${imgs.length} ảnh tải lỗi` : undefined);
        });
      },
      error: (e) => { this.saving.set(false); this.toast.error('Không tạo được', e?.error?.message ?? ''); }
    });
  }

  /** Đóng modal sau khi tạo (có/không kèm ảnh). warn có giá trị → cảnh báo mềm thay vì success. */
  private finishOk(warn?: string): void {
    this.saving.set(false);
    if (warn) { this.toast.warning(warn); }
    else { this.toast.success('Đã tạo', 'Thành công'); }
    this.created.emit();
    this.closed.emit();
  }
}
