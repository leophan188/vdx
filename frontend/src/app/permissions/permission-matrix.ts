import { Component, computed, inject, signal, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { PermissionService, FeatureDef, PermRole, UserRef } from '../core/permission.service';
import { PageHeader } from '../shared/page-header/page-header';
import { Skeleton, EmptyState } from '../shared/skeleton/skeleton';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { EmployeeChip } from '../shared/employee-chip/employee-chip';
import { ToastService } from '../shared/toast/toast.service';

/** Một nhóm chức năng + danh sách feature thuộc nhóm (giữ thứ tự xuất hiện). */
interface FeatureGroup {
  group: string;
  features: FeatureDef[];
}

/**
 * Phân quyền chức năng — vai trò phân quyền là THỰC THỂ RIÊNG.
 * Mỗi vai trò là một khối: bật/tắt chức năng (gom nhóm), sửa/xoá, và gán nhân sự.
 */
@Component({
  selector: 'app-permission-matrix',
  imports: [FormsModule, PageHeader, Skeleton, EmptyState, Modal, ConfirmDialog, EmployeeChip],
  templateUrl: './permission-matrix.html',
  styleUrl: './permission-matrix.scss'
})
export class PermissionMatrix implements OnInit {
  private permSvc = inject(PermissionService);
  private toast = inject(ToastService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly saving = signal<string | null>(null);

  readonly features = signal<FeatureDef[]>([]);
  readonly roles = signal<PermRole[]>([]);

  /** Bản nháp chức năng: code vai trò → Set(key chức năng đang bật). */
  readonly draft = signal<Record<string, Set<string>>>({});
  /** Trạng thái đã lưu (để so sánh thay đổi & Đặt lại). */
  private readonly baseline = signal<Record<string, Set<string>>>({});

  /** Chức năng gom theo nhóm, giữ thứ tự nhóm xuất hiện trong features. */
  readonly groups = computed<FeatureGroup[]>(() => {
    const out: FeatureGroup[] = [];
    const idx = new Map<string, FeatureGroup>();
    for (const f of this.features()) {
      let g = idx.get(f.group);
      if (!g) {
        g = { group: f.group, features: [] };
        idx.set(f.group, g);
        out.push(g);
      }
      g.features.push(f);
    }
    return out;
  });

  readonly featureCount = computed(() => this.features().length);

  // ===== Modal Tạo / Sửa vai trò =====
  readonly roleModalOpen = signal(false);
  readonly roleModalMode = signal<'create' | 'edit'>('create');
  /** code vai trò đang sửa (rỗng khi tạo mới). */
  readonly roleEditCode = signal<string>('');
  rf = { name: '', description: '' };
  readonly roleSaving = signal(false);

  // ===== Xác nhận xoá =====
  readonly delOpen = signal(false);
  readonly delTarget = signal<PermRole | null>(null);

  // ===== Modal Thành viên =====
  readonly memberOpen = signal(false);
  readonly memberRole = signal<PermRole | null>(null);
  readonly memberLoading = signal(false);
  readonly memberSaving = signal(false);
  readonly allUsers = signal<UserRef[]>([]);
  /** Set userId đang được chọn thuộc vai trò. */
  readonly memberSel = signal<Set<string>>(new Set());
  /** Từ khoá tìm thành viên — PHẢI là signal để computed lọc chạy lại khi gõ. */
  readonly memberQuery = signal('');

  readonly memberFiltered = computed<UserRef[]>(() => {
    const q = this.memberQuery().trim().toLowerCase();
    const list = this.allUsers();
    if (!q) return list;
    return list.filter(
      (u) => (u.fullName || '').toLowerCase().includes(q) || (u.username || '').toLowerCase().includes(q)
    );
  });
  readonly memberSelCount = computed(() => this.memberSel().size);

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ features: this.permSvc.features(), roles: this.permSvc.roles() }).subscribe({
      next: ({ features, roles }) => {
        this.features.set(features);
        this.applyRoles(roles);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Không tải được dữ liệu phân quyền (cần quyền quản trị).');
        this.loading.set(false);
      }
    });
  }

  /** Cập nhật roles + baseline + draft chức năng từ danh sách trả về. */
  private applyRoles(roles: PermRole[]): void {
    this.roles.set(roles);
    const draft: Record<string, Set<string>> = {};
    const base: Record<string, Set<string>> = {};
    for (const r of roles) {
      draft[r.code] = new Set(r.features);
      base[r.code] = new Set(r.features);
    }
    this.draft.set(draft);
    this.baseline.set(base);
  }

  // ===== Chức năng (bản nháp) =====
  isOn(code: string, key: string): boolean {
    return this.draft()[code]?.has(key) ?? false;
  }

  toggle(code: string, key: string): void {
    this.draft.update((d) => {
      const next = { ...d };
      const set = new Set(next[code] ?? []);
      if (set.has(key)) set.delete(key);
      else set.add(key);
      next[code] = set;
      return next;
    });
  }

  isDirty(code: string): boolean {
    const a = this.draft()[code];
    const b = this.baseline()[code];
    if (!a || !b) return false;
    if (a.size !== b.size) return true;
    for (const k of a) if (!b.has(k)) return true;
    return false;
  }

  countOn(code: string): number {
    return this.draft()[code]?.size ?? 0;
  }

  resetRow(code: string): void {
    this.draft.update((d) => ({ ...d, [code]: new Set(this.baseline()[code] ?? []) }));
  }

  /** Lưu chức năng của một vai trò (giữ nguyên tên/mô tả hiện có). */
  saveRow(role: PermRole): void {
    if (!this.isDirty(role.code) || this.saving()) return;
    const features = [...(this.draft()[role.code] ?? [])];
    this.saving.set(role.code);
    this.permSvc
      .updateRole(role.code, { name: role.name, description: role.description, features })
      .subscribe({
        next: (updated) => {
          this.mergeRole(updated);
          this.saving.set(null);
          this.toast.success('Đã lưu phân quyền', updated.name);
        },
        error: () => {
          this.saving.set(null);
          this.toast.error('Không lưu được phân quyền', 'Vui lòng thử lại.');
        }
      });
  }

  /** Trộn một vai trò đã cập nhật vào state (giữ nguyên các vai trò khác). */
  private mergeRole(updated: PermRole): void {
    this.roles.update((rs) => rs.map((r) => (r.code === updated.code ? updated : r)));
    this.baseline.update((b) => ({ ...b, [updated.code]: new Set(updated.features) }));
    this.draft.update((d) => ({ ...d, [updated.code]: new Set(updated.features) }));
  }

  // ===== Tạo / Sửa vai trò =====
  openCreate(): void {
    this.roleModalMode.set('create');
    this.roleEditCode.set('');
    this.rf = { name: '', description: '' };
    this.roleModalOpen.set(true);
  }

  openEdit(role: PermRole): void {
    this.roleModalMode.set('edit');
    this.roleEditCode.set(role.code);
    this.rf = { name: role.name, description: role.description };
    this.roleModalOpen.set(true);
  }

  submitRole(): void {
    if (!this.rf.name.trim() || this.roleSaving()) return;
    this.roleSaving.set(true);
    const name = this.rf.name.trim();
    const description = this.rf.description.trim();
    if (this.roleModalMode() === 'create') {
      this.permSvc.createRole(name, description).subscribe({
        next: () => {
          this.roleSaving.set(false);
          this.roleModalOpen.set(false);
          this.toast.success('Đã tạo vai trò', name);
          this.reload();
        },
        error: () => {
          this.roleSaving.set(false);
          this.toast.error('Không tạo được vai trò', 'Tên trùng hoặc dữ liệu không hợp lệ.');
        }
      });
    } else {
      const code = this.roleEditCode();
      // Giữ nguyên chức năng đã lưu (baseline), chỉ đổi tên/mô tả.
      const features = [...(this.baseline()[code] ?? [])];
      this.permSvc.updateRole(code, { name, description, features }).subscribe({
        next: (updated) => {
          this.mergeRole(updated);
          this.roleSaving.set(false);
          this.roleModalOpen.set(false);
          this.toast.success('Đã cập nhật vai trò', updated.name);
        },
        error: () => {
          this.roleSaving.set(false);
          this.toast.error('Không cập nhật được vai trò');
        }
      });
    }
  }

  // ===== Xoá vai trò =====
  askDelete(role: PermRole): void {
    this.delTarget.set(role);
    this.delOpen.set(true);
  }

  confirmDelete(): void {
    const role = this.delTarget();
    if (!role) return;
    this.delOpen.set(false);
    this.permSvc.deleteRole(role.code).subscribe({
      next: () => {
        this.toast.success('Đã xoá vai trò', role.name);
        this.reload();
      },
      error: () => this.toast.error('Không xoá được vai trò')
    });
  }

  // ===== Thành viên =====
  openMembers(role: PermRole): void {
    this.memberRole.set(role);
    this.memberQuery.set('');
    this.memberSel.set(new Set());
    this.allUsers.set([]);
    this.memberOpen.set(true);
    this.memberLoading.set(true);
    forkJoin({ users: this.permSvc.users(), members: this.permSvc.members(role.code) }).subscribe({
      next: ({ users, members }) => {
        this.allUsers.set(users);
        this.memberSel.set(new Set(members.map((m) => m.userId)));
        this.memberLoading.set(false);
      },
      error: () => {
        this.memberLoading.set(false);
        this.toast.error('Không tải được danh sách tài khoản');
      }
    });
  }

  isMember(userId: string): boolean {
    return this.memberSel().has(userId);
  }

  toggleMember(userId: string): void {
    this.memberSel.update((s) => {
      const next = new Set(s);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }

  /** Người này đang thuộc vai trò KHÁC với vai trò đang mở? → sẽ bị chuyển. */
  otherRoleCode(u: UserRef): string | null {
    const cur = this.memberRole()?.code ?? '';
    return u.roleCode && u.roleCode !== cur ? u.roleCode : null;
  }

  roleName(code: string | null): string {
    if (!code) return '';
    return this.roles().find((r) => r.code === code)?.name ?? code;
  }

  saveMembers(): void {
    const role = this.memberRole();
    if (!role || this.memberSaving()) return;
    this.memberSaving.set(true);
    const ids = [...this.memberSel()];
    this.permSvc.setMembers(role.code, ids).subscribe({
      next: (members) => {
        const count = members.length;
        this.roles.update((rs) =>
          rs.map((r) => (r.code === role.code ? { ...r, memberCount: count } : r))
        );
        this.memberSaving.set(false);
        this.memberOpen.set(false);
        this.toast.success('Đã cập nhật thành viên', role.name + ' · ' + count + ' nhân sự');
      },
      error: () => {
        this.memberSaving.set(false);
        this.toast.error('Không lưu được thành viên', 'Vui lòng thử lại.');
      }
    });
  }
}
