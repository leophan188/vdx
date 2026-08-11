import { Component, ElementRef, effect, input, output, viewChild } from '@angular/core';

/** Một ảnh đã đính kèm: số thứ tự hiện trong mô tả + link xem. */
export interface DescShot { no: number; url: string; }

/**
 * Gỡ mọi đánh dấu "[Ảnh n]" khỏi mô tả, dọn luôn dòng trống thừa để lại.
 *
 * Dùng khi CHÉP nội dung sang task khác mà không chép ảnh: giữ đánh dấu mồ côi thì đến lúc
 * thêm ảnh mới (được đánh số từ 1) sẽ trùng số với đánh dấu cũ, làm một ảnh hiện ở hai chỗ.
 */
export function stripShotMarkers(text: string | null | undefined): string {
  return (text ?? '')
    .replace(/[ \t]*\[Ảnh \d+\][ \t]*/g, '')
    .replace(/\n{3,}/g, '\n\n');
}

/**
 * Ô MÔ TẢ có ảnh hiện THẲNG trong dòng chữ (selector app-desc-editor).
 *
 * <h3>Vì sao không lưu HTML</h3>
 * Ảnh hiện trong khung soạn thảo, nhưng giá trị lưu xuống vẫn là VĂN BẢN THUẦN dạng
 * "…bấm nút Xuất file [Ảnh 1]…". Nhờ vậy file xuất Excel/Word, bản in PDF, danh sách công
 * việc và tìm kiếm dùng lại mô tả mà không phải sửa gì, và không phát sinh nhu cầu lọc HTML
 * chống chèn mã độc. Khung soạn thảo chỉ là lớp HIỂN THỊ: đọc vào thì đổi "[Ảnh n]" thành
 * thẻ ảnh, ghi ra thì đổi thẻ ảnh ngược lại thành "[Ảnh n]".
 *
 * <h3>Vì sao không vẽ lại khung khi đang gõ</h3>
 * Vẽ lại nội dung contenteditable sẽ ĐẶT LẠI con trỏ về đầu. Nên khung chỉ được dựng lại khi
 * giá trị bên ngoài khác hẳn thứ nó vừa phát ra (mở form khác, đổi task), còn trong lúc gõ thì
 * chỉ đọc ra chứ không ghi ngược vào. Cùng bài học với ô nhập số giờ.
 */
@Component({
  selector: 'app-desc-editor',
  standalone: true,
  template: `
    <div #box class="de__box" contenteditable="true" role="textbox" aria-multiline="true"
         [attr.data-ph]="placeholder()"
         (input)="emit()" (blur)="emit()" (keydown)="onKey($event)" (paste)="onPaste($event)"></div>
  `,
  styles: [`
    :host { display: block; max-width: 100%; }
    /* Khung LUÔN co theo popup: max-width 100% + box-sizing để padding không đẩy tràn, và
       overflow-wrap:anywhere để chuỗi dài không có dấu cách (URL, lệnh curl người dùng hay
       dán vào để tái hiện lỗi) bị bẻ dòng thay vì đẩy cả popup trượt ngang. */
    .de__box { min-height: 220px; max-height: 460px; overflow-y: auto; overflow-x: hidden;
      max-width: 100%; box-sizing: border-box; padding: var(--space-3);
      border: 1px solid var(--color-border); border-radius: var(--radius-md);
      background: var(--color-surface); color: var(--color-text); font: inherit; line-height: 1.6;
      white-space: pre-wrap; word-break: break-word; overflow-wrap: anywhere; outline: none; }
    .de__box:focus { border-color: var(--color-primary); }
    .de__box:empty::before { content: attr(data-ph); color: var(--color-text-muted); }
    /* Cỡ ảnh đặt bằng style inline trong imgEl() — xem chú thích ở đó. Ở đây chỉ còn hiệu ứng
       di chuột, dùng ::ng-deep vì thẻ ảnh tạo bằng JS không mang thuộc tính phạm vi của Angular. */
    :host ::ng-deep .de__box img:hover { border-color: var(--color-primary) !important; }
  `]
})
export class DescEditor {
  /** Văn bản mô tả (có thể chứa "[Ảnh n]") — chỉ dùng để DỰNG khung, không ghi đè khi đang gõ. */
  readonly value = input<string>('');
  /** Ảnh đã đính kèm, để đổi "[Ảnh n]" thành ảnh thật. */
  readonly shots = input<DescShot[]>([]);
  readonly placeholder = input<string>('');

