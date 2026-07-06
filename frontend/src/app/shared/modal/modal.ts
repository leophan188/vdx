import { Component, input, output, HostListener } from '@angular/core';

/**
 * Modal/Dialog dùng chung cho popup CRUD (DESIGN-SYSTEM §3).
 * Dùng: <app-modal [open] title="…" (closed)="…"> nội dung <div modalFooter>…</div> </app-modal>
 * ESC hoặc click nền → đóng. Nội dung chiếu vào body; nút ở slot [modalFooter].
 */
@Component({
  selector: 'app-modal',
  template: `
    @if (open()) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" [class.modal--wide]="wide()" [class.modal--xwide]="xwide()" role="dialog" aria-modal="true">
          <div class="modal__header">
            <span class="modal__title">{{ title() }}</span>
            <button type="button" class="modal__close" aria-label="Đóng" (click)="closed.emit()">×</button>
          </div>
          <div class="modal__body">
            <ng-content />
          </div>
          <div class="modal__footer">
            <ng-content select="[modalFooter]" />
          </div>
        </div>
      </div>
    }
  `
})
export class Modal {
  readonly open = input(false);
  readonly title = input('');
  readonly wide = input(false);
  readonly xwide = input(false); // rộng hơn nữa — cho bảng nhiều cột (vd xem trước import)
  readonly closed = output<void>();

  onBackdrop(e: MouseEvent): void {
    if ((e.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.closed.emit();
    }
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEsc(e: Event): void {
    if (!this.open()) {
      return;
    }
    // Đang gõ trong ô nhập (input/textarea/select/contenteditable) → Esc CHỈ rời khỏi ô đó,
    // KHÔNG đóng popup. Nhấn Esc lần nữa (khi không còn ở ô nhập) mới đóng.
    const el = document.activeElement as HTMLElement | null;
    if (el && this.isEditable(el)) {
      el.blur();
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    this.closed.emit();
  }

  private isEditable(el: HTMLElement): boolean {
    const tag = el.tagName;
    return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable;
  }
}
