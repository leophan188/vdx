import { Component, input } from '@angular/core';

/**
 * Control hiển thị nhân sự gọn: Mã NV · Tên · Vị trí · Bộ phận. Dùng lại ở mọi nơi tham chiếu nhân sự.
 * <employee-chip [code]="e.empCode" [name]="e.fullName" [position]="e.jobPosition" [dept]="e.deptCode" />
 */
@Component({
  selector: 'employee-chip',
  template: `
    <span class="empchip">
      @if (code()) { <span class="empchip__code">{{ code() }}</span> }
      <span class="empchip__name">{{ name() || '—' }}</span>
      @if (position()) { <span class="empchip__pos">· {{ position() }}</span> }
      @if (dept()) { <span class="empchip__dept">{{ dept() }}</span> }
    </span>
  `,
  styleUrl: './employee-chip.scss'
})
export class EmployeeChip {
  readonly code = input<string | null>('');
  readonly name = input<string | null>('');
  readonly position = input<string | null>('');
  readonly dept = input<string | null>('');
}
