import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ToastService } from '../../shared/toast/toast.service';
import { ProjectService, ProjectMember, DiaryEntry, DiaryRequest, DiaryAction, DiaryActionStatus } from '../../core/project.service';

/** Phân loại buổi làm việc — value LƯU nguyên chuỗi (tự do phía backend); badge màu theo token. */
interface Category { value: string; badge: string; }

const CATEGORIES: Category[] = [
  { value: 'Demo', badge: 'dg-cat--demo' },
  { value: 'Khảo sát', badge: 'dg-cat--survey' },
  { value: 'UAT', badge: 'dg-cat--uat' },
  { value: 'Nghiệm thu', badge: 'dg-cat--accept' },
  { value: 'Đào tạo', badge: 'dg-cat--train' },
  { value: 'Họp-trao đổi', badge: 'dg-cat--meet' },
  { value: 'Hỗ trợ', badge: 'dg-cat--support' },
  { value: 'Khác', badge: 'dg-cat--other' }
];

/**
 * Tab "Nhật ký dự án" (selector app-prj-diary) — GHI TAY các buổi làm việc với KHÁCH HÀNG
 * (khác tab Log tự động sinh từ hoạt động task).
 * Load thành viên (chọn team) + danh sách nhật ký (mới → cũ). CRUD qua modal.
 */
