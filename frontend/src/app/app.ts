import { Component, computed, inject, signal } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive, Router, NavigationEnd } from '@angular/router';
import { UpperCasePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { filter } from 'rxjs/operators';
import { AuthService } from './core/auth.service';
import { ThemeService } from './shared/theme.service';
import { ToastHost } from './shared/toast/toast-host';
import { NotificationBell } from './shared/notification-bell/notification-bell';
import { QuickCreate } from './shared/quick-create/quick-create';
import { BrandMark } from './shared/brand-mark/brand-mark';
import { QuickCreateType } from './core/me-bug.service';
import { loadPref, savePref } from './shared/view-prefs';
import { PROJECT_TABS, PrjTabGroup, groupPrjTabs } from './projects/project-tabs';

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
  ['/excel-reports', 'Công cụ'],
  ['/timesheet-control', 'Công cụ'],
  ['/erp-integrations', 'Quản trị hệ thống'],
];

/** Một mục điều hướng con (trong flyout). */
interface NavItem { label: string; icon: string; link: string; feature: string; exact?: boolean; badge?: 'inbox' | 'mytask'; }
/** Một NHÓM điều hướng (icon trên rail dọc; item xổ ngang). */
interface NavGroup { key: string; label: string; icon: string; items: NavItem[]; }

/** Cấu hình điều hướng: 5 nhóm (rail dọc) → mục con (flyout ngang). */
const NAV_GROUPS: NavGroup[] = [
  { key: 'personal', label: 'Cá nhân', icon: '🏠', items: [
    { label: 'Bảng tin VDX', icon: '🌐', link: '/home', feature: 'SOCIAL' },
    { label: 'Việc của tôi', icon: '📥', link: '/inbox', feature: 'INBOX', badge: 'inbox' },
    { label: 'Hồ sơ của tôi', icon: '🗂️', link: '/my-requests', feature: 'REQUESTS' },
    { label: 'Backlog của tôi', icon: '📋', link: '/my-tasks', feature: 'MYTASKS', badge: 'mytask' },
    { label: 'Đăng ký OT', icon: '🕒', link: '/ot', feature: 'OT' },
    { label: 'Đăng ký nghỉ', icon: '🌴', link: '/leave', feature: 'LEAVE' },
    { label: 'Tài liệu', icon: '📄', link: '/documents', feature: 'DOCS' },
  ] },
  { key: 'project', label: 'Dự án', icon: '📁', items: [
    { label: 'Quản lý dự án', icon: '📁', link: '/projects', feature: 'PROJECT', exact: true },
  ] },
  { key: 'reports', label: 'Báo cáo & Thống kê', icon: '📊', items: [
    { label: 'Dashboard', icon: '📊', link: '/dashboard', feature: 'REPORTS' },
    { label: 'Reports vận hành', icon: '📈', link: '/reports', feature: 'REPORTS' },
  ] },
  { key: 'tools', label: 'Công cụ', icon: '🧰', items: [
    { label: 'Import Excel → Kết quả', icon: '🧰', link: '/excel-reports', feature: 'IMPORT' },
    { label: 'Kiểm soát giờ công', icon: '⏱️', link: '/timesheet-control', feature: 'IMPORT' },
  ] },
  { key: 'admin', label: 'Quản trị hệ thống', icon: '⚙️', items: [
    { label: 'Quản lý nhân sự', icon: '👥', link: '/employees', feature: 'HR' },
    { label: 'Quản lý tài khoản', icon: '👤', link: '/accounts', feature: 'ACCOUNTS' },
    { label: 'Phân quyền', icon: '🔐', link: '/permissions', feature: 'PERMISSION' },
    { label: 'Danh mục', icon: '🗂️', link: '/catalog', feature: 'CATALOG' },
    { label: 'Quy trình', icon: '🔀', link: '/processes', feature: 'PROCESS' },
    { label: 'Biểu mẫu', icon: '📋', link: '/forms', feature: 'PROCESS' },
    { label: 'Theo dõi quy trình', icon: '📡', link: '/tracking', feature: 'TRACKING' },
    { label: 'Cấu hình & dữ liệu', icon: '🛠️', link: '/system-config', feature: 'SYSTEM' },
    { label: 'Tích hợp ERP', icon: '🔌', link: '/erp-integrations', feature: 'SYSTEM' },
    { label: 'Truy vết kiểm toán', icon: '📜', link: '/audit', feature: 'AUDIT' },
  ] },
];

