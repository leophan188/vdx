import { Component, ElementRef, HostListener, computed, effect, input, output, signal, viewChild } from '@angular/core';

/** Một phần tử xem trong lightbox. kind mặc định IMAGE. */
export interface LightboxItem {
  url: string;
  name?: string;
  kind?: 'IMAGE' | 'VIDEO';
}

/**
 * Lightbox xem ảnh/video dùng CHUNG (MXH + đính kèm task/bug).
 * Zoom: nút ＋/－/reset, con lăn chuột (theo con trỏ), nhấp đúp, kéo để di chuyển, chụm 2 ngón (mobile).
 * Điều hướng ‹ ›, phím tắt Esc/←/→/＋/－/0. Mở/đóng qua input `index` (null = đóng), phát `closed`.
 */
@Component({
  selector: 'app-image-lightbox',
  standalone: true,
  template: `
    @if (isOpen()) {
      <div class="lbx" (click)="close()" (wheel)="onWheel($event)">
        <!-- Thanh công cụ zoom -->
        <div class="lbx__tools" (click)="$event.stopPropagation()">
          <button type="button" class="lbx__tbtn" (click)="zoomOut()" [disabled]="!canImage()" title="Thu nhỏ (−)">−</button>
          <button type="button" class="lbx__pct" (click)="resetZoom()" [disabled]="!canImage()" title="Về 100% (0)">{{ pct() }}%</button>
          <button type="button" class="lbx__tbtn" (click)="zoomIn()" [disabled]="!canImage()" title="Phóng to (+)">＋</button>
        </div>
        <button type="button" class="lbx__close" (click)="close()" aria-label="Đóng" title="Đóng (Esc)">✕</button>

        @if (count() > 1) {
          <button type="button" class="lbx__nav lbx__nav--prev"
                  (click)="$event.stopPropagation(); prev()" aria-label="Ảnh trước" title="Trước (←)">‹</button>
          <button type="button" class="lbx__nav lbx__nav--next"
                  (click)="$event.stopPropagation(); next()" aria-label="Ảnh sau" title="Sau (→)">›</button>
        }

        <div #stage class="lbx__stage" (click)="$event.stopPropagation()">
          @if (item(); as it) {
            @if (canImage()) {
              <img class="lbx__media" [class.is-zoom]="scale() > 1" [class.is-drag]="dragging()"
                   [src]="it.url" [alt]="it.name || ''" draggable="false"
                   [style.transform]="transform()"
                   (pointerdown)="onDown($event)" (pointermove)="onMove($event)"
                   (pointerup)="onUp($event)" (pointercancel)="onUp($event)"
                   (dblclick)="toggleZoom($event)"
                   (touchstart)="onTouchStart($event)" (touchmove)="onTouchMove($event)" (touchend)="onTouchEnd($event)" />
            } @else {
              <video class="lbx__media" [src]="it.url" controls autoplay></video>
            }
          }
        </div>

        @if (count() > 1) {
          <span class="lbx__counter">{{ cur() + 1 }} / {{ count() }}</span>
        }
        @if (canImage() && scale() > 1) {
          <span class="lbx__hint">Kéo để di chuyển · nhấp đúp để thu về</span>
        }
      </div>
    }
  `,
  styles: [`
    .lbx { position: fixed; inset: 0; z-index: 1000; background: rgba(0,0,0,.9);
      display: flex; align-items: center; justify-content: center; user-select: none; }
    .lbx__stage { max-width: 96vw; max-height: 92vh; display: flex; align-items: center; justify-content: center;
      overflow: hidden; touch-action: none; }
    .lbx__media { max-width: 96vw; max-height: 92vh; object-fit: contain; display: block;
      transform-origin: center center; will-change: transform; -webkit-user-drag: none; }
    .lbx__media.is-zoom { cursor: grab; }
    .lbx__media.is-drag { cursor: grabbing; transition: none; }
    img.lbx__media { transition: transform .12s ease-out; }

    .lbx__tools { position: fixed; top: 14px; left: 50%; transform: translateX(-50%);
      display: flex; align-items: center; gap: 4px; background: rgba(0,0,0,.55);
      border: 1px solid rgba(255,255,255,.18); border-radius: 999px; padding: 4px; backdrop-filter: blur(4px); }
    .lbx__tbtn, .lbx__pct { background: transparent; color: #fff; border: none; cursor: pointer;
      font-size: 18px; line-height: 1; border-radius: 999px; }
    .lbx__tbtn { width: 34px; height: 34px; display: grid; place-items: center; }
    .lbx__pct { min-width: 58px; height: 34px; font-size: 13px; font-variant-numeric: tabular-nums; }
    .lbx__tbtn:hover, .lbx__pct:hover { background: rgba(255,255,255,.16); }
    .lbx__tbtn:disabled, .lbx__pct:disabled { opacity: .35; cursor: default; }

    .lbx__close { position: fixed; top: 14px; right: 16px; width: 40px; height: 40px; border-radius: 999px;
      background: rgba(0,0,0,.55); color: #fff; border: 1px solid rgba(255,255,255,.18); font-size: 18px;
      cursor: pointer; display: grid; place-items: center; }
    .lbx__close:hover { background: rgba(255,255,255,.16); }

    .lbx__nav { position: fixed; top: 50%; transform: translateY(-50%); width: 48px; height: 48px; border-radius: 999px;
      background: rgba(0,0,0,.5); color: #fff; border: 1px solid rgba(255,255,255,.18); font-size: 28px; line-height: 1;
      cursor: pointer; display: grid; place-items: center; }
    .lbx__nav:hover { background: rgba(255,255,255,.16); }
    .lbx__nav--prev { left: 16px; }
    .lbx__nav--next { right: 16px; }

    .lbx__counter { position: fixed; bottom: 16px; left: 50%; transform: translateX(-50%);
      color: #fff; font-size: 13px; background: rgba(0,0,0,.5); padding: 4px 12px; border-radius: 999px; }
    .lbx__hint { position: fixed; bottom: 46px; left: 50%; transform: translateX(-50%);
      color: rgba(255,255,255,.75); font-size: 12px; }

    @media (max-width: 640px) {
      .lbx__nav { width: 40px; height: 40px; font-size: 22px; }
      .lbx__hint { display: none; }
    }
  `]
})
export class ImageLightbox {
  /** Danh sách phần tử. */
  readonly items = input<LightboxItem[]>([]);
  /** Index đang mở; null = đóng. */
  readonly index = input<number | null>(null);
  /** Yêu cầu đóng (parent đặt index về null). */
  readonly closed = output<void>();

