import { Component, inject, signal } from '@angular/core';
import { StatusBadge } from '../shared/status-badge/status-badge';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { GridDetailDirective } from '../shared/data-grid/grid-detail.directive';
import { Modal } from '../shared/modal/modal';
import { ConfirmDialog } from '../shared/confirm-dialog/confirm-dialog';
import { StatCard } from '../shared/stat-card/stat-card';
import { PageHeader } from '../shared/page-header/page-header';
import { Avatar } from '../shared/avatar/avatar';
import { ThemeService } from '../shared/theme.service';

/** Gallery sống của hệ thiết kế (DESIGN-SYSTEM §1). Nơi tra cứu & copy mẫu khi dựng màn mới. */
@Component({
  selector: 'app-styleguide',
  imports: [StatusBadge, DataGrid, GridCellDirective, GridDetailDirective, Modal, ConfirmDialog, StatCard, PageHeader, Avatar],
  templateUrl: './styleguide.html'
})
export class Styleguide {
  protected readonly themeSvc = inject(ThemeService);

  readonly swatches = [
    { name: 'primary', var: '--color-primary' },
    { name: 'surface', var: '--color-surface' },
    { name: 'surface-alt', var: '--color-surface-alt' },
    { name: 'border', var: '--color-border' },
    { name: 'text', var: '--color-text' },
    { name: 'pending', var: '--status-pending' },
    { name: 'active', var: '--status-active' },
    { name: 'done', var: '--status-done' },
    { name: 'cancel', var: '--status-cancel' },
    { name: 'overdue', var: '--overdue' }
  ];

  readonly demoCols: GridColumn[] = [
    { key: 'code', header: 'Mã', sortable: true, width: '120px' },
    { key: 'name', header: 'Tên hồ sơ', sortable: true },
    { key: 'status', header: 'Trạng thái', width: '160px' },
    { key: 'actions', header: '', align: 'right', width: '120px' }
  ];
  readonly demoRows = [
    { code: 'HS-001', name: 'Hồ sơ mẫu A', status: 'active', overdue: false },
    { code: 'HS-002', name: 'Hồ sơ mẫu B', status: 'done', overdue: false },
    { code: 'HS-003', name: 'Hồ sơ mẫu C', status: 'pending', overdue: true },
    { code: 'HS-004', name: 'Hồ sơ mẫu D', status: 'cancel', overdue: false }
  ];

  readonly modalOpen = signal(false);
  readonly confirmOpen = signal(false);
}
