import { Component, HostListener, OnInit, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { TypeFilter } from '../../shared/type-filter/type-filter';
import { memberPersonOptions } from '../../shared/person-options';
import { todayIso } from '../../shared/format';
import { buildParentOptions } from '../work-stats';
import { BUG_DESCRIPTION_TEMPLATE, mergeBugFieldsIntoDescription } from '../../shared/bug-template';
import { forkJoin, of } from 'rxjs';
import { ToastService } from '../../shared/toast/toast.service';
import { AuthService } from '../../core/auth.service';
import { PrjTaskDetail } from '../task-detail/task-detail';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ImageLightbox, LightboxItem } from '../../shared/image-lightbox/image-lightbox';
import { HoursInput } from '../../shared/hours-input/hours-input';
import { workRoleForTransition } from '../work-stats';
import {
  ProjectService, ProjectTask, TaskRequest, TaskType, TaskStatus, TaskPriority, BugSeverity, ProjectMember,
  WorkEntry, WorkRole
} from '../../core/project.service';
import { WorkEntryDialog } from '../work-entry/work-entry-dialog';
import { DescEditor, DescShot, stripShotMarkers } from '../desc-editor/desc-editor';

/**
 * TAB Quản lý Bug/Issue. Bug/Issue gắn TRỰC TIẾP vào task cha (parentId).
 * Lọc listTasks lấy type ∈ {BUG, ISSUE}. Lưới + bộ lọc (status/type) + báo lỗi (modal).
 * Bấm 1 bug → mở <app-prj-task-detail> (Jira style).
 */
