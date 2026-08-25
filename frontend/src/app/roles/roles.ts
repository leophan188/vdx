import { Component, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RoleService, Role } from '../core/role.service';
import { PositionService, Position } from '../core/position.service';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { PageHeader } from '../shared/page-header/page-header';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-roles',
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, ConfirmDialog, PageHeader],
  templateUrl: './roles.html'
})
export class Roles implements OnInit {
  private roleSvc = inject(RoleService);
  private positionSvc = inject(PositionService);
  private toast = inject(ToastService);

  readonly roles = signal<Role[]>([]);
  readonly positions = signal<Position[]>([]);
  readonly error = signal<string | null>(null);
  readonly message = signal<string | null>(null);

  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', sortable: true, width: '180px' },
    { key: 'name', header: 'Tên vai trò', sortable: true, width: '220px' },
    { key: 'permissions', header: 'Quyền' },
    { key: 'actions', header: 'Thao tác', align: 'right', width: '160px' }
  ];

  // Modal tạo vai trò
  readonly createOpen = signal(false);
  newCode = '';
  newName = '';
  newPerms = '';

  // Modal sửa vai trò (không đổi code)
  readonly editOpen = signal(false);
  readonly editTarget = signal<Role | null>(null);
  editName = '';
  editPerms = '';

  // Xóa
  readonly delOpen = signal(false);
  readonly delTarget = signal<Role | null>(null);

  // Modal gán vai trò cho vị trí
  readonly assignOpen = signal(false);
  assignPositionId = '';
  assignRoleCode = '';

  ngOnInit(): void {
    this.reload();
    this.positionSvc.all().subscribe({ next: (p) => this.positions.set(p), error: () => {} });
  }

  reload(): void {
    this.roleSvc.list().subscribe({
      next: (r) => this.roles.set(r),
      error: () => this.error.set('Không tải được vai trò (cần quyền quản trị).')
    });
  }

  private splitPerms(s: string): string[] {
    return s.split(',').map((p) => p.trim()).filter((p) => p.length > 0);
  }

  openCreate(): void {
    this.newCode = this.newName = this.newPerms = '';
    this.createOpen.set(true);
  }

  create(): void {
    const perms = this.splitPerms(this.newPerms);
    this.roleSvc.create(this.newCode, this.newName, perms).subscribe({
      next: () => {
        this.toast.success('Đã tạo vai trò', this.newCode);
        this.createOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không tạo được vai trò', 'Mã vai trò có thể đã tồn tại.')
    });
  }

  openEdit(r: Role): void {
    this.editTarget.set(r);
    this.editName = r.name;
    this.editPerms = r.permissions.join(', ');
    this.editOpen.set(true);
  }

  saveEdit(): void {
    const r = this.editTarget();
    if (!r || !this.editName.trim()) return;
    this.roleSvc.update(r.code, this.editName.trim(), this.splitPerms(this.editPerms)).subscribe({
      next: () => {
        this.toast.success('Đã cập nhật vai trò', r.code);
        this.editOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không cập nhật được vai trò')
    });
  }

  askDelete(r: Role): void {
    this.delTarget.set(r);
    this.delOpen.set(true);
  }

  confirmDelete(): void {
    const r = this.delTarget();
    if (!r) return;
    this.delOpen.set(false);
    this.roleSvc.remove(r.code).subscribe({
      next: () => {
        this.toast.success('Đã xóa vai trò', r.code);
        this.reload();
      },
      error: () => this.toast.error('Không xóa được vai trò', 'Vai trò đang được gán cho vị trí?')
    });
  }

  openAssign(): void {
    this.assignPositionId = this.assignRoleCode = '';
    this.assignOpen.set(true);
  }

  assign(): void {
    if (!this.assignPositionId || !this.assignRoleCode) {
      return;
    }
    this.roleSvc.assign(this.assignPositionId, this.assignRoleCode).subscribe({
      next: () => {
        this.toast.success('Đã gán vai trò cho vị trí');
        this.assignOpen.set(false);
      },
      error: () => this.toast.error('Không gán được vai trò')
    });
  }
}
