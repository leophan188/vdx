import { Component, computed, input } from '@angular/core';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { TaskType, TaskStatus } from '../../core/project.service';
import { TYPE_META } from '../work-stats';

/**
 * Card công việc DÙNG LẠI cho mọi màn có danh sách việc.
 * Hiện: badge loại + mã, tiêu đề, CHUỖI CHA (Epic › Story › Task cha) nếu có,
 * người thực hiện, hạn, và thanh % hoàn thành. Bấm card → phát click (parent tự xử lý mở chi tiết).
 */
@Component({
  selector: 'app-task-card',
  standalone: true,
  imports: [EmployeeChip],
  template: `
    <div class="tk" [class.tk--over]="overdue()">
      <div class="tk__head">
        <span class="tk__badge" [style.--c]="color()">{{ typeShort() }} · {{ code() }}</span>
        @if (status(); as s) { <span class="badge" [class]="statusBadge()">{{ statusLabel() }}</span> }
        <span class="tk__spacer"></span>
        @if (progressPct() !== null) { <span class="tk__pct">{{ pct() }}%</span> }
      </div>

      <div class="tk__title" [title]="title()">{{ title() }}</div>

      @if (parentPath()) {
        <div class="tk__parent" [title]="parentPath()!">↳ {{ parentPath() }}</div>
      }

      @if (assigneeName() || dueDate()) {
        <div class="tk__meta">
          @if (assigneeName()) { <employee-chip [name]="assigneeName()!" /> }
          @else { <span class="tk__muted">— Chưa gán —</span> }
          @if (dueDate()) { <span class="tk__due" [class.tk__due--over]="overdue()">📅 {{ dueDate() }}</span> }
        </div>
      }

      @if (progressPct() !== null) {
        <div class="tk__bar"><div class="tk__fill" [style.width.%]="pct()"></div></div>
      }
    </div>
  `,
  styles: [`
    .tk { display: grid; gap: 6px; padding: var(--space-3) var(--space-4);
      border: 1px solid var(--color-border); border-left: 3px solid var(--c, var(--color-border));
      border-radius: var(--radius-md); background: var(--color-surface); }
    .tk:hover { border-color: var(--color-primary); }
    .tk--over { border-left-color: var(--overdue, #e5484d); }

    .tk__head { display: flex; align-items: center; gap: var(--space-2); }
    .tk__spacer { flex: 1 1 auto; }
    .tk__badge { flex: 0 0 auto; font-size: var(--text-xs); font-weight: 700; padding: 1px 8px; border-radius: 999px;
      white-space: nowrap; color: var(--c, var(--color-primary));
      background: color-mix(in srgb, var(--c, var(--color-primary)) 14%, transparent);
      border: 1px solid color-mix(in srgb, var(--c, var(--color-primary)) 36%, transparent); }
    .tk__pct { font-size: var(--text-xs); color: var(--color-text-muted); font-variant-numeric: tabular-nums; }

    .tk__title { font-weight: var(--weight-medium); line-height: 1.35; overflow: hidden;
      display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }

    .tk__parent { font-size: var(--text-xs); color: var(--color-text-muted); overflow: hidden;
      text-overflow: ellipsis; white-space: nowrap; }

    .tk__meta { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; font-size: var(--text-xs); }
    .tk__muted { color: var(--color-text-muted); }
    .tk__due { color: var(--color-text-muted); font-variant-numeric: tabular-nums; }
    .tk__due--over { color: var(--overdue, #e5484d); font-weight: var(--weight-semibold); }

    .tk__bar { height: 6px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .tk__fill { height: 100%; border-radius: var(--radius-full); background: var(--status-done); }
  `]
})
export class TaskCard {
  readonly code = input('');
  readonly title = input('');
  readonly type = input<TaskType>('TASK');
  readonly status = input<TaskStatus | null>(null);
  readonly parentPath = input<string | null>(null);
  readonly assigneeName = input<string | null>(null);
  readonly dueDate = input<string | null>(null);
  readonly progressPct = input<number | null>(null);
  readonly overdue = input(false);

  readonly color = computed(() => TYPE_META[this.type()]?.color ?? 'var(--color-primary)');
  readonly typeShort = computed(() => TYPE_META[this.type()]?.short ?? this.type());
  readonly pct = computed(() => Math.max(0, Math.min(100, Math.round(this.progressPct() ?? 0))));

  readonly statusBadge = computed(() => {
    switch (this.status()) {
      case 'TODO': return 'badge--pending';
      case 'IN_PROGRESS': return 'badge--active';
      case 'IN_REVIEW': return 'badge--active';
      case 'DONE': return 'badge--done';
      default: return 'badge--neutral';
    }
  });
  readonly statusLabel = computed(() => {
    switch (this.status()) {
      case 'BACKLOG': return 'Backlog';
      case 'TODO': return 'Cần làm';
      case 'IN_PROGRESS': return 'Đang làm';
      case 'IN_REVIEW': return 'Kiểm thử';
      case 'DONE': return 'Hoàn thành';
      default: return '';
    }
  });
}