@Component({
  selector: 'app-root',
  imports: [BrandMark, RouterOutlet, RouterLink, RouterLinkActive, UpperCasePipe, ToastHost, NotificationBell, QuickCreate],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly themeSvc = inject(ThemeService);
  private readonly router = inject(Router);

  /** Tiêu đề trang hiện tại (cập nhật theo Router) — hiển thị bên trái topbar. */
  protected readonly pageTitle = signal(this.titleFor(this.router.url));
  /** URL hiện tại (cho việc tô sáng nhóm đang mở trên rail). */
  protected readonly currentUrl = signal(this.router.url);

  /** Nhóm (MODULE) có ≥1 mục hiển thị theo quyền — hiện trên TOOLBAR NGANG. */
  protected readonly visibleGroups = computed<NavGroup[]>(() =>
    NAV_GROUPS
      .map((g) => ({ ...g, items: g.items.filter((i) => this.has(i.feature)) }))
      .filter((g) => g.items.length > 0)
  );

  /** Module chứa route hiện tại (để tự chọn module theo trang). */
  protected readonly activeGroupKey = computed<string | null>(() => {
    const path = (this.currentUrl() || '').split(/[?#]/)[0];
    for (const g of NAV_GROUPS) {
      for (const it of g.items) {
        if (path === it.link || path.startsWith(it.link + '/')) return g.key;
      }
    }
    return null;
  });

  /** Module người dùng bấm chọn trên toolbar (null = theo route). */
  protected readonly selectedModule = signal<string | null>(null);
  /** Module đang hiển thị: ưu tiên bấm chọn → theo route → module đầu tiên. */
  protected readonly activeModule = computed<string | null>(() => {
    const groups = this.visibleGroups();
    const sel = this.selectedModule();
    if (sel && groups.some((g) => g.key === sel)) return sel;
    const byRoute = this.activeGroupKey();
    if (byRoute && groups.some((g) => g.key === byRoute)) return byRoute;
    return groups[0]?.key ?? null;
  });
  /** Mục con (chức năng) của module đang chọn — hiện ở SIDEBAR DỌC. */
  protected readonly moduleItems = computed<NavItem[]>(() =>
    this.visibleGroups().find((g) => g.key === this.activeModule())?.items ?? []
  );
  protected readonly activeModuleLabel = computed<string>(() =>
    this.visibleGroups().find((g) => g.key === this.activeModule())?.label ?? ''
  );

  // ===== Ngữ cảnh DỰ ÁN: khi ở /projects/{id}, sidebar hiện TAB của dự án =====
  /** ID dự án nếu đang ở trong 1 dự án cụ thể (không phải trang danh sách /projects). */
  protected readonly projectId = computed<string | null>(() => {
    const m = (this.currentUrl() || '').match(/^\/projects\/([^/?#]+)/);
    return m ? m[1] : null;
  });
  protected readonly inProject = computed<boolean>(() => !!this.projectId());
  /** Tab dự án đang chọn (từ query ?tab=), mặc định 'overview'. */
  protected readonly activeProjectTab = computed<string>(() => {
    const q = (this.currentUrl() || '').split('?')[1] || '';
    const tab = new URLSearchParams(q).get('tab');
    return tab || 'overview';
  });
  /** Tab dự án theo quyền, gom nhóm — cho sidebar khi đang trong dự án. */
  protected readonly projectTabGroups = computed<PrjTabGroup[]>(() =>
    groupPrjTabs(PROJECT_TABS.filter((t) => !t.feature || this.has(t.feature)))
  );
  /** Bấm module trên toolbar: chọn module + đi tới mục đầu của nó. */
  protected clickModule(key: string): void {
    this.selectedModule.set(key);
    const g = this.visibleGroups().find((x) => x.key === key);
    if (g && g.items.length) this.router.navigateByUrl(g.items[0].link);
    this.closeMobileNav();
  }
  /** Số badge cho mục (inbox / my-task). */
  protected badgeFor(kind: 'inbox' | 'mytask' | undefined): number {
    if (kind === 'inbox') return this.inboxCount();
    if (kind === 'mytask') return this.myTaskCount();
    return 0;
  }

  private readonly http = inject(HttpClient);
  /** Số đếm việc — hiện badge cạnh menu "Việc của tôi" / "Việc dự án của tôi". */
  protected readonly inboxCount = signal(0);
  protected readonly myTaskCount = signal(0);

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => {
        this.pageTitle.set(this.titleFor(e.urlAfterRedirects));
        this.currentUrl.set(e.urlAfterRedirects);
        this.selectedModule.set(this.activeGroupKey()); // module theo trang hiện tại
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

  /** Map URL → tiêu đề theo tiền tố (bỏ query/fragment); mặc định "Plan X". */
  private titleFor(url: string): string {
    const path = (url || '').split(/[?#]/)[0];
    const hit = PAGE_TITLES.find(([prefix]) => path === prefix || path.startsWith(prefix + '/'));
    return hit ? hit[1] : 'Plan X';
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

  /** Thu gọn sidebar: false = menu dọc đầy đủ (mặc định), true = rail icon + flyout. Lưu localStorage. */
  protected readonly collapsed = signal<boolean>(loadPref<boolean>('bpm.shell.collapsed', false));
  protected toggleSidebar(): void {
    const v = !this.collapsed();
    this.collapsed.set(v);
    savePref('bpm.shell.collapsed', v);
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
