import { Component, ElementRef, HostListener, computed, effect, inject, input, model, signal } from '@angular/core';

export interface SelectOption {
  value: string;
  label: string;
  sub?: string; // dòng phụ (vd mã, bộ phận)
  badge?: string; // nhãn LOẠI hiển thị dạng pill màu (vd EPIC/STORY/BUG)
  badgeColor?: string; // màu pill (css color/token); mặc định primary
}

/**
 * Dropdown có ô GÕ TÌM KIẾM (combobox/typeahead) — thay cho <select> thường.
 * <searchable-select [options]="opts" [(value)]="id" placeholder="Chọn…" [allowEmpty]="true" emptyLabel="— Tất cả —" />
 */
@Component({
  selector: 'searchable-select',
  templateUrl: './searchable-select.html',
  styleUrl: './searchable-select.scss'
})
export class SearchableSelect {
  private host = inject(ElementRef<HTMLElement>);

  readonly options = input<SelectOption[]>([]);
  readonly value = model<string>('');
  readonly placeholder = input('Chọn…');
  readonly allowEmpty = input(false);
  readonly emptyLabel = input('— Tất cả —');

  readonly open = signal(false);
  readonly query = signal('');
  /** Đã gõ để lọc chưa (phân biệt với chuỗi đang hiển thị nhãn đã chọn). */
  private readonly typing = signal(false);

  readonly selectedOption = computed(() => this.options().find((o) => o.value === this.value()) ?? null);
  readonly selectedLabel = computed(() => this.selectedOption()?.label ?? '');

  /**
   * Bề rộng ô nhập tính theo ĐỘ DÀI GIÁ TRỊ đang hiển thị, qua thuộc tính size.
   *
   * Mặc định input rộng cứng ~20 ký tự, nên trong bảng nó ép cột phình lên ~310px dù cột
   * chỉ chứa nhãn ngắn như "Backlog". Nếu chữa bằng CSS width:0 thì ngược lại: bề rộng nội
   * tại về 0, bảng co cột xuống dưới cả cỡ chữ và "Backlog" bị cắt còn "Back".
   *
   * size khiến bề rộng bám đúng nội dung — vừa đủ chứa giá trị, không dư không thiếu. Cộng
   * 2 ký tự đệm vì size đo theo bề rộng ký tự TRUNG BÌNH, chữ hoa và dấu tiếng Việt rộng
   * hơn mức đó. Chặn trần 26 để tên người dài không kéo cột đi quá xa.
   */
  readonly inputSize = computed<number>(() => {
    const shown = this.selectedLabel() || this.placeholder();
    return Math.min(26, Math.max(6, shown.length + 2));
  });

  readonly filtered = computed<SelectOption[]>(() => {
    const q = noDiacritics(this.query().trim());
    if (!this.typing() || !q) return this.options();
    return this.options().filter(
      (o) => noDiacritics(o.label).includes(q) || noDiacritics(o.sub ?? '').includes(q)
    );
  });

  constructor() {
    // Khi value đổi từ ngoài → đồng bộ chữ hiển thị.
    effect(() => {
      const label = this.selectedLabel();
      if (!this.open()) this.query.set(label);
    });
  }

  openIt(): void {
    this.open.set(true);
    this.typing.set(false);
  }
  onType(v: string): void {
    this.query.set(v);
    this.typing.set(true);
    this.open.set(true);
  }
  pick(val: string): void {
    this.value.set(val);
    this.query.set(this.options().find((o) => o.value === val)?.label ?? '');
    this.typing.set(false);
    this.open.set(false);
  }

  @HostListener('document:mousedown', ['$event'])
  onOutside(e: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(e.target as Node)) {
      this.open.set(false);
      this.typing.set(false);
      this.query.set(this.selectedLabel());
    }
  }
}

/** Chuẩn hoá để tìm KHÔNG phân biệt dấu tiếng Việt: thường hoá + bỏ dấu + đ→d. */
function noDiacritics(s: string): string {
  return (s || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, "").replace(/\u0111/g, 'd');
}
