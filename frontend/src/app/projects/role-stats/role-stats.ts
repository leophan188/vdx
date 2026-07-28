import { Component, computed, input, output, signal } from '@angular/core';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { ProjectTask, TaskStatus } from '../../core/project.service';
import { catOf, WORK_CATS, WorkCat } from '../work-stats';

/** Một dòng bảng vai DEV — task người đó THỰC HIỆN, tách theo trạng thái. */
interface DevRow {
  key: string; userId: string | null; name: string; unassigned: boolean;
  todo: ProjectTask[];    // Cần làm + Backlog (chưa khởi động)
  doing: ProjectTask[];   // Đang làm
  review: ProjectTask[];  // đã bàn giao, đang chờ kiểm thử
  done: ProjectTask[];    // đã hoàn thành
  items: ProjectTask[];
  pct: number;
}

/**
 * Một dòng bảng vai TESTER. Tập gốc {@code assigned} = việc người này phụ trách kiểm thử
 * (là người kiểm thử; bug/issue chưa gán tester thì người log chính là người verify).
 * Ba cột trạng thái chia HẾT tập gốc nên luôn có: done + waitTest + waitDev = assigned.
 */
interface TestRow {
  key: string; userId: string | null; name: string;
  logged: ProjectTask[];   // bug/issue do người này TẠO
  assigned: ProjectTask[]; // tổng việc mình phụ trách kiểm thử
  done: ProjectTask[];     // đã Hoàn thành
  waitTest: ProjectTask[]; // đang ở Kiểm thử — nằm ở chân TESTER
  waitDev: ProjectTask[];  // Backlog/Cần làm/Đang làm — còn ở chân DEV
  pct: number;
}

/**
 * Hai bảng THỐNG KÊ THEO VAI (selector app-role-stats) — dùng chung cho Tổng quan và
 * Báo cáo ngày/tuần để hai màn không bao giờ lệch nhau.
 *
 * Vai DEV và vai TESTER có tập trạng thái khác nhau nên tách hẳn hai bảng: dùng chung một
 * bộ cột sẽ đẻ ra nhiều ô vô nghĩa (tester không có "Đang làm", dev không có "Đã log").
 *
 * Một task xuất hiện ở CẢ HAI bảng (dev làm, tester kiểm) — đó là 2 phần việc của 2 người,
 * không phải đếm trùng; tổng task của dự án vẫn là 1.
 */
