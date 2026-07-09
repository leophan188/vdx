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
    // Mở form bước đầu ở chế độ NHÁP (chưa tạo hồ sơ) — chỉ tạo khi bấm Gửi hoặc mở soạn thảo tài liệu.
    this.proc().openStartForm({ id: p.id, name: p.name });
  }

  /** Sau khi gửi form bước đầu → về Việc của tôi. */
  onSubmitted(): void {
    this.toast.success('Đã gửi yêu cầu', 'Hồ sơ đã chuyển sang bước tiếp theo.');
    this.router.navigate(['/inbox']);
  }
}
