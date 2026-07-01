import { Directive, TemplateRef, inject, input } from '@angular/core';

/**
 * Khai báo template render tùy biến cho một cột của <data-grid>.
 * Dùng: <ng-template gridCell="status" let-row> <status-badge … /> </ng-template>
 */
@Directive({ selector: '[gridCell]' })
export class GridCellDirective {
  /** Khóa cột mà template này render. */
  readonly columnKey = input.required<string>({ alias: 'gridCell' });
  readonly tpl = inject(TemplateRef);
}
