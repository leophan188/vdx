import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../shared/page-header/page-header';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { ToastService } from '../shared/toast/toast.service';
import { HrDemoSeedResult, ProjectDemoSeedResult, SystemCategory, SystemService, WipeResult } from '../core/system.service';

/**
 * Cấu hình hệ thống → Xoá dữ liệu hệ thống (vùng nguy hiểm). CHỈ ADMIN.
 * Chọn nhóm dữ liệu, gõ "XOA" xác nhận, xác nhận lần hai qua dialog, gọi wipe.
 * KHÔNG xoá tài khoản admin (đảm bảo ở backend).
 */
@Component({
  selector: 'app-system-config',
  imports: [FormsModule, PageHeader, ConfirmDialog],
  templateUrl: './system-config.html',
  styleUrl: './system-config.scss'
})
export class SystemConfig implements OnInit {
  private svc = inject(SystemService);
  private toast = inject(ToastService);

  /** Chuỗi xác nhận bắt buộc. */
  readonly CONFIRM_TOKEN = 'XOA';

  readonly categories = signal<SystemCategory[]>([]);
  readonly selected = signal<Set<string>>(new Set());
  readonly loading = signal(false);
  readonly wiping = signal(false);
  readonly confirmText = signal('');
  readonly confirmOpen = signal(false);
  readonly error = signal<string | null>(null);

  /** Trạng thái tạo dữ liệu demo quy trình nhân sự. */
  readonly seeding = signal(false);
  readonly fixing = signal(false);
  /** Trạng thái tạo dữ liệu demo module Quản lý dự án. */
  readonly seedingProject = signal(false);

  readonly selectedCount = computed(() => this.selected().size);
  readonly allSelected = computed(
    () => this.categories().length > 0 && this.selected().size === this.categories().length
  );
  readonly canWipe = computed(
    () => this.selectedCount() > 0 && this.confirmText().trim() === this.CONFIRM_TOKEN && !this.wiping()
  );

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.error.set(null);
    this.svc.categories().subscribe({
      next: (c) => {
        this.categories.set(c);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Không tải được danh mục nhóm dữ liệu.');
        this.loading.set(false);
      }
    });
  }

  isChecked(key: string): boolean {
    return this.selected().has(key);
  }

  toggle(key: string): void {
    const next = new Set(this.selected());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.selected.set(next);
  }

  toggleAll(): void {
    if (this.allSelected()) {
      this.selected.set(new Set());
    } else {
      this.selected.set(new Set(this.categories().map((c) => c.key)));
    }
  }

  askWipe(): void {
    if (!this.canWipe()) {
      return;
    }
    this.confirmOpen.set(true);
  }

  cancelWipe(): void {
    this.confirmOpen.set(false);
  }

  doWipe(): void {
    this.confirmOpen.set(false);
    if (!this.canWipe()) {
      return;
    }
    const keys = [...this.selected()];
    this.wiping.set(true);
    this.svc.wipe(keys, this.confirmText().trim()).subscribe({
      next: (res) => {
        this.wiping.set(false);
        this.reportResult(res);
        // Reset xác nhận + lựa chọn, tải lại số đếm.
        this.confirmText.set('');
        this.selected.set(new Set());
        this.reload();
      },
      error: (err) => {
        this.wiping.set(false);
        const msg = err?.error?.error ?? 'Xoá dữ liệu thất bại.';
        this.toast.error('Lỗi xoá dữ liệu', msg);
      }
    });
  }

  /** Tạo dữ liệu demo quy trình nhân sự (CHỈ THÊM — không xoá dữ liệu thật). */
  seedHrDemo(): void {
    if (this.seeding()) {
      return;
    }
    this.seeding.set(true);
    this.svc.seedHrDemo().subscribe({
      next: (res: HrDemoSeedResult) => {
        this.seeding.set(false);
        if (res.seeded) {
          this.toast.success(
            'Đã tạo dữ liệu demo nhân sự',
            `${res.processes} quy trình, ${res.forms} biểu mẫu, ${res.instances} hồ sơ.`
          );
        } else {
          this.toast.info('Bỏ qua', res.message);
        }
        this.reload();
      },
      error: (err) => {
        this.seeding.set(false);
        const msg = err?.error?.message ?? err?.error?.error ?? 'Tạo dữ liệu demo thất bại.';
        this.toast.error('Lỗi tạo dữ liệu demo', msg);
      }
    });
  }

  /** Tạo dữ liệu demo module Quản lý dự án (CHỈ THÊM — không xoá dữ liệu thật). */
  seedProjectDemo(): void {
    if (this.seedingProject()) {
      return;
    }
    this.seedingProject.set(true);
    this.svc.seedProjectDemo().subscribe({
      next: (res: ProjectDemoSeedResult) => {
        this.seedingProject.set(false);
        if (res.seeded) {
          this.toast.success(
            'Đã tạo dữ liệu dự án mẫu',
            `${res.projects} dự án, ${res.members} thành viên, ${res.tasks} công việc (${res.bugs} bug).`
          );
        } else {
          this.toast.info('Bỏ qua', res.message);
        }
        this.reload();
      },
      error: (err) => {
        this.seedingProject.set(false);
        const msg = err?.error?.message ?? err?.error?.error ?? 'Tạo dữ liệu dự án mẫu thất bại.';
        this.toast.error('Lỗi tạo dữ liệu dự án mẫu', msg);
      }
    });
  }

  fixHrData(): void {
    if (this.fixing()) return;
    if (!confirm('Chuẩn hoá dữ liệu nhân sự?\n• Giữ số 0 đầu Mã NV & SĐT\n• Reset mật khẩu tài khoản nhân sự về "1111"\n• Gán quyền cho vai trò chức danh')) return;
    this.fixing.set(true);
    this.svc.fixHrData().subscribe({
      next: (r) => {
        this.fixing.set(false);
        this.toast.success('Đã chuẩn hoá dữ liệu nhân sự',
          `Mã NV sửa ${r['codesFixed']} · SĐT sửa ${r['phonesFixed']} · Reset mật khẩu ${r['passwordsReset']} (= "${r['password']}") · Vai trò ${r['rolesUpdated']}`);
      },
      error: (err) => {
        this.fixing.set(false);
        this.toast.error('Lỗi chuẩn hoá', err?.error?.message ?? err?.error?.error ?? 'Thất bại.');
      }
    });
  }

  private labelOf(key: string): string {
    return this.categories().find((c) => c.key === key)?.label ?? key;
  }

  private reportResult(res: WipeResult): void {
    let ok = 0;
    let fail = 0;
    for (const [key, val] of Object.entries(res)) {
      const label = this.labelOf(key);
      if (typeof val === 'number') {
        ok++;
        this.toast.success(`Đã xoá: ${label}`, `${val} bản ghi`);
      } else {
        fail++;
        this.toast.error(`Lỗi nhóm: ${label}`, String(val));
      }
    }
    if (ok > 0 && fail === 0) {
      this.toast.success('Hoàn tất xoá dữ liệu', `${ok} nhóm đã xoá. Tài khoản admin được giữ lại.`);
    }
  }
}
