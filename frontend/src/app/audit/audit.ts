import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuditService, AuditEvent } from '../core/audit.service';
import { DataGrid, GridColumn } from '../shared/data-grid/data-grid';
import { GridCellDirective } from '../shared/data-grid/grid-cell.directive';
import { PageHeader } from '../shared/page-header/page-header';

/** Trang truy vết kiểm toán (Story 1.8) — tra vết append-only theo đối tượng hoặc theo nhiệm vụ. */
@Component({
  selector: 'app-audit',
  imports: [FormsModule, DataGrid, GridCellDirective, PageHeader],
  templateUrl: './audit.html'
})
export class Audit implements OnInit {
  private auditSvc = inject(AuditService);

  readonly events = signal<AuditEvent[]>([]);
  readonly error = signal<string | null>(null);
  readonly searched = signal(false);
  readonly loading = signal(false);

  ngOnInit(): void { this.loadRecent(); }

  /** Tải 200 sự kiện kiểm toán gần nhất (mặc định khi mở màn). */
  loadRecent(): void {
    this.error.set(null);
    this.loading.set(true);
    this.auditSvc.recent().subscribe({
      next: (e) => { this.events.set(e); this.searched.set(true); this.loading.set(false); },
      error: () => { this.error.set('Không tải được nhật ký kiểm toán (cần quyền quản trị).'); this.loading.set(false); }
    });
  }

  readonly cols: GridColumn[] = [
    { key: 'createdAt', header: 'Thời điểm', sortable: true, width: '200px' },
    { key: 'action', header: 'Hành động', sortable: true, width: '180px' },
    { key: 'object', header: 'Đối tượng', width: '240px' },
    { key: 'actor', header: 'Người thực hiện', sortable: true, width: '190px' },
    { key: 'detail', header: 'Chi tiết' }
  ];

  mode: 'object' | 'task' = 'object';
  objectType = 'TaskAssignment';
  objectId = '';
  taskId = '';

  search(): void {
    this.error.set(null);
    const obs =
      this.mode === 'task'
        ? this.auditSvc.trailForTask(this.taskId.trim())
        : this.auditSvc.trail(this.objectType.trim(), this.objectId.trim());
    obs.subscribe({
      next: (e) => {
        this.events.set(e);
        this.searched.set(true);
      },
      error: () => this.error.set('Không tải được vết kiểm toán (cần quyền quản trị).')
    });
  }
}
