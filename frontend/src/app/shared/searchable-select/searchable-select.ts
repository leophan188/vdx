import { Component, ElementRef, HostListener, computed, effect, inject, input, model, signal } from '@angular/core';

export interface SelectOption {
  value: string;
  label: string;
  sub?: string; // dòng phụ (vd mã, bộ phận)
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

  readonly selectedLabel = computed(() => this.options().find((o) => o.value === this.value())?.label ?? '');

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