@Component({
  selector: 'app-prj-diary',
  standalone: true,
  imports: [FormsModule, Modal, SearchableSelect, EmployeeChip],
  templateUrl: './diary.html',
  styles: [`
    .dg { display: grid; gap: var(--space-4); color: var(--color-text); }
    .dg__head { display: flex; align-items: center; justify-content: space-between; gap: var(--space-3); flex-wrap: wrap; }
    .dg__head-actions { display: flex; gap: var(--space-3); align-items: center; flex-wrap: wrap; }
    .dg__search { height: var(--control-h-sm); min-width: 260px; padding: 0 var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text); font-size: var(--text-sm); }
    .dg__count { font-size: var(--text-xs); color: var(--color-text-muted);
      background: var(--color-surface-alt); padding: 1px var(--space-2); border-radius: var(--radius-full); }

    .dg__list { display: grid; gap: var(--space-3); }
    .dg__card { border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); padding: var(--space-3) var(--space-4); display: grid; gap: var(--space-3); }
    .dg__card:hover { border-color: var(--color-primary); }
    .dg__row1 { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .dg__date { font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; }
    .dg__spacer { flex: 1 1 auto; }

    .dg-cat { font-size: var(--text-xs); font-weight: var(--weight-semibold);
      padding: 2px var(--space-2); border-radius: var(--radius-full); border: 1px solid transparent; }
    .dg-cat--demo    { color: var(--color-primary); background: var(--color-primary-soft); }
    .dg-cat--survey  { color: var(--color-info, var(--color-primary)); background: var(--color-info-soft, var(--color-primary-soft)); }
    .dg-cat--uat     { color: var(--color-warning, var(--color-text)); background: var(--color-warning-soft, var(--color-surface-alt)); }
    .dg-cat--accept  { color: var(--color-success, var(--color-primary)); background: var(--color-success-soft, var(--color-primary-soft)); }
    .dg-cat--train   { color: var(--color-primary); background: var(--color-surface-alt); }
    .dg-cat--meet    { color: var(--color-text); background: var(--color-surface-alt); }
    .dg-cat--support { color: var(--color-info, var(--color-primary)); background: var(--color-surface-alt); }
    .dg-cat--other   { color: var(--color-text-muted); background: var(--color-surface-alt); }

    .dg__team { display: flex; gap: var(--space-2); flex-wrap: wrap; align-items: center; }
    .dg__meta { display: grid; gap: 4px; font-size: var(--text-sm); }
    .dg__label { font-size: var(--text-xs); color: var(--color-text-muted); font-weight: var(--weight-semibold); }
    .dg__text { white-space: pre-wrap; overflow-wrap: anywhere; }
    .dg__text--clamp { display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical;
      overflow: hidden; }
    .dg__more { align-self: flex-start; background: none; border: none; padding: 2px 0; cursor: pointer;
      color: var(--color-primary); font-size: var(--text-xs); font-weight: var(--weight-semibold); }
    .dg__more:hover { text-decoration: underline; }
    .dg__foot { display: flex; align-items: center; gap: var(--space-2); font-size: var(--text-xs);
      color: var(--color-text-muted); border-top: 1px dashed var(--color-border); padding-top: var(--space-2); }
    .dg__foot-actions { margin-left: auto; display: flex; gap: 4px; }

    .dg__empty, .dg__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }

    .dg-form { display: grid; gap: var(--space-4); width: 100%; min-width: min(680px, 82vw); }
    .dg-form .field { display: grid; gap: var(--space-2); }
    .dg-form label { font-size: var(--text-sm); font-weight: 600; }
    .dg-form textarea { min-height: 150px; resize: vertical; line-height: 1.5; }
    .dg-form textarea.dg-form__tall { min-height: 220px; }
    /*
     * Danh sách nhân sự: LIST DỌC 1 cột, dòng GỌN.
     * Dòng là <label> nên bị .dg-form label (nhãn field) áp vào → phải ghi đè rõ ràng
     * (font-weight/margin/line-height), nếu không mỗi dòng cao ~46px, chỉ thấy 4 người.
     */
    .dg-form__members { display: block; max-height: 220px; overflow-y: auto;
      border: 1px solid var(--color-border); border-radius: var(--radius-md); padding: 2px; }
    .dg-form .dg-form__mrow { display: flex; align-items: center; gap: var(--space-2);
      font-size: var(--text-sm); font-weight: 400; cursor: pointer;
      margin: 0; padding: 2px var(--space-2); min-height: 26px; line-height: 1.2;
      border-radius: var(--radius-sm, 6px); }
    .dg-form__mrow:hover { background: var(--color-surface-alt); }
    .dg-form__mrow.is-checked { background: var(--color-primary-soft); }
    .dg-form__mrow input { width: auto; flex: none; margin: 0; }
    .dg-form__mcode { font-size: var(--text-xs); color: var(--color-text-muted);
      font-variant-numeric: tabular-nums; flex: none; min-width: 42px; }
    /* Tên KHÔNG bị cắt; chức danh chỉ hiện khi còn chỗ (màn hẹp thì ẩn cho gọn). */
    .dg-form__mname { flex: 1 1 auto; overflow-wrap: anywhere; }
    .dg-form__mpos { font-size: var(--text-xs); color: var(--color-text-muted); }
    @media (max-width: 720px) { .dg-form__mpos { display: none; } }
    .dg-form__mdept { font-size: var(--text-xs); color: var(--color-text-muted); flex: none;
      background: var(--color-surface-alt); padding: 0 var(--space-2); border-radius: var(--radius-full); }
    /*
     * Dropdown chọn nhiều người. Panel bung TRONG DÒNG (không position:absolute) vì
     * .modal__body có overflow:auto → panel nổi sẽ bị CẮT khi vượt đáy modal.
     */
    .dg-pick { display: grid; gap: var(--space-2); }
    .dg-pick__trigger { display: flex; align-items: center; gap: var(--space-2); width: 100%;
      min-height: var(--control-h-sm, 34px); padding: 4px var(--space-3); font: inherit; cursor: pointer;
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); box-sizing: border-box; }
    .dg-pick__trigger:hover, .dg-pick__trigger.is-open { border-color: var(--color-primary); }
    .dg-pick__val { flex: 1; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .dg-pick__val--empty { color: var(--color-text-muted); }
    .dg-pick__caret { color: var(--color-text-muted); flex: none; }
    .dg-pick__panel { display: grid; gap: var(--space-2); padding: var(--space-2);
      border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface-alt); }
    .dg-pick__bar { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
    .dg-pick__done { justify-self: end; }
    .dg-form__mhead { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; }
    .dg-form__mfilter { flex: 1 1 200px; height: var(--control-h-sm); padding: 0 var(--space-2);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); font-size: var(--text-xs); }

    /* Next action hiển thị trên card */
    .dg__act-no { color: var(--color-text-muted); font-variant-numeric: tabular-nums; flex: none; }
    .dg__acts { display: grid; gap: 4px; }
    .dg__act { display: flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm); flex-wrap: wrap; }
    .dg__act-st { font-size: var(--text-xs); padding: 1px var(--space-2); border-radius: var(--radius-full);
      background: var(--color-surface-alt); color: var(--color-text-muted); flex: none; }
    .dg__act-st--DOING { color: var(--color-primary); background: var(--color-primary-soft); }
    .dg__act-st--DONE { color: var(--color-success, var(--color-primary)); background: var(--color-success-soft, var(--color-primary-soft)); }
    .dg__act-meta { font-size: var(--text-xs); color: var(--color-text-muted); }
  `]
})
export class PrjDiary {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();

  readonly loading = signal(true);
  readonly entries = signal<DiaryEntry[]>([]);
  readonly members = signal<ProjectMember[]>([]);
  /** Tìm kiếm nội dung nhật ký (nội dung/kết luận/phân loại/khách hàng/người/ngày). */
  readonly search = signal('');
  readonly filtered = computed<DiaryEntry[]>(() => {
    const q = this.search().trim().toLowerCase();
    if (!q) return this.entries();
    return this.entries().filter((e) =>
      [e.content, e.conclusion, e.category, e.clientContacts, (e.teamNames || []).join(' '), e.createdByName, e.workDate]
        .some((v) => (v || '').toLowerCase().includes(q)));
  });