  readonly valueChange = output<string>();
  /** Người dùng dán ảnh — cha nhận tệp, thêm vào hàng chờ rồi gọi lại insertShots(). */
  readonly pastedFiles = output<File[]>();
  /** Bấm vào ảnh trong mô tả — cha mở xem to. */
  readonly shotClicked = output<number>();

  private readonly box = viewChild.required<ElementRef<HTMLDivElement>>('box');
  /**
   * Giá trị vừa phát ra — để phân biệt "cha đổi thật" với "chính mình vừa gõ".
   * null = chưa phát lần nào, nên lần đầu luôn dựng khung. Dùng null chứ không lấy một
   * chuỗi mồi bất kỳ, vì chuỗi nào cũng có thể trùng với mô tả thật rồi bỏ qua lần dựng đầu.
   */
  private lastEmitted: string | null = null;

  constructor() {
    effect(() => {
      const v = this.value();
      const list = this.shots();
      const el = this.box().nativeElement;
      // Chỉ dựng lại khi giá trị đến từ BÊN NGOÀI, hoặc số ảnh đổi (thêm/xoá ảnh).
      if (v === this.lastEmitted && el.querySelectorAll('img').length === this.countMarkers(v, list)) {
        return;
      }
      this.render(v, list);
      this.lastEmitted = v;
    });
  }

  private countMarkers(text: string, shots: DescShot[]): number {
    const known = new Set(shots.map((s) => s.no));
    let n = 0;
    for (const m of text.matchAll(/\[Ảnh (\d+)\]/g)) {
      if (known.has(+m[1])) n++;
    }
    return n;
  }

  // ===== Hiển thị: văn bản + [Ảnh n] → thẻ ảnh =====
  private render(text: string, shots: DescShot[]): void {
    const el = this.box().nativeElement;
    const byNo = new Map(shots.map((s) => [s.no, s.url]));
    el.replaceChildren();
    let last = 0;
    for (const m of text.matchAll(/\[Ảnh (\d+)\]/g)) {
      const at = m.index ?? 0;
      this.appendText(el, text.slice(last, at));
      const no = +m[1];
      const url = byNo.get(no);
      // Ảnh đã bị xoá khỏi đính kèm → giữ nguyên chữ để người dùng thấy mà xử lý.
      if (url) el.append(this.imgEl(no, url)); else this.appendText(el, m[0]);
      last = at + m[0].length;
    }
    this.appendText(el, text.slice(last));
  }
  private appendText(el: HTMLElement, s: string): void {
    if (s) el.append(document.createTextNode(s));
  }
  /**
   * Kích thước ĐẶT THẲNG bằng style inline, KHÔNG dựa vào CSS của component.
   *
   * Angular dùng emulated encapsulation: luật ".de__box img" được biên dịch thành
   * ".de__box[_ngcontent-x] img[_ngcontent-x]". Thẻ tạo bằng document.createElement KHÔNG
   * mang thuộc tính _ngcontent-x nên luật đó không bao giờ khớp — ảnh giữ nguyên cỡ gốc
   * (ảnh chụp màn hình 2000-3000px) và đẩy vỡ cả popup. Style inline thì không phụ thuộc
   * phạm vi nên chắc chắn áp được.
   */
  private imgEl(no: number, url: string): HTMLImageElement {
    const img = document.createElement('img');
    img.src = url;
    img.alt = `Ảnh ${no}`;
    img.title = `Ảnh ${no} — bấm để xem to`;
    img.dataset['no'] = String(no);
    img.contentEditable = 'false'; // ảnh là một khối, không cho gõ vào trong
    img.style.cssText = 'display:inline-block;width:110px;height:80px;object-fit:cover;'
      + 'vertical-align:middle;margin:3px 4px 3px 0;border:1px solid var(--color-border);'
      + 'border-radius:var(--radius-md);background:var(--color-surface-alt);cursor:zoom-in;';
    img.addEventListener('click', () => this.shotClicked.emit(no));
    return img;
  }

