import { Component, input, output } from '@angular/core';

export interface TypeChip { value: string; label: string; }

/**
 * Bộ lọc loại task dạng CHIP — DÙNG CHUNG cho Backlog & Timeline (đồng bộ thiết kế, tránh lệch).
 * options: danh sách {value,label}; selected: Set value đang bật; toggle: phát value khi bấm.
 */
@Component({
  selector: 'app-type-filter',
  standalone: true,
  template: `
    @for (o of options(); track o.value) {
      <button type="button" class="tf-chip" [class.tf-chip--on]="selected().has(o.value)"
              (click)="toggle.emit(o.value)">{{ o.label }}</button>
    }
  `,
  styles: [`
    :host { display: inline-flex; gap: 4px; flex-wrap: wrap; align-items: center; }
    .tf-chip {
      padding: 4px var(--space-3); border: 1px solid var(--color-border); border-radius: var(--radius-full);
      background: var(--color-surface); color: var(--color-text-muted); cursor: pointer;
      font-size: var(--font-size-sm, var(--text-sm)); line-height: 1.4;
    }
    .tf-chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .tf-chip--on {
      background: var(--color-primary-soft); border-color: var(--color-primary);
      color: var(--color-primary); font-weight: 600;
    }
  `]
})
export class TypeFilter {
  readonly options = input<TypeChip[]>([]);
  readonly selected = input<Set<string>>(new Set());
  readonly toggle = output<string>();
}