@Component({
  selector: 'app-role-stats',
  standalone: true,
  imports: [EmployeeChip],
  template: `
    <div class="rs">
      <!-- Bộ lọc LOẠI dùng chung cho cả hai bảng -->
      <div class="rs__filters">
        <span class="rs__filters-lbl">Loại:</span>
        <button type="button" class="rs__chip" [class.is-active]="cat() === 'ALL'"
                (click)="cat.set('ALL')">Tất cả <b>{{ scoped().length }}</b></button>
        @for (c of workCats; track c.key) {
          <button type="button" class="rs__chip" [class.is-active]="cat() === c.key"
                  (click)="cat.set(c.key)">{{ c.icon }} {{ c.label }} <b>{{ countOf(c.key) }}</b></button>
        }
      </div>

      <!-- ===== VAI DEV ===== -->
      <h4 class="rs__h">👨‍💻 Theo vai Lập trình (dev) — {{ devRows().length }} người{{ scopeSuffix() }}</h4>
      <div class="rs__wrap">
        <div class="rs__grid rs__grid--dev">
          <div class="rs__row rs__row--head">
            <span class="rs__name">Nhân sự</span>
            <span title="Cần làm + Backlog">Cần làm</span>
            <span>Đang làm</span>
            <span title="Đã bàn giao, đang chờ kiểm thử">Kiểm thử</span>
            <span>Hoàn thành</span>
            <span>Tổng</span>
            <span>% HT</span>
          </div>
          @for (r of devRows(); track r.key) {
            <div class="rs__row">
              <span class="rs__name">
                @if (r.userId) { <employee-chip [name]="r.name" /> } @else { <span>{{ r.name }}</span> }
              </span>
              <span>@if (r.todo.length) {
                <button type="button" class="rs__num" (click)="pick(r.name + ' · Cần làm', r.todo)">{{ r.todo.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.doing.length) {
                <button type="button" class="rs__num rs__num--doing" (click)="pick(r.name + ' · Đang làm', r.doing)">{{ r.doing.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.review.length) {
                <button type="button" class="rs__num rs__num--review" (click)="pick(r.name + ' · Chờ kiểm thử', r.review)">{{ r.review.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.done.length) {
                <button type="button" class="rs__num rs__num--done" (click)="pick(r.name + ' · Hoàn thành', r.done)">{{ r.done.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.items.length) {
                <button type="button" class="rs__num" (click)="pick('Công việc dev của ' + r.name, r.items)">{{ r.items.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span class="rs__pct">
                <span class="rs__bar"><span class="rs__fill" [style.width.%]="r.pct"></span></span>
                <span class="rs__pctv">{{ r.pct }}%</span>
              </span>
            </div>
          } @empty { <div class="rs__empty">Chưa có dữ liệu.</div> }
        </div>
      </div>

      <!-- ===== VAI TESTER ===== -->
      <h4 class="rs__h">🔎 Theo vai Kiểm thử (tester) — {{ testRows().length }} người{{ scopeSuffix() }}</h4>
      <div class="rs__wrap">
        <div class="rs__grid rs__grid--test">
          <div class="rs__row rs__row--head">
            <span class="rs__name">Nhân sự</span>
            <span title="Số bug/issue do người này tạo">Đã log</span>
            <span title="Việc đã ở trạng thái Hoàn thành">Hoàn thành</span>
            <span title="Đang ở trạng thái Kiểm thử — nằm ở chân tester, chờ người này verify">Chờ test</span>
            <span title="Backlog / Cần làm / Đang làm — còn ở chân dev, chưa bàn giao">Chờ Dev</span>
            <span title="Tổng việc người này phụ trách kiểm thử = Hoàn thành + Chờ test + Chờ Dev">Tổng</span>
            <span title="Hoàn thành / Tổng">% HT</span>
          </div>
          @for (r of testRows(); track r.key) {
            <div class="rs__row">
              <span class="rs__name">
                @if (r.userId) { <employee-chip [name]="r.name" /> } @else { <span>{{ r.name }}</span> }
              </span>
              <span>@if (r.logged.length) {
                <button type="button" class="rs__num" (click)="pick('Bug/Issue do ' + r.name + ' log', r.logged)">{{ r.logged.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.done.length) {
                <button type="button" class="rs__num rs__num--done" (click)="pick(r.name + ' · Hoàn thành', r.done)">{{ r.done.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.waitTest.length) {
                <button type="button" class="rs__num rs__num--review" (click)="pick(r.name + ' · Chờ test (đang ở chân tester)', r.waitTest)">{{ r.waitTest.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.waitDev.length) {
                <button type="button" class="rs__num rs__num--doing" (click)="pick(r.name + ' · Chờ Dev (chưa bàn giao)', r.waitDev)">{{ r.waitDev.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span>@if (r.assigned.length) {
                <button type="button" class="rs__num" (click)="pick(r.name + ' · Tổng việc phụ trách kiểm thử', r.assigned)">{{ r.assigned.length }}</button>
              } @else { <i class="rs__zero">0</i> }</span>
              <span class="rs__pct">
                <span class="rs__bar"><span class="rs__fill" [style.width.%]="r.pct"></span></span>
                <span class="rs__pctv">{{ r.pct }}%</span>
              </span>
            </div>
          } @empty { <div class="rs__empty">Chưa có dữ liệu.</div> }
        </div>
      </div>

      <p class="rs__note">Bảng tester: <b>Hoàn thành + Chờ test + Chờ Dev = Tổng</b>. “Chờ Dev” là việc đã log
        nhưng dev chưa bàn giao nên tester chưa động vào được, “Chờ test” là việc đang nằm trên tay tester.
        Cột “Đã log” đếm bug/issue do người đó tạo — có thể lệch Tổng nếu bug được giao cho người khác kiểm thử.
        <br>Một task nằm ở cả hai bảng — phần việc của dev và phần việc của tester.
        Đó là 2 phần việc của 2 người, không phải đếm trùng; tổng task của dự án vẫn là 1.
        @if (scopeLabel()) {
          <br>Phạm vi: <b>{{ scopeLabel() }}</b> — chỉ gồm công việc có thay đổi trong khoảng thời gian
          đang chọn; cột trạng thái là trạng thái HIỆN TẠI của những việc đó.
        }</p>
    </div>
  `,
  styles: [`
    .rs { display: grid; gap: var(--space-3); }
    .rs__h { margin: var(--space-2) 0 0; font-size: .95rem; font-weight: var(--weight-semibold); }
    .rs__filters { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
    .rs__filters-lbl { font-size: var(--text-xs); color: var(--color-text-muted); margin-right: 2px; }
    .rs__chip { border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer;
      font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--color-text-muted);
      padding: 3px 10px; border-radius: 999px; white-space: nowrap; }
    .rs__chip b { font-variant-numeric: tabular-nums; opacity: .75; }
    .rs__chip:hover { border-color: var(--color-primary); color: var(--color-primary); }
    .rs__chip.is-active { border-color: var(--color-primary); color: var(--color-primary);
      background: color-mix(in srgb, var(--color-primary) 12%, transparent); }

    .rs__wrap { overflow-x: auto; }
    .rs__grid { display: grid; gap: 2px; }
    .rs__grid--dev { min-width: 660px; }
    .rs__grid--test { min-width: 740px; }
    .rs__row { display: grid; align-items: center; gap: var(--space-1); padding: 5px var(--space-3);
      border-radius: var(--radius-md); background: var(--color-surface-alt); font-size: var(--text-sm); }
    .rs__grid--dev .rs__row { grid-template-columns: minmax(180px, 2fr) repeat(5, minmax(72px, .9fr)) minmax(120px, 1.2fr); }
    .rs__grid--test .rs__row { grid-template-columns: minmax(180px, 2fr) repeat(5, minmax(74px, 1fr)) minmax(120px, 1.2fr); }
    .rs__row > span:not(.rs__name) { text-align: center; }
    .rs__row--head { background: none; color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .02em; }
    .rs__name { display: inline-flex; align-items: center; gap: var(--space-2); min-width: 0;
      font-weight: var(--weight-medium); overflow: hidden; }
    .rs__name span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .rs__num { border: 0; background: none; padding: 1px 6px; border-radius: var(--radius-sm); cursor: pointer;
      font: inherit; font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; color: var(--color-primary); }
    .rs__num:hover { background: var(--color-primary-soft, var(--color-surface)); text-decoration: underline; }
    .rs__num--doing { color: var(--status-active); }
    .rs__num--review { color: var(--status-pending); }
    .rs__num--done { color: var(--status-done); }
    .rs__zero { font-style: normal; color: var(--color-text-muted); opacity: .5; font-variant-numeric: tabular-nums; }
    .rs__pct { display: flex; align-items: center; gap: var(--space-2); justify-content: center; }
    .rs__bar { flex: 1; max-width: 70px; height: 6px; border-radius: 999px; background: var(--color-border); overflow: hidden; }
    .rs__fill { display: block; height: 100%; border-radius: 999px; background: var(--status-done); }
    .rs__pctv { min-width: 34px; text-align: right; font-size: var(--text-xs); color: var(--color-text-muted); }
    .rs__empty { padding: var(--space-2) var(--space-3); color: var(--color-text-muted); font-style: italic; font-size: var(--text-sm); }
    .rs__note { margin: 0; font-size: var(--text-xs); color: var(--color-text-muted); line-height: 1.5; }
  `]
})
export class RoleStats {
  /**
   * Tập task để thống kê (đã gồm Epic/Story — component tự bỏ).
   * Tổng quan truyền TOÀN BỘ task dự án; Báo cáo kỳ truyền task ĐÃ XỬ LÝ TRONG KỲ.
   */
  readonly tasks = input<ProjectTask[]>([]);
  /** Nhãn phạm vi ghép vào tiêu đề, vd "việc xử lý trong kỳ" — rỗng = toàn dự án. */
  readonly scopeLabel = input<string>('');
  /** Bấm một ô số → cha mở popup danh sách. */
  readonly picked = output<{ title: string; items: ProjectTask[] }>();

