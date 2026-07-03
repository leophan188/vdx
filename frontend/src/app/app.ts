import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { filter } from 'rxjs/operators';
import { AuthService } from './core/auth.service';
import { ThemeService } from './shared/theme.service';
import { ToastHost } from './shared/toast/toast-host';
import { NotificationBell } from './shared/notification-bell/notification-bell';
import { GlobalSearch } from './shared/global-search/global-search';
import { QuickCreate } from './shared/quick-create/quick-create';
import { QuickCreateType } from './core/me-bug.service';

/** Bản đồ tiền tố URL → tiêu đề trang (khớp startsWith; dài hơn ưu tiên trước cho route con). */
const PAGE_TITLES: ReadonlyArray<readonly [string, string]> = [
  ['/home', 'Bảng tin VDX'],
  ['/projects', 'Quản lý dự án'],
  ['/dashboard', 'Dashboard'],
  ['/reports', 'Báo cáo'],
  ['/employees', 'Quản lý nhân sự'],
  ['/accounts', 'Quản lý tài khoản'],
  ['/inbox', 'Việc của tôi'],
  ['/my-tasks', 'Backlog của tôi'],
  ['/ot', 'Đăng ký OT'],
  ['/leave', 'Đăng ký nghỉ'],
  ['/documents', 'Tài liệu'],
  ['/account', 'Tài khoản của tôi'],
  ['/permissions', 'Phân quyền'],
  ['/catalog', 'Danh mục'],
  ['/audit', 'Truy vết kiểm toán'],
  ['/system-config', 'Cấu hình & dữ liệu'],
  ['/processes', 'Quy trình'],
  ['/forms', 'Biểu mẫu'],
  ['/tracking', 'Theo dõi quy trình'],
  ['/excel-reports', 'Import Excel'],
];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, UpperCasePipe, ToastHost, NotificationBell, GlobalSearch, QuickCreate],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly themeSvc = inject(ThemeService);
  private readonly router = inject(Router);

  /** Tiêu đề trang hiện tại (cập nhật theo Router) — hiển thị bên trái topbar. */
  protected readonly pageTitle = signal(this.titleFor(this.router.url));

  private readonly http = inject(HttpClient);
  /** Số đếm việc — hiện badge cạnh menu "Việc của tôi" / "Việc dự án của tôi". */
  protected readonly inboxCount = signal(0);
  protected readonly myTaskCount = signal(0);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.pageTitle.set(this.titleFor(e.urlAfterRedirects));
        this.loadCounts();
      });
    this.loadCounts();
  }

  /** Nạp số đếm việc của tôi (inbox BPM) + việc dự án (my-tasks). Lỗi → bỏ qua. */
  private loadCounts(): void {
    if (!this.auth.currentUser()) return;
    if (this.auth.hasFeature('INBOX')) {
      this.http.get<unknown[]>('/api/v1/inbox', { withCredentials: true })
        .subscribe({ next: (r) => this.inboxCount.set(r.length), error: () => {} });
    }
    if (this.auth.hasFeature('MYTASKS')) {
      this.http.get<unknown[]>('/api/v1/projects/my-tasks', { withCredentials: true })
        .subscribe({ next: (r) => this.myTaskCount.set(r.length), error: () => {} });
    }
  }

  /** Map URL → tiêu đề theo tiền tố (bỏ query/fragment); mặc định "VMO DX". */
  private titleFor(url: string): string {
    const path = (url || '').split(/[?#]/)[0];
    const hit = PAGE_TITLES.find(([prefix]) => path === prefix || path.startsWith(prefix + '/'));
    return hit ? hit[1] : 'VMO DX';
  }

  /** Phiên bản avatar (cache-bust ảnh đại diện ở topbar). */
  protected readonly avatarVer = signal(1);

  /** Tên hiển thị trên topbar — ưu tiên họ tên, fallback mã đăng nhập. */
  protected readonly displayName = computed(() =>
    this.auth.currentUser()?.fullName || this.auth.currentUser()?.username || 'Người dùng');

  /** Dòng phụ dưới tên: vị trí công việc thật (hồ sơ nhân sự); nếu chưa liên kết → nhãn theo quyền. */
  protected readonly userPosition = computed(() => {
    const jt = this.auth.currentUser()?.jobTitle;
    if (jt && jt.trim()) return jt.trim();
    return this.isAdmin() ? 'Quản trị' : 'Nhân viên';
  });

  // ===== TẠO NHANH (toolbar): menu chọn Task/Bug + modal =====
  protected readonly quickMenuOpen = signal(false);
  protected readonly quickOpen = signal(false);
  protected readonly quickType = signal<QuickCreateType>('TASK');
  protected toggleQuickMenu(): void { this.quickMenuOpen.update((o) => !o); }
  protected closeQuickMenu(): void { this.quickMenuOpen.set(false); }
  protected openQuickCreate(type: QuickCreateType): void {
    this.quickType.set(type);
    this.quickMenuOpen.set(false);
    this.quickOpen.set(true);
  }
  protected closeQuickCreate(): void { this.quickOpen.set(false); }
  protected onQuickCreated(): void { this.loadCounts(); }

  /** Menu người dùng (đổi MK / thông tin / đăng xuất). */
  protected readonly userMenuOpen = signal(false);
  protected toggleUserMenu(): void { this.userMenuOpen.update((o) => !o); }
  protected closeUserMenu(): void { this.userMenuOpen.set(false); }

  /** Menu giao diện trên topbar (Sáng/Tối + bộ màu). */
  protected readonly themeMenuOpen = signal(false);
  protected toggleThemeMenu(): void { this.themeMenuOpen.update((o) => !o); }
  protected closeThemeMenu(): void { this.themeMenuOpen.set(false); }

  /** Thu gọn sidebar (DESIGN.md: điều hướng thu gọn được). */
  protected readonly collapsed = signal(false);
  protected toggleSidebar(): void {
    this.collapsed.update((c) => !c);
  }

  /** Mở sidebar dạng overlay trên mobile (≤860px). Trên desktop class này vô hại (media query). */
  protected readonly mobileOpen = signal(false);
  protected toggleMobileNav(): void {
    this.mobileOpen.update((o) => !o);
  }
  protected closeMobileNav(): void {
    this.mobileOpen.set(false);
  }

  /** AC-4: lọc menu theo vai trò — chỉ ADMIN thấy khu quản trị. */
  protected readonly isAdmin = computed(() =>
    (this.auth.currentUser()?.authorities ?? []).some((a) => a.authority === 'ROLE_ADMIN')
  );

  /** Lọc menu theo CHỨC NĂNG (phân quyền ma trận). key vd "PROJECT", "HR". ADMIN luôn thấy. */
  protected has(feature: string): boolean {
    return this.auth.hasFeature(feature);
  }

  /** Có thấy nhóm "Quản trị hệ thống" không (bất kỳ chức năng quản trị nào). */
  protected hasAdminTools(): boolean {
    return ['HR', 'ACCOUNTS', 'CATALOG', 'PROCESS', 'TRACKING', 'SYSTEM', 'AUDIT', 'PERMISSION']
      .some((f) => this.auth.hasFeature(f));
  }

  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => this.router.navigate(['/login'])
    });
  }
}
