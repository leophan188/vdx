import { Component, input } from '@angular/core';
import { Breadcrumb, Crumb } from '../breadcrumb/breadcrumb';

/**
 * Tiêu đề trang chuẩn (tham khảo DMS): tiêu đề + mô tả (trái) · breadcrumb + nút hành động (phải).
 * Dùng đầu mọi màn: <app-page-header title="…" subtitle="…" [breadcrumb]="[…]"> <btn actions/> </app-page-header>
 */
@Component({
  selector: 'app-page-header',
  imports: [Breadcrumb],
  template: `
    <div class="page-header">
      <div class="page-header__main">
        <h1>{{ title() }}</h1>
        @if (subtitle()) { <p class="page-header__sub">{{ subtitle() }}</p> }
      </div>
      <div class="page-header__aside">
        @if (breadcrumb().length) { <app-breadcrumb [items]="breadcrumb()" /> }
        <span class="page-header__actions"><ng-content select="[actions]" /></span>
      </div>
    </div>
  `
})
export class PageHeader {
  readonly title = input('');
  readonly subtitle = input('');
  readonly breadcrumb = input<Crumb[]>([]);
}
