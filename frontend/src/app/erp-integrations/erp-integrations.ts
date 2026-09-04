import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../shared/page-header/page-header';
import { ToastService } from '../shared/toast/toast.service';
import { ErpIntegrationService, ErpConnection, ErpIntegration } from '../core/erp-integration.service';

/**
 * Cấu hình TÍCH HỢP ERP — một chỗ khai kết nối chung và link của từng luồng dữ liệu
 * (dự án · billable · sơ đồ tổ chức & nhân sự · công nhân sự · tuyển dụng).
 *
 * Người dùng chỉ có đường link đang mở trên trình duyệt, còn API cần tên model nằm lẫn trong link đó
 * — nên ô nhập nhận thẳng link và tự tách model ra. Mỗi luồng có nút kiểm tra riêng: đọc thử và đếm
 * số bản ghi, vì "lưu thành công" chưa chứng minh được link trỏ đúng chỗ.
 */
@Component({
  selector: 'app-erp-integrations',
  imports: [FormsModule, PageHeader],
  templateUrl: './erp-integrations.html',
  styles: [`
    .erpi-card { border: 1px solid var(--color-border); border-radius: var(--radius-md);
      padding: var(--space-4); background: var(--color-surface); display: grid;
      gap: var(--space-3); margin-bottom: var(--space-4); }
    .erpi-form { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: var(--space-3); }
    .erpi-hint { font-size: var(--text-sm); color: var(--color-text-muted); margin: 0; }
    .erpi-actions { display: flex; gap: var(--space-2); flex-wrap: wrap; align-items: center; }
    .erpi-item { border: 1px solid var(--color-border); border-radius: var(--radius-md);
      padding: var(--space-3) var(--space-4); background: var(--color-surface);
      display: grid; gap: var(--space-2); margin-bottom: var(--space-3); }
    .erpi-item__head { display: flex; align-items: baseline; gap: var(--space-3); flex-wrap: wrap; }
    .erpi-item__title { font-weight: var(--weight-semibold); }
    .erpi-item__desc { font-size: var(--text-sm); color: var(--color-text-muted); }
    .erpi-item__row { display: grid; grid-template-columns: 1fr 220px auto auto;
      gap: var(--space-2); align-items: center; }
    @media (max-width: 900px) { .erpi-item__row { grid-template-columns: 1fr; } }
    .erpi-item input[type="text"] { width: 100%; }
    .erpi-status { font-size: var(--text-sm); }
    .erpi-status--ok { color: var(--status-done, #16a34a); }
    .erpi-status--err { color: var(--overdue, #e5484d); }
    .erpi-toggle { display: inline-flex; align-items: center; gap: var(--space-2);
      font-size: var(--text-sm); color: var(--color-text-muted); white-space: nowrap; }
  `]
})
export class ErpIntegrations {
  private svc = inject(ErpIntegrationService);
  private toast = inject(ToastService);

  readonly loading = signal(true);
  readonly busy = signal('');
  readonly connection = signal<ErpConnection | null>(null);
  readonly integrations = signal<ErpIntegration[]>([]);
  readonly form = { baseUrl: '', dbName: '', username: '', apiKey: '' };
  /** Bản nháp đang gõ của từng luồng, khoá theo key — chưa lưu thì chưa gửi đi đâu. */
  readonly draft: Record<string, { linkUrl: string; modelName: string; enabled: boolean }> = {};

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading.set(true);
    this.svc.overview().subscribe({
      next: (o) => {
        this.connection.set(o.connection);
        this.form.baseUrl = o.connection.baseUrl ?? '';
        this.form.dbName = o.connection.dbName ?? '';
        this.form.username = o.connection.username ?? '';
        this.integrations.set(o.integrations);
        for (const it of o.integrations) {
          this.draft[it.key] = {
            linkUrl: it.linkUrl ?? '',
            // Chưa khai thì điền sẵn model gợi ý để người dùng thấy hệ thống định đọc bảng nào.
            modelName: it.modelName ?? it.suggestedModel ?? '',
            enabled: it.enabled
          };
        }
        this.loading.set(false);
      },
      error: () => { this.loading.set(false); this.toast.error('Không tải được cấu hình tích hợp.'); }
    });
  }

  saveConnection(): void {
    this.busy.set('connection');
    this.svc.saveConnection({ ...this.form }).subscribe({
      next: (c) => {
        this.connection.set(c);
        this.form.apiKey = '';
        this.busy.set('');
        this.toast.success('Đã lưu kết nối ERP.');
      },
      error: (e) => { this.busy.set(''); this.toast.error(msg(e, 'Không lưu được kết nối.')); }
    });
  }

  testConnection(): void {
    this.busy.set('connection');
    this.svc.testConnection({ ...this.form }).subscribe({
      next: (r) => { this.busy.set(''); this.toast.success(r.message); this.reload(); },
      error: (e) => { this.busy.set(''); this.toast.error(msg(e, 'Không kết nối được ERP.')); this.reload(); }
    });
  }

  save(it: ErpIntegration): void {
    const d = this.draft[it.key];
    this.busy.set(it.key);
    this.svc.save(it.key, { linkUrl: d.linkUrl, modelName: d.modelName, enabled: d.enabled }).subscribe({
      next: (updated) => {
        this.replace(updated);
        this.busy.set('');
        this.toast.success(`Đã lưu "${it.label}".`);
      },
      error: (e) => { this.busy.set(''); this.toast.error(msg(e, 'Không lưu được.')); }
    });
  }

  /** Lưu rồi kiểm tra luôn: bấm Kiểm tra mà vẫn đọc link cũ trong CSDL thì kết quả nói về bản cũ. */
  test(it: ErpIntegration): void {
    const d = this.draft[it.key];
    this.busy.set(it.key);
    this.svc.save(it.key, { linkUrl: d.linkUrl, modelName: d.modelName, enabled: d.enabled }).subscribe({
      next: () => this.svc.test(it.key).subscribe({
        next: (updated) => {
          this.replace(updated);
          this.busy.set('');
          this.toast.success(updated.lastCheckStatus ?? 'Đọc được dữ liệu.');
        },
        error: (e) => { this.busy.set(''); this.reload(); this.toast.error(msg(e, 'Không đọc được dữ liệu.')); }
      }),
      error: (e) => { this.busy.set(''); this.toast.error(msg(e, 'Không lưu được.')); }
    });
  }

  private replace(updated: ErpIntegration): void {
    this.integrations.update((list) => list.map((x) => (x.key === updated.key ? updated : x)));
    this.draft[updated.key] = {
      linkUrl: updated.linkUrl ?? '',
      modelName: updated.modelName ?? updated.suggestedModel ?? '',
      enabled: updated.enabled
    };
  }

  statusClass(it: ErpIntegration): string {
    if (!it.lastCheckStatus) return '';
    return it.lastCheckStatus.startsWith('OK') ? 'erpi-status--ok' : 'erpi-status--err';
  }

  when(iso: string | null): string {
    return iso ? new Date(iso).toLocaleString('vi-VN') : '';
  }
}

function msg(e: unknown, fallback: string): string {
  const err = e as { error?: { message?: string; error?: string } };
  return err?.error?.message || err?.error?.error || fallback;
}
