import { Component, input, model } from '@angular/core';

export interface TabItem {
  key: string;
  label: string;
  icon?: string;
}

/** Thanh tab dùng chung (tham khảo DMS #10–11). Hai chiều qua [(active)]; cha tự render panel. */
@Component({
  selector: 'app-tabs',
  template: `
    <div class="tabs">
      @for (t of tabs(); track t.key) {
        <button type="button" class="tab" [class.active]="active() === t.key" (click)="active.set(t.key)">
          @if (t.icon) { <span>{{ t.icon }}</span> } {{ t.label }}
        </button>
      }
    </div>
  `
})
export class Tabs {
  readonly tabs = input<TabItem[]>([]);
  readonly active = model<string>('');
}
