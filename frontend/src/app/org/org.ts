import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { OrgService, OrgUnit } from '../core/org.service';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { PageHeader } from '../shared/page-header/page-header';
import { OrgTree } from '../shared/org-tree/org-tree';
import { SearchableSelect, SelectOption } from '../shared/searchable-select/searchable-select';
import { ToastService } from '../shared/toast/toast.service';

@Component({
  selector: 'app-org',
  imports: [FormsModule, Modal, ConfirmDialog, PageHeader, OrgTree, SearchableSelect],
  templateUrl: './org.html'
})
export class Org implements OnInit {
  private org = inject(OrgService);
  private toast = inject(ToastService);

  readonly units = signal<OrgUnit[]>([]);
  readonly error = signal<string | null>(null);

  newName = '';
  newParentId = '';

  // Đơn vị đang chọn ở cây (để xem chi tiết + thao tác bên phải).
  readonly selectedUnitId = signal<string | null>(null);
  readonly selectedUnit = computed(() => this.units().find((u) => u.id === this.selectedUnitId()) ?? null);

  readonly createOpen = signal(false);

  // Sửa (đổi tên + đổi đơn vị cha)
  readonly editOpen = signal(false);
  readonly editTarget = signal<OrgUnit | null>(null);
  editName = '';
  editParentId = '';

  readonly deleteOpen = signal(false);
  readonly deleteTarget = signal<OrgUnit | null>(null);

  parentName(u: OrgUnit): string {
    return u.parentId ? (this.units().find((x) => x.id === u.parentId)?.name ?? '—') : '(Gốc)';
  }
  childCount(id: string): number {
    return this.units().filter((u) => u.parentId === id).length;
  }
  /** Mở popup thêm đơn vị con dưới đơn vị đang chọn. */
  openCreateUnder(u: OrgUnit): void {
    this.newName = '';
    this.newParentId = u.id;
    this.createOpen.set(true);
  }

  /** Đơn vị có thể làm cha khi sửa: loại chính nó (backend chặn thêm vòng lặp sâu hơn). */
  readonly parentOptions = computed(() => {
    const id = this.editTarget()?.id;
    return this.units().filter((u) => u.id !== id);
  });

  readonly parentSelAll = computed<SelectOption[]>(() => this.units().map((u) => ({ value: u.id, label: u.name })));
  readonly parentSelEdit = computed<SelectOption[]>(() => this.parentOptions().map((u) => ({ value: u.id, label: u.name })));

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.org.all().subscribe({
      next: (list) => this.units.set(list),
      error: () => this.error.set('Không tải được cây tổ chức (cần quyền quản trị).')
    });
  }

  openCreate(): void {
    this.newName = '';
    this.newParentId = '';
    this.createOpen.set(true);
  }

  create(): void {
    this.org.create(this.newName, this.newParentId || null).subscribe({
      next: () => {
        this.toast.success('Đã thêm đơn vị', this.newName);
        this.createOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không tạo được đơn vị')
    });
  }

  openEdit(u: OrgUnit): void {
    this.editTarget.set(u);
    this.editName = u.name;
    this.editParentId = u.parentId ?? '';
    this.editOpen.set(true);
  }

  saveEdit(): void {
    const u = this.editTarget();
    if (!u) return;
    const ops = [];
    const name = this.editName.trim();
    if (name && name !== u.name) {
      ops.push(this.org.rename(u.id, name));
    }
    const newParent = this.editParentId || null;
    if (newParent !== (u.parentId ?? null)) {
      ops.push(this.org.move(u.id, newParent));
    }
    if (ops.length === 0) {
      this.editOpen.set(false);
      return;
    }
    forkJoin(ops).subscribe({
      next: () => {
        this.toast.success('Đã cập nhật đơn vị', name);
        this.editOpen.set(false);
        this.reload();
      },
      error: () => this.toast.error('Không cập nhật được đơn vị', 'Đổi đơn vị cha có thể tạo vòng lặp.')
    });
  }

  askDelete(u: OrgUnit): void {
    this.deleteTarget.set(u);
    this.deleteOpen.set(true);
  }

  confirmDelete(): void {
    const u = this.deleteTarget();
    if (!u) return;
    this.deleteOpen.set(false);
    this.org.remove(u.id).subscribe({
      next: () => {
        this.toast.success('Đã xóa đơn vị', u.name);
        this.reload();
      },
      error: () => this.toast.error('Không xóa được đơn vị', 'Còn đơn vị con hoặc đang được dùng?')
    });
  }

  indent(level: number): string {
    return '— '.repeat(level);
  }
}
