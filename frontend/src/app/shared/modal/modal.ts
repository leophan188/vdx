import { Component, ElementRef, HostListener, effect, inject, input, output, signal } from '@angular/core';

/**
 * Modal/Dialog dùng chung cho popup CRUD (DESIGN-SYSTEM §3).
 * Dùng: <app-modal [open] title="…" (closed)="…"> nội dung <div modalFooter>…</div> </app-modal>
 * ESC / click nền → đóng. NHƯNG nếu đang có dữ liệu nhập dở (ô nhập có nội dung) thì HỎI xác nhận
 * trước khi đóng, tránh mất dữ liệu. Nút × và nút ở footer là hành động đóng có chủ đích → đóng thẳng.
 */
@Component({
  selector: 'app-modal',
  template: `
    @if (open()) {
      <div class="modal-backdrop" (click)="onBackdrop($event)">
        <div class="modal" [class.modal--wide]="wide()" [class.modal--xwide]="xwide()" [class.modal--full]="full()" role="dialog" aria-modal="true"
             style="position:relative;">
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

          @if (askClose()) {
            <div class="modal__guard"
                 style="position:absolute;inset:0;display:flex;align-items:center;justify-content:center;
                        background:rgba(0,0,0,.45);border-radius:inherit;z-index:5;">
              <div style="background:var(--color-surface,#fff);color:var(--color-text,#111);max-width:360px;
                          padding:var(--space-4,20px);border-radius:var(--radius-lg,10px);
                          box-shadow:0 10px 40px rgba(0,0,0,.35);text-align:center;">
                <p style="margin:0 0 4px;font-weight:600;">Đóng cửa sổ?</p>
                <p style="margin:0 0 16px;font-size:.9em;opacity:.8;">Bạn đang nhập dữ liệu — đóng lại sẽ mất phần chưa lưu.</p>
                <div style="display:flex;gap:8px;justify-content:center;">
                  <button type="button" class="btn btn--ghost" (click)="askClose.set(false)">Tiếp tục nhập</button>
                  <button type="button" class="btn btn--danger" (click)="confirmClose()">Đóng, bỏ nhập</button>
                </div>
              </div>
            </div>
          }
        </div>
      </div>
    }
  `
})
export class Modal {
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly open = input(false);
  readonly title = input('');
  readonly wide = input(false);
  readonly xwide = input(false); // rộng hơn nữa — cho bảng nhiều cột (vd xem trước import)
  readonly full = input(false);  // toàn màn hình — cho màn xử lý việc/soạn thảo tài liệu
  readonly closed = output<void>();

  /** Đang hỏi xác nhận đóng (khi có dữ liệu nhập dở). */
  readonly askClose = signal(false);

  constructor() {
    // Mỗi lần mở lại → bỏ trạng thái hỏi cũ.
    effect(() => {
      if (!this.open()) this.askClose.set(false);
    });
  }

  onBackdrop(e: MouseEvent): void {
    if ((e.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.attemptClose();
    }
  }

  @HostListener('document:keydown.escape', ['$event'])
  onEsc(e: Event): void {
    if (!this.open()) {
      return;
    }
    // Nếu đang hỏi xác nhận → Esc = tiếp tục nhập (đóng hộp hỏi).
    if (this.askClose()) {
      this.askClose.set(false);
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    // Đang gõ trong ô nhập → Esc CHỈ rời khỏi ô đó, không đóng popup.
    const el = document.activeElement as HTMLElement | null;
    if (el && this.isEditable(el)) {
      el.blur();
      e.preventDefault();
      e.stopPropagation();
      return;
    }
    e.preventDefault();
    e.stopPropagation();
    this.attemptClose();
  }

  /** Đóng có kiểm tra: còn dữ liệu nhập dở thì hỏi, không thì đóng thẳng. */
  private attemptClose(): void {
    if (this.askClose()) {
      return;
    }
    if (this.hasDirtyInput()) {
      this.askClose.set(true);
    } else {
      this.closed.emit();
    }
  }

  confirmClose(): void {
    this.askClose.set(false);
    this.closed.emit();
  }

  private isEditable(el: HTMLElement): boolean {
    const tag = el.tagName;
    return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || el.isContentEditable;
  }

  /** Có ô nhập nào (đang hiển thị, sửa được) đang chứa dữ liệu người dùng không. */
  private hasDirtyInput(): boolean {
    const body = this.host.nativeElement.querySelector('.modal__body') as HTMLElement | null;
    if (!body) {
      return false;
    }
    const els = body.querySelectorAll('input, textarea, [contenteditable="true"]');
    for (const node of Array.from(els)) {
      const el = node as HTMLElement;
      if (el.offsetParent === null) {
        continue; // đang ẩn
      }
      if (el instanceof HTMLInputElement) {
        if (el.readOnly || el.disabled) continue;
        if (el.type === 'checkbox' || el.type === 'radio') {
          if (el.checked) return true;
          continue;
        }
        if (el.type === 'file') {
          if (el.files && el.files.length) return true;
          continue;
        }
        if ((el.value || '').trim()) return true;
      } else if (el instanceof HTMLTextAreaElement) {
        if (!el.readOnly && !el.disabled && (el.value || '').trim()) return true;
      } else if (el.isContentEditable) {
        if ((el.textContent || '').trim()) return true;
      }
    }
    return false;
  }
}
