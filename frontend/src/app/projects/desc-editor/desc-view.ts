import { Component, computed, input, output } from '@angular/core';
import { DescShot } from './desc-editor';

/** Một mảnh của mô tả sau khi tách: hoặc đoạn chữ, hoặc một ảnh. */
interface DescPart { text: string | null; shot: DescShot | null; }

/**
 * XEM mô tả có ảnh hiện thẳng trong dòng chữ (selector app-desc-view) — bản CHỈ ĐỌC của
 * {@link DescEditor}. Mô tả lưu là văn bản thuần "…thao tác sai [Ảnh 1]…"; ở đây đánh dấu
 * "[Ảnh n]" được đổi thành ảnh thật, bấm vào để xem to.
 *
 * Ảnh thứ n = ảnh đính kèm thứ n của task, đúng thứ tự lúc tải lên — cùng quy ước với form
 * nhập. Ảnh đã bị xoá khỏi đính kèm thì GIỮ NGUYÊN chữ "[Ảnh n]" để người xem biết là thiếu,
 * chứ không lặng lẽ bỏ đi.
 */
@Component({
  selector: 'app-desc-view',
  standalone: true,
  template: `
    <div class="dv">
      @for (p of parts(); track $index) {
        @if (p.shot) {
          <img class="dv__img" [src]="p.shot.url" [alt]="'Ảnh ' + p.shot.no"
               [title]="'Ảnh ' + p.shot.no + ' — bấm để xem to'" loading="lazy"
               (click)="shotClicked.emit(p.shot.no)" />
        } @else {
          <span>{{ p.text }}</span>
        }
      }
    </div>
  `,
  styles: [`
    .dv { white-space: pre-wrap; word-break: break-word; line-height: 1.6; }
    .dv__img { display: block; max-width: min(100%, 420px); max-height: 260px; margin: 6px 0;
      border: 1px solid var(--color-border); border-radius: var(--radius-md); cursor: zoom-in; }
    .dv__img:hover { border-color: var(--color-primary); }
  `]
})
export class DescView {
  readonly text = input<string>('');
  readonly shots = input<DescShot[]>([]);
  /** Bấm vào ảnh — cha mở lightbox theo số thứ tự. */
  readonly shotClicked = output<number>();

  readonly parts = computed<DescPart[]>(() => {
    const text = this.text() ?? '';
    const byNo = new Map(this.shots().map((s) => [s.no, s.url]));
    const out: DescPart[] = [];
    let last = 0;
    for (const m of text.matchAll(/\[Ảnh (\d+)\]/g)) {
      const at = m.index ?? 0;
      if (at > last) out.push({ text: text.slice(last, at), shot: null });
      const no = +m[1];
      const url = byNo.get(no);
      out.push(url ? { text: null, shot: { no, url } } : { text: m[0], shot: null });
      last = at + m[0].length;
    }
    if (last < text.length) out.push({ text: text.slice(last), shot: null });
    return out;
  });
}
