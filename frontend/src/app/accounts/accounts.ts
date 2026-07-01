import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService, UserAccount } from '../core/auth.service';
import { AuditService, AuditEvent } from '../core/audit.service';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { StatusBadge, TaskStatus } from '../shared/status-badge/status-badge';
import { PageHeader } from '../shared/page-header/page-header';
import { Avatar } from '../shared/avatar/avatar';
import { Stepper } from '../shared/stepper/stepper';
import { Tabs, TabItem } from '../shared/tabs/tabs';
import { ToastService } from '../shared/toast/toast.service';
import { PermissionService, PermRole } from '../core/permission.service';
import { SearchableSelect, SelectOption } from '../shared/searchable-select/searchable-select';

@Component({
  selector: 'app-accounts',
  imports: [
    FormsModule, DataGrid, GridCellDirective, Modal, ConfirmDialog, StatusBadge,
    PageHeader, Avatar, Stepper, Tabs, SearchableSelect
  ],
  templateUrl: './accounts.html'
})
export class Accounts implements OnInit {
  private auth = inject(AuthService);
  private audit = inject(AuditService);
  private toast = inject(ToastService);
  private permSvc = inject(PermissionService);

  readonly users = signal<UserAccount[]>([]);
  readonly error = signal<string | null>(null);

  // --- Vai trò chức năng (vai trò phân quyền) ---
  readonly funcRoles = signal<PermRole[]>([]);
  /** Options cho searchable-select: mục "Mặc định" + từng vai trò chức năng. */
  readonly funcRoleOptions = computed<SelectOption[]>(() => [
    { value: '', label: '— Mặc định (nhân viên) —' },
    ...this.funcRoles().map((r) => ({ value: r.code, label: r.name, sub: r.code }))
  ]);
  /** Tên vai trò chức năng theo code (cho cột bảng). */
  funcRoleName(code: string | null): string {
    if (!code) return 'Mặc định';
    return this.funcRoles().find((r) => r.code === code)?.name ?? code;
  }

  readonly cols: GridColumn[] = [
    { key: 'username', header: 'Tên đăng nhập', sortable: true },
    { key: 'fullName', header: 'Họ tên', sortable: true },
    { key: 'role', header: 'Vai trò', sortable: true, width: '110px' },
    { key: 'roleCode', header: 'Vai trò chức năng', sortable: true, width: '170px' },
    { key: 'status', header: 'Trạng thái', width: '140px' },
    { key: 'actions', header: 'Thao tác', align: 'right', width: '230px' }
  ];
  readonly roleOptions = ['ADMIN', 'USER'];

  // --- Bộ lọc nâng cao (#9) ---
  readonly filterOpen = signal(false);
  filterRole = '';
  filterStatus = '';
  readonly appliedRole = signal('');
  readonly appliedStatus = signal('');
  readonly filteredUsers = computed(() => {
    const r = this.appliedRole();
    const s = this.appliedStatus();
    return this.users().filter((u) => (!r || u.role === r) && (!s || u.status === s));
  });

  // --- Wizard thêm (#2–4) ---
  readonly createOpen = signal(false);
  readonly step = signal(1);
  readonly steps = ['Thông tin chung', 'Vai trò & quyền', 'Xác nhận'];
  w = { username: '', fullName: '', email: '', phone: '', password: '', role: 'USER' };

  // --- Sửa (#5) ---
  readonly editOpen = signal(false);
  readonly editTarget = signal<UserAccount | null>(null);
  e = { fullName: '', email: '', phone: '', role: 'USER', roleCode: '' };

  // --- Khóa (#6) / Xóa (#7) ---
  readonly lockOpen = signal(false);
  readonly lockTarget = signal<UserAccount | null>(null);
  readonly delOpen = signal(false);
  readonly delTarget = signal<UserAccount | null>(null);

  // --- Reset mật khẩu ---
  readonly resetOpen = signal(false);
  readonly resetTarget = signal<UserAccount | null>(null);
  resetPw = '';

  // --- Chi tiết + lịch sử (#10–11) ---
  readonly detailOpen = signal(false);
  readonly detailTarget = signal<UserAccount | null>(null);
  readonly detailTab = signal('info');
  readonly detailTabs: TabItem[] = [
    { key: 'info', label: 'Thông tin chung', icon: 'ℹ️' },
    { key: 'history', label: 'Lịch sử hoạt động', icon: '🕒' }
  ];
  readonly history = signal<AuditEvent[]>([]);

  // --- Xuất Excel (#8) ---
  readonly exportOpen = signal(false);
  exportScope: 'all' | 'filtered' = 'filtered';

  ngOnInit(): void {
    this.reload();
    this.permSvc.roles().subscribe({
      next: (list) => this.funcRoles.set(list),
      error: () => {}
    });
  }

  reload(): void {
    this.auth.listUsers().subscribe({
      next: (list) => this.users.set(list),
      error: () => this.error.set('Không tải được danh sách tài khoản (cần quyền quản trị).')
    });
  }

