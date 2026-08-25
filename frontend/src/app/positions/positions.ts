import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PositionService, Position } from '../core/position.service';
import { OrgService, OrgUnit } from '../core/org.service';
import { AuthService, UserAccount } from '../core/auth.service';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { PageHeader } from '../shared/page-header/page-header';
import { OrgTree } from '../shared/org-tree/org-tree';
import { OrgTreePicker } from '../shared/org-tree-picker/org-tree-picker';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-positions',
  imports: [FormsModule, DataGrid, GridCellDirective, Modal, ConfirmDialog, PageHeader, OrgTree, OrgTreePicker],
  templateUrl: './positions.html'
})
export class Positions implements OnInit {
  private positionSvc = inject(PositionService);
  private orgSvc = inject(OrgService);
  private authSvc = inject(AuthService);
  private toast = inject(ToastService);

  readonly units = signal<OrgUnit[]>([]);
  readonly users = signal<UserAccount[]>([]);
  readonly positions = signal<Position[]>([]);
  readonly error = signal<string | null>(null);

  readonly selectedUnitId = signal<string | null>(null);
  readonly selectedUnit = computed(() => this.units().find((u) => u.id === this.selectedUnitId()) ?? null);

  readonly cols: GridColumn[] = [
    { key: 'title', header: 'Vị trí', sortable: true },
    { key: 'holder', header: 'Người giữ hiện hành', width: '260px' },
    { key: 'actions', header: 'Thao tác', align: 'right', width: '280px' }
  ];

  // Modal tạo vị trí
  readonly createOpen = signal(false);
  newTitle = '';

  // Modal gán người
  readonly assignOpen = signal(false);
  readonly assignTarget = signal<Position | null>(null);
  assignUserId = '';

  // Sửa
  readonly editOpen = signal(false);
  readonly editTarget = signal<Position | null>(null);
  editTitle = '';

  // Xóa
  readonly delOpen = signal(false);
  readonly delTarget = signal<Position | null>(null);

  ngOnInit(): void {
    this.orgSvc.all().subscribe({ next: (u) => this.units.set(u), error: () => this.fail() });
    this.authSvc.listUsers().subscribe({ next: (u) => this.users.set(u), error: () => {} });
  }

  /** Chọn đơn vị ở cây bên trái → tải vị trí của đơn vị đó. */
  onUnitPick(id: string | null): void {
    this.selectedUnitId.set(id);
    if (id) {
      this.loadPositions();
    } else {
      this.positions.set([]);
    }
  }

  loadPositions(): void {
    const id = this.selectedUnitId();
    if (!id) { this.positions.set([]); return; }
    this.positionSvc.byOrgUnit(id).subscribe({
      next: (p) => this.positions.set(p),
      error: () => this.fail()
    });
  }

  openCreate(): void {
    this.newTitle = '';
    this.createOpen.set(true);
  }

  create(): void {
    const unitId = this.selectedUnitId();
    if (!unitId || !this.newTitle) {
      return;
    }
    this.positionSvc.create(this.newTitle, unitId).subscribe({
      next: () => {
        this.toast.success('Đã thêm vị trí', this.newTitle);
        this.newTitle = '';
        this.createOpen.set(false);
        this.loadPositions();
      },
      error: () => this.toast.error('Không tạo được vị trí')
    });
  }

  openEdit(p: Position): void {
    this.editTarget.set(p);
    this.editTitle = p.title;
    this.editOpen.set(true);
  }

  saveEdit(): void {
    const p = this.editTarget();
    if (!p || !this.editTitle.trim()) return;
    this.positionSvc.update(p.id, this.editTitle.trim()).subscribe({
      next: () => {
        this.toast.success('Đã cập nhật vị trí', this.editTitle);
        this.editOpen.set(false);
        this.loadPositions();
      },
      error: () => this.toast.error('Không cập nhật được vị trí')
    });
  }

  askDelete(p: Position): void {
    this.delTarget.set(p);
    this.delOpen.set(true);
  }

  confirmDelete(): void {
    const p = this.delTarget();
    if (!p) return;
    this.delOpen.set(false);
    this.positionSvc.remove(p.id).subscribe({
      next: () => {
        this.toast.success('Đã xóa vị trí', p.title);
        this.loadPositions();
      },
      error: () => this.toast.error('Không xóa được vị trí', 'Vị trí đang có người giữ?')
    });
  }

  openAssign(p: Position): void {
    this.assignTarget.set(p);
    this.assignUserId = p.currentHolderUserId ?? '';
    this.assignOpen.set(true);
  }

  doAssign(): void {
    const p = this.assignTarget();
    if (p && this.assignUserId) {
      this.assign(p, this.assignUserId);
      this.assignOpen.set(false);
    }
  }

  assign(p: Position, userId: string): void {
    if (!userId) {
      return;
    }
    this.positionSvc.assign(p.id, userId).subscribe({
      next: () => this.loadPositions(),
      error: () => this.fail()
    });
  }

  userName(id: string | null): string {
    if (!id) {
      return '(trống)';
    }
    return this.users().find((u) => u.id === id)?.fullName ?? id;
  }

  private fail(): void {
    this.error.set('Thao tác thất bại (cần quyền quản trị).');
  }
}
