import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { Modal } from '../../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../../shared/searchable-select/searchable-select';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ToastService } from '../../shared/toast/toast.service';
import { ProjectService, ProjectMember, Person, ProjectRole, UpdateMemberRequest } from '../../core/project.service';

/**
 * Tab "Thành viên dự án" (selector app-prj-members) — load người vào dự án.
 * listMembers → data-grid (chip tên + mã, vai trò, ngày tham gia, nút Gỡ).
 * "Thêm thành viên" → modal: searchable-select chọn người (people) + vai trò → addMember → reload.
 */
@Component({
  selector: 'app-prj-members',
  standalone: true,
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, SearchableSelect, EmployeeChip],
  templateUrl: './members.html',
  styles: [`
    .prj-mb__add { display: grid; gap: var(--space-4); width: 100%; min-width: 360px; }
    .prj-mb__add .field { display: grid; gap: var(--space-2); }
    .prj-mb__add label { font-size: var(--font-size-sm); font-weight: 600; }
    .prj-mb__roles { display: flex; gap: var(--space-2); flex-wrap: wrap; }
    .prj-mb__role-btn { padding: var(--space-2) var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); cursor: pointer;
      font-size: var(--font-size-sm); color: var(--color-text); }
    .prj-mb__role-btn--on { border-color: var(--color-primary); color: var(--color-primary);
      background: var(--color-primary-soft); font-weight: 600; }
  `]
})
export class PrjMembers {
  private svc = inject(ProjectService);
  private toast = inject(ToastService);

  readonly projectId = input.required<string>();

  readonly members = signal<ProjectMember[]>([]);
  readonly loading = signal(true);
  readonly people = signal<Person[]>([]);

  readonly addOpen = signal(false);
  readonly saving = signal(false);
  /** Rỗng = chế độ THÊM; có id = chế độ SỬA thành viên (không đổi người). */
  readonly editingId = signal('');
  editName = '';   // tên người đang sửa (hiển thị read-only trong modal)
  selUserId = '';
  selRole: ProjectRole = 'MEMBER';
  selStart = '';  // yyyy-MM-dd (input type=date)
  selEnd = '';
  selEffort = 100;  // % effort dành cho dự án (1–100)

  readonly roles: ProjectRole[] = ['PM', 'LEAD', 'MEMBER'];

  /** Số ngày công (T2–T6 giữa 2 ngày) tạm tính. */
  readonly previewWorkdays = computed(() => this.workdays(this.selStart, this.selEnd));
  /** Man-day THỰC TẾ tạm tính = ngày công × % effort. */
  readonly previewManday = computed(() =>
    Math.round(this.previewWorkdays() * Math.max(0, Math.min(100, this.selEffort || 0)) / 100));

  readonly cols: GridColumn[] = [
    { key: 'member', header: 'Thành viên' },
    { key: 'role', header: 'Vai trò', width: '120px' },
    { key: 'active', header: 'Truy cập', width: '132px', align: 'center' },
    { key: 'period', header: 'Thời gian (BĐ → KT)', width: '200px' },
    { key: 'effort', header: '% Effort', width: '90px', align: 'center', sortable: true },
    { key: 'manday', header: 'Man-day', width: '110px', align: 'center', sortable: true },
    { key: 'mm', header: 'Man-month', width: '110px', align: 'center', sortable: true },
    { key: 'actions', header: '', width: '116px' }
  ];

  /** Man-month của thành viên = manday / 22 (1 MM ≈ 22 man-day), làm tròn 2 chữ số. */
  mm(manday: number): number {
    return Math.round((manday / 22) * 100) / 100;
  }

  private toDmy(d: string): string | undefined {
    if (!d) return undefined;
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : d;
  }
  /** dd/MM/yyyy → yyyy-MM-dd (đổ vào input type=date khi sửa). */
  private fromDmy(d: string | null): string {
    if (!d) return '';
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : d;
  }
  private workdays(s: string, e: string): number {
    if (!s || !e) return 0;
    const a = new Date(s + 'T00:00:00'), b = new Date(e + 'T00:00:00');
    if (b < a) return 0;
    let n = 0;
    for (const d = new Date(a); d <= b; d.setDate(d.getDate() + 1)) {
      const w = d.getDay();
      if (w !== 0 && w !== 6) n++;
    }
    return n;
  }

  /** Loại bỏ người đã là thành viên khỏi danh sách chọn. */
  readonly peopleOptions = computed<SelectOption[]>(() => {
    const taken = new Set(this.members().map((m) => m.userId));
    return this.people()
      .filter((p) => !taken.has(p.userId))
      .map((p) => ({ value: p.userId, label: p.name,
        sub: [p.empCode, p.jobPosition, p.deptCode].filter(Boolean).join(' · ') }));
  });

  constructor() {
    effect(() => {
      const id = this.projectId();
      if (id) this.reload(id);
    });
  }

