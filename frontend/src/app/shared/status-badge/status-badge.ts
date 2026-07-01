import { Component, computed, input } from '@angular/core';

export type TaskStatus = 'pending' | 'active' | 'done' | 'cancel' | 'neutral';

/**
 * Badge trạng thái nhiệm vụ DÙNG CHUNG (DESIGN-SYSTEM §3). Bắt buộc dùng ở mọi nơi hiện trạng thái
 * (hộp thư việc, dashboard, báo cáo — Epic 3/4) để 5 màu trạng thái nhất quán tuyệt đối.
 * Cờ quá hạn là chấm đỏ TRỰC GIAO chồng lên, không đổi màu badge gốc (AD-5).
 */
@Component({
  selector: 'status-badge',
  template: `
    <span class="badge" [class]="'badge--' + status()">
      @if (overdue()) {
        <span class="badge__overdue-dot" title="Quá hạn"></span>
      } @else if (dot()) {
        <span class="badge__dot"></span>
      }
      {{ label() || defaultLabel() }}
    </span>
  `
})
export class StatusBadge {
  readonly status = input<TaskStatus>('neutral');
  readonly overdue = input(false);
  readonly label = input<string>('');
  readonly dot = input(true);

  private readonly labels: Record<TaskStatus, string> = {
    pending: 'Chờ phê duyệt',
    active: 'Đang xử lý',
    done: 'Đã hoàn thành',
    cancel: 'Hủy',
    neutral: '—'
  };
  protected readonly defaultLabel = computed(() => this.labels[this.status()]);
}