@Component({
  selector: 'app-prj-bugs',
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, SearchableSelect, PrjTaskDetail, EmployeeChip, TypeFilter,
    WorkEntryDialog, ImageLightbox, DescEditor, HoursInput],
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
    .bug-search { min-width: 230px; height: var(--control-h-sm); padding: 0 var(--space-3);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); font: inherit; }
    /* Khi đang lọc thì hiện thêm "/ tổng cả dự án" để biết mình đang xem một phần. */
    .bug-stats__all { font-style: normal; font-weight: 400; opacity: .65; }
    .field-hint { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px; }
    .bug-req { color: var(--overdue, #e5484d); }
    /* Khối ghi công kiểm thử — span cả 2 cột của .form-2col để không phá lưới. */
    .bug-work { grid-column: 1 / -1; display: grid; gap: var(--space-2); padding: 12px;
      border-radius: 10px; background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .bug-work__head { font-size: .9rem; font-weight: var(--weight-semibold); }
    .bug-work__row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); align-items: end; }
    @media (max-width: 560px) { .bug-work__row { grid-template-columns: 1fr; } }
    .bug-work__hint { margin: 0; font-size: var(--text-xs); color: var(--color-text-muted); line-height: 1.5; }
    .bug-work__hint b { color: var(--color-text); }
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
  /** Tăng khi task được sửa ở popup chi tiết → tải lại DỮ LIỆU mà không dựng lại component,
   *  nhờ vậy bộ lọc / nhóm đang gập / trang / vị trí cuộn giữ nguyên như trước khi mở popup. */
  readonly refreshKey = input(0);

  readonly tasks = signal<ProjectTask[]>([]);
  readonly loading = signal(true);
  /** CHỈ thành viên dự án (không phải toàn bộ nhân sự hệ thống) — gán bug đúng người trong dự án. */
  readonly members = signal<ProjectMember[]>([]);
  /** Ảnh đính kèm chờ tải lên (queue khi báo lỗi, upload sau khi tạo task). */
  readonly queuedFiles = signal<{ file: File; url: string }[]>([]);
  /** Ô Mô tả có ảnh hiện thẳng trong dòng chữ — cần tham chiếu để chèn ảnh tại con trỏ. */
  private readonly descEditor = viewChild(DescEditor);
  /**
   * Ảnh ĐÃ ĐÍNH KÈM của bug đang sửa (rỗng khi báo lỗi mới hoặc chép).
   *
   * Bắt buộc phải biết danh sách này: đánh dấu "[Ảnh n]" đánh số theo THỨ TỰ ĐÍNH KÈM của
   * task. Bug đang có 3 ảnh mà ảnh mới thêm lại được đánh số 1 thì mô tả có hai "[Ảnh 1]",
   * cả hai cùng trỏ về ảnh cũ, còn ảnh vừa thêm không bao giờ hiện.
   */
  readonly existingShots = signal<DescShot[]>([]);

  /** Ảnh đang xem to (index trong danh sách gộp cũ + mới); null = đóng. */
  readonly shotIndex = signal<number | null>(null);
  /** Ảnh cấp cho ô Mô tả: ảnh cũ giữ nguyên số, ảnh mới đánh tiếp phía sau. */
  readonly descShots = computed<DescShot[]>(() => {
    const old = this.existingShots();
    return [...old, ...this.queuedFiles().map((q, i) => ({ no: old.length + i + 1, url: q.url }))];
  });
  readonly shots = computed<LightboxItem[]>(() =>
    this.descShots().map((s) => ({ url: s.url, name: `Ảnh ${s.no}` })));

  /** Đưa ảnh vào hàng chờ upload, trả về số thứ tự + URL để chèn vào mô tả. */
  private queueImages(files: File[]): DescShot[] {
    const from = this.existingShots().length + this.queuedFiles().length;
    const add = files.map((file) => ({ file, url: URL.createObjectURL(file) }));
    this.queuedFiles.update((q) => [...q, ...add]);
    return add.map((a, i) => ({ no: from + i + 1, url: a.url }));
  }

  /** Nạp ảnh đã đính kèm của bug đang sửa, để đánh số ảnh mới tiếp nối chứ không đè lên. */
  private loadExistingShots(taskId: string): void {
    this.existingShots.set([]);
    this.svc.listAttachments(this.projectId(), taskId).subscribe({
      next: (list) => this.existingShots.set(
        list.filter((a) => !a.commentId)
            .map((a, i) => ({ no: i + 1, url: this.svc.attachmentUrl(this.projectId(), taskId, a.id) }))),
      error: () => this.existingShots.set([])
    });
  }

  /** Dán ảnh khi con trỏ ĐANG Ở ô Mô tả → ảnh hiện ngay tại chỗ đang gõ. */
  onDescPaste(files: File[]): void {
    const shots = this.queueImages(files);
    this.descEditor()?.insertShots(shots);
    this.toast.success('Đã dán ảnh', `${shots.length} ảnh — đã chèn vào Mô tả.`);
  }

  /**
   * Lưới — cần tham chiếu vì nút xuất Excel nay nằm ở thanh lọc (cùng hàng với Báo lỗi),
   * còn logic xuất vẫn thuộc data-grid để mọi màn xuất ra cùng một định dạng.
   */
  private readonly grid = viewChild<DataGrid>('grid');
  exportExcel(): void { void this.grid()?.exportExcel(); }

  /** Bấm ảnh trong mô tả → xem to (dùng chung lightbox với dải ảnh đính kèm). */
  onDescShotClick(no: number): void {
    if (no >= 1 && no <= this.descShots().length) this.shotIndex.set(no - 1);
  }

  // ----- Bộ lọc (chip đa chọn — signal để computed lọc CHẠY LẠI khi đổi; đồng bộ với Kanban/Log) -----
  readonly STATUS_KEYS = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE', 'CANCELLED'];
  readonly TYPE_KEYS = ['BUG', 'ISSUE'];
  readonly PRIORITY_KEYS = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'];
  readonly SEVERITY_KEYS = ['BLOCKER', 'CRITICAL', 'MAJOR', 'MINOR', 'TRIVIAL'];
  readonly statusFilter = signal<Set<string>>(new Set(this.STATUS_KEYS));
  readonly typeFilter = signal<Set<string>>(new Set(this.TYPE_KEYS));
  readonly priorityFilter = signal<Set<string>>(new Set(this.PRIORITY_KEYS));
  /** Mức độ nghiêm trọng — lỗi CHƯA đánh giá (severity rỗng) gom vào khoá "NONE". */
  readonly severityFilter = signal<Set<string>>(new Set([...this.SEVERITY_KEYS, 'NONE']));
  /** Tìm theo MÃ hoặc TIÊU ĐỀ — tester hay được báo "lỗi #319" và cần nhảy thẳng tới. */
  readonly search = signal('');
  /** Lọc theo người thực hiện (dev đang sửa) và người kiểm thử. Rỗng = tất cả. */
  readonly assigneeFilter = signal('');
  readonly testerFilter = signal('');

  /** Bật/tắt một chip trong bộ lọc; bỏ hết thì tự bật lại tất cả (tránh lưới trống khó hiểu). */
  private toggleIn(sig: ReturnType<typeof signal<Set<string>>>, all: string[], v: string): void {
    const s = new Set(sig());
    s.has(v) ? s.delete(v) : s.add(v);
    if (s.size === 0) all.forEach((x) => s.add(x));
    sig.set(s);
  }
  toggleStatus(v: string): void { this.toggleIn(this.statusFilter, this.STATUS_KEYS, v); }
  toggleType(v: string): void { this.toggleIn(this.typeFilter, this.TYPE_KEYS, v); }
  togglePriority(v: string): void { this.toggleIn(this.priorityFilter, this.PRIORITY_KEYS, v); }
  toggleSeverity(v: string): void { this.toggleIn(this.severityFilter, [...this.SEVERITY_KEYS, 'NONE'], v); }

  readonly typeOptions: { value: TaskType; label: string }[] = [
    { value: 'BUG', label: 'Bug' }, { value: 'ISSUE', label: 'Issue' }
  ];
  readonly statusOptions: { value: TaskStatus; label: string }[] = [
    { value: 'BACKLOG', label: 'Backlog' }, { value: 'TODO', label: 'To Do' },
    { value: 'IN_PROGRESS', label: 'In Progress' }, { value: 'IN_REVIEW', label: 'Testing' },
    { value: 'DONE', label: 'Done' }, { value: 'CANCELLED', label: 'Cancelled' }
  ];
  readonly priorityOptions: { value: TaskPriority; label: string }[] = [
    { value: 'LOW', label: 'Low' }, { value: 'MEDIUM', label: 'Medium' },
    { value: 'HIGH', label: 'High' }, { value: 'URGENT', label: 'Urgent' }
  ];
  readonly severityOptions: { value: BugSeverity; label: string }[] = [
    { value: 'BLOCKER', label: 'Blocker' }, { value: 'CRITICAL', label: 'Critical' },
    { value: 'MAJOR', label: 'Major' }, { value: 'MINOR', label: 'Minor' },
    { value: 'TRIVIAL', label: 'Trivial' }
  ];

  /** Chip lọc mức độ — thêm "Chưa đánh giá" để soi riêng nhóm lỗi log vội, còn thiếu thông tin. */
  readonly severityFilterOptions: { value: string; label: string }[] = [
    ...this.severityOptions.map((o) => ({ value: o.value as string, label: o.label })),
    { value: 'NONE', label: 'Chưa đánh giá' }
  ];

  readonly typeSel: SelectOption[] = this.typeOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly statusSel: SelectOption[] = this.statusOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly prioritySel: SelectOption[] = this.priorityOptions.map((o) => ({ value: o.value, label: o.label }));
  readonly severitySel: SelectOption[] = this.severityOptions.map((o) => ({ value: o.value, label: o.label }));
  /** Bỏ người đã tạm ngưng khỏi danh sách gán việc; giữ lại người đang được chọn trong form. */
  readonly peopleSel = computed<SelectOption[]>(() => memberPersonOptions(this.members(), this.f.assigneeUserId ?? null));

  // Cột Tiêu đề CỐ Ý không đặt width — data-grid coi cột đầu tiên không có width là cột co
  // giãn và cho nó nuốt hết chỗ thừa. Các cột còn lại siết sát nội dung thật (đều là nhãn
  // ngắn hoặc con số), trước đây rộng dư gần 140px lấy mất chỗ của tiêu đề.
  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '80px', sortable: true },
    { key: 'type', header: 'Loại', width: '84px' },
    { key: 'title', header: 'Tiêu đề', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '132px' },
    { key: 'priority', header: 'Ưu tiên', width: '96px' },
    { key: 'severity', header: 'Mức độ', width: '116px' },
    { key: 'assignee', header: 'Người thực hiện', width: '190px' },
    { key: 'progress', header: '%', width: '96px' },
    { key: 'est', header: 'Est', width: '64px', align: 'right' },
    { key: 'act', header: '', width: '70px', align: 'center' }
  ];

  /**
   * Toàn bộ bug/issue (chưa lọc), MỚI LOG NHẤT LÊN ĐẦU.
   * Ưu tiên createdAt; bản ghi cũ thiếu createdAt thì lùi về seq (seq tăng dần theo thứ tự tạo).
   */
  readonly bugs = computed<ProjectTask[]>(() =>
    this.tasks()
      .filter((t) => t.type === 'BUG' || t.type === 'ISSUE')
      .slice()
      .sort((a, b) => {
        const ta = a.createdAt ? Date.parse(a.createdAt) : NaN;
        const tb = b.createdAt ? Date.parse(b.createdAt) : NaN;
        if (!isNaN(ta) && !isNaN(tb) && ta !== tb) return tb - ta;
        return b.seq - a.seq;
      }));

  /** Mọi task — để gắn bug vào task cha (parentId). Badge loại + chuỗi cha (đồng bộ Backlog/Tạo nhanh). */
  readonly parentSel = computed<SelectOption[]>(() => buildParentOptions(this.tasks(), this.tasks()));

  readonly filtered = computed<ProjectTask[]>(() => {
    const st = this.statusFilter();
    const ty = this.typeFilter();
    const pr = this.priorityFilter();
    const sv = this.severityFilter();
    const asg = this.assigneeFilter();
    const tst = this.testerFilter();
    const q = this.search().trim().toLowerCase();
    return this.bugs().filter((t) =>
      (st.size >= this.STATUS_KEYS.length || st.has(t.status)) &&
      (ty.size >= this.TYPE_KEYS.length || ty.has(t.type)) &&
      (pr.size >= this.PRIORITY_KEYS.length || pr.has(t.priority)) &&
      // Chưa đánh giá mức độ → khoá "NONE", để lọc riêng nhóm lỗi còn thiếu thông tin.
      (sv.size > this.SEVERITY_KEYS.length || sv.has(t.severity || 'NONE')) &&
      (!asg || t.assigneeUserId === asg) &&
      (!tst || t.testerUserId === tst) &&
      // Tìm cả TÊN NGƯỜI: ô tìm kiếm sẵn có của lưới (quét mọi cột) đã bị tắt vì trùng ô này,
      // nếu chỉ khớp mã/tiêu đề thì ai quen gõ tên người vào sẽ không ra kết quả nào.
      (!q || (t.code || '').toLowerCase().includes(q)
          || (t.title || '').toLowerCase().includes(q)
          || (t.assigneeName || '').toLowerCase().includes(q)
          || (t.testerName || '').toLowerCase().includes(q)
          || (t.reporterName || '').toLowerCase().includes(q)));
  });

  /** Có đang lọc gì không — để nút "Đặt lại" tự hiện/ẩn thay vì lúc nào cũng chiếm chỗ. */
  readonly hasFilter = computed<boolean>(() =>
    !!this.search().trim() || !!this.assigneeFilter() || !!this.testerFilter()
    || this.statusFilter().size < this.STATUS_KEYS.length
    || this.typeFilter().size < this.TYPE_KEYS.length
    || this.priorityFilter().size < this.PRIORITY_KEYS.length
    || this.severityFilter().size <= this.SEVERITY_KEYS.length);

  /**
   * Thống kê tính trên phần ĐANG LỌC, không phải toàn bộ. Nếu để tổng cố định thì khi lọc
   * "Blocker của Linh" con số trên đầu vẫn là tổng cả dự án, đọc rất dễ hiểu nhầm.
   */
  /**
   * "Đang mở" = còn phải xử lý. Lỗi ĐÃ HUỶ nằm ngoài phạm vi nên KHÔNG tính vào đây — trước đây
   * đếm mọi thứ khác DONE nên bug huỷ vẫn bị coi là việc đang mở, con số này luôn cao hơn thực tế.
   * Đếm huỷ riêng để tổng vẫn cộng đủ: total = open + done + cancelled.
   */
  readonly stats = computed(() => {
    const b = this.filtered();
    const done = b.filter((t) => t.status === 'DONE').length;
    const cancelled = b.filter((t) => t.status === 'CANCELLED').length;
    return { total: b.length, open: b.length - done - cancelled, done, cancelled, all: this.bugs().length };
  });

  // ----- Modal báo lỗi / sửa lỗi -----
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  /** id bug đang sửa; null = đang tạo mới. */
  readonly editingId = signal<string | null>(null);
  /** Mã lỗi nguồn khi đang COPY (null = báo lỗi mới / sửa) — để nhắc người dùng đang chép từ đâu. */
  readonly copiedFrom = signal<string | null>(null);
  readonly modalTitle = computed(() => {
    if (this.editingId()) return 'Sửa lỗi';
    const src = this.copiedFrom();
    return src ? `Copy lỗi từ ${src}` : 'Báo lỗi';
  });
  f: TaskRequest = this.emptyForm();
  /**
   * GIỜ KIỂM THỬ tuỳ chọn khi báo lỗi — thời gian tìm ra + viết lại RIÊNG lỗi này.
   * KHÔNG phải cả buổi test: tester log tới 20+ lỗi/ngày, nhập giờ cả buổi vào từng lỗi
   * sẽ cộng trùng thành hàng chục giờ trong một ngày. Ghi vai TEST cho người đang log.
   */
  readonly logHours = signal<string>('');
  readonly logDate = signal<string>(todayIso());

  // ----- Chi tiết task (Jira) -----
  readonly detailOpen = signal(false);
  readonly detailTask = signal<ProjectTask | null>(null);

  constructor() {
    effect(() => {
      const id = this.projectId();
      this.refreshKey();
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
   * Dán ảnh khi con trỏ Ở NGOÀI ô Mô tả (đang ở ô Tiêu đề, hoặc chưa bấm vào đâu).
   * Dán ngay trong ô Mô tả thì ô đó tự xử lý và chặn sự kiện, nên không chạy vào đây.
   * Ở đây không biết chèn vào chỗ nào nên nối ảnh xuống CUỐI mô tả.
   */
  @HostListener('document:paste', ['$event'])
  onPaste(ev: ClipboardEvent): void {
    if (!this.modalOpen()) return; // chỉ khi form báo/sửa lỗi đang mở
    const items = ev.clipboardData?.items;
    if (!items) return;
    const files: File[] = [];
    for (const it of Array.from(items)) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const raw = it.getAsFile();
        if (!raw) continue;
        const ext = (it.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        // Ảnh clipboard thường không có tên → đặt tên gợi nhớ theo thời điểm.
        files.push(raw.name && raw.name !== 'image.png'
          ? raw
          : new File([raw], `screenshot-${Date.now()}.${ext}`, { type: it.type }));
      }
    }
    if (files.length) {
      ev.preventDefault();
      const shots = this.queueImages(files);
      const cur = this.f.description ?? '';
      const tail = shots.map((s) => `[Ảnh ${s.no}]`).join('\n');
      this.f.description = cur ? `${cur}\n${tail}` : tail;
      this.toast.success('Đã dán ảnh', `${shots.length} ảnh — đã thêm vào cuối Mô tả.`);
    }
  }
  removeQueued(i: number): void {
    this.queuedFiles.update((q) => {
      const item = q[i];
      if (item) URL.revokeObjectURL(item.url);
      return q.filter((_, idx) => idx !== i);
    });
  }
  /**
   * Dọn ảnh của lần mở form trước. Xoá CẢ ảnh cũ đã nạp, nếu không thì mở Sửa bug A rồi
   * bấm Báo lỗi mới sẽ vẫn còn ảnh của A, và ảnh mới bị đánh số tiếp nối sai.
   */
  private clearQueued(): void {
    for (const q of this.queuedFiles()) URL.revokeObjectURL(q.url);
    this.queuedFiles.set([]);
    this.existingShots.set([]);
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
    this.priorityFilter.set(new Set(this.PRIORITY_KEYS));
    this.severityFilter.set(new Set([...this.SEVERITY_KEYS, 'NONE']));
    this.search.set('');
    this.assigneeFilter.set('');
    this.testerFilter.set('');
  }

  // ----- Modal -----
  openReport(): void {
    this.editingId.set(null);
    this.copiedFrom.set(null);
    this.f = this.emptyForm();
    this.logHours.set('');
    this.logDate.set(todayIso());
    this.clearQueued();
    this.modalOpen.set(true);
  }

  /** Mở modal ở chế độ SỬA — đổ dữ liệu bug vào form. */
  openEdit(t: ProjectTask): void {
    this.editingId.set(t.id);
    this.copiedFrom.set(null);
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
    // Dọn ảnh còn sót của lần mở form trước, rồi nạp ảnh CŨ để ảnh thêm mới đánh số tiếp nối.
    this.clearQueued();
    this.loadExistingShots(t.id);
    this.modalOpen.set(true);
  }

  /**
   * COPY — mở modal TẠO MỚI với dữ liệu chép từ một lỗi có sẵn (lỗi lặp lại ở màn khác,
   * lỗi tương tự trên nhiều môi trường… đỡ phải gõ lại mô tả/các bước).
   * GIỮ: task cha, loại, mô tả, mức độ, ưu tiên, môi trường, người thực hiện/kiểm thử, est.
   * ĐẶT LẠI: tiêu đề thêm "(Copy)", trạng thái về Backlog, ngày để trống — vì đây là lỗi MỚI.
   * Ảnh đính kèm KHÔNG chép (dán/chọn ảnh mới nếu cần).
   */
  openCopy(t: ProjectTask): void {
    this.editingId.set(null);
    this.copiedFrom.set(t.code);
    this.f = {
      parentId: t.parentId,
      title: `${t.title} (Copy)`,
      // GỠ đánh dấu "[Ảnh n]" vì ảnh KHÔNG được chép sang. Giữ lại thì đánh dấu mồ côi vẫn
      // nằm trong mô tả, đến khi thêm ảnh mới (cũng được đánh số 1) là trùng số — một ảnh
      // hiện ra ở hai chỗ.
      description: stripShotMarkers(mergeBugFieldsIntoDescription(t)),
      type: t.type, status: 'BACKLOG', priority: t.priority,
      assigneeUserId: t.assigneeUserId,
      testerUserId: t.testerUserId ?? this.auth.currentUser()?.userId ?? null,
      // Lỗi CHÉP ra là task MỚI nên phải theo trần 4h hiện hành; task nguồn có thể mang
      // est cũ (vd 8h) từ trước khi có trần, chép nguyên sẽ bị backend từ chối lúc lưu.
      estimateHours: Math.min(t.estimateHours || 1, 4),
      startDate: null, dueDate: null,
      severity: t.severity, stepsToReproduce: '', expectedResult: '', actualResult: '',
      environment: t.environment ?? ''
    };
    this.clearQueued();
    this.modalOpen.set(true);
  }

  save(): void {
    if (!this.f.title?.trim()) { this.toast.warning('Thiếu tiêu đề lỗi'); return; }
    if (!this.f.parentId) { this.toast.warning('Bắt buộc chọn task cha'); return; }
    const newBug = !this.editingId();
    const h = Number(this.logHours());
    // Báo lỗi MỚI bắt buộc có giờ tìm ra lỗi (backend cũng chặn) — chặn sớm để báo rõ ràng.
    if (newBug && (!h || h <= 0)) { this.toast.warning('Nhập số giờ đã bỏ ra để tìm & ghi nhận lỗi'); return; }
    if (newBug && h > 4) { this.toast.warning('Mỗi lần ghi giờ không quá 4h'); return; }
    this.saving.set(true);
    const body: TaskRequest = {
      ...this.f,
      title: this.f.title.trim(),
      parentId: this.f.parentId,
      // Giờ tìm lỗi gửi NGAY trong lệnh tạo — không gọi API thứ hai để khỏi mất giờ khi lệnh sau lỗi.
      testHours: newBug ? h : null,
      workDate: newBug ? this.logDate() : null,
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

  // ===== Nhập giờ tại mốc bàn giao (Kiểm thử / Hoàn thành) =====
  readonly workOpen = signal(false);
  readonly workRole = signal<WorkRole>('DEV');
  readonly workTitle = signal('');
  /** Chuyển sang Hoàn thành mà task chưa có hạn → ngày nhập sẽ thành hạn hoàn thành. */
  readonly workFillsDue = signal(false);
  private pendingMove: { task: ProjectTask; status: TaskStatus } | null = null;

  // ----- Đổi status nhanh trên lưới -----
  changeStatus(t: ProjectTask, status: string): void {
    if (!status || status === t.status) return;
    const next = status as TaskStatus;
    const role = workRoleForTransition(t, next);
    if (role) {
      this.pendingMove = { task: t, status: next };
      this.workRole.set(role);
      this.workTitle.set(t.title);
      this.workFillsDue.set(next === 'DONE' && !t.dueDate);
      this.workOpen.set(true);
      return;
    }
    this.applyStatus(t, next);
  }
  onWorkConfirmed(w: WorkEntry): void {
    const mv = this.pendingMove;
    this.pendingMove = null;
    this.workOpen.set(false);
    if (mv) this.applyStatus(mv.task, mv.status, w);
  }
  onWorkCancelled(): void {
    this.pendingMove = null;
    this.workOpen.set(false);
    this.reload(); // trả ô chọn về trạng thái cũ
  }

  private applyStatus(t: ProjectTask, status: TaskStatus, work?: WorkEntry): void {
    this.svc.updateTaskStatus(this.projectId(), t.id, status, work).subscribe({
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
  /** Badge mức độ: Blocker/Critical = đỏ; Major = cam; Minor/Trivial = xám. */
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
      // Log nhanh: est mặc định 1 giờ (vẫn cho sửa). Đa số lỗi log ra là việc nhỏ, để 4h
      // thì người log ngại sửa xuống, ước lượng cả dự án bị thổi phồng.
      // Bug/Issue không có ô ngày → để trống.
      priority: 'MEDIUM', assigneeUserId: null, estimateHours: 1,
      startDate: null, dueDate: null,
      // Người kiểm thử MẶC ĐỊNH = người log/tạo (chính mình) — vẫn cho chỉnh lại.
      testerUserId: this.auth.currentUser()?.userId ?? null,
      severity: null, stepsToReproduce: '', expectedResult: '', actualResult: '', environment: ''
    };
  }
}
