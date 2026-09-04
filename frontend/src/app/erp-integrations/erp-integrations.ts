import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { PageHeader } from '../shared/page-header/page-header';
import { ToastService } from '../shared/toast/toast.service';
import { ErpIntegrationService, ErpConnection, ErpIntegration } from '../core/erp-integration.service';

/**
 * Cấu hình TÍCH HỢP ERP — một kết nối dùng chung (máy chủ, database, tài khoản) và mỗi luồng dữ liệu
 * một đường link: dự án · billable · sơ đồ tổ chức & nhân sự · công nhân sự · tuyển dụng.
 *
 * Màn chỉ có ô LINK, không hỏi tên model: model nằm sẵn trong link (model=…) nên hệ thống tự tách,
 * bắt người dùng khai thêm một thứ họ phải đi tra là thêm một chỗ để sai. Mỗi dòng có nút kiểm tra
 * đọc thử và đếm bản ghi — "lưu thành công" chưa chứng minh link trỏ đúng chỗ.
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
    /* Mỗi luồng một dòng: nhãn · ô link · nút kiểm tra · trạng thái. Bảng dọc kiểu này đọc nhanh hơn
       năm thẻ rời, vì việc của người dùng là dán năm cái link chứ không phải cấu hình năm thứ. */
    .erpi-line { display: grid; grid-template-columns: 220px 1fr auto minmax(0, 260px);
      gap: var(--space-2); align-items: center; padding: var(--space-1) 0; }
    @media (max-width: 900px) { .erpi-line { grid-template-columns: 1fr; } }
    .erpi-line__label { font-size: var(--text-sm); font-weight: var(--weight-semibold); }
    .erpi-line input[type="text"] { width: 100%; }
    .erpi-status { font-size: var(--text-xs); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .erpi-status--ok { color: var(--status-done, #16a34a); }
    .erpi-status--err { color: var(--overdue, #e5484d); }
    .erpi-status--muted { color: var(--color-text-muted); }
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
  /** Link đang gõ của từng luồng, khoá theo key — chưa lưu thì chưa gửi đi đâu. */
  readonly draft: Record<string, { linkUrl: string }> = {};

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
          this.draft[it.key] = { linkUrl: it.linkUrl ?? '' };
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

  /** Lưu toàn bộ link trong một lần bấm — người dùng dán năm cái link rồi mới lưu, không lưu từng cái. */
  saveAll(): void {
    this.busy.set('all');
    const list = this.integrations();
    let remaining = list.length;
    let failed = 0;
    for (const it of list) {
      this.svc.save(it.key, this.payload(it)).subscribe({
        next: (updated) => {
          this.replace(updated);
          if (--remaining === 0) {
            this.finishSaveAll(failed);
          }
        },
        error: () => {
          failed++;
          if (--remaining === 0) {
            this.finishSaveAll(failed);
          }
        }
      });
    }
  }

  private finishSaveAll(failed: number): void {
    this.busy.set('');
    if (failed) {
      this.toast.error(`Có ${failed} luồng không lưu được.`);
    } else {
      this.toast.success('Đã lưu các đường link.');
    }
  }

  /** Lưu rồi kiểm tra luôn: bấm Kiểm tra mà vẫn đọc link cũ trong CSDL thì kết quả nói về bản cũ. */
  test(it: ErpIntegration): void {
    this.busy.set(it.key);
    this.svc.save(it.key, this.payload(it)).subscribe({
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

  /**
   * Model để rỗng — backend tự tách từ link. Luồng nào đã dán link thì coi như đang dùng, khỏi bắt
   * bật thêm một công tắc nữa.
   */
  private payload(it: ErpIntegration): { linkUrl: string; modelName: string; enabled: boolean } {
    const link = (this.draft[it.key]?.linkUrl ?? '').trim();
    return { linkUrl: link, modelName: '', enabled: link.length > 0 };
  }

  private replace(updated: ErpIntegration): void {
    this.integrations.update((list) => list.map((x) => (x.key === updated.key ? updated : x)));
    this.draft[updated.key] = { linkUrl: updated.linkUrl ?? '' };
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
