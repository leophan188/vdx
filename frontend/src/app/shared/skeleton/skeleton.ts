import { Component, computed, input } from '@angular/core';

/**
 * Khung xương (skeleton) tải dữ liệu — vài dòng xám nhấp nháy thay cho "Đang tải…".
 * Dùng: <app-skeleton [rows]="6" /> (mặc định 5 dòng). Mỗi dòng bề rộng so le cho tự nhiên.
 * Khi rỗng/empty-state dùng <app-empty-state> bên dưới.
 */
@Component({
  selector: 'app-skeleton',
  template: `
    <div class="sk" role="status" aria-busy="true" aria-live="polite">
      <span class="sk__sr">Đang tải…</span>
      @for (w of widths(); track $index) {
        <div class="sk__row" [style.width.%]="w"></div>
      }
    </div>
  `,
  styles: [`
    .sk { display: flex; flex-direction: column; gap: var(--space-3); padding: var(--space-2) 0; width: 100%; }
    .sk__row {
      height: 14px; border-radius: var(--radius-sm);
      background: linear-gradient(90deg,
        var(--color-surface-alt) 25%,
        color-mix(in srgb, var(--color-surface-alt) 55%, var(--color-border)) 50%,
        var(--color-surface-alt) 75%);
      background-size: 200% 100%;
      animation: sk-shimmer 1.3s ease-in-out infinite;
    }
    .sk__sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
    @keyframes sk-shimmer { 0% { background-position: 200% 0; } 100% { background-position: -200% 0; } }
    @media (prefers-reduced-motion: reduce) { .sk__row { animation: none; } }
  `]
})
export class Skeleton {
  /** Số dòng vẽ. */
  readonly rows = input(5);
  /** Bề rộng so le cho mỗi dòng (%), lặp theo số dòng. */
  protected readonly widths = computed(() => {
    const pattern = [100, 92, 96, 78, 88, 70];
    const n = Math.max(1, this.rows());
    return Array.from({ length: n }, (_, i) => pattern[i % pattern.length]);
  });
}

/**
 * Trạng thái rỗng — icon + tiêu đề + gợi ý hành động. Nút hành động qua slot [emptyAction].
 * Dùng: <app-empty-state icon="📁" title="Chưa có dự án" hint="Bấm Tạo dự án để bắt đầu." />
 */
@Component({
  selector: 'app-empty-state',
  template: `
    <div class="empty">
      <div class="empty__icon" aria-hidden="true">{{ icon() }}</div>
      <div class="empty__title">{{ title() }}</div>
      @if (hint()) { <div class="empty__hint">{{ hint() }}</div> }
      <div class="empty__action"><ng-content select="[emptyAction]" /></div>
    </div>
  `,
  styles: [`
    .empty {
      display: flex; flex-direction: column; align-items: center; justify-content: center;
      gap: var(--space-2); text-align: center; padding: var(--space-10) var(--space-4);
      color: var(--color-text-muted);
    }
    .empty__icon { font-size: 40px; line-height: 1; opacity: .85; }
    .empty__title { font-size: var(--text-base); font-weight: var(--weight-semibold); color: var(--color-text); }
    .empty__hint { font-size: var(--text-sm); max-width: 360px; }
    .empty__action:empty { display: none; }
    .empty__action { margin-top: var(--space-2); }
  `]
})
export class EmptyState {
  readonly icon = input('📭');
  readonly title = input('Chưa có dữ liệu');
  readonly hint = input('');
}