  /** Các bản ghi đang mở rộng (id) — mặc định thu gọn để danh sách gọn. */
  readonly expanded = signal<Set<string>>(new Set());
  isExpanded(id: string): boolean { return this.expanded().has(id); }
  toggleExpand(id: string): void {
    const next = new Set(this.expanded());
    if (next.has(id)) next.delete(id); else next.add(id);
    this.expanded.set(next);
  }
  /** Nội dung/kết luận đủ dài để cần thu gọn (theo số ký tự hoặc số dòng). */
  isLong(e: DiaryEntry): boolean {
    const long = (s: string | null) => !!s && (s.length > 140 || (s.match(/\n/g)?.length ?? 0) >= 2);
    return long(e.content) || long(e.conclusion);
  }

  readonly categories = CATEGORIES;
  readonly categoryOptions: SelectOption[] = CATEGORIES.map((c) => ({ value: c.value, label: c.value }));

  // Modal state
  readonly modalOpen = signal(false);
  readonly saving = signal(false);
  readonly editingId = signal('');   // rỗng = thêm; có id = sửa

  fWorkDate = '';        // yyyy-MM-dd (input type=date)
  fCategory = '';        // nhãn phân loại
  fTeam = signal<Set<string>>(new Set());  // userId đã chọn
  fClient = '';
  fContent = '';
  fConclusion = '';
  fLocation = '';
  fStart = '';           // HH:mm
  fEnd = '';             // HH:mm
  /** Next action: ô text tự do, MỖI DÒNG = 1 việc (biên bản in thành bảng đánh số). */
  fNextText = '';
  /** Lọc nhanh danh sách nhân sự trong form (danh sách dài). */
  readonly memberFilter = signal('');
  /** Dropdown chọn nhân sự: thu gọn mặc định để form đỡ tốn diện tích. */
  readonly pickerOpen = signal(false);

  /** Tóm tắt hiển thị trên nút dropdown: "Chọn nhân sự…" / "3 người · A, B, +1". */
  readonly teamSummary = computed<string>(() => {
    const ids = this.fTeam();
    if (!ids.size) return 'Chọn nhân sự tham gia…';
    const names = this.members().filter((m) => ids.has(m.userId)).map((m) => m.name);
    const head = names.slice(0, 2).join(', ');
    const rest = names.length - 2;
    return `${ids.size} người · ${head}${rest > 0 ? ` +${rest}` : ''}`;
  });

  clearTeam(): void { this.fTeam.set(new Set()); }

  private readonly statusLabels: Record<string, string> = {
    NEW: 'Mới', DOING: 'Đang làm', DONE: 'Hoàn thành'
  };
  statusLabel(s: string | null): string {
    return this.statusLabels[s ?? 'NEW'] ?? 'Mới';
  }
  /** Việc này có thông tin ngoài nội dung không (phụ trách/hạn/đã đổi trạng thái)? */
  hasActionMeta(a: DiaryAction): boolean {
    return !!(a.owner || a.dueDate || (a.status && a.status !== 'NEW'));
  }

  /** Nhân sự khớp từ khoá lọc (mã / tên / phòng ban). */
  readonly membersShown = computed<ProjectMember[]>(() => {
    const q = this.memberFilter().trim().toLowerCase();
    const all = this.members();
    if (!q) return all;
    return all.filter((m) => [m.empCode, m.name, m.deptCode, m.jobPosition]
      .some((v) => (v || '').toLowerCase().includes(q)));
  });

  // ===== Next action: text nhiều dòng ⇄ danh sách việc =====
  /** Mỗi dòng không rỗng → 1 việc. */
  private actionsFromText(text: string): DiaryAction[] {
    return text.split('\n').map((l) => l.trim()).filter(Boolean)
      .map((content) => ({ content, owner: null, dueDate: null, status: 'NEW' as DiaryActionStatus }));
  }
  /** Danh sách việc → text để sửa. Giữ phụ trách/hạn ở cuối dòng nếu bản ghi cũ có. */
  private textFromActions(actions: DiaryAction[] | null | undefined): string {
    return (actions ?? []).map((a) => {
      const extra = [a.owner, a.dueDate].filter(Boolean).join(' – ');
      return (a.content ?? '') + (extra ? ` (${extra})` : '');
    }).join('\n');
  }

  /** Tải biên bản họp (.docx) của bản ghi — mở anchor để trình duyệt tự tải kèm cookie phiên. */
  exportMinutes(e: DiaryEntry): void {
    window.open(this.svc.diaryMinutesUrl(this.projectId(), e.id), '_blank');
  }

  constructor() {
    effect(() => {
      const id = this.projectId();
      if (id) this.reload(id);
    });
  }

