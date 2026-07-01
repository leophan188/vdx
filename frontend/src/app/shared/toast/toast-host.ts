import { Component, inject } from '@angular/core';
import { ToastService } from './toast.service';

/** Khu hiển thị toast (góc phải-trên). Đặt một lần ở app shell. */
@Component({
  selector: 'app-toast-host',
  template: `
    <div class="toast-host" role="status" aria-live="polite" aria-atomic="true">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast toast--{{ t.kind }}">
          <span class="toast__icon">{{ icon(t.kind) }}</span>
          <div class="toast__body">
            <strong>{{ t.title }}</strong>
            @if (t.text) { <span class="toast__text">{{ t.text }}</span> }
          </div>
          <button type="button" class="toast__close" (click)="toast.dismiss(t.id)" aria-label="Đóng">×</button>
        </div>
      }
    </div>
  `
})
export class ToastHost {
  protected readonly toast = inject(ToastService);
  protected icon(kind: string): string {
    return kind === 'success' ? '✓' : kind === 'warning' ? '⚠' : kind === 'error' ? '✕' : 'ℹ';
  }
}