  private readonly stageRef = viewChild<ElementRef<HTMLElement>>('stage');

  readonly cur = signal(0);
  readonly scale = signal(1);
  readonly tx = signal(0);
  readonly ty = signal(0);
  readonly dragging = signal(false);

  private static readonly MIN = 1;
  private static readonly MAX = 6;

  // Trạng thái kéo (pointer) + chụm (touch)
  private dragStartX = 0; private dragStartY = 0; private baseTx = 0; private baseTy = 0;
  private pinchDist = 0; private pinchScale = 1;

  readonly item = computed(() => this.items()[this.cur()] ?? null);
  readonly count = computed(() => this.items().length);
  readonly isOpen = computed(() => this.index() !== null && this.item() !== null);
  readonly canImage = computed(() => (this.item()?.kind ?? 'IMAGE') === 'IMAGE');
  readonly pct = computed(() => Math.round(this.scale() * 100));
  readonly transform = computed(() => `translate(${this.tx()}px, ${this.ty()}px) scale(${this.scale()})`);

  constructor() {
    // Khi mở (index đổi từ parent) → nhảy tới ảnh đó + reset zoom.
    effect(() => {
      const i = this.index();
      if (i !== null) {
        this.cur.set(i);
        this.resetZoom();
      }
    });
  }

  close(): void { this.closed.emit(); }

  prev(): void {
    const n = this.count(); if (!n) return;
    this.cur.set((this.cur() - 1 + n) % n);
    this.resetZoom();
  }
  next(): void {
    const n = this.count(); if (!n) return;
    this.cur.set((this.cur() + 1) % n);
    this.resetZoom();
  }

