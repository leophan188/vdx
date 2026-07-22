import { Component, input } from '@angular/core';

/**
 * Dấu hiệu thương hiệu "Plan X" — chữ X dựng bằng 3 mảng khối:
 *  · nét "\" xanh dương (gradient) — thân chính
 *  · nửa trên nét "/" xanh ngọc — mũi hướng lên (tiến độ, phát triển)
 *  · nửa dưới nét "/" xanh navy đậm — nền tảng, hệ thống
 *
 * Dùng: <brand-mark />, <brand-mark size="lg" />, <brand-mark size="sm" />
 * Mỗi thể hiện tự sinh id gradient RIÊNG: nhiều logo trên cùng trang mà trùng id thì
 * SVG sau sẽ ăn gradient của SVG trước (lỗi hay gặp khi copy-paste defs).
 */
@Component({
  selector: 'brand-mark',
  standalone: true,
  template: `
    <span class="bmk" [class.bmk--lg]="size() === 'lg'" [class.bmk--sm]="size() === 'sm'" aria-hidden="true">
      <svg viewBox="0 0 48 48" role="img">
        <defs>
          <linearGradient [attr.id]="'bmk-blue-' + uid" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#3B82F6" />
            <stop offset="100%" stop-color="#1D4ED8" />
          </linearGradient>
          <linearGradient [attr.id]="'bmk-teal-' + uid" x1="0" y1="1" x2="1" y2="0">
            <stop offset="0%" stop-color="#2DD4BF" />
            <stop offset="100%" stop-color="#22B8CF" />
          </linearGradient>
          <linearGradient [attr.id]="'bmk-navy-' + uid" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#25456F" />
            <stop offset="100%" stop-color="#16233A" />
          </linearGradient>
        </defs>
        <!-- Thứ tự vẽ: navy → teal → blue, để nét xanh dương liền mạch vắt qua tâm như thiết kế. -->
        <!-- Nhánh DƯỚI-PHẢI: navy (nền tảng, hệ thống) -->
        <path [attr.fill]="'url(#bmk-navy-' + uid + ')'"
              d="M19.2 24H28.8L42 43H32.4z" />
        <!-- Nhánh TRÊN-PHẢI: xanh ngọc (mũi hướng lên) -->
        <path [attr.fill]="'url(#bmk-teal-' + uid + ')'"
              d="M32.4 5H42L28.8 24H19.2z" />
        <!-- Hai nhánh TRÁI tạo hình "<" liền mạch: xanh dương -->
        <path [attr.fill]="'url(#bmk-blue-' + uid + ')'"
              d="M6 5h9.6l13.2 19H19.2zM19.2 24H28.8L15.6 43H6z" />
      </svg>
    </span>
  `,
  styles: [`
    .bmk { display: inline-flex; width: 34px; height: 34px; flex: none; }
    .bmk svg { width: 100%; height: 100%; display: block; }
    .bmk--lg { width: 52px; height: 52px; }
    .bmk--sm { width: 26px; height: 26px; }
  `]
})
export class BrandMark {
  private static seq = 0;
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  /** Hậu tố id gradient, duy nhất theo từng thể hiện. */
  readonly uid = String(BrandMark.seq++);
}