  private reload(id: string): void {
    this.loading.set(true);
    this.svc.listMembers(id).subscribe({
      next: (m) => { this.members.set(m); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách thành viên.'); this.loading.set(false); }
    });
  }

  openAdd(): void {
    this.editingId.set('');
    this.editName = '';
    this.selUserId = '';
    this.selRole = 'MEMBER';
    this.selStart = '';
    this.selEnd = '';
    this.selEffort = 100;
    this.addOpen.set(true);
    // Nạp lại danh sách người MỖI lần mở → nhân sự mới tạo (vd thuê ngoài) xuất hiện ngay, không cần F5.
    this.svc.people().subscribe({
      next: (p) => this.people.set(p),
      error: () => this.toast.error('Không tải được danh sách người dùng.')
    });
  }

  /** Mở modal SỬA thành viên — giữ nguyên người, đổ sẵn vai trò/ngày/%effort. */
  openEdit(m: ProjectMember): void {
    this.editingId.set(m.id);
    this.editName = m.name;
    this.selUserId = m.userId;
    this.selRole = m.roleInProject;
    this.selStart = this.fromDmy(m.startDate);
    this.selEnd = this.fromDmy(m.endDate);
    this.selEffort = m.effortPct;
    this.addOpen.set(true);
  }

  pickRole(r: ProjectRole): void { this.selRole = r; }

  save(): void {
    this.saving.set(true);
    const done = (verb: string) => (m: ProjectMember) => {
      this.saving.set(false);
      this.addOpen.set(false);
      this.toast.success(verb, `${m.name} · ${this.roleLabel(m.roleInProject)}`);
      this.reload(this.projectId());
    };
    const fail = (verb: string) => (e: { error?: { message?: string; detail?: string } }) => {
      this.saving.set(false);
      this.toast.error(verb, e?.error?.message ?? e?.error?.detail ?? '');
    };
    const effort = Math.max(1, Math.min(100, Number(this.selEffort) || 100));

    // Chế độ SỬA — không đổi người.
    if (this.editingId()) {
      const body: UpdateMemberRequest = {
        role: this.selRole,
        startDate: this.toDmy(this.selStart), endDate: this.toDmy(this.selEnd),
        effortPct: effort
      };
      this.svc.updateMember(this.projectId(), this.editingId(), body)
        .subscribe({ next: done('Đã cập nhật thành viên'), error: fail('Không cập nhật được thành viên') });
      return;
    }

    // Chế độ THÊM.
    if (!this.selUserId) { this.saving.set(false); this.toast.warning('Hãy chọn người để thêm vào dự án.'); return; }
    this.svc.addMember(this.projectId(), {
      userId: this.selUserId, role: this.selRole,
      startDate: this.toDmy(this.selStart), endDate: this.toDmy(this.selEnd),
      effortPct: effort
    }).subscribe({ next: done('Đã thêm thành viên'), error: fail('Không thêm được thành viên') });
  }

  /**
   * Tạm ngưng / mở lại quyền vào dự án. Khác hẳn "Gỡ khỏi dự án": giữ nguyên lịch sử công việc,
   * % effort và man-day đã ghi, nên dùng cho người rời đội tạm thời hoặc đã nghỉ.
   */
  toggleActive(m: ProjectMember): void {
    const next = !m.active;
    if (!next && !confirm(`Tạm ngưng ${m.name}? Người này sẽ không vào được dự án nữa `
        + `(dữ liệu công việc vẫn giữ nguyên).`)) {
      return;
    }
    this.svc.setMemberActive(this.projectId(), m.id, next).subscribe({
      next: () => {
        this.toast.success(next ? 'Đã mở lại quyền vào dự án' : 'Đã tạm ngưng thành viên', m.name);
        this.reload(this.projectId());
      },
      error: (e) => this.toast.error('Không đổi được trạng thái', e?.error?.message ?? e?.error?.detail ?? '')
    });
  }

  remove(m: ProjectMember): void {
    if (!confirm(`Gỡ ${m.name} khỏi dự án?`)) return;
    this.svc.removeMember(this.projectId(), m.id).subscribe({
      next: () => {
        this.toast.success('Đã gỡ thành viên', m.name);
        this.reload(this.projectId());
      },
      error: (e) => this.toast.error('Không gỡ được thành viên', e?.error?.message ?? e?.error?.detail ?? '')
    });
  }

  roleLabel(r: ProjectRole): string {
    switch (r) {
      case 'PM': return 'Quản lý DA';
      case 'LEAD': return 'Trưởng nhóm';
      case 'MEMBER': return 'Thành viên';
      default: return r;
    }
  }

  roleBadge(r: ProjectRole): string {
    switch (r) {
      case 'PM': return 'badge--active';
      case 'LEAD': return 'badge--pending';
      case 'MEMBER': return 'badge--neutral';
      default: return 'badge--neutral';
    }
  }

  fmt(iso: string | null): string {
    if (!iso) return '—';
    return new Date(iso).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
  }
}