  readonly workCats = WORK_CATS;
  readonly cat = signal<WorkCat | 'ALL'>('ALL');
  readonly scopeSuffix = computed(() => this.scopeLabel() ? ` · ${this.scopeLabel()}` : '');

  /** Việc thực (bỏ Epic/Story) và bỏ việc Huỷ — khớp cách backend đếm theo nhân sự. */
  private readonly base = computed(() =>
    this.tasks().filter((t) => catOf(t.type) !== null && t.status !== 'CANCELLED'));

  /** Sau khi áp bộ lọc Loại. */
  readonly scoped = computed(() => {
    const c = this.cat();
    return c === 'ALL' ? this.base() : this.base().filter((t) => catOf(t.type) === c);
  });

  countOf(c: WorkCat): number { return this.base().filter((t) => catOf(t.type) === c).length; }

  pick(title: string, items: ProjectTask[]): void {
    if (items.length) this.picked.emit({ title, items });
  }

  readonly devRows = computed<DevRow[]>(() => {
    const map = new Map<string, DevRow>();
    for (const t of this.scoped()) {
      const key = t.assigneeUserId || t.assigneeName || '__none__';
      let r = map.get(key);
      if (!r) {
        r = { key, userId: t.assigneeUserId, name: t.assigneeName || '— Chưa gán —',
          unassigned: !t.assigneeUserId && !t.assigneeName,
          todo: [], doing: [], review: [], done: [], items: [], pct: 0 };
        map.set(key, r);
      }
      r.items.push(t);
      bucketOf(r, t.status).push(t);
    }
    const rows = [...map.values()];
    for (const r of rows) r.pct = r.items.length ? Math.round((r.done.length / r.items.length) * 100) : 0;
    return rows.sort((a, b) =>
      (a.unassigned ? 1 : 0) - (b.unassigned ? 1 : 0) || b.items.length - a.items.length
      || a.name.localeCompare(b.name, 'vi'));
  });

