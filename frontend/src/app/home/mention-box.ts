import { Component, computed, ElementRef, inject, input, output, signal, viewChild } from '@angular/core';
import { PostService, MentionSuggestion } from '../core/post.service';

/**
 * Ô nhập có hỗ trợ @mention — dùng lại cho 3 chỗ: soạn bài, bình luận, reply.
 * Gõ "@" + ký tự → gọi mentionSuggest → dropdown chọn người → chèn "@Tên " vào text
 * và tích luỹ userId vào danh sách mentions của ô. Phát ra (textChange) + (mentionsChange).
 *
 * - multiline=true → <textarea>; ngược lại <input> (Enter để submit nếu có (enter)).
 * - value() là nguồn sự thật text; mentions() là nguồn sự thật userId được nhắc.
 */
@Component({
  selector: 'app-mention-box',
  imports: [],
  template: `
    <div class="mbox">
      @if (multiline()) {
        <textarea #field class="mbox__field" [rows]="rows()" [placeholder]="placeholder()"
                  [value]="value()" (input)="onInput($any($event.target))"
                  (keydown)="onKeydown($event)" (blur)="onBlur()"></textarea>
      } @else {
        <input #field class="mbox__field" [placeholder]="placeholder()"
               [value]="value()" (input)="onInput($any($event.target))"
               (keydown)="onKeydown($event)" (blur)="onBlur()" />
      }
      @if (open() && suggestions().length) {
        <ul class="mbox__menu">
          @for (s of suggestions(); track s.userId; let i = $index) {
            <li class="mbox__item" [class.mbox__item--active]="i === activeIndex()"
                (mousedown)="$event.preventDefault(); pick(s)">{{ s.name }}</li>
          }
        </ul>
      }
    </div>
  `,
  styles: [`
    .mbox { position: relative; flex: 1; }
    .mbox__field {
      width: 100%; box-sizing: border-box;
    }
    .mbox__menu {
      position: absolute; left: 0; right: 0; top: calc(100% + 2px); z-index: 40;
      margin: 0; padding: 4px; list-style: none;
      background: var(--surface, #fff); border: 1px solid var(--border, #e2e8f0);
      border-radius: 8px; box-shadow: 0 8px 24px rgba(0,0,0,.12);
      max-height: 220px; overflow-y: auto;
    }
    .mbox__item {
      padding: 6px 10px; border-radius: 6px; cursor: pointer; font-size: 14px;
      white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
    }
    .mbox__item:hover, .mbox__item--active { background: var(--surface-hover, #eef2ff); }
  `]
})
export class MentionBox {
  private api = inject(PostService);

  readonly value = input<string>('');
  readonly placeholder = input<string>('');
  readonly multiline = input<boolean>(false);
  readonly rows = input<number>(3);

  readonly textChange = output<string>();
  readonly mentionsChange = output<string[]>();
  readonly enter = output<void>();

  private readonly field = viewChild<ElementRef<HTMLInputElement | HTMLTextAreaElement>>('field');

  // userId đang được nhắc trong ô (tích luỹ khi chọn từ dropdown)
  private mentionIds = signal<string[]>([]);

  readonly suggestions = signal<MentionSuggestion[]>([]);
  readonly open = signal(false);
  readonly activeIndex = signal(0);

  // vị trí "@" đang gõ (token bắt đầu) trong text; -1 = không trong token
  private tokenStart = -1;
  private debounce: ReturnType<typeof setTimeout> | null = null;

  readonly hasSuggestions = computed(() => this.suggestions().length > 0);

  onInput(el: HTMLInputElement | HTMLTextAreaElement): void {
    const text = el.value;
    this.textChange.emit(text);
    this.detectToken(text, el.selectionStart ?? text.length);
  }

  private detectToken(text: string, caret: number): void {
    // tìm "@" gần nhất trước con trỏ, không có khoảng trắng/xuống dòng ở giữa
    let i = caret - 1;
    while (i >= 0 && !/\s/.test(text[i]) && text[i] !== '@') i--;
    if (i >= 0 && text[i] === '@') {
      const query = text.slice(i + 1, caret);
      this.tokenStart = i;
      this.fetch(query);
    } else {
      this.tokenStart = -1;
      this.close();
    }
  }

  private fetch(query: string): void {
    if (this.debounce) clearTimeout(this.debounce);
    this.debounce = setTimeout(() => {
      this.api.mentionSuggest(query).subscribe({
        next: (list) => {
          this.suggestions.set(list);
          this.activeIndex.set(0);
          this.open.set(list.length > 0);
        },
        error: () => this.close()
      });
    }, 150);
  }

  onKeydown(ev: KeyboardEvent): void {
    if (this.open() && this.suggestions().length) {
      if (ev.key === 'ArrowDown') {
        ev.preventDefault();
        this.activeIndex.update((i) => (i + 1) % this.suggestions().length);
        return;
      }
      if (ev.key === 'ArrowUp') {
        ev.preventDefault();
        this.activeIndex.update((i) => (i - 1 + this.suggestions().length) % this.suggestions().length);
        return;
      }
      if (ev.key === 'Enter' || ev.key === 'Tab') {
        ev.preventDefault();
        this.pick(this.suggestions()[this.activeIndex()]);
        return;
      }
      if (ev.key === 'Escape') {
        this.close();
        return;
      }
    }
    if (ev.key === 'Enter' && !this.multiline() && !this.open()) {
      ev.preventDefault();
      this.enter.emit();
    }
  }

  pick(s: MentionSuggestion): void {
    const el = this.field()?.nativeElement;
    if (!el || this.tokenStart < 0) return;
    const text = el.value;
    const caret = el.selectionStart ?? text.length;
    const before = text.slice(0, this.tokenStart);
    const after = text.slice(caret);
    const inserted = `@${s.name} `;
    const next = before + inserted + after;
    this.mentionIds.update((ids) => (ids.includes(s.userId) ? ids : [...ids, s.userId]));
    this.mentionsChange.emit(this.mentionIds());
    this.textChange.emit(next);
    this.close();
    // đặt lại con trỏ sau token vừa chèn
    setTimeout(() => {
      el.value = next;
      const pos = before.length + inserted.length;
      el.setSelectionRange(pos, pos);
      el.focus();
    });
  }

  onBlur(): void {
    // delay để mousedown trên item kịp chạy
    setTimeout(() => this.close(), 120);
  }

  private close(): void {
    this.open.set(false);
    this.suggestions.set([]);
  }

  /** Cho cha gọi để reset (sau khi gửi). */
  reset(): void {
    this.mentionIds.set([]);
    this.mentionsChange.emit([]);
    this.close();
  }
}
