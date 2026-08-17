import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { AuthService } from '../core/auth.service';
import { PostService, PostView, PostCategory } from '../core/post.service';
import { HrHighlightsService, BirthdayView, OnboardingView, AnniversaryView } from '../core/hr-highlights.service';
import { ToastService } from '../shared/toast/toast.service';
import { Avatar } from '../shared/avatar/avatar';
import { PostCard } from './post-card';
import { PostComposer } from './post-composer';

/**
 * Trang chủ ONEConnect — bảng tin MXH nội bộ (Epic 2). Feed DỮ LIỆU THẬT từ /api/v1/posts:
 * composer (admin) + post-card (ảnh/video, like, bình luận 1 cấp, ghim, ẩn/xoá admin) + lọc + tải-thêm.
 * Widget cột phải giữ trang trí (chưa có nguồn backend riêng). Render trong app-shell BPM.
 */
@Component({
  selector: 'app-home',
  imports: [PostCard, PostComposer, Avatar],
  templateUrl: './home.html'
})
export class Home implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly api = inject(PostService);
  private readonly hr = inject(HrHighlightsService);
  private readonly toast = inject(ToastService);

  // ===== Việc B + C: điểm nhấn nhân sự (sinh nhật hôm nay + sắp onboard) =====
  readonly birthdaysToday = signal<BirthdayView[]>([]);
  readonly onboardingSoon = signal<OnboardingView[]>([]);
  /** Tri ân thâm niên: đúng hôm nay, và trong 7 ngày tới để chuẩn bị trước. */
  /** Sinh nhật sắp tới trong 7 ngày (không gồm hôm nay — hôm nay đã có danh sách riêng). */
  readonly birthdaysThisWeek = signal<BirthdayView[]>([]);
  readonly anniversariesToday = signal<AnniversaryView[]>([]);
  readonly anniversariesUpcoming = signal<AnniversaryView[]>([]);
  readonly hrLoaded = signal(false);

  // ===== Việc 3: Widget Thông báo / Sự kiện sắp tới dùng DATA THẬT từ feed =====
  readonly announcements = signal<PostView[]>([]);
  readonly events = signal<PostView[]>([]);

  // userId có ảnh tải lỗi / null → rơi về avatar chữ cái (app-avatar).
  private readonly brokenAvatars = signal<Set<string>>(new Set());

  /** URL ảnh đại diện theo userId (null nếu không có userId hoặc ảnh đã tải lỗi). */
  avatarUrl(userId: string | null): string | null {
    if (!userId || this.brokenAvatars().has(userId)) return null;
    return `/api/v1/me/avatar/${userId}`;
  }

  /** Ảnh tải lỗi (chưa có ảnh) → đánh dấu để hiển thị avatar chữ cái. */
  onAvatarError(userId: string | null): void {
    if (!userId) return;
    this.brokenAvatars.update((s) => new Set(s).add(userId));
  }

  private static readonly BATCH = 20;

  readonly userName = computed(() => this.auth.currentUser()?.username || 'Bạn');
  readonly isAdmin = computed(() =>
    (this.auth.currentUser()?.authorities ?? []).some((a) => a.authority === 'ROLE_ADMIN'));
  /** Được phép ĐĂNG BÀI? (phân quyền ma trận FEAT_SOCIAL_POST; ADMIN luôn có). */
  readonly canPost = computed(() => this.auth.hasFeature('SOCIAL_POST'));

  readonly posts = signal<PostView[]>([]);
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly hasMore = signal(true);
  private page = 0;

  // Bộ lọc nhanh theo PHÂN LOẠI ('' = tất cả). Bỏ lọc theo phòng ban (bài đăng toàn công ty).
  readonly filterCategory = signal<'' | PostCategory>('');
  readonly cats: { value: '' | PostCategory; label: string }[] = [
    { value: '', label: 'Tất cả' },
    { value: 'ANNOUNCEMENT', label: '📣 Thông báo' },
    { value: 'NEWS', label: '📰 Tin tức' },
    { value: 'EVENT', label: '📅 Sự kiện' }
  ];

  initials(name: string): string {
    const p = (name || '').trim().split(/\s+/);
    return ((p[0]?.[0] ?? '') + (p.length > 1 ? p[p.length - 1][0] : '')).toUpperCase();
  }
  tone(name: string): string {
    const tones = ['blue', 'purple', 'pink', 'green', 'orange', 'indigo'];
    let h = 0;
    for (const c of name || '') h = (h + c.charCodeAt(0)) % tones.length;
    return tones[h];
  }

  ngOnInit(): void {
    this.reload();
    this.loadHighlights();
    this.loadSidebar();
  }

  /** Tải widget Thông báo + Sự kiện sắp tới từ feed (data thật). Lỗi → để rỗng, không phá trang. */
  private loadSidebar(): void {
    this.api.feed({ page: 0, size: 5, category: 'ANNOUNCEMENT' }).subscribe({
      next: (r) => this.announcements.set(r),
      error: () => this.announcements.set([])
    });
    this.api.feed({ page: 0, size: 5, category: 'EVENT' }).subscribe({
      next: (r) => this.events.set(r),
      error: () => this.events.set([])
    });
  }

  /** Cắt nội dung ~80 ký tự: bỏ marker '#...' cuối, gộp xuống dòng, thêm '…' nếu dài. */
  excerpt(body: string): string {
    let t = (body || '').replace(/\s*#[^\s#]+\s*$/g, '').replace(/\s+/g, ' ').trim();
    if (t.length > 80) t = t.slice(0, 80).trimEnd() + '…';
    return t || '(không có nội dung)';
  }

  /** Ngày giờ VN (ngày/tháng giờ:phút) — giống fmt trong post-card. */
  fmtTime(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  /** Số ngày trong tháng (2 chữ số) từ ISO — dùng cho ô lịch sự kiện. */
  evDay(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit' });
  }
  /** Nhãn tháng "THG x" từ ISO. */
  evMonth(iso: string): string {
    return 'THG ' + (new Date(iso).getMonth() + 1);
  }
  /** Giờ:phút VN từ ISO. */
  evHour(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit' });
  }

  /** Tải điểm nhấn nhân sự (sinh nhật hôm nay + sắp onboard). Lỗi → ẩn widget, không phá feed. */
  private loadHighlights(): void {
    this.hr.highlights().subscribe({
      next: (h) => {
        this.birthdaysToday.set(h.birthdaysToday ?? []);
        this.onboardingSoon.set(h.onboardingSoon ?? []);
        this.birthdaysThisWeek.set(h.birthdaysThisWeek ?? []);
        this.anniversariesToday.set(h.anniversariesToday ?? []);
        // Loại người đã hiện ở ô "hôm nay" để không xuất hiện hai lần trên cùng màn.
        this.anniversariesUpcoming.set((h.anniversariesUpcoming ?? []).filter((a) => a.inDays > 0));
        this.hrLoaded.set(true);
      },
      error: () => { this.hrLoaded.set(true); }
    });
  }

  private query(page: number) {
    return {
      page,
      size: Home.BATCH,
      category: this.filterCategory() || undefined
    };
  }

  /** Admin: hiện CẢ bài đã ẩn (để xem lại / bỏ ẩn). adminList gồm bài hidden. */
  readonly showHidden = signal(false);
  toggleHidden(): void {
    this.showHidden.update((v) => !v);
    this.reload();
  }
  /** Nguồn feed: bài ẩn (adminList, admin) hay feed thường (lọc theo phân loại). */
  private source(page: number) {
    return this.showHidden() && this.isAdmin()
      ? this.api.adminList(page, Home.BATCH)
      : this.api.feed(this.query(page));
  }

  reload(): void {
    this.loading.set(true);
    this.page = 0;
    this.source(0).subscribe({
      next: (r) => {
        this.posts.set(r);
        this.hasMore.set(r.length === Home.BATCH);
        this.loading.set(false);
      },
      error: () => { this.toast.error('Không tải được bảng tin'); this.loading.set(false); }
    });
  }

  loadMore(): void {
    if (this.loadingMore() || !this.hasMore()) return;
    this.loadingMore.set(true);
    const next = this.page + 1;
    this.source(next).subscribe({
      next: (r) => {
        this.posts.update((cur) => [...cur, ...r]);
        this.page = next;
        this.hasMore.set(r.length === Home.BATCH);
        this.loadingMore.set(false);
      },
      error: () => { this.toast.error('Không tải thêm được'); this.loadingMore.set(false); }
    });
  }

  /** Chọn nhanh phân loại để lọc feed. */
  selectCategory(c: '' | PostCategory): void {
    if (this.filterCategory() === c) return;
    this.filterCategory.set(c);
    this.reload();
  }
}
