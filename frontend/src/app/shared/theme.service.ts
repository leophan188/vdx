import { Injectable, effect, inject, signal, untracked } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { AuthService } from '../core/auth.service';

export type Theme = 'light' | 'dark';
const STORAGE_KEY = 'bpm-theme';
const ACCENT_KEY = 'accent';

/**
 * Một SKIN có tên (= data-accent). Ngoài key/label, chứa metadata để render THUMBNAIL
 * mini-mockup (dải sidebar + thanh topbar + chấm accent) bằng inline-style — không cần class CSS riêng.
 *   - sidebar: màu nền dải menu trái của card preview
 *   - accent:  màu nhấn (chấm + nút) — = --color-primary của skin
 *   - topbar:  màu thanh topbar của card preview
 * `swatch` giữ lại (= accent) để tương thích chỗ cũ nào còn đọc swatch.
 */
export interface AccentDef {
  key: string;
  label: string;
  sidebar: string;
  accent: string;
  topbar: string;
  /** @deprecated dùng `accent` — giữ để tương thích ngược. */
  swatch: string;
}

const def = (
  key: string,
  label: string,
  sidebar: string,
  accent: string,
  topbar: string
): AccentDef => ({ key, label, sidebar, accent, topbar, swatch: accent });

/** 12 SKIN có tên — mặc định 'navy' (khớp nhận diện Plan X). Mỗi skin theme cả sidebar + topbar + accent. */
export const ACCENTS: readonly AccentDef[] = [
  def('orange', 'Cam', '#2a1c10', '#ee6c1e', '#ffffff'),
  def('minimal', 'Tối giản', '#ffffff', '#2563eb', '#ffffff'),
  def('navy', 'Navy', '#0f172a', '#3b82f6', '#ffffff'),
  def('slate', 'Slate', '#1e293b', '#94a3b8', '#ffffff'),
  def('indigo', 'Indigo', '#312e81', '#6366f1', '#ffffff'),
  def('ocean', 'Đại dương', '#0c4a6e', '#0ea5e9', '#ffffff'),
  def('teal', 'Xanh ngọc', '#134e4a', '#14b8a6', '#ffffff'),
  def('emerald', 'Ngọc lục bảo', '#064e3b', '#10b981', '#ffffff'),
  def('rose', 'Hồng đỏ', '#881337', '#f43f5e', '#ffffff'),
  def('violet', 'Tím', '#3b0764', '#8b5cf6', '#ffffff'),
  def('amber', 'Hổ phách', '#451a03', '#f59e0b', '#ffffff'),
  def('midnight', 'Nửa đêm', '#020617', '#818cf8', '#ffffff')
];

const DEFAULT_ACCENT = 'navy';

/**
 * Ánh xạ KEY skin cũ → mới (đổi tên khi bỏ thương hiệu cũ).
 * Người dùng đã lưu 'vmo' trong localStorage hoặc cột theme_accent vẫn giữ đúng bộ màu cam,
 * không bị rơi về mặc định.
 */
const LEGACY_ACCENTS: Record<string, string> = { vmo: 'orange' };
const normalizeAccent = (key: string | null | undefined): string | null =>
  key ? (LEGACY_ACCENTS[key] ?? key) : null;
const ACCENT_KEYS = new Set(ACCENTS.map((a) => a.key));

