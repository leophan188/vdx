import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';

export interface TypeChip { value: string; label: string; }

/**
 * Bộ lọc nhiều lựa chọn dạng DROPDOWN CÓ CHECKBOX — dùng chung cho Backlog, Bug/Issue,
 * Kanban, Log, Timeline.
 *
 * Trước đây mỗi nhóm trải hết chip ra thanh lọc: riêng Backlog đã là 6 + 6 + 4 = 16 chip,
 * chiếm gần hai hàng màn hình và mắt phải quét cả dãy mới biết đang lọc gì. Nay thu về một
 * nút gọn, mở ra mới thấy danh sách — thanh lọc ngắn lại, phần lưới được thêm chỗ.
 *
 * MẶC ĐỊNH LÀ CHỌN HẾT (nút hiện "Tất cả"), đúng nghĩa "không lọc gì". Chỉ khi bỏ bớt mục
 * nút mới đổi màu để thấy ngay là danh sách đang bị lọc.
 *
 * Giữ nguyên hợp đồng cũ: chỉ phát {@link toggle} kèm value — nên mọi màn đang dùng không
 * phải sửa gì, kể cả quy tắc "bỏ chọn hết thì tự bật lại tất cả" nằm ở phía cha.
 */
@Component({
  selector: 'app-type-filter',
  standalone: true,
  template: `
    <div class="tf" [class.tf--open]="open()">
      <button type="button" class="tf__btn" [class.tf__btn--filtered]="isFiltered()"
              [attr.aria-expanded]="open()" [title]="fullTitle()"
              (click)="open.set(!open())">
        <span class="tf__txt">{{ summary() }}</span>
        <span class="tf__caret" aria-hidden="true">▾</span>
      </button>

      @if (open()) {
        <div class="tf__menu" role="listbox">
          <button type="button" class="tf__all" [disabled]="!isFiltered()" (click)="selectAll()">
            ✓ Chọn tất cả
          </button>
          <div class="tf__list">
            @for (o of options(); track o.value) {
              <label class="tf__item" [class.is-on]="selected().has(o.value)">
                <input type="checkbox" [checked]="selected().has(o.value)"
                       (change)="toggle.emit(o.value)" />
                <span>{{ o.label }}</span>
              </label>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    :host { display: inline-block; }
    .tf { position: relative; }
    .tf__btn { display: inline-flex; align-items: center; gap: 6px; min-width: 132px;
      height: var(--control-h-sm); padding: 0 var(--space-2) 0 var(--space-3);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); cursor: pointer;
      font: inherit; font-size: var(--text-sm); text-align: left; }
    .tf__btn:hover { border-color: var(--color-primary); }
    .tf--open .tf__btn { border-color: var(--color-primary); box-shadow: 0 0 0 3px var(--color-primary-soft); }
    /* Đang lọc thì nút phải KHÁC HẲN lúc "Tất cả" — nếu không, người dùng quên mất là
       danh sách đang bị lọc rồi kết luận sai vì thiếu dòng. */
    .tf__btn--filtered { border-color: var(--color-primary); color: var(--color-primary);
      background: var(--color-primary-soft); font-weight: 600; }
    .tf__txt { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .tf__caret { flex: none; font-size: 10px; color: var(--color-text-muted); }

    .tf__menu { position: absolute; z-index: 1200; top: calc(100% + 4px); left: 0; min-width: 100%;
      width: max-content; max-width: 260px; padding: 4px;
      background: var(--color-surface); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); box-shadow: var(--shadow-pop); }
    .tf__all { width: 100%; padding: 6px var(--space-2); margin-bottom: 2px; border: 0;
      border-bottom: 1px solid var(--color-border); border-radius: var(--radius-sm);
      background: none; color: var(--color-primary); cursor: pointer; font: inherit;
      font-size: var(--text-xs); font-weight: 600; text-align: left; }
    .tf__all:hover:not(:disabled) { background: var(--color-surface-alt); }
    .tf__all:disabled { color: var(--color-text-muted); cursor: default; opacity: .6; }
    .tf__list { max-height: 280px; overflow: auto; }
    .tf__item { display: flex; align-items: center; gap: 8px; padding: 6px var(--space-2);
      border-radius: var(--radius-sm); cursor: pointer; font-size: var(--text-sm);
      color: var(--color-text); white-space: nowrap; }
    .tf__item:hover { background: var(--color-surface-alt); }
    .tf__item.is-on { color: var(--color-primary); font-weight: 600; }
    /* Ghim cỡ ô tích: input trong form dính quy tắc width:100% dùng chung sẽ phình ra. */
    .tf__item input[type="checkbox"] { flex: 0 0 15px !important; width: 15px !important;
      height: 15px !important; margin: 0 !important; padding: 0 !important;
      cursor: pointer; accent-color: var(--color-primary); }
  `]
})
export class TypeFilter {
  private readonly host = inject(ElementRef<HTMLElement>);

  readonly options = input<TypeChip[]>([]);
  readonly selected = input<Set<string>>(new Set());
  readonly toggle = output<string>();

  readonly open = signal(false);

  /** Đang lọc = có mục bị bỏ chọn. Chọn hết nghĩa là không lọc gì. */
  readonly isFiltered = computed<boolean>(() =>
    this.options().some((o) => !this.selected().has(o.value)));

  /**
   * Chữ trên nút: ưu tiên đọc được ngay đang lọc CÁI GÌ. Ít mục thì liệt kê thẳng tên,
   * nhiều mục mới rút thành "n / m" — liệt kê 5 tên sẽ tràn nút và lại rối như cũ.
   */
  readonly summary = computed<string>(() => {
    const opts = this.options();
    const on = opts.filter((o) => this.selected().has(o.value));
    if (!opts.length || on.length === opts.length) return 'Tất cả';
    if (on.length === 0) return 'Chưa chọn';
    if (on.length <= 2) return on.map((o) => o.label).join(', ');
    return `${on.length} / ${opts.length} mục`;
  });

  /** Tooltip liệt kê đầy đủ, cho trường hợp nút chỉ hiện "3 / 6 mục". */
  readonly fullTitle = computed<string>(() => {
    const on = this.options().filter((o) => this.selected().has(o.value));
    return this.isFiltered() ? 'Đang lọc: ' + on.map((o) => o.label).join(', ') : 'Đang hiện tất cả';
  });

  /**
   * Bật lại mọi mục = về mặc định. Phát toggle cho từng mục đang tắt thay vì thêm output
   * mới, để các màn đang dùng không phải sửa gì.
   */
  selectAll(): void {
    for (const o of this.options()) {
      if (!this.selected().has(o.value)) this.toggle.emit(o.value);
    }
  }

  @HostListener('document:click', ['$event'])
  onDocClick(ev: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(ev.target as Node)) this.open.set(false);
  }

  @HostListener('document:keydown.escape')
  onEsc(): void { this.open.set(false); }
}
