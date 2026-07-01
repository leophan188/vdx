import { Directive, ElementRef, HostListener, forwardRef, inject } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { formatThousands } from './format';

/**
 * Directive DÙNG CHUNG cho ô nhập SỐ (tiền / số lượng lớn): tự phân tách hàng nghìn khi gõ,
 * nhưng [(ngModel)]/form vẫn nhận GIÁ TRỊ SỐ thật. Áp dụng cho mọi ô số trong hệ thống.
 *
 * Dùng: <input type="text" appNumberFormat [(ngModel)]="f.budget" />  (KHÔNG dùng type="number").
 * Mặc định số NGUYÊN; cần thập phân: [decimals]="2" (nhập dấu phẩy hoặc chấm làm thập phân).
 *
 * Xem quy ước memory bpm-fe-conventions.
 */
@Directive({
  selector: 'input[appNumberFormat]',
  standalone: true,
  providers: [
    { provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => NumberFormatDirective), multi: true }
  ]
})
export class NumberFormatDirective implements ControlValueAccessor {
  private readonly el = inject(ElementRef<HTMLInputElement>);
  private onChange: (v: number | null) => void = () => {};
  private onTouched: () => void = () => {};

  // ----- ControlValueAccessor -----
  writeValue(v: number | null): void {
    this.el.nativeElement.value = (v == null || isNaN(v)) ? '' : formatThousands(v);
  }
  registerOnChange(fn: (v: number | null) => void): void { this.onChange = fn; }
  registerOnTouched(fn: () => void): void { this.onTouched = fn; }
  setDisabledState(disabled: boolean): void { this.el.nativeElement.disabled = disabled; }

  @HostListener('input', ['$event'])
  onInput(e: Event): void {
    const input = e.target as HTMLInputElement;
    // Giữ phần nguyên + 1 dấu thập phân (chấp nhận ',' hoặc '.'); '.' nhóm hàng nghìn bị loại khi parse.
    const m = /^(-?\d*)([.,]\d*)?/.exec(input.value.replace(/[^\d.,-]/g, '')) ?? [];
    const intRaw = (m[1] ?? '').replace(/\./g, '').replace(/(?!^)-/g, '');
    const decRaw = (m[2] ?? '').replace(/[.,]/g, '');
    const cleaned = intRaw + (decRaw ? '.' + decRaw : '');
    const num = cleaned && cleaned !== '-' ? Number(cleaned) : null;
    this.onChange(num != null && isNaN(num) ? null : num);
    // Format lại phần nguyên, GIỮ dấu thập phân đang gõ dở (vd "5.000," / "5.000,5").
    const hasDecSep = (m[2] ?? '').length > 0;
    input.value = intRaw === '' && !hasDecSep ? ''
      : formatThousands(intRaw === '' ? 0 : Number(intRaw)) + (hasDecSep ? ',' + decRaw : '');
  }

  @HostListener('blur') onBlur(): void { this.onTouched(); }
}
