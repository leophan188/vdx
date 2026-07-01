import { Component, input } from '@angular/core';

/** Thẻ KPI cho dashboard (tham khảo DMS): icon nền pastel + số lớn + nhãn + xu hướng. */
@Component({
  selector: 'stat-card',
  template: `
    <div class="stat-card">
      <span class="stat-card__icon" [style.--tint]="tint()">{{ icon() }}</span>
      <div class="stat-card__body">
        <span class="stat-card__label">{{ label() }}</span>
        <span class="stat-card__value">{{ value() }}</span>
        @if (trend()) { <span class="stat-card__trend">↑ {{ trend() }}</span> }
      </div>
    </div>
  `
})
export class StatCard {
  readonly icon = input('📊');
  readonly label = input('');
  readonly value = input<string | number>('');
  readonly trend = input('');
  /** Màu nền pastel của icon (token màu trạng thái/primary). */
  readonly tint = input('var(--color-primary)');
}
