import { Component, OnInit, OnDestroy, inject, input, signal } from '@angular/core';
import { DocumentService, EditorConfig } from '../core/document.service';

/**
 * Nhúng trình soạn thảo OnlyOffice cho MỘT tài liệu (dùng trong modal xử lý việc — soạn thảo theo bước).
 * Gọn: chỉ editor, không route/sidebar. Mount vào 1 div id duy nhất; huỷ editor khi rời.
 */
@Component({
  selector: 'app-office-embed',
  styles: [`
    :host { display:block; width:100%; }
    .oo-frame { width:100%; height:calc(100vh - 210px); min-height:480px; border:1px solid var(--color-border); border-radius:8px; overflow:hidden; background:#fff; }
  `],
  template: `
    @if (error()) {
      <div class="alert alert--error" style="margin:0 0 8px">{{ error() }}</div>
    }
    <div [id]="containerId" class="oo-frame"></div>
  `
})
export class OfficeEmbed implements OnInit, OnDestroy {
  private svc = inject(DocumentService);

  /** Id tài liệu cần soạn. */
  readonly docId = input.required<string>();
  readonly error = signal<string | null>(null);

  private static seq = 0;
  readonly containerId = 'oo-embed-' + (++OfficeEmbed.seq);
  private editor?: { destroyEditor?: () => void };
  private saveTimer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.svc.editorConfig(this.docId()).subscribe({
      next: (cfg) => this.mount(cfg),
      error: () => this.error.set('Không lấy được cấu hình soạn thảo tài liệu.')
    });
    // Tự lưu định kỳ khi editor còn mở (phiên OnlyOffice còn sống → forcesave ghi được về kho).
    this.saveTimer = setInterval(() => this.svc.forceSave(this.docId()).subscribe({ error: () => {} }), 30000);
  }

  private mount(cfg: EditorConfig): void {
    const apiUrl = cfg.documentServerUrl.replace(/\/$/, '') + '/web-apps/apps/api/documents/api.js';
    this.loadScript(apiUrl)
      .then(() => {
        const DocsAPI = (window as unknown as { DocsAPI?: { DocEditor: new (id: string, c: unknown) => { destroyEditor?: () => void } } }).DocsAPI;
        if (!DocsAPI) { this.error.set('Không tải được OnlyOffice (DocsAPI).'); return; }
        const config = { ...(cfg.config as Record<string, unknown>), width: '100%', height: '100%', type: 'desktop' };
        this.editor = new DocsAPI.DocEditor(this.containerId, config);
        // OnlyOffice nhúng đôi khi render canvas ĐEN khi container vừa đổi kích thước (đổi tab/inset modal)
        // → ép re-layout vài lần sau khi mount.
        [300, 800, 1500].forEach((ms) => setTimeout(() => window.dispatchEvent(new Event('resize')), ms));
      })
      .catch(() => this.error.set('Không kết nối được OnlyOffice Document Server. Kiểm tra dịch vụ đang chạy.'));
  }

  private loadScript(src: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if ((window as unknown as { DocsAPI?: unknown }).DocsAPI) { resolve(); return; }
      const existing = document.querySelector(`script[src="${src}"]`) as HTMLScriptElement | null;
      if (existing) {
        existing.addEventListener('load', () => resolve());
        existing.addEventListener('error', () => reject());
        return;
      }
      const s = document.createElement('script');
      s.src = src;
      s.async = true;
      s.onload = () => resolve();
      s.onerror = () => reject();
      document.body.appendChild(s);
    });
  }

  ngOnDestroy(): void {
    if (this.saveTimer) clearInterval(this.saveTimer);
    // Rời editor (đóng popup việc) → yêu cầu OnlyOffice lưu ngay (forcesave) trước khi huỷ.
    try { this.svc.forceSave(this.docId()).subscribe({ error: () => {} }); } catch { /* ignore */ }
    try { this.editor?.destroyEditor?.(); } catch { /* ignore */ }
  }
}
