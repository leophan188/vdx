import { Component, computed, input } from '@angular/core';

/** Avatar chữ-cái dùng chung (topbar, ô bảng người dùng). Màu nền theo token, mặc định primary. */
@Component({
  selector: 'app-avatar',
  template: `<span class="avatar" [style.width.px]="size()" [style.height.px]="size()"
                   [style.font-size.px]="size() * 0.42" [style.background]="bg()">{{ initials() }}</span>`
})
export class Avatar {
  readonly name = input('');
  readonly size = input(34);
  readonly bg = input('var(--color-primary)');

  protected readonly initials = computed(() => {
    const parts = this.name().trim().split(/\s+/).filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0][0].toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  });
}
