import { Component, input } from '@angular/core';

/** Chỉ báo bước cho wizard nhiều bước (tham khảo DMS #2–4). current là chỉ số bước (1-based). */
@Component({
  selector: 'app-stepper',
  template: `
    <div class="stepper">
      @for (s of steps(); track $index; let i = $index) {
        <div class="step" [class.is-active]="i + 1 === current()" [class.is-done]="i + 1 < current()">
          <span class="step__num">{{ i + 1 < current() ? '✓' : i + 1 }}</span>
          <span class="step__label">{{ s }}</span>
        </div>
        @if (!$last) { <span class="step__line" [class.is-done]="i + 1 < current()"></span> }
      }
    </div>
  `
})
export class Stepper {
  readonly steps = input<string[]>([]);
  readonly current = input(1);
}
