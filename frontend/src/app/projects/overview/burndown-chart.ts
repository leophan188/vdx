import { Component, computed, input } from '@angular/core';
import { Burndown, BurndownPoint } from '../../core/project.service';

/** Một điểm đã chiếu lên toạ độ SVG (cho cả ideal/actual). */
interface PlotPt { x: number; y: number; p: BurndownPoint; }
/** Nhãn mốc thời gian hiển thị thưa trên trục X. */
interface XTick { x: number; label: string; }
/** Vạch + nhãn trên trục Y (giờ còn lại). */
interface YTick { y: number; label: string; }

/**
 * Biểu đồ Burndown vẽ bằng SVG thuần (KHÔNG thư viện chart).
 * 2 đường: Ideal (nét đứt, xám) và Actual (nét liền, màu nhấn).
 * Trục X = các mốc ngày (points[].date, nhãn thưa), trục Y = giờ còn lại (0 → totalEstimate).
 * Responsive: viewBox + width 100%. Tooltip hover đơn giản theo điểm actual.
 */
@Component({
  selector: 'app-burndown-chart',
  standalone: true,
  template: `
    <section class="bd">
      <header class="bd__head">
        <div>
          <h3 class="bd__title">Burndown — giờ còn lại</h3>
          @if (data(); as d) {
            <p class="bd__sub">
              Tổng est {{ d.totalEstimate }}h · đã log {{ d.totalSpent }}h · man-day nhóm {{ d.teamManday }}
            </p>
          }
        </div>
        <div class="bd__legend">
          <span class="bd__leg"><span class="bd__swatch bd__swatch--ideal"></span>Kế hoạch (ideal)</span>
          <span class="bd__leg"><span class="bd__swatch bd__swatch--actual"></span>Thực tế (actual)</span>
        </div>
      </header>

      @if (hasData()) {
        <svg class="bd__svg" [attr.viewBox]="'0 0 ' + W + ' ' + H"
             preserveAspectRatio="xMidYMid meet" role="img"
             aria-label="Biểu đồ burndown giờ còn lại theo thời gian">
          <!-- Lưới ngang + nhãn trục Y -->
          @for (t of yTicks(); track t.y) {
            <line class="bd__grid" [attr.x1]="PAD_L" [attr.y1]="t.y" [attr.x2]="W - PAD_R" [attr.y2]="t.y" />
            <text class="bd__axis-label" [attr.x]="PAD_L - 8" [attr.y]="t.y + 4" text-anchor="end">{{ t.label }}</text>
          }
          <!-- Trục -->
          <line class="bd__axis" [attr.x1]="PAD_L" [attr.y1]="PAD_T" [attr.x2]="PAD_L" [attr.y2]="H - PAD_B" />
          <line class="bd__axis" [attr.x1]="PAD_L" [attr.y1]="H - PAD_B" [attr.x2]="W - PAD_R" [attr.y2]="H - PAD_B" />

          <!-- Nhãn trục X (thưa) -->
          @for (t of xTicks(); track t.x) {
            <text class="bd__axis-label" [attr.x]="t.x" [attr.y]="H - PAD_B + 18" text-anchor="middle">{{ t.label }}</text>
          }

          <!-- Đường Ideal: nét đứt, xám -->
          <polyline class="bd__line bd__line--ideal" [attr.points]="idealPath()" />
          <!-- Đường Actual: nét liền, màu nhấn -->
          <polyline class="bd__line bd__line--actual" [attr.points]="actualPath()" />

          <!-- Điểm + vùng hover cho Actual (tooltip) -->
          @for (pt of actualPts(); track pt.x) {
            <circle class="bd__dot" [attr.cx]="pt.x" [attr.cy]="pt.y" r="2.5" />
            <circle class="bd__hit" [attr.cx]="pt.x" [attr.cy]="pt.y" r="10">
              <title>{{ pt.p.date }} — còn lại {{ pt.p.actual }}h (kế hoạch {{ pt.p.ideal }}h)</title>
            </circle>
          }
        </svg>
      } @else {
        <p class="bd__empty">Chưa đủ dữ liệu để vẽ burndown.</p>
      }
    </section>
  `,
  styles: [`
    .bd { padding: var(--space-5); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); box-shadow: var(--shadow-sm); }
    .bd__head { display: flex; flex-wrap: wrap; align-items: flex-start;
      justify-content: space-between; gap: var(--space-3); margin-bottom: var(--space-3); }
    .bd__title { margin: 0; font-size: var(--font-size-md, 1rem); }
    .bd__sub { margin: 4px 0 0; color: var(--color-text-muted); font-size: var(--text-sm, .85rem); }
    .bd__legend { display: flex; flex-wrap: wrap; gap: var(--space-3); font-size: var(--text-sm, .85rem);
      color: var(--color-text-muted); }
    .bd__leg { display: inline-flex; align-items: center; gap: 6px; }
    .bd__swatch { width: 18px; height: 0; border-radius: 2px; }
    .bd__swatch--ideal { border-top: 2px dashed var(--color-text-muted); }
    .bd__swatch--actual { border-top: 3px solid var(--color-primary); }

    .bd__svg { display: block; width: 100%; height: auto; overflow: visible; }
    .bd__grid { stroke: var(--color-border); stroke-width: 1; stroke-dasharray: 2 4; opacity: .7; }
    .bd__axis { stroke: var(--color-border); stroke-width: 1.5; }
    .bd__axis-label { fill: var(--color-text-muted); font-size: 11px; }
    .bd__line { fill: none; stroke-linejoin: round; stroke-linecap: round; }
    .bd__line--ideal { stroke: var(--color-text-muted); stroke-width: 2; stroke-dasharray: 6 5; opacity: .85; }
    .bd__line--actual { stroke: var(--color-primary); stroke-width: 2.5; }
    .bd__dot { fill: var(--color-primary); }
    .bd__hit { fill: transparent; cursor: pointer; }

    .bd__empty { margin: var(--space-4) 0; padding: var(--space-5); text-align: center;
      color: var(--color-text-muted); border: 1px dashed var(--color-border);
      border-radius: var(--radius-md, 8px); background: var(--color-surface-alt); }
  `]
})
export class BurndownChart {
  readonly data = input<Burndown | null>(null);

