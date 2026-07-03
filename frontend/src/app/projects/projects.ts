import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { SearchableSelect, SelectOption } from '../shared/searchable-select/searchable-select';
import { Skeleton, EmptyState } from '../shared/skeleton/skeleton';
import { ToastService } from '../shared/toast/toast.service';
import { ProjectService, Project, ProjectRequest, Person } from '../core/project.service';
import { AuthService } from '../core/auth.service';
import { formatThousands } from '../shared/format';
import { NumberFormatDirective } from '../shared/number-format.directive';

/** Danh sách & khai báo dự án (mini-Jira). Bấm một dự án để vào không gian làm việc. */
@Component({
  selector: 'app-projects',
  imports: [FormsModule, PageHeader, DataGrid, GridCellDirective, Modal, SearchableSelect, Skeleton, EmptyState, NumberFormatDirective],
  templateUrl: './projects.html',
  styles: [`
    .prj-bar { height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; min-width: 120px; }
    .prj-bar__fill { height: 100%; background: var(--color-primary); }
  `]
})
export class Projects implements OnInit {
  private api = inject(ProjectService);
  private toast = inject(ToastService);
  private router = inject(Router);
  private auth = inject(AuthService);

  /** Quyền "Thêm mới dự án" — ẩn nút Tạo nếu vai trò không được cấp. */
  readonly canCreate = computed(() => this.auth.hasFeature('PROJECT_CREATE'));

  readonly rows = signal<Project[]>([]);
  readonly loading = signal(true);
  readonly people = signal<Person[]>([]);

  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '90px', sortable: true },
    { key: 'name', header: 'Dự án', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '130px' },
    { key: 'completion', header: 'Hoàn thành', width: '180px' },
    { key: 'budget', header: 'Ngân sách (VNĐ)', width: '150px', align: 'right' },
    { key: 'counts', header: 'Task / Thành viên', width: '150px', align: 'center' },
    { key: 'actions', header: '', width: '140px' }
  ];

  readonly statusOptions: SelectOption[] = [
    { value: 'PLANNING', label: 'Lên kế hoạch' },
    { value: 'ACTIVE', label: 'Đang chạy' },
    { value: 'ON_HOLD', label: 'Tạm dừng' },
    { value: 'DONE', label: 'Hoàn thành' },
    { value: 'CANCELLED', label: 'Đã huỷ' }
  ];
  readonly ownerOptions = computed<SelectOption[]>(() =>
    this.people().map((p) => ({ value: p.userId, label: p.name, sub: p.empCode ?? undefined })));

  readonly createOpen = signal(false);
  /** id dự án đang sửa (null = đang TẠO mới). Form dùng chung create/sửa. */
  readonly editingId = signal<string | null>(null);
  f: ProjectRequest = this.blank();

  ngOnInit(): void {
    this.reload();
    this.api.people().subscribe({ next: (p) => this.people.set(p), error: () => {} });
  }

  reload(): void {
    this.loading.set(true);
    this.api.list().subscribe({
      next: (r) => { this.rows.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách dự án'); this.loading.set(false); }
    });
  }

  private blank(): ProjectRequest {
    return { code: '', name: '', description: '', status: 'PLANNING', startDate: '', dueDate: '', ownerUserId: '', budget: null, plannedEffortMm: null };
  }

  openCreate(): void { this.editingId.set(null); this.f = this.blank(); this.createOpen.set(true); }

  /** Mở modal SỬA — đổ dữ liệu dự án vào form dùng chung. code không sửa khi PUT (ô bị khoá). */
  openEdit(p: Project): void {
    this.editingId.set(p.id);
    this.f = {
      code: p.code,
      name: p.name,
      description: p.description ?? '',
      status: p.status,
      startDate: this.toIso(p.startDate),
      dueDate: this.toIso(p.dueDate),
      ownerUserId: p.ownerUserId ?? '',
      budget: p.budget ?? null,
      plannedEffortMm: p.plannedEffortMm ?? null
    };
    this.createOpen.set(true);
  }

  private toDmy(d?: string | null): string | undefined {
    if (!d) return undefined;
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(d);
    return m ? `${m[3]}/${m[2]}/${m[1]}` : d;
  }

  /** dd/MM/yyyy → yyyy-MM-dd (cho <input type="date">). */
  private toIso(d?: string | null): string {
    if (!d) return '';
    const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(d);
    return m ? `${m[3]}-${m[2]}-${m[1]}` : d;
  }

  /** Lưu form: TẠO mới hoặc CẬP NHẬT theo editingId. */
  save(): void { this.editingId() ? this.update() : this.create(); }

  create(): void {
    if (!this.f.name?.trim()) { this.toast.warning('Nhập tên dự án'); return; }
    const body: ProjectRequest = { ...this.f, startDate: this.toDmy(this.f.startDate), dueDate: this.toDmy(this.f.dueDate) };
    this.api.create(body).subscribe({
      next: (p) => {
        this.toast.success('Đã tạo dự án', p.code + ' · ' + p.name);
        this.createOpen.set(false);
        this.open(p);
      },
      error: (e) => this.toast.error('Không tạo được dự án', e?.error?.message ?? '')
    });
  }

  update(): void {
    const id = this.editingId();
    if (!id) return;
    if (!this.f.name?.trim()) { this.toast.warning('Nhập tên dự án'); return; }
    const body: ProjectRequest = { ...this.f, startDate: this.toDmy(this.f.startDate), dueDate: this.toDmy(this.f.dueDate) };
    this.api.update(id, body).subscribe({
      next: (p) => {
        this.toast.success('Đã cập nhật dự án', p.code + ' · ' + p.name);
        this.createOpen.set(false);
        this.editingId.set(null);
        this.reload();
      },
      error: (e) => this.toast.error('Không cập nhật được dự án', e?.error?.message ?? '')
    });
  }

  /** Định dạng số có phân tách hàng nghìn (helper chung). */
  formatNumber(n: number | null | undefined): string {
    return formatThousands(n);
  }


  open(p: Project): void { this.router.navigate(['/projects', p.id]); }

  statusLabel(s: string): string {
    return this.statusOptions.find((o) => o.value === s)?.label ?? s;
  }
  statusBadge(s: string): string {
    switch (s) {
      case 'ACTIVE': return 'badge--active';
      case 'DONE': return 'badge--done';
      case 'CANCELLED': return 'badge--cancel';
      case 'ON_HOLD': return 'badge--pending';
      default: return 'badge--neutral';
    }
  }
}
