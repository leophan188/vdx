import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { Avatar } from '../shared/avatar/avatar';
import { ToastService } from '../shared/toast/toast.service';
import { AuthService } from '../core/auth.service';
import { MeService, MyProfile } from '../core/me.service';
import { ThemeService, Theme } from '../shared/theme.service';

/**
 * "Tài khoản của tôi" (tự phục vụ): xem/sửa thông tin cá nhân + đổi mật khẩu + tải ảnh đại diện.
 * Tên hiển thị ưu tiên Employee (QLNS): khi linkedEmployee → ô Họ tên readonly ("Theo hồ sơ nhân sự").
 */
@Component({
  selector: 'app-account',
  standalone: true,
  imports: [FormsModule, PageHeader, Avatar],
  templateUrl: './account.html',
  styleUrl: './account.scss'
})
export class Account implements OnInit {
  private me = inject(MeService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private router = inject(Router);
  readonly themeSvc = inject(ThemeService);

  // --- Giao diện (lưu theo tài khoản) ---
  readonly accents = this.themeSvc.accents;
  readonly currentAccent = this.themeSvc.accent;
  readonly currentTheme = this.themeSvc.theme;

  setMode(mode: Theme): void {
    this.themeSvc.setMode(mode);
  }

  setAccent(key: string): void {
    this.themeSvc.setAccent(key);
  }

  readonly profile = signal<MyProfile | null>(null);
  readonly loading = signal(true);

  // --- Thiết lập bắt buộc (đọc cờ từ phiên đăng nhập) ---
  /** Đang dùng mật khẩu mặc định và bị ép đổi. */
  readonly mustChangePassword = computed(() => this.auth.currentUser()?.mustChangePassword === true);
  /** Chưa có ảnh đại diện (bị ép bổ sung). */
  readonly mustAddAvatar = computed(() => this.auth.currentUser()?.hasAvatar === false);
  /** Còn ít nhất một việc thiết lập bắt buộc chưa xong → hiện banner, chặn rời màn. */
  readonly setupRequired = computed(() => this.mustChangePassword() || this.mustAddAvatar());

  // Cache-bust avatar: đổi sau mỗi lần upload để trình duyệt nạp ảnh mới.
  readonly avatarVer = signal<number>(Date.now());
  readonly hasAvatar = signal(false);
  readonly avatarSrc = computed(() => (this.hasAvatar() ? this.me.avatarUrl(this.avatarVer()) : null));

  // --- Form thông tin ---
  fEmail = '';
  fPhone = '';
  fFullName = '';
  readonly savingProfile = signal(false);

  // --- Form đổi mật khẩu ---
  oldPwd = '';
  newPwd = '';
  confirmPwd = '';
  readonly changingPwd = signal(false);

  readonly uploading = signal(false);

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);
    this.me.getProfile().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.fEmail = p.email ?? '';
        this.fPhone = p.phone ?? '';
        this.fFullName = p.fullName ?? '';
        this.hasAvatar.set(p.hasAvatar);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.toast.error('Không tải được thông tin tài khoản');
      }
    });
  }

  // ===== Avatar =====
  onPickAvatar(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.toast.error('Tệp không hợp lệ', 'Vui lòng chọn một tệp ảnh.');
      input.value = '';
      return;
    }
    this.uploading.set(true);
    this.me.uploadAvatar(file).subscribe({
      next: () => {
        this.uploading.set(false);
        this.hasAvatar.set(true);
        this.avatarVer.set(Date.now());
        input.value = '';
        this.toast.success('Đã cập nhật ảnh đại diện');
        // Đồng bộ cờ thiết lập: avatar đã có.
        this.auth.patchSetupFlags({ hasAvatar: true });
        this.afterSetupStep();
      },
      error: (err: HttpErrorResponse) => {
        this.uploading.set(false);
        input.value = '';
        this.toast.error('Không tải được ảnh', this.msg(err, 'Ảnh phải là JPEG/PNG/GIF/WEBP và ≤10MB.'));
      }
    });
  }

  // ===== Thông tin =====
  saveProfile(): void {
    this.savingProfile.set(true);
    const body: { email: string; phone: string; fullName?: string } = {
      email: this.fEmail.trim(),
      phone: this.fPhone.trim()
    };
    // Chỉ gửi fullName khi được phép sửa (không liên kết hồ sơ nhân sự).
    if (!this.profile()?.linkedEmployee) {
      body.fullName = this.fFullName.trim();
    }
    this.me.updateProfile(body).subscribe({
      next: (p) => {
        this.savingProfile.set(false);
        this.profile.set(p);
        this.fFullName = p.fullName ?? '';
        this.toast.success('Đã lưu thông tin tài khoản');
      },
      error: (err: HttpErrorResponse) => {
        this.savingProfile.set(false);
        this.toast.error('Không lưu được thông tin', this.msg(err));
      }
    });
  }

  // ===== Đổi mật khẩu =====
  changePassword(): void {
    if (this.newPwd.length < 4) {
      this.toast.warning('Mật khẩu mới quá ngắn', 'Tối thiểu 4 ký tự.');
      return;
    }
    if (this.newPwd !== this.confirmPwd) {
      this.toast.warning('Mật khẩu nhập lại không khớp');
      return;
    }
    this.changingPwd.set(true);
    this.me.changePassword(this.oldPwd, this.newPwd).subscribe({
      next: () => {
        this.changingPwd.set(false);
        this.oldPwd = this.newPwd = this.confirmPwd = '';
        this.toast.success('Đã đổi mật khẩu');
        // Đồng bộ cờ thiết lập: không còn dùng mật khẩu mặc định.
        this.auth.patchSetupFlags({ mustChangePassword: false });
        this.afterSetupStep();
      },
      error: (err: HttpErrorResponse) => {
        this.changingPwd.set(false);
        this.toast.error('Không đổi được mật khẩu', this.msg(err, 'Vui lòng kiểm tra lại.'));
      }
    });
  }

  /**
   * Sau mỗi bước thiết lập bắt buộc: re-sync cờ từ /auth/me (nguồn sự thật), rồi nếu đã xong hết
   * → tự rời /account về /home. Lỗi /me không chặn (đã patch cờ local).
   */
  private afterSetupStep(): void {
    this.auth.me().subscribe({
      next: () => this.maybeLeaveSetup(),
      error: () => this.maybeLeaveSetup()
    });
  }

  private maybeLeaveSetup(): void {
    if (!this.setupRequired()) {
      this.toast.success('Hoàn tất thiết lập', 'Bạn đã có thể sử dụng hệ thống.');
      this.router.navigateByUrl('/home');
    }
  }

  /** Lấy thông điệp lỗi từ ApiError backend (vd "Mật khẩu hiện tại không đúng"). */
  private msg(err: HttpErrorResponse, fallback = 'Vui lòng thử lại.'): string {
    return err?.error?.message ?? fallback;
  }
}