  // ----- Toạ độ viewBox (đơn vị "user units" của SVG, scale theo width 100%) -----
  readonly W = 720;
  readonly H = 320;
  readonly PAD_L = 48;   // chừa trục Y (nhãn giờ)
  readonly PAD_R = 16;
  readonly PAD_T = 16;
  readonly PAD_B = 36;   // chừa trục X (nhãn ngày)

  /** Đủ dữ liệu để vẽ: cần ≥ 2 điểm và có est > 0. */
  readonly hasData = computed(() => {
    const d = this.data();
    return !!d && d.points.length >= 2 && this.yMax() > 0;
  });

  /** Trục Y cao nhất = totalEstimate (fallback theo điểm) — 0 ở đáy. */
  private readonly yMax = computed(() => {
    const d = this.data();
    if (!d) return 0;
    const fromPoints = Math.max(0, ...d.points.flatMap((p) => [p.ideal, p.actual]));
    return Math.max(d.totalEstimate, fromPoints);
  });

  // ----- Hàm chiếu giá trị → toạ độ SVG -----
  private xAt(i: number, n: number): number {
    const span = this.W - this.PAD_L - this.PAD_R;
    return this.PAD_L + (n <= 1 ? 0 : (span * i) / (n - 1));
  }
  private yAt(v: number): number {
    const span = this.H - this.PAD_T - this.PAD_B;
    const max = this.yMax() || 1;
    return this.PAD_T + span * (1 - Math.max(0, Math.min(max, v)) / max);
  }

  private project(pick: (p: BurndownPoint) => number): PlotPt[] {
    const d = this.data();
    if (!d) return [];
    const n = d.points.length;
    return d.points.map((p, i) => ({ x: this.xAt(i, n), y: this.yAt(pick(p)), p }));
  }

  readonly actualPts = computed<PlotPt[]>(() => this.project((p) => p.actual));
  private readonly idealPts = computed<PlotPt[]>(() => this.project((p) => p.ideal));

  readonly actualPath = computed(() => this.toPoints(this.actualPts()));
  readonly idealPath = computed(() => this.toPoints(this.idealPts()));

  private toPoints(pts: PlotPt[]): string {
    return pts.map((q) => `${q.x.toFixed(1)},${q.y.toFixed(1)}`).join(' ');
  }

  /** Nhãn trục X hiển thị thưa (tối đa ~7 nhãn) để không chồng. */
  readonly xTicks = computed<XTick[]>(() => {
    const d = this.data();
    if (!d) return [];
    const n = d.points.length;
    const maxLabels = 7;
    const step = Math.max(1, Math.ceil(n / maxLabels));
    const ticks: XTick[] = [];
    for (let i = 0; i < n; i += step) {
      ticks.push({ x: this.xAt(i, n), label: this.shortDate(d.points[i].date) });
    }
    // Luôn có nhãn mốc cuối.
    const last = n - 1;
    if (last > 0 && (last % step !== 0)) {
      ticks.push({ x: this.xAt(last, n), label: this.shortDate(d.points[last].date) });
    }
    return ticks;
  });

  /** 5 vạch ngang trên trục Y (0 → yMax). */
  readonly yTicks = computed<YTick[]>(() => {
    const max = this.yMax();
    if (max <= 0) return [];
    const steps = 4;
    const ticks: YTick[] = [];
    for (let i = 0; i <= steps; i++) {
      const v = (max * i) / steps;
      ticks.push({ y: this.yAt(v), label: Math.round(v) + 'h' });
    }
    return ticks;
  });

  /** dd/MM/yyyy → dd/MM (gọn nhãn trục). */
  private shortDate(d: string): string {
    const parts = d.split('/');
    return parts.length >= 2 ? `${parts[0]}/${parts[1]}` : d;
  }
}
