import { Component, OnInit, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { PageHeader } from '../shared/page-header/page-header';
import { TaskProcessor } from '../shared/task-processor/task-processor';
import { ToastService } from '../shared/toast/toast.service';
import { WorkflowService, StartableProcess } from '../core/workflow.service';

/** Màn "Tạo yêu cầu" (Story 3.1): chọn quy trình đã ban hành → khai báo form bước đầu → gửi đi. */
@Component({
  selector: 'app-new-request',
  imports: [PageHeader, TaskProcessor],
  templateUrl: './new-request.html'
})
export class NewRequest implements OnInit {
  private wf = inject(WorkflowService);
  private toast = inject(ToastService);
  private router = inject(Router);

  readonly proc = viewChild.required(TaskProcessor);

  readonly procs = signal<StartableProcess[]>([]);
  readonly loading = signal(true);
  readonly busyId = signal<string | null>(null);

  ngOnInit(): void {
    this.wf.startable().subscribe({
      next: (r) => { this.procs.set(r); this.loading.set(false); },
      error: () => { this.toast.error('Không tải được danh sách quy trình'); this.loading.set(false); }
    });
  }

  /** Hai chữ cái đại diện cho thẻ quy trình. */
  initials(name: string): string {
    const p = name.replace(/^Quy trình\s+/i, '').trim().split(/\s+/);
    return ((p[0]?.[0] ?? '') + (p[1]?.[0] ?? '')).toUpperCase();
  }

  create(p: StartableProcess): void {
    this.busyId.set(p.id);
    this.wf.start(p.id, {}).subscribe({
      next: (resp) => {
        this.busyId.set(null);
        if (resp.firstTaskId) {
          // Mở ngay form bước đầu để người dùng khai báo thông tin đã cấu hình, rồi gửi.
          this.proc().openTask(resp.firstTaskId);
        } else {
          this.toast.success('Đã tạo yêu cầu', p.name);
          this.router.navigate(['/inbox']);
        }
      },
      error: (e) => {
        this.busyId.set(null);
        this.toast.error('Không tạo được yêu cầu', e?.error?.message || 'Vui lòng thử lại.');
      }
    });
  }

  /** Sau khi gửi form bước đầu → về Việc của tôi. */
  onSubmitted(): void {
    this.toast.success('Đã gửi yêu cầu', 'Hồ sơ đã chuyển sang bước tiếp theo.');
    this.router.navigate(['/inbox']);
  }
}
