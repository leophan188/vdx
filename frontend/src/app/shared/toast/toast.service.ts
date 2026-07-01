import { Injectable, signal } from '@angular/core';

export type ToastKind = 'success' | 'info' | 'warning' | 'error';
export interface Toast {
  id: number;
  kind: ToastKind;
  title: string;
  text?: string;
}

/** Trung tâm thông báo toast (tham khảo DMS #12). Tự ẩn sau ~4s. Dùng ở mọi màn. */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  show(kind: ToastKind, title: string, text?: string): void {
    const id = ++this.seq;
    this.toasts.update((t) => [...t, { id, kind, title, text }]);
    setTimeout(() => this.dismiss(id), 4000);
  }
  success(title: string, text?: string): void { this.show('success', title, text); }
  info(title: string, text?: string): void { this.show('info', title, text); }
  warning(title: string, text?: string): void { this.show('warning', title, text); }
  error(title: string, text?: string): void { this.show('error', title, text); }

  dismiss(id: number): void {
    this.toasts.update((t) => t.filter((x) => x.id !== id));
  }
}
