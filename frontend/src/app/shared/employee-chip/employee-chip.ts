import { Component, computed, input } from '@angular/core';

/**
 * Control hiển thị nhân sự gọn: Mã NV · Tên · Vị trí · Chức danh · Bộ phận. Dùng lại ở mọi nơi tham chiếu nhân sự.
 * <employee-chip [code]="e.empCode" [name]="e.fullName" [position]="e.jobPosition" [title]="e.title" [dept]="e.deptCode" />
 */
@Component({
  selector: 'employee-chip',
  template: `
    <span class="empchip" [title]="full()">
      @if (code()) { <span class="empchip__code">{{ code() }}</span> }
      <span class="empchip__name">{{ name() || '—' }}</span>
      @if (position()) { <span class="empchip__pos">· {{ position() }}</span> }
      @if (title()) { <span class="empchip__title">· {{ title() }}</span> }
      @if (dept()) { <span class="empchip__dept">{{ dept() }}</span> }
    </span>
  `,
  styleUrl: './employee-chip.scss'
})
export class EmployeeChip {
  readonly code = input<string | null>('');
  readonly name = input<string | null>('');
  readonly position = input<string | null>('');
  readonly title = input<string | null>('');
  readonly dept = input<string | null>('');

  /**
   * Chuỗi đầy đủ cho tooltip. Chip nằm trong ô lưới bị cắt theo bề rộng cột (xem .scss) — không có
   * tooltip thì phần vị trí/chức danh/bộ phận biến mất hẳn thay vì chỉ khuất.
   */
  readonly full = computed(() =>
    [this.code(), this.name(), this.position(), this.title(), this.dept()].filter((v) => !!v).join(' · '));
}