  private reload(id: string): void {
    this.loading.set(true);
    this.svc.listMembers(id).subscribe({
      next: (m) => this.members.set(m ?? []),
      error: () => this.members.set([])
    });
    this.svc.listDiary(id).subscribe({
      next: (d) => { this.entries.set(d ?? []); this.loading.set(false); },
      error: () => { this.entries.set([]); this.loading.set(false); this.toast.error('Không tải được nhật ký dự án.'); }
    });
  }

  catBadge(cat: string | null): string {
    return CATEGORIES.find((c) => c.value === cat)?.badge ?? 'dg-cat--other';
  }

  memberChecked(userId: string): boolean {
    return this.fTeam().has(userId);
  }
  toggleMember(userId: string): void {
    const next = new Set(this.fTeam());
    if (next.has(userId)) next.delete(userId); else next.add(userId);
    this.fTeam.set(next);
  }

  openAdd(): void {
    this.editingId.set('');
    this.fWorkDate = this.today();
    this.fCategory = '';
    this.fTeam.set(new Set());
    this.fClient = '';
    this.fContent = '';
    this.fConclusion = '';
    this.fLocation = '';
    this.fStart = '';
    this.fEnd = '';
    this.fNextText = '';
    this.memberFilter.set('');
    this.modalOpen.set(true);
  }

  openEdit(e: DiaryEntry): void {
    this.editingId.set(e.id);
    this.fWorkDate = this.fromDmy(e.workDate);
    this.fCategory = e.category ?? '';
    this.fTeam.set(new Set(e.teamUserIds ?? []));
    this.fClient = e.clientContacts ?? '';
    this.fContent = e.content ?? '';
    this.fConclusion = e.conclusion ?? '';
    this.fLocation = e.location ?? '';
    this.fStart = e.startTime ?? '';
    this.fEnd = e.endTime ?? '';
    this.fNextText = this.textFromActions(e.nextActions);
    this.memberFilter.set('');
    this.modalOpen.set(true);
  }

  save(): void {
    const pid = this.projectId();
    if (!this.fWorkDate) { this.toast.warning('Hãy chọn ngày làm việc.'); return; }
    this.saving.set(true);
    const body: DiaryRequest = {
      workDate: this.fWorkDate,                    // yyyy-MM-dd — backend chấp nhận
      category: this.fCategory || null,
      teamUserIds: Array.from(this.fTeam()),
      clientContacts: this.fClient || null,
      content: this.fContent || null,
      conclusion: this.fConclusion || null,
      location: this.fLocation || null,
      startTime: this.fStart || null,
      endTime: this.fEnd || null,
      nextActions: this.actionsFromText(this.fNextText)
    };
    const done = (verb: string) => () => {
      this.saving.set(false);
      this.modalOpen.set(false);
      this.toast.success(verb);
      this.reload(pid);
    };
    const fail = (verb: string) => (err: { error?: { message?: string; detail?: string } }) => {
      this.saving.set(false);
      this.toast.error(verb, err?.error?.message ?? err?.error?.detail ?? '');
    };
    if (this.editingId()) {
      this.svc.updateDiary(pid, this.editingId(), body)
        .subscribe({ next: done('Đã cập nhật nhật ký'), error: fail('Không cập nhật được nhật ký') });
    } else {
      this.svc.createDiary(pid, body)
        .subscribe({ next: done('Đã thêm nhật ký'), error: fail('Không thêm được nhật ký') });
    }
  }

  remove(e: DiaryEntry): void {
    if (!confirm('Xoá bản ghi nhật ký này?')) return;
    this.svc.deleteDiary(this.projectId(), e.id).subscribe({
      next: () => { this.toast.success('Đã xoá nhật ký'); this.reload(this.projectId()); },
      error: (err) => this.toast.error('Không xoá được nhật ký', err?.error?.message ?? err?.error?.detail ?? '')
    });
  }

  /** Chip nhân sự cho từng userId trong team (tra từ members). */
  memberOf(userId: string): ProjectMember | undefined {
    return this.members().find((m) => m.userId === userId);
  }

  /** ISO Instant → "dd/MM/yyyy HH:mm" (giờ địa phương). */
  when(iso: string | null): string {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    const p = (n: number) => (n < 10 ? '0' + n : String(n));
    return `${p(d.getDate())}/${p(d.getMonth() + 1)}/${d.getFullYear()} ${p(d.getHours())}:${p(d.getMinutes())}`;
  }

  private today(): string {
    const d = new Date();
    const p = (n: number) => (n < 10 ? '0' + n : String(n));
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }

  /** dd/MM/yyyy → yyyy-MM-dd (đổ vào input type=date khi sửa). */
  private fromDmy(d: string | null): string {
    if (!d) return '';
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : d;
  }
}