  readonly testRows = computed<TestRow[]>(() => {
    const map = new Map<string, TestRow>();
    const row = (id: string | null, name: string | null): TestRow => {
      const key = id || name || '__none__';
      let r = map.get(key);
      if (!r) {
        r = { key, userId: id, name: name || '— Không rõ —',
          logged: [], assigned: [], done: [], waitTest: [], waitDev: [], pct: 0 };
        map.set(key, r);
      }
      return r;
    };
    for (const t of this.scoped()) {
      // Đã LOG: chỉ bug/issue mới có khái niệm người log.
      if ((t.type === 'BUG' || t.type === 'ISSUE') && (t.reporterUserId || t.reporterName)) {
        row(t.reporterUserId ?? null, t.reporterName ?? null).logged.push(t);
      }
      // Vai kiểm thử: bug/issue chưa có tester thì người log chính là người verify (khớp backend).
      const isBug = t.type === 'BUG' || t.type === 'ISSUE';
      const tid = t.testerUserId ?? (isBug ? t.reporterUserId : null);
      const tname = t.testerName ?? (isBug ? t.reporterName : null);
      if (tid || tname) {
        const r = row(tid ?? null, tname ?? null);
        r.assigned.push(t);
        if (t.status === 'DONE') r.done.push(t);
        else if (t.status === 'IN_REVIEW') r.waitTest.push(t); // đang ở chân tester
        else r.waitDev.push(t);                                // Backlog/Cần làm/Đang làm → còn ở chân dev
      }
    }
    const rows = [...map.values()];
    for (const r of rows) {
      // Mẫu số là TOÀN BỘ việc mình phụ trách kiểm thử. Nếu chỉ lấy việc đã tới tay
      // (Hoàn thành + Chờ test) thì người log 50 bug, xong 7, còn 43 bug dev chưa bàn giao
      // sẽ ra 100% — con số vô nghĩa vì phần lớn việc còn chưa kiểm thử.
      r.pct = r.assigned.length ? Math.round((r.done.length / r.assigned.length) * 100) : 0;
    }
    return rows.sort((a, b) =>
      b.assigned.length - a.assigned.length
      || b.logged.length - a.logged.length || a.name.localeCompare(b.name, 'vi'));
  });
}

/** Trạng thái → ô tương ứng trong bảng vai dev (Cần làm gộp cả Backlog). */
function bucketOf(r: DevRow, s: TaskStatus): ProjectTask[] {
  switch (s) {
    case 'IN_PROGRESS': return r.doing;
    case 'IN_REVIEW': return r.review;
    case 'DONE': return r.done;
    default: return r.todo; // TODO / BACKLOG
  }
}
