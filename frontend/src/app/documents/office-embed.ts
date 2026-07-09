import { Component, OnInit, OnDestroy, inject, input, signal } from '@angular/core';
import { DocumentService, EditorConfig } from '../core/document.service';
import { ToastService } from '../shared/toast/toast.service';

/**
 * Nhúng trình soạn thảo OnlyOffice cho MỘT tài liệu (dùng trong modal xử lý việc — soạn thảo theo bước).
 * Gọn: chỉ editor, không route/sidebar. Mount vào 1 div id duy nhất; huỷ editor khi rời.
 */
@Component({
  selector: 'app-office-embed',
  styles: [`
    /* Đúng khuôn editor toàn trang đang chạy tốt: cột flex chiều cao xác định + khung flex:1;min-height:0.
       KHÔNG đặt height:calc() thẳng lên khung — trong modal (grid/overflow) iframe hay phân giải sai chiều
       cao → vùng soạn thảo của OnlyOffice sập 0/đen. */
    /* min-height:0 để embed co vừa khít khoảng trống → KHÔNG tràn body sinh thanh cuộn ngoài;
       tài liệu dài đã có thanh cuộn nội bộ của OnlyOffice lo. */
    :host { display:flex; flex-direction:column; width:100%; flex:1 1 auto; min-height:0; }
    .oo-bar { display:flex; justify-content:flex-end; margin-bottom:6px; }
    .oo-frame { flex:1; min-height:0; width:100%; border:1px solid var(--color-border); border-radius:8px; overflow:hidden; background:var(--color-surface-alt); }
    .oo-frame > iframe { width:100%; height:100%; border:0; display:block; }
  `],
  template: `
    @if (error()) {
      <div class="alert alert--error" style="margin:0 0 8px">{{ error() }}</div>
    }
    <div class="oo-bar">
      <button type="button" class="btn btn--primary btn--sm" (click)="save()">💾 Lưu tài liệu</button>
    </div>
    <div [id]="containerId" class="oo-frame"></div>
  `
})
export class OfficeEmbed implements OnInit, OnDestroy {
  private svc = inject(DocumentService);
  private toast = inject(ToastService);

  /** Nút Lưu cứng: yêu cầu OnlyOffice ghi ngay về kho. */
  save(): void {
    this.svc.forceSave(this.docId()).subscribe({
      next: () => this.toast.success('Đã lưu tài liệu'),
      error: () => this.toast.error('Không lưu được tài liệu')
    });
  }

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
        // OnlyOffice nhúng hay render canvas ĐEN khi container đổi kích thước (đổi tab / inset modal) hoặc khi
        // trình soạn đo container lúc chưa xong layout. Cách chắc ăn: đợi editor báo SẴN SÀNG rồi mới ép
        // re-layout — dispatch resize LÚC container còn 0px (trước 1.5s trên server nguội) không có tác dụng.
        const config = {
          ...(cfg.config as Record<string, unknown>),
          width: '100%', height: '100%', type: 'desktop',
          events: {
            onAppReady: () => this.repaint(),
            onDocumentReady: () => this.repaint(),
          },
        };
        this.editor = new DocsAPI.DocEditor(this.containerId, config);
        // Phòng khi editor không bắn event (bản cũ) → vẫn ép re-layout vài nhịp sau mount.
        [400, 1200, 2500].forEach((ms) => setTimeout(() => this.repaint(), ms));
      })
      .catch(() => this.error.set('Không kết nối được OnlyOffice Document Server. Kiểm tra dịch vụ đang chạy.'));
  }

  /**
   * Ép trình soạn OnlyOffice vẽ lại vùng tài liệu (chống "canvas đen"). Chỉ bắn sự kiện `resize` là
   * không đủ khi kích thước container KHÔNG đổi — nên thay đổi thật 1px rồi trả lại để buộc reflow.
   */
  private repaint(): void {
    const el = document.getElementById(this.containerId);
    if (!el) return;
    // Bóp chiều cao thật đi 1px rồi trả lại (xoá inline → về đúng chiều cao theo CSS) để buộc reflow.
    el.style.height = (el.getBoundingClientRect().height - 1) + 'px';
    window.dispatchEvent(new Event('resize'));
    requestAnimationFrame(() => {
      el.style.height = '';
      window.dispatchEvent(new Event('resize'));
    });
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
