import { Component, OnInit, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ToastService } from '../shared/toast/toast.service';
import { DocumentService, EditorConfig } from '../core/document.service';
import { FormService, FormSummary } from '../core/form.service';
import { SearchableSelect, SelectOption } from '../shared/searchable-select/searchable-select';

interface OoConnector { callCommand: (fn: unknown, isNoCalc?: boolean) => void; }
interface OoEditor { destroyEditor?: () => void; createConnector?: () => OoConnector; }
declare global {
  interface Window { DocsAPI?: { DocEditor: new (id: string, config: unknown) => OoEditor }; }
}

/** Trình soạn thảo OnlyOffice nhúng (Story 3.10) + chèn mã trộn dữ liệu từ biểu mẫu (mail-merge). */
@Component({
  selector: 'app-doc-editor',
  imports: [FormsModule, SearchableSelect],
  templateUrl: './doc-editor.html'
})
export class DocEditor implements OnInit, OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private svc = inject(DocumentService);
  private formSvc = inject(FormService);
  private toast = inject(ToastService);

  readonly id = this.route.snapshot.paramMap.get('id')!;
  readonly title = signal('Tài liệu');
  readonly error = signal<string | null>(null);
  private editor?: OoEditor;
  private connector?: OoConnector;
  private saveTimer?: ReturnType<typeof setInterval>;

  // ----- Chèn mã trộn dữ liệu (chỉ hiện với tài liệu MẪU — instanceId null) -----
  readonly isTemplate = signal(false);
  readonly forms = signal<FormSummary[]>([]);
  readonly selectedFormId = signal<string>('');
  readonly mergeFields = signal<{ key: string; label: string }[]>([]);
  /** Options cho ô chọn biểu mẫu có LỌC theo tên (searchable-select tự lọc không dấu). */
  readonly formOptions = computed<SelectOption[]>(() =>
    this.forms().map((f) => ({ value: f.id, label: f.name, sub: f.formKey })));

  ngOnInit(): void {
    this.svc.editorConfig(this.id).subscribe({
      next: (cfg) => { this.title.set(cfg.name); this.mount(cfg); },
      error: () => this.error.set('Không lấy được cấu hình soạn thảo.')
    });
    // Panel chèn mã trộn: chỉ cho tài liệu mẫu (không gắn hồ sơ). Nạp danh sách biểu mẫu để chọn trường.
    this.svc.get(this.id).subscribe({
      next: (d) => {
        if (!d.instanceId) {
          this.isTemplate.set(true);
          this.formSvc.list().subscribe({ next: (f) => this.forms.set(f), error: () => {} });
        }
      },
      error: () => {}
    });
    // Tự lưu định kỳ khi đang mở (phiên còn sống → forcesave ghi được về kho).
    this.saveTimer = setInterval(() => this.svc.forceSave(this.id).subscribe({ error: () => {} }), 30000);
  }

  /** Chọn biểu mẫu → nạp danh sách trường (key + nhãn) để chèn mã «key» vào mẫu. */
  onSelectForm(formId: string): void {
    this.selectedFormId.set(formId);
    if (!formId) { this.mergeFields.set([]); return; }
    this.formSvc.get(formId).subscribe({
      next: (f) => {
        try {
          const parsed = f.schemaJson ? JSON.parse(f.schemaJson) : { fields: [] };
          this.mergeFields.set((parsed.fields ?? [])
            .filter((x: { key?: string; type?: string }) => x.key && x.type !== 'section')
            .map((x: { key: string; label?: string }) => ({ key: x.key, label: x.label || x.key })));
        } catch { this.mergeFields.set([]); }
      },
      error: () => this.mergeFields.set([])
    });
  }

  /**
   * Chèn mã «key» vào vị trí con trỏ trong OnlyOffice (Document Builder qua connector.callCommand — mã NHÚNG
   * thẳng vào lệnh, KHÔNG cần Asc.scope ở host). Bản OnlyOffice không có connector → fallback copy clipboard.
   */
  insertToken(key: string): void {
    const token = '«' + key + '»';
    if (this.editor?.createConnector) {
      try {
        if (!this.connector) this.connector = this.editor.createConnector();
        const safe = token.replace(/\\/g, '\\\\').replace(/'/g, "\\'");
        // Hàm chạy TRONG trình soạn (Api là global ở đó); dựng bằng Function để không vướng type + nhúng mã.
        const cmd = new Function(
          `var d=Api.GetDocument();var p=Api.CreateParagraph();p.AddText('${safe}');d.InsertContent([p],true);`
        );
        this.connector!.callCommand(cmd, false);
        this.toast.success('Đã chèn mã', token);
        return;
      } catch { /* rơi xuống fallback copy */ }
    }
    navigator.clipboard?.writeText(token).then(
      () => this.toast.success('Đã copy mã — dán (Ctrl+V) vào tài liệu', token),
      () => this.toast.error('Không chèn được mã', token)
    );
  }

  /** Bắt đầu KÉO trường (dùng MIME riêng để trình soạn không tự chèn trùng; việc chèn do onDropToken lo). */
  onDragToken(ev: DragEvent, key: string): void {
    ev.dataTransfer?.setData('application/x-bpm-field', key);
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'copy';
  }

  /** Kết thúc KÉO: nếu thả TRÊN vùng tài liệu → chèn mã (qua connector, hoặc copy nếu không hỗ trợ). */
  onDropToken(ev: DragEvent, key: string): void {
    const frame = document.getElementById('onlyoffice-editor');
    if (!frame) return;
    const r = frame.getBoundingClientRect();
    const inside = ev.clientX >= r.left && ev.clientX <= r.right && ev.clientY >= r.top && ev.clientY <= r.bottom;
    if (inside) this.insertToken(key);
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
