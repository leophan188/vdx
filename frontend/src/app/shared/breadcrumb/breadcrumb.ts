import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

export interface Crumb {
  label: string;
  link?: string;
}

/** Dải breadcrumb (tham khảo DMS): 🏠 | mục cha / mục hiện tại. */
@Component({
  selector: 'app-breadcrumb',
  imports: [RouterLink],
  template: `
    <nav class="breadcrumb" aria-label="breadcrumb">
      <a routerLink="/dashboard" class="breadcrumb__home" title="Trang chủ">🏠</a>
      @for (c of items(); track c.label; let last = $last) {
        <span class="breadcrumb__sep">/</span>
        @if (c.link && !last) {
          <a [routerLink]="c.link" class="breadcrumb__item">{{ c.label }}</a>
        } @else {
          <span class="breadcrumb__item" [class.is-current]="last">{{ c.label }}</span>
        }
      }
    </nav>
  `
})
export class Breadcrumb {
  readonly items = input<Crumb[]>([]);
}