  resetZoom(): void { this.scale.set(1); this.tx.set(0); this.ty.set(0); this.dragging.set(false); }

  private clamp(s: number): number { return Math.min(ImageLightbox.MAX, Math.max(ImageLightbox.MIN, s)); }

  zoomIn(): void { this.applyZoom(this.clamp(this.scale() * 1.3), null, null); }
  zoomOut(): void { this.applyZoom(this.clamp(this.scale() / 1.3), null, null); }

  toggleZoom(e: MouseEvent): void {
    if (!this.canImage()) return;
    if (this.scale() > 1) { this.resetZoom(); }
    else { this.applyZoom(2.5, e.clientX, e.clientY); }
  }

  /** Zoom về `target`, giữ điểm (cx,cy) theo màn hình đứng yên (null = giữ tâm). */
  private applyZoom(target: number, cx: number | null, cy: number | null): void {
    if (!this.canImage()) return;
    const stage = this.stageRef()?.nativeElement;
    const factor = target / this.scale();
    if (stage && cx !== null && cy !== null) {
      const r = stage.getBoundingClientRect();
      const px = cx - (r.left + r.width / 2);
      const py = cy - (r.top + r.height / 2);
      this.tx.set(px - (px - this.tx()) * factor);
      this.ty.set(py - (py - this.ty()) * factor);
    }
    this.scale.set(target);
    if (target <= 1) { this.tx.set(0); this.ty.set(0); }
  }

  onWheel(e: WheelEvent): void {
    if (!this.canImage()) return;
    e.preventDefault();
    const target = this.clamp(this.scale() * (e.deltaY < 0 ? 1.15 : 1 / 1.15));
    this.applyZoom(target, e.clientX, e.clientY);
  }

  // ===== Kéo di chuyển (pointer) =====
  onDown(e: PointerEvent): void {
    if (this.scale() <= 1) return;
    this.dragging.set(true);
    this.dragStartX = e.clientX; this.dragStartY = e.clientY;
    this.baseTx = this.tx(); this.baseTy = this.ty();
    (e.target as HTMLElement).setPointerCapture?.(e.pointerId);
  }
  onMove(e: PointerEvent): void {
    if (!this.dragging()) return;
    this.tx.set(this.baseTx + (e.clientX - this.dragStartX));
    this.ty.set(this.baseTy + (e.clientY - this.dragStartY));
  }
  onUp(e: PointerEvent): void {
    if (!this.dragging()) return;
    this.dragging.set(false);
    (e.target as HTMLElement).releasePointerCapture?.(e.pointerId);
  }

  // ===== Chụm 2 ngón (mobile) =====
  onTouchStart(e: TouchEvent): void {
    if (e.touches.length === 2) {
      this.pinchDist = this.dist(e.touches);
      this.pinchScale = this.scale();
    }
  }
  onTouchMove(e: TouchEvent): void {
    if (e.touches.length === 2 && this.pinchDist > 0) {
      e.preventDefault();
      const d = this.dist(e.touches);
      const mid = this.mid(e.touches);
      this.applyZoom(this.clamp(this.pinchScale * (d / this.pinchDist)), mid.x, mid.y);
    }
  }
  onTouchEnd(e: TouchEvent): void {
    if (e.touches.length < 2) this.pinchDist = 0;
  }
  private dist(t: TouchList): number {
    const dx = t[0].clientX - t[1].clientX, dy = t[0].clientY - t[1].clientY;
    return Math.hypot(dx, dy);
  }
  private mid(t: TouchList): { x: number; y: number } {
    return { x: (t[0].clientX + t[1].clientX) / 2, y: (t[0].clientY + t[1].clientY) / 2 };
  }

  // ===== Phím tắt =====
  @HostListener('document:keydown', ['$event'])
  onKey(e: KeyboardEvent): void {
    if (!this.isOpen()) return;
    switch (e.key) {
      case 'Escape': this.close(); break;
      case 'ArrowLeft': this.prev(); break;
      case 'ArrowRight': this.next(); break;
      case '+': case '=': this.zoomIn(); break;
      case '-': case '_': this.zoomOut(); break;
      case '0': this.resetZoom(); break;
    }
  }
}
