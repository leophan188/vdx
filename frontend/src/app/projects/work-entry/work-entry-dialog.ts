import { Component, computed, inject, input, output, signal } from '@angular/core';
import { Modal } from '../../shared/modal/modal';
import { WorkEntry, WorkRole } from '../../core/project.service';

/**
 * Popup NHẬP GIỜ tại mốc bàn giao (selector app-work-entry).
 *
 * Backend bắt buộc có giờ khi chuyển sang Kiểm thử (giờ lập trình) và Hoàn thành (giờ kiểm thử),
 * nên MỌI đường đổi trạng thái đều phải đi qua popup này: chi tiết công việc, kéo thả Kanban,
 * đổi trạng thái ở Backlog và màn Bug. Thiếu một chỗ là người dùng lách được và timesheet thủng.
 *
 * Ngày mặc định hôm nay nhưng SỬA ĐƯỢC: làm xong từ hôm qua mà sáng nay mới bấm thì công phải
 * thuộc hôm qua, nếu dồn hết vào ngày bấm thì chấm công theo ngày sẽ sai.
 */
@Component({
  selector: 'app-work-entry',
  standalone: true,
  imports: [Modal],
  template: `
    <app-modal [open]="open()" [title]="title()" (closed)="cancelled.emit()">
      <div class="we">
        <p class="we__hint">{{ hint() }}</p>
        <div class="we__row">
          <label class="we__field">
            <span>Số giờ <b class="we__req">*</b> <i class="we__cap">tối đa {{ maxHours }}h</i></span>
            <input type="number" min="0.25" step="0.25" [max]="maxHours" [value]="hours()" autofocus
                   (input)="hours.set(+$any($event.target).value)" (keydown.enter)="submit()" />
          </label>
          <label class="we__field">
            <span>Ngày tính công</span>
            <input type="date" [value]="workDate()" [max]="today"
                   (change)="workDate.set($any($event.target).value)" />
          </label>
        </div>
        <div class="we__quick">
          @for (h of quickHours; track h) {
            <button type="button" class="we__chip" [class.is-active]="hours() === h"
                    (click)="hours.set(h)">{{ h }}h</button>
          }
        </div>
        <label class="we__field">
          <span>Ghi chú (không bắt buộc)</span>
          <input type="text" [value]="note()" placeholder="Vd: sửa lỗi hiển thị, test lại luồng đăng nhập…"
                 (input)="note.set($any($event.target).value)" (keydown.enter)="submit()" />
        </label>
        @if (fillsDueDate()) {
          <p class="we__fill">📌 Công việc này <b>chưa có hạn hoàn thành</b> — ngày chọn ở trên sẽ được
            ghi luôn thành hạn hoàn thành của công việc.</p>
        }
        @if (error()) { <p class="we__err">{{ error() }}</p> }
      </div>
      <div modalFooter>
        <button type="button" class="btn btn--ghost" (click)="cancelled.emit()">Huỷ</button>
        <button type="button" class="btn btn--primary" [disabled]="saving()" (click)="submit()">
          {{ saving() ? 'Đang lưu…' : confirmLabel() }}
        </button>
      </div>
    </app-modal>
  `,
  styles: [`
    .we { display: grid; gap: var(--space-3); min-width: 320px; }
    .we__hint { margin: 0; font-size: var(--text-sm); color: var(--color-text-muted); line-height: 1.5; }
    .we__row { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }
    @media (max-width: 520px) { .we__row { grid-template-columns: 1fr; } }
    .we__field { display: grid; gap: 4px; }
    .we__field > span { font-size: var(--text-xs); color: var(--color-text-muted); font-weight: var(--weight-medium); }
    .we__req { color: var(--overdue, #e5484d); }
    .we__cap { font-style: normal; opacity: .8; }
    .we__field input { height: var(--control-h-sm); padding: 0 var(--space-3); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text); font: inherit; }
    .we__quick { display: flex; flex-wrap: wrap; gap: 4px; }
    .we__chip { border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer;
      font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--color-text-muted);
      padding: 3px 10px; border-radius: 999px; }
    .we__chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .we__chip.is-active { border-color: var(--color-primary); color: var(--color-primary);
      background: var(--color-primary-soft, transparent); }
    .we__fill { margin: 0; padding: 8px 10px; border-radius: 8px; font-size: var(--text-xs); line-height: 1.5;
      color: var(--color-text-muted); background: var(--color-surface-alt); border: 1px solid var(--color-border); }
    .we__fill b { color: var(--color-text); }
    .we__err { margin: 0; font-size: var(--text-sm); color: var(--overdue, #e5484d); }
  `]
})
export class WorkEntryDialog {
  readonly open = input(false);
  /** DEV = giờ lập trình (bàn giao Kiểm thử) · TEST = giờ kiểm thử (chuyển Hoàn thành). */
  readonly role = input<WorkRole>('DEV');
  readonly taskTitle = input<string>('');
  readonly saving = input(false);
  /** Task chưa có hạn hoàn thành và lần chuyển này sẽ lấy "Ngày tính công" làm hạn. */
  readonly fillsDueDate = input(false);

  readonly confirmed = output<WorkEntry>();
  readonly cancelled = output<void>();

  readonly today = new Date().toISOString().slice(0, 10);
  /** Trần giờ MỖI LẦN ghi trên task lá — khớp MAX_LEAF_HOURS ở backend. */
  readonly maxHours = 4;
  readonly quickHours = [0.5, 1, 2, 3, 4];

  readonly hours = signal<number>(1);
  readonly workDate = signal<string>(this.today);
  readonly note = signal<string>('');
  readonly error = signal<string>('');

  readonly isDev = computed(() => this.role() === 'DEV');
  readonly title = computed(() =>
    this.isDev() ? 'Ghi giờ lập trình trước khi bàn giao' : 'Ghi giờ kiểm thử trước khi hoàn thành');
  readonly confirmLabel = computed(() =>
    this.isDev() ? 'Lưu & chuyển Kiểm thử' : 'Lưu & chuyển Hoàn thành');
  readonly hint = computed(() => {
    const t = this.taskTitle() ? `“${this.taskTitle()}” — ` : '';
    return this.isDev()
      ? `${t}nhập số giờ bạn đã bỏ ra để làm công việc này. Giờ sẽ được tính vào timesheet của ngày chọn bên dưới.`
      : `${t}nhập số giờ đã kiểm thử công việc này. Giờ sẽ được tính vào timesheet của ngày chọn bên dưới.`;
  });

  submit(): void {
    const h = this.hours();
    if (!h || h <= 0) {
      this.error.set('Số giờ phải lớn hơn 0');
      return;
    }
    if (h > this.maxHours) {
      this.error.set(`Mỗi lần ghi không được quá ${this.maxHours}h — hãy tách nhỏ công việc `
        + 'hoặc ghi thành nhiều lần theo từng ngày');
      return;
    }
    this.error.set('');
    this.confirmed.emit({ hours: h, workDate: this.workDate() || this.today, note: this.note().trim() || null });
  }

  /** Đặt lại về mặc định — gọi mỗi lần mở popup cho một task khác. */
  reset(): void {
    this.hours.set(1);
    this.workDate.set(this.today);
    this.note.set('');
    this.error.set('');
  }
}
