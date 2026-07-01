import { Directive, TemplateRef, inject } from '@angular/core';

/**
 * Template hàng chi tiết mở rộng của <data-grid> (khi [expandable]=true).
 * Dùng: <ng-template gridDetail let-row> …bảng con / chi tiết… </ng-template>
 */
@Directive({ selector: '[gridDetail]' })
export class GridDetailDirective {
  readonly tpl = inject(TemplateRef);
}
