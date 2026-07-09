import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastService } from '../shared/toast/toast.service';
import { DocumentService, EditorConfig } from '../core/document.service';

declare global {
  interface Window { DocsAPI?: { DocEditor: new (id: string, config: unknown) => { destroyEditor?: () => void } }; }
}

/** Trình soạn thảo OnlyOffice nhúng (Story 3.10) + bảng cộng tác (3.11–3.17). */
@Component({
  selector: 'app-doc-editor',
  imports: [],
  templateUrl: './doc-editor.html'
})
export class DocEditor implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private svc = inject(DocumentService);
  private toast = inject(ToastService);

  readonly id = this.route.snapshot.paramMap.get('id')!;
  readonly title = signal('Tài liệu');
  readonly error = signal<string | null>(null);
  private editor?: { destroyEditor?: () => void };
  private saveTimer?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.svc.editorConfig(this.id).subscribe({
      next: (cfg) => { this.title.set(cfg.name); this.mount(cfg); },
      error: () => this.error.set('Không lấy được cấu hình soạn thảo.')
    });
    // Tự lưu định kỳ khi đang mở (phiên còn sống → forcesave ghi được về kho).
    this.saveTimer = setInterval(() => this.svc.forceSave(this.id).subscribe({ error: () => {} }), 30000);
  }

  private mount(cfg: EditorConfig): void {
    const apiUrl = cfg.documentServerUrl.replace(/\/$/, '') + '/web-apps/apps/api/documents/api.js';
    this.loadScript(apiUrl)
      .then(() => {
        if (!window.DocsAPI) { this.error.set('Không tải được OnlyOffice (DocsAPI).'); return; }
        const config = { ...(cfg.config as Record<string, unknown>), width: '100%', height: '100%', type: 'desktop' };
        this.editor = new window.DocsAPI.DocEditor('onlyoffice-editor', config);
      })
      .catch(() => this.error.set('Không kết nối được OnlyOffice Document Server (:8082). Kiểm tra container đang chạy.'));
  }

  private loadScript(src: string): Promise<void> {
    return new Promise((resolve, reject) => {
      if (window.DocsAPI) { resolve(); return; }
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

  /** Nút Lưu cứng: yêu cầu OnlyOffice ghi ngay về kho. */
  save(): void {
    this.svc.forceSave(this.id).subscribe({
      next: () => this.toast.success('Đã lưu tài liệu'),
      error: () => this.toast.error('Không lưu được tài liệu')
    });
  }

  back(): void {
    // Lưu ngay trước khi rời (không chờ status=2 đóng editor).
    try { this.svc.forceSave(this.id).subscribe({ error: () => {} }); } catch { /* ignore */ }
    this.router.navigate(['/documents']);
  }

  ngOnDestroy(): void {
    if (this.saveTimer) clearInterval(this.saveTimer);
    try { this.svc.forceSave(this.id).subscribe({ error: () => {} }); } catch { /* ignore */ }
    try { this.editor?.destroyEditor?.(); } catch { /* ignore */ }
  }
}
