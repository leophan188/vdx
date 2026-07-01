import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { NotificationService, AppNotification } from '../../core/notification.service';

/** Khoảng poll cập nhật gần realtime (ms). */
const POLL_MS = 25000;

/** Chuông thông báo in-app trên topbar (Story 4.9): badge chưa đọc + dropdown danh sách. */
@Component({
  selector: 'app-notification-bell',
  imports: [],
  templateUrl: './notification-bell.html',
  styles: [`
    /* Rung nhẹ khi có thông báo mới (số chưa đọc tăng). */
    .noti__icon--ring { display: inline-block; transform-origin: 50% 0; animation: noti-shake 0.7s ease; }
    @keyframes noti-shake {
      0%, 100% { transform: rotate(0); }
      15% { transform: rotate(12deg); }
      30% { transform: rotate(-10deg); }
      45% { transform: rotate(8deg); }
      60% { transform: rotate(-6deg); }
      75% { transform: rotate(3deg); }
    }
    @media (prefers-reduced-motion: reduce) {
      .noti__icon--ring { animation: none; }
    }
    /* Bố cục item — SCOPED (thắng global, nạp kèm component) để CHỐNG ĐÈ CHỮ triệt để. */
    .noti__item { display: flex; gap: 10px; align-items: flex-start; position: static; }
    .noti__body { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1 1 auto; position: static; }
    .noti__body > strong { display: block; font-size: 13.5px; font-weight: 600; line-height: 1.35;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .noti__text { display: block; font-size: 12px; line-height: 1.45; color: var(--color-text-muted);
      white-space: normal; overflow-wrap: anywhere;
      display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
    .noti__time { display: block; font-size: 11px; line-height: 1.3; color: var(--color-text-muted); opacity: .8; white-space: nowrap; }
  `]
})
export class NotificationBell implements OnInit, OnDestroy {
  private svc = inject(NotificationService);
  private router = inject(Router);

  readonly open = signal(false);
  readonly items = signal<AppNotification[]>([]);
  readonly unread = signal(0);
  /** Bật animation rung chuông trong 1 nhịp khi có thông báo mới. */
  readonly ring = signal(false);
  private timer?: ReturnType<typeof setInterval>;
  private ringTimer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.refreshCount();
    // Cập nhật gần realtime (Story 4.7): poll im lặng mỗi ~25s, bỏ qua khi tab ẩn.
    this.timer = setInterval(() => this.poll(), POLL_MS);
  }
  ngOnDestroy(): void {
    if (this.timer) clearInterval(this.timer);
    if (this.ringTimer) clearTimeout(this.ringTimer);
  }

  /** Poll định kỳ im lặng: bỏ qua nếu tab ẩn để tiết kiệm; cập nhật badge + danh sách (nếu panel mở). */
  private poll(): void {
    if (typeof document !== 'undefined' && document.hidden) return;
    this.refreshCount();
    if (this.open()) {
      this.svc.list().subscribe({ next: (r) => this.items.set(r), error: () => {} });
    }
  }

  refreshCount(): void {
    this.svc.unreadCount().subscribe({
      next: (r) => {
        const prev = this.unread();
        this.unread.set(r.count);
        if (r.count > prev) this.pulse();
      },
      error: () => {}
    });
  }

  /** Kích hoạt animation rung chuông trong 1 nhịp ngắn. */
  private pulse(): void {
    this.ring.set(true);
    if (this.ringTimer) clearTimeout(this.ringTimer);
    this.ringTimer = setTimeout(() => this.ring.set(false), 800);
  }

  toggle(): void {
    const next = !this.open();
    this.open.set(next);
    if (next) {
      this.svc.list().subscribe({ next: (r) => this.items.set(r), error: () => {} });
    }
  }

  fmt(iso: string): string {
    return new Date(iso).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });
  }

  /** Rút gọn nội dung thông báo: bỏ xuống dòng + marker ẩn, gộp khoảng trắng, cắt ~110 ký tự. */
  preview(body: string | null | undefined): string {
    const s = (body ?? '')
      .replace(/[​‌﻿]*#(celebrate-[a-z]+-[\w.-]+|demo-mxh)\b/gi, '')
      .replace(/\s+/g, ' ')
      .trim();
    return s.length > 110 ? s.slice(0, 110).trimEnd() + '…' : s;
  }

  click(n: AppNotification): void {
    if (!n.read) {
      this.svc.markRead(n.id).subscribe({ next: () => this.refreshCount(), error: () => {} });
    }
    this.open.set(false);
    if (n.link) { this.router.navigateByUrl(n.link); }
  }

  markAll(): void {
    this.svc.markAllRead().subscribe({
      next: () => { this.unread.set(0); this.items.update((l) => l.map((n) => ({ ...n, read: true }))); },
      error: () => {}
    });
  }
}