  statusKind(status: string): TaskStatus {
    return status === 'LOCKED' ? 'cancel' : 'done';
  }
  statusLabel(status: string): string {
    return status === 'LOCKED' ? 'Đã khóa' : 'Hoạt động';
  }

  // ---- Bộ lọc ----
  applyFilter(): void {
    this.appliedRole.set(this.filterRole);
    this.appliedStatus.set(this.filterStatus);
    this.filterOpen.set(false);
  }
  resetFilter(): void {
    this.filterRole = this.filterStatus = '';
    this.appliedRole.set('');
    this.appliedStatus.set('');
  }

  // ---- Wizard thêm ----
  openCreate(): void {
    this.w = { username: '', fullName: '', email: '', phone: '', password: '', role: 'USER' };
    this.step.set(1);
    this.createOpen.set(true);
  }
  canNext(): boolean {
    if (this.step() === 1) return !!(this.w.username && this.w.fullName && this.w.password.length >= 8);
    return true;
  }
  next(): void { if (this.canNext()) this.step.update((s) => Math.min(3, s + 1)); }
  back(): void { this.step.update((s) => Math.max(1, s - 1)); }
  submitCreate(): void {
    this.auth.createUser({ ...this.w }).subscribe({
      next: () => {
        this.toast.success('Tài khoản đã được tạo thành công', this.w.username);
        this.createOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không thể tạo tài khoản', 'Tên đăng nhập trùng hoặc dữ liệu không hợp lệ.')
    });
  }

  // ---- Sửa ----
  openEdit(u: UserAccount): void {
    this.editTarget.set(u);
    this.e = { fullName: u.fullName, email: u.email ?? '', phone: u.phone ?? '', role: u.role, roleCode: u.roleCode ?? '' };
    this.editOpen.set(true);
  }
  saveEdit(): void {
    const u = this.editTarget();
    if (!u) return;
    const { roleCode, ...rest } = this.e;
    this.auth.updateUser(u.id, { ...rest, roleCode: roleCode || null }).subscribe({
      next: () => {
        this.toast.success('Cập nhật thành công', 'Thông tin tài khoản đã được cập nhật.');
        this.editOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không thể cập nhật tài khoản')
    });
  }

  // ---- Khóa / Mở ----
  askLock(u: UserAccount): void { this.lockTarget.set(u); this.lockOpen.set(true); }
  confirmLock(): void {
    const u = this.lockTarget();
    if (!u) return;
    const lock = u.status !== 'LOCKED';
    this.lockOpen.set(false);
    this.auth.setLocked(u.id, lock).subscribe({
      next: () => {
        this.toast.success(lock ? 'Đã khóa tài khoản' : 'Đã mở khóa tài khoản', u.username);
        this.reload();
      },
      error: () => this.toast.error('Không đổi được trạng thái tài khoản')
    });
  }

  // ---- Xóa ----
  askDelete(u: UserAccount): void { this.delTarget.set(u); this.delOpen.set(true); }
  confirmDelete(): void {
    const u = this.delTarget();
    if (!u) return;
    this.delOpen.set(false);
    this.auth.deleteUser(u.id).subscribe({
      next: () => {
        this.toast.success('Đã xóa tài khoản', u.username);
        this.reload();
      },
      error: () => this.toast.error('Không thể xóa tài khoản')
    });
  }

  // ---- Reset mật khẩu ----
  openReset(u: UserAccount): void { this.resetTarget.set(u); this.resetPw = ''; this.resetOpen.set(true); }
  doReset(): void {
    const u = this.resetTarget();
    if (!u || !this.resetPw) return;
    this.auth.resetPassword(u.id, this.resetPw).subscribe({
      next: () => {
        this.toast.success('Đã đặt lại mật khẩu', u.username);
        this.resetOpen.set(false);
      },
      error: () => this.toast.error('Không đặt lại được mật khẩu', 'Mật khẩu quá ngắn?')
    });
  }

  // ---- Chi tiết + lịch sử ----
  openDetail(u: UserAccount): void {
    this.detailTarget.set(u);
    this.detailTab.set('info');
    this.history.set([]);
    this.detailOpen.set(true);
    this.audit.trail('UserAccount', u.id).subscribe({
      next: (ev) => this.history.set(ev),
      error: () => {}
    });
  }

  // ---- Xuất Excel (CSV) ----
  doExport(): void {
    const rows = this.exportScope === 'filtered' ? this.filteredUsers() : this.users();
    const header = ['Tên đăng nhập', 'Họ tên', 'Email', 'SĐT', 'Vai trò', 'Trạng thái'];
    const cell = (v: unknown) => {
      const s = String(v ?? '');
      return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    };
    const lines = rows.map((u) =>
      [u.username, u.fullName, u.email, u.phone, u.role, this.statusLabel(u.status)].map(cell).join(',')
    );
    const csv = '﻿' + [header.join(','), ...lines].join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'tai-khoan.csv';
    a.click();
    URL.revokeObjectURL(url);
    this.exportOpen.set(false);
    this.toast.success('Đã xuất Excel', rows.length + ' bản ghi');
  }
}
