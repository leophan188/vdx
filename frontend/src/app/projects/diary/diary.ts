import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ToastService } from '../../shared/toast/toast.service';
import { ProjectService, ProjectMember, DiaryEntry, DiaryRequest } from '../../core/project.service';

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
    .dg-form__members { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: var(--space-2); max-height: 220px; overflow: auto; border: 1px solid var(--color-border);
      border-radius: var(--radius-md); padding: var(--space-2); }
    .dg-form__mrow { display: flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm); cursor: pointer; }
    .dg-form__mrow input { width: auto; }
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
      conclusion: this.fConclusion || null
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
