import { Component, input, output } from '@angular/core';
import { Modal } from '../modal/modal';

/** Hộp xác nhận xóa/hành động (DESIGN-SYSTEM). Dùng cho thao tác phá hủy/khó hoàn tác. */
@Component({
  selector: 'app-confirm-dialog',
  imports: [Modal],
  template: `
    <app-modal [open]="open()" [title]="title()" (closed)="cancel.emit()">
      <p class="modal__msg">{{ message() }}</p>
      <ng-container modalFooter>
        <button type="button" class="btn btn--ghost" (click)="cancel.emit()">Hủy</button>
        <button type="button" class="btn" [class.btn--danger]="danger()" [class.btn--primary]="!danger()"
                (click)="confirm.emit()">{{ confirmLabel() }}</button>
      </ng-container>
    </app-modal>
  `
})
export class ConfirmDialog {
  readonly open = input(false);
  readonly title = input('Xác nhận');
  readonly message = input('Bạn có chắc chắn?');
  readonly confirmLabel = input('Đồng ý');
  readonly danger = input(false);
  readonly confirm = output<void>();
  readonly cancel = output<void>();
}
