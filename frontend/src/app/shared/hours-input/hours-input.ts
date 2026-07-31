import { Component, computed, effect, input, output, signal } from '@angular/core';

/**
 * MỐC GIỜ CHỌN NHANH dùng chung cho ô ước lượng (est) và popup ghi giờ thực tế.
 *
 * Mốc phút quy thẳng ra giờ (10' = 0.17h) vì hệ thống chấm công theo GIỜ — lưu số phút sẽ
 * phải đổi đơn vị ở mọi báo cáo phía sau. Có mốc phút là vì đa số việc kiểm thử chỉ mất
 * 10–30 phút, để người nhập tự quy đổi thì hay ra 0.15 (9 phút) khi ý là 15 phút.
 */
export const HOUR_PRESETS: { label: string; hours: number }[] = [
  { label: "10'", hours: 0.17 }, { label: "15'", hours: 0.25 }, { label: "20'", hours: 0.33 },
  { label: "30'", hours: 0.5 },
  { label: '1h', hours: 1 }, { label: '2h', hours: 2 }, { label: '4h', hours: 4 }
];

/**
 * Ô NHẬP SỐ GIỜ + mốc chọn nhanh (selector app-hours-input).
 *
 * Dùng input[type=text] chứ KHÔNG dùng type=number: ô số sẽ trả về chuỗi rỗng khi nội dung
 * đang dở dang ("0." lúc vừa xoá chữ số cuối), giá trị bị quy về 0 rồi ghi đè ngược vào ô
 * làm CON TRỎ NHẢY VỀ ĐẦU DÒNG. Ở đây chuỗi người dùng gõ được giữ nguyên, số chỉ suy ra
 * khi cần, nên gõ dở dang bao nhiêu cũng không bị nhảy.
 *
 * Nhận cả dấu phẩy thập phân kiểu Việt ("0,5").
 */
@Component({
  selector: 'app-hours-input',
  standalone: true,
  template: `
    <div class="hi">
      <div class="hi__row">
        <input type="text" inputmode="decimal" autocomplete="off" [value]="text()"
               [disabled]="disabled()" [placeholder]="placeholder()"
               (input)="onInput($any($event.target).value)" />
        @if (minutesLabel()) { <i class="hi__mins">= {{ minutesLabel() }}</i> }
      </div>
      @if (!disabled()) {
        <div class="hi__quick">
          @for (q of picks(); track q.label) {
            <button type="button" class="hi__chip" [class.is-active]="hours() === q.hours"
                    [title]="q.hours + ' giờ'" (click)="pick(q.hours)">{{ q.label }}</button>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: block; }
    .hi { display: grid; gap: 6px; }
    .hi__row { display: flex; align-items: center; gap: 8px; }
    .hi__row input { flex: 1; min-width: 0; }
    .hi__mins { flex: 0 0 auto; font-style: normal; font-size: var(--text-xs);
      color: var(--color-primary); font-weight: 600; white-space: nowrap; }
    .hi__quick { display: flex; flex-wrap: wrap; gap: 4px; }
    .hi__chip { border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer;
      font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--color-text-muted);
      padding: 3px 10px; border-radius: 999px; }
    .hi__chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .hi__chip.is-active { border-color: var(--color-primary); color: var(--color-primary);
      background: var(--color-primary-soft, transparent); }
  `]
})
export class HoursInput {
  readonly value = input<number | string | null | undefined>(null);
  /** Trần giờ — mốc lớn hơn trần sẽ bị ẩn. 0 = không giới hạn (Epic/Story tổng hợp từ con). */
  readonly max = input<number>(4);
  readonly disabled = input(false);
  readonly placeholder = input<string>('0');

  readonly valueChange = output<number>();

  /** Chuỗi ĐANG GÕ — không bao giờ bị ghi đè bởi giá trị đã parse. */
  readonly text = signal<string>('');
  readonly hours = computed<number>(() => {
    const n = parseFloat(this.text().replace(',', '.').trim());
    return isNaN(n) ? 0 : n;
  });
  /** Quy đổi ra phút để người nhập hiểu 0.17h nghĩa là gì. */
  readonly minutesLabel = computed<string>(() => {
    const h = this.hours();
    if (!h || h <= 0) return '';
    const m = Math.round(h * 60);
    return m >= 60 ? `${Math.round((m / 60) * 100) / 100} giờ` : `${m} phút`;
  });
  readonly picks = computed(() => {
    const cap = this.max();
    return cap > 0 ? HOUR_PRESETS.filter((p) => p.hours <= cap) : HOUR_PRESETS;
  });

  /** Số đã phát ra gần nhất — để phân biệt "cha đổi thật" với "chính mình vừa gõ". */
  private lastEmitted: number | null = null;

  constructor() {
    effect(() => {
      const v = this.value();
      const n = typeof v === 'string' ? parseFloat(v.replace(',', '.')) : v;
      const num = n == null || isNaN(n as number) ? 0 : (n as number);
      // Chỉ nạp lại ô khi giá trị đến từ BÊN NGOÀI (mở form khác, đổi task); trong lúc gõ thì
      // giữ nguyên chuỗi, nếu không con trỏ sẽ nhảy về đầu.
      if (this.lastEmitted !== null && Math.abs(num - this.lastEmitted) < 0.0001) return;
      this.lastEmitted = num;
      this.text.set(num ? String(num) : '');
    });
  }

  onInput(raw: string): void {
    this.text.set(raw);
    this.lastEmitted = this.hours();
    this.valueChange.emit(this.hours());
  }

  pick(h: number): void {
    this.text.set(String(h));
    this.lastEmitted = h;
    this.valueChange.emit(h);
  }
}