  // ===== Ghi ra: thẻ ảnh → [Ảnh n] =====
  emit(): void {
    const text = this.serialize(this.box().nativeElement);
    this.lastEmitted = text;
    this.valueChange.emit(text);
  }
  private serialize(root: HTMLElement): string {
    let out = '';
    const walk = (n: Node): void => {
      if (n.nodeType === Node.TEXT_NODE) { out += (n as Text).data; return; }
      if (n.nodeType !== Node.ELEMENT_NODE) return;
      const el = n as HTMLElement;
      if (el.tagName === 'IMG') { out += `[Ảnh ${el.dataset['no'] ?? '?'}]`; return; }
      if (el.tagName === 'BR') { out += '\n'; return; }
      // Trình duyệt bọc dòng mới bằng DIV/P — quy về xuống dòng cho khớp văn bản thuần.
      if (/^(DIV|P)$/.test(el.tagName) && out && !out.endsWith('\n')) out += '\n';
      el.childNodes.forEach(walk);
    };
    root.childNodes.forEach(walk);
    return out;
  }

  /** Enter chèn xuống dòng thật, không để trình duyệt tự đẻ thẻ DIV lồng nhau. */
  onKey(ev: KeyboardEvent): void {
    if (ev.key === 'Enter') {
      ev.preventDefault();
      document.execCommand('insertLineBreak');
      this.emit();
    }
  }

  /** Dán: ảnh thì báo cha xử lý; chữ thì dán THUẦN để không mang HTML lạ vào. */
  onPaste(ev: ClipboardEvent): void {
    const items = ev.clipboardData?.items;
    if (!items) return;
    const files: File[] = [];
    for (const it of Array.from(items)) {
      if (it.kind === 'file' && it.type.startsWith('image/')) {
        const raw = it.getAsFile();
        if (!raw) continue;
        const ext = (it.type.split('/')[1] || 'png').replace('jpeg', 'jpg');
        files.push(raw.name && raw.name !== 'image.png'
          ? raw : new File([raw], `screenshot-${Date.now()}.${ext}`, { type: it.type }));
      }
    }
    if (files.length) {
      ev.preventDefault();
      ev.stopPropagation(); // đừng để handler dán chung của form xử lý lại lần nữa
      this.pastedFiles.emit(files);
      return;
    }
    const text = ev.clipboardData?.getData('text/plain');
    if (text != null) {
      ev.preventDefault();
      document.execCommand('insertText', false, text);
      this.emit();
    }
  }

  /** Cha gọi sau khi đã thêm ảnh vào hàng chờ: chèn ảnh ngay tại vị trí con trỏ. */
  insertShots(list: DescShot[]): void {
    if (!list.length) return;
    const el = this.box().nativeElement;
    el.focus();
    const sel = window.getSelection();
    let range: Range;
    if (sel && sel.rangeCount && el.contains(sel.anchorNode)) {
      range = sel.getRangeAt(0);
    } else {
      range = document.createRange();
      range.selectNodeContents(el);
      range.collapse(false); // không đặt được con trỏ → chèn vào cuối
    }
    range.deleteContents();
    const frag = document.createDocumentFragment();
    for (const s of list) {
      frag.append(this.imgEl(s.no, s.url));
      frag.append(document.createTextNode('\n'));
    }
    range.insertNode(frag);
    range.collapse(false);
    sel?.removeAllRanges();
    sel?.addRange(range);
    this.emit();
  }
}