/**
 * Quản lý giao diện: chế độ Sáng/Tối (data-theme) + bộ màu accent (data-accent).
 * Áp vào <html>, lưu localStorage, và LƯU THEO TÀI KHOẢN qua PUT /api/v1/me/theme (best-effort).
 * ĐỒNG BỘ: effect() theo auth.currentUser() — đăng nhập / F5 khôi phục có themeAccent/themeMode
 * thì ưu tiên giá trị server. ThemeService inject AuthService (1 chiều) để tránh vòng lặp DI.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private http = inject(HttpClient);
  private auth = inject(AuthService);

  readonly theme = signal<Theme>('light');
  readonly accent = signal<string>(DEFAULT_ACCENT);
  readonly accents = ACCENTS;
  /** Alias rõ nghĩa cho gallery skin (cùng nguồn với `accents`). */
  get presets(): readonly AccentDef[] {
    return ACCENTS;
  }

  constructor() {
    // --- Khôi phục từ localStorage + áp attribute ngay (trước khi biết tài khoản) ---
    let initialTheme: Theme = 'light';
    let initialAccent = DEFAULT_ACCENT;
    try {
      const savedTheme = localStorage.getItem(STORAGE_KEY) as Theme | null;
      if (savedTheme === 'light' || savedTheme === 'dark') {
        initialTheme = savedTheme;
      } else if (window.matchMedia?.('(prefers-color-scheme: dark)').matches) {
        initialTheme = 'dark';
      }
      const savedAccent = normalizeAccent(localStorage.getItem(ACCENT_KEY));
      if (savedAccent && ACCENT_KEYS.has(savedAccent)) {
        initialAccent = savedAccent;
      }
    } catch {
      /* localStorage không khả dụng — giữ mặc định */
    }
    this.applyMode(initialTheme);
    this.applyAccent(initialAccent);

    // --- Đồng bộ theo tài khoản: server ưu tiên khi ĐỔI tài khoản (đăng nhập / F5 khôi phục).
    // CHỈ phụ thuộc currentUser(); đọc accent()/theme() trong untracked() để effect KHÔNG chạy lại
    // mỗi khi người dùng tự đổi màu (tránh "giành" revert về giá trị server cũ gây giật/đơ). ---
    effect(() => {
      const user = this.auth.currentUser();
      if (!user) return;
      untracked(() => {
        const fromServer = normalizeAccent(user.themeAccent);
        if (fromServer && ACCENT_KEYS.has(fromServer) && fromServer !== this.accent()) {
          this.applyAccent(fromServer);
        }
        if ((user.themeMode === 'light' || user.themeMode === 'dark') && user.themeMode !== this.theme()) {
          this.applyMode(user.themeMode);
        }
      });
    });
  }

  toggle(): void {
    this.setMode(this.theme() === 'dark' ? 'light' : 'dark');
  }

  /** Đổi chế độ Sáng/Tối: áp + lưu localStorage + lưu BE (best-effort). */
  setMode(theme: Theme): void {
    this.applyMode(theme);
    this.persist();
  }

  /** Đổi bộ màu accent: áp + lưu localStorage + lưu BE (best-effort). */
  setAccent(key: string): void {
    if (!ACCENT_KEYS.has(key)) return;
    this.applyAccent(key);
    this.persist();
  }

  private applyMode(theme: Theme): void {
    this.theme.set(theme);
    document.documentElement.dataset['theme'] = theme;
    try {
      localStorage.setItem(STORAGE_KEY, theme);
    } catch {
      /* bỏ qua nếu không lưu được */
    }
  }

  private applyAccent(key: string): void {
    this.accent.set(key);
    document.documentElement.dataset['accent'] = key;
    try {
      localStorage.setItem(ACCENT_KEY, key);
    } catch {
      /* bỏ qua nếu không lưu được */
    }
  }

  /** Gửi tùy chọn hiện tại lên BE — nuốt lỗi (chưa đăng nhập / mạng) để không chặn UI. */
  private persist(): void {
    // Cập nhật currentUser cục bộ cho khớp (effect không revert; nhất quán khi F5 chưa kịp gọi /me).
    const u = this.auth.currentUser();
    if (u) {
      this.auth.currentUser.set({ ...u, themeAccent: this.accent(), themeMode: this.theme() });
    }
    this.http
      .put('/api/v1/me/theme', { accent: this.accent(), mode: this.theme() }, { withCredentials: true })
      .subscribe({ next: () => {}, error: () => {} });
  }
}
