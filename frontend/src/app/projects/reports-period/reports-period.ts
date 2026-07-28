import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { StatCard } from '../../shared/stat-card/stat-card';
import { Modal } from '../../shared/modal/modal';
import { PrjTaskDetail } from '../task-detail/task-detail';
import { RoleStats } from '../role-stats/role-stats';
import {
  ProjectService, PeriodReport, ReportTaskItem, TaskStatus, TaskType, TaskPriority, ProjectTask
} from '../../core/project.service';
import { WORK_CATS, catOf, WorkCat, TYPE_META, STATUS_META, isOverdue, ownerOf } from '../work-stats';

/** Kỳ báo cáo đang xem. */
type Period = 'daily' | 'weekly';

/** Một khối danh sách task (Đã xong / Đang làm / Sắp làm / Trễ hạn). */
interface ReportBlock {
  key: 'done' | 'inProgress' | 'upcoming' | 'overdue' | 'todo';
  icon: string;
  title: string;
  rows: ReportTaskItem[];
  emptyText: string;
}

/** Mức ưu tiên (thống kê bug/issue). */
const PRIORITY_META: { key: TaskPriority; label: string; color: string }[] = [
  { key: 'URGENT', label: 'Khẩn cấp', color: 'var(--overdue, #e5484d)' },
  { key: 'HIGH', label: 'Cao', color: 'var(--status-pending, #d97706)' },
  { key: 'MEDIUM', label: 'Trung bình', color: 'var(--status-active, #2563eb)' },
  { key: 'LOW', label: 'Thấp', color: 'var(--color-text-muted, #64748b)' }
];

interface TypeStatusRow {
  key: WorkCat; label: string; icon: string; color: string;
  byStatus: Record<string, number>; itemsByStatus: Record<string, ReportTaskItem[]>;
  items: ReportTaskItem[]; total: number;
}
interface PriorityStat {
  key: TaskPriority; label: string; color: string; count: number; pct: number; items: ReportTaskItem[];
}
interface PersonStat {
  userId: string | null; name: string;
  total: number; task: number; bug: number; issue: number; done: number;
  items: ReportTaskItem[];
}
interface BugPerson { userId: string | null; name: string; count: number; items: ReportTaskItem[]; }

/**
 * Báo cáo Daily & Weekly (selector app-prj-reports-period).
 * Ngoài số liệu tổng quan + 4 khối danh sách (thu gọn được), bổ sung:
 *  1) Ma trận Task/Bug/Issue × trạng thái.
 *  2) Bug/Issue theo mức ưu tiên.
 *  3) Các trạng thái công việc (Đã xong/Trễ/Đang làm/Sắp làm) — mỗi phần thu gọn được.
 *  4) Thống kê theo nhân sự; bấm 1 người → popup danh sách công việc của người đó.
 */
@Component({
  selector: 'app-prj-reports-period',
  imports: [DataGrid, GridCellDirective, EmployeeChip, StatCard, Modal, PrjTaskDetail, RoleStats],
  templateUrl: './reports-period.html',
  styles: [`
    .rpp { display: grid; gap: var(--space-4); font-size: var(--text-sm); color: var(--color-text); }

    .rpp__head { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; }
    .rpp__switch { display: inline-flex; border: 1px solid var(--color-border); border-radius: var(--radius-md); overflow: hidden; }
    .rpp__switch button { border: 0; background: var(--color-surface); color: var(--color-text-muted);
      padding: 0 var(--space-4); height: var(--control-h-sm); font: inherit; cursor: pointer; }
    .rpp__switch button + button { border-left: 1px solid var(--color-border); }
    .rpp__switch button.is-active { background: var(--color-primary); color: var(--color-text-invert); font-weight: var(--weight-medium); }
    .rpp__period { font-weight: var(--weight-semibold); color: var(--color-text); }
    .rpp__date { display: inline-flex; align-items: center; gap: var(--space-2); font-size: var(--text-sm);
      color: var(--color-text-muted); }
    .rpp__date input { height: var(--control-h-sm); border: 1px solid var(--color-border);
      border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text);
      padding: 0 var(--space-2); font: inherit; }
    .rpp__head-spacer { flex: 1 1 auto; }

    .rpp__hero { display: grid; gap: var(--space-2); padding: var(--space-5);
      border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); }
    .rpp__hero-top { display: flex; align-items: baseline; justify-content: space-between; gap: var(--space-3); }
    .rpp__hero-label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: var(--weight-semibold); }
    .rpp__hero-pct { font-size: 28px; font-weight: var(--weight-semibold); color: var(--color-primary); line-height: 1; }
    .rpp__hero-bar { height: 16px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rpp__hero-fill { height: 100%; border-radius: var(--radius-full);
      background: linear-gradient(90deg, var(--status-active), var(--status-done)); transition: width .3s ease; }
    .rpp__stats { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); }
    /* Thẻ số liệu bấm được → mở danh sách việc đằng sau con số */
    .rpp__statbtn { cursor: pointer; border-radius: var(--radius-lg); transition: transform .12s ease, box-shadow .12s ease; }
    .rpp__statbtn:hover { transform: translateY(-1px); box-shadow: var(--shadow-md, 0 4px 14px rgba(0,0,0,.18)); }

    /* ===== Ô SỐ bấm được (ma trận, ưu tiên, nhân sự, Epic/Story) ===== */
    .rpp__num { border: 0; background: none; padding: 1px 6px; border-radius: var(--radius-sm); cursor: pointer;
      font: inherit; font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; color: var(--color-primary); }
    .rpp__num:hover { background: var(--color-primary-soft, var(--color-surface-alt)); text-decoration: underline; }
    .rpp__num--done { color: var(--status-done); }
    .rpp__num--open { color: var(--status-pending); }
    .rpp__num-zero { padding: 1px 6px; color: var(--color-text-muted); opacity: .5; font-variant-numeric: tabular-nums; }

    /* Dòng Epic/Story (có thêm cột Xong / Chưa xong) */
    .rpp__es-row { display: flex; align-items: center; gap: 10px; padding: 5px 0; border-bottom: 1px solid var(--color-border); }
    .rpp__es-row--head { color: var(--color-text-muted); font-size: var(--text-xs); font-weight: var(--weight-semibold);
      text-transform: uppercase; letter-spacing: .02em; }
    .rpp__es-nums { flex: 0 0 auto; display: flex; align-items: center; }
    .rpp__es-nums > * { flex: 0 0 122px; text-align: center; white-space: nowrap; }
    /* Pill LOẠI (Epic/Story) — màu theo loại (design system không có badge--info) */
    .rpp__tag { flex: 0 0 auto; font-size: var(--text-xs); font-weight: 700; padding: 1px 8px; border-radius: 999px;
      white-space: nowrap; color: var(--tb-color, var(--color-primary));
      background: color-mix(in srgb, var(--tb-color, var(--color-primary)) 14%, transparent); }

    /* Mã / tiêu đề công việc bấm được → mở chi tiết */
    .rpp__code-btn { border: 0; cursor: pointer; font: inherit; }
    .rpp__code-btn:hover { text-decoration: underline; }
    .rpp__title-btn { border: 0; background: none; padding: 0; cursor: pointer; font: inherit; color: inherit;
      text-align: left; }
    .rpp__title-btn:hover { color: var(--color-primary); text-decoration: underline; }

    /* Bộ lọc LOẠI trong popup chi tiết */
    .rpp__d-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
    .rpp__d-filters-lbl { font-size: var(--text-xs); color: var(--color-text-muted); margin-right: 2px; }
    .rpp__d-chip { border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer;
      font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--color-text-muted);
      padding: 3px 9px; border-radius: 999px; white-space: nowrap; }
    .rpp__d-chip b { font-variant-numeric: tabular-nums; opacity: .75; }
    .rpp__d-chip:hover { border-color: var(--tb-color, var(--color-primary)); color: var(--tb-color, var(--color-primary)); }
    .rpp__d-chip.is-active { border-color: var(--tb-color, var(--color-primary)); color: var(--tb-color, var(--color-primary));
      background: color-mix(in srgb, var(--tb-color, var(--color-primary)) 12%, transparent); }

    /* Section chung (thu gọn được) */
    .rpp__sec { border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); overflow: hidden; }
    .rpp__sec-head { display: flex; align-items: center; gap: var(--space-2); width: 100%;
      padding: var(--space-3) var(--space-4); background: var(--color-surface); border: 0; cursor: pointer;
      font: inherit; color: var(--color-text); font-weight: var(--weight-semibold); text-align: left; }
    .rpp__sec-head:hover { background: var(--color-surface-alt); }
    .rpp__sec-caret { transition: transform .15s ease; color: var(--color-text-muted); }
    .rpp__sec-caret.is-collapsed { transform: rotate(-90deg); }
    .rpp__sec-count { margin-left: auto; font-size: var(--text-xs); font-weight: var(--weight-medium);
      color: var(--color-text-muted); background: var(--color-surface-alt); padding: 1px var(--space-2); border-radius: var(--radius-full); }
    .rpp__sec-body { padding: var(--space-4); border-top: 1px solid var(--color-border); display: grid; gap: var(--space-3); }

    /* Ma trận loại × trạng thái */
    .rpp__matrix { display: grid; gap: 2px; overflow-x: auto; }
    .rpp__mrow { display: grid; grid-template-columns: minmax(140px, 1.4fr) repeat(6, minmax(52px, 1fr)) minmax(56px, .8fr) minmax(120px, 1.2fr);
      align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3);
      border-radius: var(--radius-md); font-variant-numeric: tabular-nums; }
    .rpp__mrow > span:not(.rpp__mname) { text-align: center; }
    .rpp__mpct { display: flex; align-items: center; gap: var(--space-2); justify-content: center; }
    .rpp__mpct-bar { flex: 1; max-width: 76px; height: 6px; border-radius: 999px; background: var(--color-border); overflow: hidden; }
    .rpp__mpct-fill { display: block; height: 100%; border-radius: 999px; background: var(--status-done); }
    .rpp__mpct-val { min-width: 34px; text-align: right; font-size: var(--text-xs); color: var(--color-text-muted); }
    .rpp__mrow:not(.rpp__mrow--head) { background: var(--color-surface-alt); }
    .rpp__mrow--head { color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .03em; }
    .rpp__mname { display: inline-flex; align-items: center; gap: var(--space-2); font-weight: var(--weight-medium); }
    .rpp__mdot { width: 8px; height: 8px; border-radius: 50%; background: var(--cat-color, var(--color-primary)); }
    .rpp__mtotal { font-weight: var(--weight-semibold); }
    .rpp__zero { color: var(--color-text-muted); opacity: .5; }

    /* Ưu tiên bug/issue */
    .rpp__prio { display: grid; gap: var(--space-2); }
    .rpp__prio-row { display: grid; grid-template-columns: 110px 1fr 44px; align-items: center; gap: var(--space-3); }
    .rpp__prio-name { display: inline-flex; align-items: center; gap: var(--space-2); }
    .rpp__prio-dot { width: 9px; height: 9px; border-radius: 50%; background: var(--p-color); }
    .rpp__prio-bar { height: 10px; border-radius: var(--radius-full); background: var(--color-surface-alt); overflow: hidden; }
    .rpp__prio-fill { display: block; height: 100%; border-radius: var(--radius-full); background: var(--p-color); }
    .rpp__prio-val { text-align: right; font-variant-numeric: tabular-nums; font-weight: var(--weight-semibold); }
    .rpp__empty-note { color: var(--color-text-muted); font-size: var(--text-sm); }

    /* Theo nhân sự */
    .rpp__note { margin: 0; font-size: var(--text-xs); color: var(--color-text-muted); line-height: 1.5; }
    .rpp__note b { color: var(--color-text); font-weight: 600; }
    .rpp__people { display: grid; gap: 2px; }
    .rpp__prow { display: grid; grid-template-columns: minmax(180px, 2fr) repeat(4, minmax(84px, 1fr)) minmax(150px, 1.5fr);
      align-items: center; gap: var(--space-2); padding: var(--space-2) var(--space-3); border-radius: var(--radius-md);
      font-variant-numeric: tabular-nums; }
    .rpp__prow > span:not(.rpp__pname) { text-align: center; }
    .rpp__ppct { display: flex; align-items: center; gap: var(--space-2); justify-content: center; }
    .rpp__ppct-bar { flex: 1; max-width: 72px; height: 6px; border-radius: 999px; background: var(--color-border); overflow: hidden; }
    .rpp__ppct-fill { display: block; height: 100%; border-radius: 999px; background: var(--status-done); }
    .rpp__ppct-val { min-width: 34px; text-align: right; font-size: var(--text-xs); color: var(--color-text-muted); }
    .rpp__prow--head { color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .03em; }
    /* Dòng nhân sự: cả dòng KHÔNG còn là nút — từng con số mới bấm được (mở đúng danh sách của số đó). */
    .rpp__prow--body { width: 100%; background: var(--color-surface-alt); color: var(--color-text); text-align: left; }
    .rpp__pname { display: inline-flex; align-items: center; gap: var(--space-2); font-weight: var(--weight-medium); }
    .rpp__ptotal { font-weight: var(--weight-semibold); }
    .rpp__pchev { color: var(--color-text-muted); }

    /* Bug/Issue theo nhân sự: tester log vs dev bị log */
    .rpp__bugcols { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); }
    .rpp__bugcol-title { font-size: var(--text-sm); font-weight: var(--weight-semibold);
      color: var(--color-text); margin: 0 0 var(--space-2); display: flex; align-items: center; gap: 6px; }
    .rpp__bugrow { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: var(--space-2); width: 100%;
      padding: var(--space-2) var(--space-3); border-radius: var(--radius-md); background: var(--color-surface-alt);
      border: 0; cursor: pointer; font: inherit; color: var(--color-text); text-align: left; margin-bottom: 2px; }
    .rpp__bugrow:hover { background: var(--color-primary-soft); color: var(--color-primary); }
    .rpp__bugrow-name { display: inline-flex; align-items: center; gap: var(--space-2); min-width: 0; }
    .rpp__bugrank { color: var(--color-text-muted); font-size: var(--text-xs); min-width: 18px; }
    .rpp__bugcount { font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums;
      background: color-mix(in srgb, var(--overdue, #e5484d) 15%, transparent); color: var(--overdue, #e5484d);
      padding: 0 9px; border-radius: 999px; font-size: var(--text-xs); }

    /* Dòng phụ chuỗi cha trong lưới danh sách */
    .rpp__li-parent { font-size: var(--text-xs); color: var(--color-text-muted); margin-top: 2px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 520px; }
    /* Số lượng đã hiện ở tiêu đề section → ẩn "N mục" của lưới bên trong (tránh trùng). */
    .rpp__sec-body ::ng-deep .grid__count { display: none; }

    .rpp__pct-cell { display: flex; align-items: center; gap: var(--space-2); }
    .rpp__mini-bar { flex: 1; min-width: 56px; height: 8px; border-radius: var(--radius-full);
      background: var(--color-surface-alt); overflow: hidden; }
    .rpp__mini-fill { height: 100%; border-radius: var(--radius-full); background: var(--status-done); }
    .rpp__pct-val { font-size: var(--text-xs); color: var(--color-text-muted); min-width: 32px; text-align: right; }

    .rpp__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
    .rpp__type-badge { font-size: var(--text-xs); font-weight: 700; padding: 1px 7px; border-radius: 999px;
      color: var(--tb-color); background: color-mix(in srgb, var(--tb-color) 14%, transparent);
      border: 1px solid color-mix(in srgb, var(--tb-color) 36%, transparent); }

    /* ===================== TRANG IN (HTML → PDF) ===================== */
    .rp-overlay { position: fixed; inset: 0; z-index: 1000; overflow: auto;
      background: #5b6472; padding: 20px 12px 40px; display: flex; flex-direction: column; align-items: center; }
    .rp-bar { position: sticky; top: 0; z-index: 2; width: 210mm; max-width: 100%;
      display: flex; align-items: center; gap: 10px; padding: 8px 12px; margin-bottom: 14px;
      background: #1f2937; color: #e5e7eb; border-radius: 8px; }
    .rp-bar__hint { font-size: 12px; opacity: .85; }
    .rp-bar__spacer { flex: 1; }

    /* Trang A4 */
    .rp-page { width: 210mm; max-width: 100%; min-height: 297mm; background: #fff; color: #1f2937;
      padding: 12mm 12mm 14mm; box-shadow: 0 6px 30px rgba(0,0,0,.35);
      font-family: system-ui, "Segoe UI", Roboto, sans-serif; font-size: 11px; line-height: 1.35; }

    .rp__head { background: #1e3a5f; color: #fff; border-radius: 8px; padding: 12px 16px; text-align: center; margin-bottom: 12px; }
    .rp__head-title { font-size: 17px; font-weight: 800; letter-spacing: .3px; }
    .rp__head-meta { margin-top: 4px; font-size: 11px; background: rgba(255,255,255,.14); display: inline-block;
      padding: 2px 12px; border-radius: 999px; }
    .rp__head-proj { margin-top: 5px; font-size: 12px; opacity: .92; }

    .rp__cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 14px; }
    .rp__card { border-radius: 10px; padding: 10px 12px; text-align: center; border: 1.5px solid; }
    .rp__card-lbl { font-size: 11px; font-weight: 800; padding: 4px 0; border-radius: 6px; color: #fff; margin: -10px -12px 8px; }
    .rp__card-num { font-size: 30px; font-weight: 800; line-height: 1; }
    .rp__card--done { border-color: #2ea05a; background: #eef8f1; color: #1e7e42; }
    .rp__card--done .rp__card-lbl { background: #2ea05a; }
    .rp__card--doing { border-color: #1e50a0; background: #eef2fb; color: #1e50a0; }
    .rp__card--doing .rp__card-lbl { background: #1e50a0; }
    .rp__card--over { border-color: #c0392b; background: #fdeeec; color: #c0392b; }
    .rp__card--over .rp__card-lbl { background: #c0392b; }

    .rp__block { margin-bottom: 12px; break-inside: avoid; }
    .rp__h { background: #1e3a5f; color: #fff; font-size: 12px; font-weight: 800; letter-spacing: .3px;
      padding: 5px 10px; border-radius: 6px 6px 0 0; margin: 0; }
    .rp__ov { border: 1px solid #e5e7eb; border-top: 0; border-radius: 0 0 6px 6px; overflow: hidden; }
    .rp__ov-row { display: grid; grid-template-columns: 26px 1fr auto; align-items: center; gap: 8px;
      padding: 5px 10px; border-top: 1px solid #eef0f2; }
    .rp__ov-row:nth-child(odd) { background: #f8fafc; }
    .rp__ov-ic { text-align: center; }
    .rp__ov-lbl { color: #475569; }
    .rp__ov-val { font-weight: 800; font-variant-numeric: tabular-nums; }
    .rp__ov-val.is-done { color: #1e7e42; } .rp__ov-val.is-doing { color: #1e50a0; } .rp__ov-val.is-over { color: #c0392b; }

    .rp__status { display: grid; grid-template-columns: 150px 1fr; gap: 14px; align-items: center;
      border: 1px solid #e5e7eb; border-top: 0; border-radius: 0 0 6px 6px; padding: 12px; }
    .rp__donut-wrap { display: flex; justify-content: center; }
    .rp__donut { width: 120px; height: 120px; border-radius: 50%; position: relative;
      -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .rp__donut-hole { position: absolute; inset: 26px; background: #fff; border-radius: 50%;
      display: flex; flex-direction: column; align-items: center; justify-content: center; }
    .rp__donut-hole b { font-size: 22px; font-weight: 800; } .rp__donut-hole small { font-size: 10px; color: #64748b; }
    .rp__legend { display: flex; flex-direction: column; gap: 2px; }
    .rp__leg-row { display: grid; grid-template-columns: 14px 1fr 40px 48px; align-items: center; gap: 6px;
      padding: 3px 6px; border-radius: 4px; }
    .rp__leg-row:nth-child(even) { background: #f8fafc; }
    .rp__leg-row--head { background: #eef2f7 !important; font-weight: 700; color: #475569; text-transform: uppercase; font-size: 9px; }
    .rp__leg-row--head span:last-child, .rp__leg-row--head span:nth-child(3) { text-align: right; }
    .rp__leg-dot { width: 10px; height: 10px; border-radius: 50%; }
    .rp__leg-cnt { text-align: right; font-weight: 700; font-variant-numeric: tabular-nums; }
    .rp__leg-pct { text-align: right; color: #64748b; font-variant-numeric: tabular-nums; }

    .rp__tbl { width: 100%; border-collapse: collapse; table-layout: fixed; }
    .rp__tbl colgroup .c-stt { width: 32px; } .rp__tbl colgroup .c-pic { width: 42px; }
    .rp__tbl colgroup .c-n { width: 58px; } .rp__tbl colgroup .c-status { width: 92px; }
    .rp__tbl colgroup .c-date { width: 78px; } .rp__tbl colgroup .c-pct { width: 130px; }
    .rp__tbl th { background: #eef2f7; color: #475569; font-size: 9.5px; text-transform: uppercase; letter-spacing: .02em;
      font-weight: 800; padding: 5px 6px; border: 1px solid #e2e8f0; text-align: center; }
    .rp__tbl td { padding: 4px 6px; border: 1px solid #eef0f2; text-align: center; vertical-align: middle;
      font-variant-numeric: tabular-nums; }
    .rp__tbl tbody tr:nth-child(even) td { background: #f8fafc; }
    .rp__l { text-align: left !important; }
    /* Tên công việc dài → XUỐNG DÒNG (không cắt "…"), giữ nguyên chiều rộng cột. */
    .rp__tbl td { vertical-align: top; word-break: break-word; overflow-wrap: anywhere; }
    .rp__tbl td.rp__l { white-space: normal; overflow: visible; }
    .rp__ava { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px;
      border-radius: 50%; background: #1e50a0; color: #fff; font-size: 9px; font-weight: 800; margin-right: 6px; vertical-align: middle; }
    .rp__ava--sm { margin-right: 0; }
    .rp__sdot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }
    .rp__es-tag { display: inline-block; font-size: 8px; font-weight: 800; letter-spacing: .03em; padding: 1px 5px;
      border-radius: 4px; margin-right: 6px; background: #e3ecf9; color: #1e50a0; vertical-align: middle; }
    .rp__es-tag--epic { background: #efe6fb; color: #6b3fb0; }
    .rp__epic-row td { background: #f3f6fb !important; font-weight: 700; }
    .rp__pbar { position: relative; height: 14px; border-radius: 999px; background: #edf0f4; overflow: hidden; }
    .rp__pbar-fill { position: absolute; left: 0; top: 0; height: 100%; border-radius: 999px; background: #3fbf6a; }
    .rp__pbar-val { position: relative; z-index: 1; font-size: 9px; font-weight: 700; color: #14532d;
      line-height: 14px; padding-right: 6px; display: block; text-align: right; }
    .rp__more { padding: 5px 8px; text-align: center; color: #64748b; font-style: italic; font-size: 10px;
      border: 1px solid #eef0f2; border-top: 0; }

    @media print {
      .no-print { display: none !important; }
      /* padding = lề giấy (do @page margin phải = 0 để Chrome bỏ header/footer URL) */
      .rp-overlay { position: static; background: #fff; padding: 10mm; box-sizing: border-box; display: block; }
      .rp-page { width: auto; min-height: auto; box-shadow: none; padding: 0; }
      .rp__donut, .rp__card, .rp__card-lbl, .rp__h, .rp__head, .rp__pbar-fill, .rp__leg-dot, .rp__sdot, .rp__ava {
        -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    }
  `]
})
export class PrjReportsPeriod {
  readonly projectId = input.required<string>();
  readonly projectName = input<string>('');

  private svc = inject(ProjectService);

  /** Bật lớp phủ "trang in" (giống biểu mẫu khách hàng) trước khi window.print(). */
  readonly printMode = signal(false);

  readonly period = signal<Period>('daily');
  readonly report = signal<PeriodReport | null>(null);
  readonly loading = signal(true);
  /** Ngày báo cáo (yyyy-MM-dd) — daily: ngày đó; weekly: tuần chứa ngày đó. Mặc định hôm nay. */
  readonly reportDate = signal<string>(this.todayIso());

  private todayIso(): string {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
  }

  readonly statusMeta = STATUS_META;
  readonly priorityMeta = PRIORITY_META;

  /** Phần đang thu gọn (ẩn). Mặc định mở hết. */
  readonly collapsed = signal<Set<string>>(new Set());
  isCollapsed(key: string): boolean { return this.collapsed().has(key); }
  toggle(key: string): void {
    const s = new Set(this.collapsed());
    s.has(key) ? s.delete(key) : s.add(key);
    this.collapsed.set(s);
  }

  // ===================== POPUP CHI TIẾT (bấm vào MỌI con số thống kê) =====================

  /**
   * Popup danh sách công việc chi tiết.
   * `byPerson` = đã lọc sẵn theo 1 nhân sự → ẩn cột "Người làm" cho đỡ thừa.
   */
  readonly detailModal = signal<{ title: string; items: ReportTaskItem[]; byPerson: boolean } | null>(null);
  /** Loại công việc đang lọc trong popup ('ALL' = tất cả). */
  readonly detailType = signal<TaskType | 'ALL'>('ALL');

  /** Mở popup; bỏ qua nếu con số bằng 0 (không có gì để xem). */
  openDetail(title: string, items: ReportTaskItem[], byPerson = false): void {
    if (!items.length) return;
    this.detailType.set('ALL');
    this.detailModal.set({ title: `${title} — ${items.length} việc`, items, byPerson });
  }
  closeDetail(): void { this.detailModal.set(null); this.detailType.set('ALL'); }

  /** Chip lọc theo loại: chỉ liệt kê loại THỰC SỰ có trong danh sách, kèm số đếm. */
  readonly detailTypeChips = computed<{ key: TaskType | 'ALL'; label: string; count: number; color: string }[]>(() => {
    const items = this.detailModal()?.items ?? [];
    if (!items.length) return [];
    const order: TaskType[] = ['EPIC', 'STORY', 'TASK', 'SUBTASK', 'BUG', 'ISSUE'];
    const chips = order
      .map((t) => ({ key: t as TaskType | 'ALL', label: this.typeLabel(t), color: this.typeColor(t),
        count: items.filter((i) => i.type === t).length }))
      .filter((c) => c.count > 0);
    return [{ key: 'ALL' as const, label: 'Tất cả', count: items.length, color: 'var(--color-primary)' }, ...chips];
  });

  /** Danh sách đưa vào lưới sau khi lọc loại. */
  readonly detailRows = computed<ReportTaskItem[]>(() => {
    const items = this.detailModal()?.items ?? [];
    const t = this.detailType();
    return t === 'ALL' ? items : items.filter((i) => i.type === t);
  });

  // ===== Danh sách task ĐẦY ĐỦ của dự án =====
  // Cần cho: (a) mở popup chi tiết công việc, (b) Epic/Story xong-chưa xong,
  // (c) danh sách đứng sau các thẻ số liệu TOÀN DỰ ÁN của báo cáo tuần.
  readonly tasks = signal<ProjectTask[]>([]);

  /**
   * "Việc thực thi" = LÁ và không phải Epic/Story — ĐÚNG quy tắc backend dùng để đếm
   * totalTasks / doneTasks / bugCount / overdueCount ở thẻ tổng quan tuần.
   * Dùng cờ `leaf` do backend trả về, không tự suy lại, để hai bên không lệch nhau.
   */
  private readonly workItems = computed(() =>
    this.tasks().filter((t) => t.leaf && t.type !== 'EPIC' && t.type !== 'STORY'));

  readonly ovTotalItems = computed(() => this.workItems().map((t) => this.toItem(t)));
  readonly ovDoneItems = computed(() => this.workItems().filter((t) => t.status === 'DONE').map((t) => this.toItem(t)));
  readonly ovBugItems = computed(() => this.workItems().filter((t) => t.type === 'BUG').map((t) => this.toItem(t)));
  readonly ovOverdueItems = computed(() =>
    this.workItems().filter((t) => isOverdue(t.dueDate, t.status)).map((t) => this.toItem(t)));

  /** ProjectTask → dòng hiển thị giống ReportTaskItem, để mọi popup dùng chung một kiểu dữ liệu. */
  private toItem(t: ProjectTask): ReportTaskItem {
    const own = ownerOf(t);
    return {
      ownerUserId: own.id, ownerName: own.name,
      testerUserId: t.testerUserId ?? null, testerName: t.testerName ?? null,
      taskId: t.id, code: t.code, title: t.title, type: t.type, status: t.status,
      assigneeName: t.assigneeName, estimateHours: t.estimateHours,
      startDate: t.startDate, dueDate: t.dueDate, progressPct: t.progressPct,
      priority: t.priority, severity: t.severity, assigneeUserId: t.assigneeUserId,
      parentPath: (t.parentChain ?? []).map((p) => p.title).join(' › ') || null,
      reporterUserId: t.reporterUserId ?? null, reporterName: t.reporterName ?? null
    };
  }

  /**
   * Tiến độ EPIC/Story kèm số việc con XONG / CHƯA XONG (đếm mọi hậu duệ là việc thực thi).
   * Chỉ số % vẫn lấy từ backend (r.epicStory) để không lệch với bản in.
   */
  readonly epicStoryRows = computed(() => {
    const es = this.report()?.epicStory ?? [];
    if (!es.length) return [];
    const kids = new Map<string, ProjectTask[]>();
    for (const t of this.tasks()) {
      const k = t.parentId ?? '';
      const arr = kids.get(k);
      arr ? arr.push(t) : kids.set(k, [t]);
    }
    const descWork = (id: string): ProjectTask[] => {
      const out: ProjectTask[] = [];
      const stack = [...(kids.get(id) ?? [])];
      let guard = 0;
      while (stack.length && guard++ < 5000) {
        const n = stack.pop()!;
        if (catOf(n.type)) out.push(n);
        stack.push(...(kids.get(n.id) ?? []));
      }
      return out;
    };
    return es.map((e) => {
      const items = descWork(e.taskId);
      return {
        ...e,
        doneItems: items.filter((i) => i.status === 'DONE').map((t) => this.toItem(t)),
        openItems: items.filter((i) => i.status !== 'DONE' && i.status !== 'CANCELLED').map((t) => this.toItem(t))
      };
    });
  });

  // ===== Chi tiết công việc (mở chồng lên popup danh sách) =====
  readonly taskDetail = signal<ProjectTask | null>(null);
  readonly taskDetailOpen = signal(false);
  /** Bấm 1 dòng bất kỳ → mở chi tiết task/bug kiểu Jira (tra ProjectTask theo taskId). */
  openTask(it: ReportTaskItem): void {
    const t = this.tasks().find((x) => x.id === it.taskId);
    if (!t) return; // chưa tải xong danh sách task — bỏ qua thay vì mở popup rỗng
    this.taskDetail.set(t);
    this.taskDetailOpen.set(true);
  }
  closeTask(): void {
    this.taskDetailOpen.set(false);
    this.taskDetail.set(null);
  }

  readonly pct = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.report()?.overview.completionPct ?? 0)))
  );

  /** Hợp nhất mọi công việc trong kỳ (khử trùng theo taskId). */
  readonly allItems = computed<ReportTaskItem[]>(() => {
    const r = this.report();
    if (!r) return [];
    const map = new Map<string, ReportTaskItem>();
    for (const it of [...r.done, ...r.inProgress, ...r.todo, ...r.upcoming, ...r.overdue]) map.set(it.taskId, it);
    return [...map.values()];
  });

  // ===== (1) Ma trận Task/Bug/Issue × trạng thái =====
  // Giữ luôn DANH SÁCH từng ô để bấm số là mở đúng những việc đã đếm.
  readonly typeStatusRows = computed<TypeStatusRow[]>(() => {
    const items = this.allItems();
    return WORK_CATS.map((c) => {
      const list = items.filter((i) => catOf(i.type) === c.key);
      const byStatus: Record<string, number> = {};
      const itemsByStatus: Record<string, ReportTaskItem[]> = {};
      for (const s of STATUS_META) {
        const bucket = list.filter((i) => i.status === s.key);
        byStatus[s.key] = bucket.length;
        itemsByStatus[s.key] = bucket;
      }
      return { key: c.key, label: c.label, icon: c.icon, color: c.color, byStatus, itemsByStatus,
        items: list, total: list.length };
    });
  });

  // ===== (2) Bug/Issue theo mức ưu tiên =====
  readonly bugPriority = computed<PriorityStat[]>(() => {
    const bugs = this.allItems().filter((i) => i.type === 'BUG' || i.type === 'ISSUE');
    const max = Math.max(1, ...PRIORITY_META.map((p) => bugs.filter((b) => b.priority === p.key).length));
    return PRIORITY_META.map((p) => {
      const items = bugs.filter((b) => b.priority === p.key);
      return { key: p.key, label: p.label, color: p.color, count: items.length, items,
        pct: Math.round((items.length / max) * 100) };
    });
  });
  readonly bugTotal = computed(() => this.allItems().filter((i) => i.type === 'BUG' || i.type === 'ISSUE').length);

  // ===== (4) Theo nhân sự =====
  readonly byPerson = computed<PersonStat[]>(() => {
    const map = new Map<string, PersonStat>();
    for (const it of this.allItems()) {
      const key = it.assigneeUserId || it.assigneeName || '__none__';
      let p = map.get(key);
      if (!p) {
        p = { userId: it.assigneeUserId, name: it.assigneeName || '— Chưa gán —',
          total: 0, task: 0, bug: 0, issue: 0, done: 0, items: [] };
        map.set(key, p);
      }
      p.items.push(it);
      p.total++;
      if (it.type === 'BUG') p.bug++;
      else if (it.type === 'ISSUE') p.issue++;
      else if (catOf(it.type) === 'TASK') p.task++;
      if (it.status === 'DONE') p.done++;
    }
    return [...map.values()].sort((a, b) => b.total - a.total);
  });

  /**
   * Thống kê nhân sự cho BÁO CÁO: tổng CV toàn dự án + số việc xử lý/hoàn thành TRONG KỲ.
   * Số TRONG KỲ lấy THẲNG từ backend (đã lọc theo kỳ). KHÔNG tự suy từ các nhóm
   * done/inProgress/upcoming/overdue nữa vì 3 nhóm sau không lọc kỳ → ra toàn bộ việc đang mở.
   */
  readonly peopleStats = computed(() => {
    const overall = this.report()?.byPerson ?? [];
    // Danh sách chi tiết lấy TỪ CÙNG nguồn với con số (periodItems của backend) → bấm vào
    // luôn thấy đúng số việc đã đếm, không còn cảnh "1 việc nhưng popup trống".
    const items = this.report()?.periodItems ?? [];
    const byUser = new Map<string, ReportTaskItem[]>();
    for (const it of items) {
      // Gom theo CHỦ HIỆN TẠI — backend đếm inPeriod/donePeriod theo ownerUserId,
      // nhóm theo assignee ở đây sẽ khiến popup lệch con số.
      const key = it.ownerUserId ?? 'NONE';
      const list = byUser.get(key);
      if (list) list.push(it); else byUser.set(key, [it]);
    }
    // Danh sách TOÀN DỰ ÁN của từng người — dựng lại ĐÚNG bộ lọc backend dùng cho cột "Tổng CV"
    // (bỏ Epic/Story và việc Huỷ; KHÔNG lọc lá; gom theo CHỦ HIỆN TẠI) để bấm vào số ra đúng chừng đó việc.
    const allByUser = new Map<string, ReportTaskItem[]>();
    for (const t of this.tasks()) {
      if (t.type === 'EPIC' || t.type === 'STORY' || t.status === 'CANCELLED') continue;
      const key = ownerOf(t).id ?? 'NONE';
      const list = allByUser.get(key);
      if (list) list.push(this.toItem(t)); else allByUser.set(key, [this.toItem(t)]);
    }
    // Đóng góp theo VAI trong kỳ — nhóm theo ĐÚNG vai: dev lấy theo assignee, tester lấy theo tester.
    const r = this.report();
    const devByUser = new Map<string, ReportTaskItem[]>();
    for (const it of r?.devHandoverItems ?? []) {
      const key = it.assigneeUserId ?? 'NONE';
      const list = devByUser.get(key);
      if (list) list.push(it); else devByUser.set(key, [it]);
    }
    const testByUser = new Map<string, ReportTaskItem[]>();
    for (const it of r?.testerDoneItems ?? []) {
      // Dữ liệu cũ chưa có tester → CHỈ bug/issue mới lùi về người log, khớp đúng
      // ProjectReportService.testerUserIdOf; lùi cho mọi loại sẽ lệch con số backend đã đếm.
      const fallback = (it.type === 'BUG' || it.type === 'ISSUE') ? it.reporterUserId : null;
      const key = it.testerUserId ?? fallback ?? 'NONE';
      const list = testByUser.get(key);
      if (list) list.push(it); else testByUser.set(key, [it]);
    }
    return overall.map((ov) => {
      const periodList = byUser.get(ov.userId ?? 'NONE') ?? [];
      return {
        userId: ov.userId, name: ov.name,
        totalAll: ov.total,               // tổng công việc (toàn dự án, bỏ Huỷ)
        pctAll: ov.pct,                   // % hoàn thành toàn dự án
        inPeriod: ov.inPeriod,            // việc CÓ THAY ĐỔI trong Ngày/Tuần
        doneInPeriod: ov.donePeriod,      // việc hoàn thành trong kỳ
        devHandover: ov.devHandover,      // vai DEV: bàn giao sang Kiểm thử trong kỳ
        testerDone: ov.testerDone,        // vai TESTER: chuyển Hoàn thành trong kỳ
        items: periodList,
        // donePeriod của backend = việc trong kỳ có trạng thái DONE → lọc lại y hệt.
        doneItems: periodList.filter((i) => i.status === 'DONE'),
        allItems: allByUser.get(ov.userId ?? 'NONE') ?? [],
        devItems: devByUser.get(ov.userId ?? 'NONE') ?? [],
        testItems: testByUser.get(ov.userId ?? 'NONE') ?? []
      };
    });
  });

  // ===== Bug/Issue theo nhân sự: tester đã LOG vs dev BỊ LOG (bug được TẠO trong kỳ Ngày/Tuần) =====
  readonly bugsInPeriod = computed<ReportTaskItem[]>(() => this.report()?.bugsLogged ?? []);

  /** Tester ĐÃ log bug (nhóm theo người tạo/report). */
  readonly bugByReporter = computed<BugPerson[]>(() => this.groupBugs('reporter'));
  /** Dev BỊ log bug (nhóm theo người thực hiện). */
  readonly bugByAssignee = computed<BugPerson[]>(() => this.groupBugs('assignee'));

  private groupBugs(kind: 'reporter' | 'assignee'): BugPerson[] {
    const map = new Map<string, BugPerson>();
    for (const b of this.bugsInPeriod()) {
      const id = kind === 'reporter' ? b.reporterUserId : b.assigneeUserId;
      const name = kind === 'reporter' ? b.reporterName : b.assigneeName;
      const key = id || name || '__none__';
      let p = map.get(key);
      if (!p) { p = { userId: id, name: name || '— Không rõ —', count: 0, items: [] }; map.set(key, p); }
      p.count++;
      p.items.push(b);
    }
    return [...map.values()].sort((a, b) => b.count - a.count);
  }

  /**
   * Task ĐÃ XỬ LÝ TRONG KỲ, dạng ProjectTask — nguồn cho bảng thống kê theo vai ở màn BÁO CÁO.
   * Tổng quan dùng toàn bộ task dự án; báo cáo phải giới hạn theo kỳ đang chọn, nếu không
   * hai màn ra số y hệt nhau và cái "báo cáo ngày/tuần" mất ý nghĩa.
   * Lấy đúng tập periodItems của backend (cùng điều kiện với bộ đếm "Xử lý trong kỳ").
   */
  readonly periodTasks = computed<ProjectTask[]>(() => {
    const ids = new Set((this.report()?.periodItems ?? []).map((i) => i.taskId));
    return ids.size ? this.tasks().filter((t) => ids.has(t.id)) : [];
  });

  /** Bấm ô số trong bảng theo vai → đổi ProjectTask sang dòng hiển thị rồi mở popup chung. */
  openRolePick(e: { title: string; items: ProjectTask[] }): void {
    this.openDetail(e.title, e.items.map((t) => this.toItem(t)));
  }

  openBugPerson(p: BugPerson, prefix: string): void {
    this.openDetail(prefix + p.name, p.items, true);
  }

  // ===== (3) 4 khối trạng thái =====
  /** Nhãn khối "sắp làm" theo kỳ: ngày = hạn 1–3 ngày tới; tuần = việc của tuần sau. */
  readonly upcomingTitle = computed<string>(() =>
    this.period() === 'weekly' ? 'Công việc tuần tiếp theo' : 'Sắp làm (đến hạn 1–3 ngày tới)');
  readonly upcomingEmpty = computed<string>(() =>
    this.period() === 'weekly' ? 'Không có công việc nào đến hạn trong tuần tiếp theo.'
      : 'Không có việc nào đến hạn trong 1–3 ngày tới.');
  /** Nhãn khối "cần làm": đã đến hạn trong kỳ nhưng vẫn Cần làm/Backlog. */
  readonly todoTitle = computed<string>(() =>
    this.period() === 'weekly' ? 'Cần làm (đến hạn trong tuần)' : 'Cần làm (đến hạn hôm nay)');

  readonly blocks = computed<ReportBlock[]>(() => {
    const r = this.report();
    return [
      { key: 'done', icon: '✅', title: 'Đã hoàn thành', rows: r?.done ?? [], emptyText: 'Chưa có công việc nào hoàn thành trong kỳ.' },
      { key: 'overdue', icon: '⛔', title: 'Trễ hạn', rows: r?.overdue ?? [], emptyText: 'Không có công việc trễ hạn. 🎉' },
      { key: 'todo', icon: '📌', title: this.todoTitle(), rows: r?.todo ?? [],
        emptyText: 'Không có việc nào đến hạn mà chưa khởi động.' },
      { key: 'inProgress', icon: '🔄', title: 'Đang làm', rows: r?.inProgress ?? [], emptyText: 'Không có công việc đang làm.' },
      { key: 'upcoming', icon: '📋', title: this.upcomingTitle(), rows: r?.upcoming ?? [], emptyText: this.upcomingEmpty() }
    ];
  });

  /** Cột cho lưới danh sách task (khối trạng thái). */
  readonly cols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '90px', sortable: true },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'assigneeName', header: 'Người làm', width: '180px' },
    { key: 'estimateHours', header: 'Est (h)', align: 'center', width: '90px', sortable: true },
    { key: 'dueDate', header: 'Hạn', align: 'center', width: '120px', sortable: true },
    { key: 'progressPct', header: '% hoàn thành', width: '170px', sortable: true }
  ];

  /**
   * Cột cho popup chi tiết theo nhân sự (thêm Loại + Trạng thái).
   * KHÔNG đặt width cho "Công việc" → cột này hưởng toàn bộ chỗ còn lại.
   * Trước đây 5 cột cố định chiếm 562px trong modal ~660px, chừa tiêu đề ~100px
   * nên mỗi dòng vỡ thành 5-6 dòng chữ. Modal cũng chuyển sang xwide cho rộng.
   */
  readonly personCols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '70px', sortable: true },
    { key: 'type', header: 'Loại', width: '78px' },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'assigneeName', header: 'Người làm', width: '150px' },
    { key: 'status', header: 'Trạng thái', width: '104px' },
    { key: 'dueDate', header: 'Hạn', align: 'center', width: '92px', sortable: true },
    { key: 'progressPct', header: '% HT', width: '110px', sortable: true }
  ];
  /**
   * Cột hiển thị: bỏ "Người làm" khi popup lọc theo người VÀ mọi dòng đều do đúng người đó thực hiện.
   * Việc ở Kiểm thử gom theo người kiểm thử/người log nên assignee là các dev khác nhau —
   * lúc đó phải GIỮ cột để còn biết ai đã làm việc đang chờ verify.
   */
  readonly detailColsShown = computed<GridColumn[]>(() => {
    const d = this.detailModal();
    if (!d?.byPerson) return this.personCols;
    const first = d.items[0]?.assigneeUserId ?? null;
    const sameAssignee = d.items.every((i) => (i.assigneeUserId ?? null) === first);
    return sameAssignee ? this.personCols.filter((c) => c.key !== 'assigneeName') : this.personCols;
  });

  constructor() {
    effect(() => {
      const pid = this.projectId();
      const p = this.period();
      const d = this.reportDate();
      if (!pid) return;
      this.loading.set(true);
      const src$ = p === 'weekly' ? this.svc.reportWeekly(pid, d) : this.svc.reportDaily(pid, d);
      src$.subscribe({
        next: (r) => { this.report.set(r); this.loading.set(false); },
        error: () => { this.report.set(null); this.loading.set(false); }
      });
    });
    // Danh sách task đầy đủ — chỉ phụ thuộc dự án, không tải lại khi đổi kỳ/ngày.
    effect(() => {
      const pid = this.projectId();
      if (pid) this.loadTasks(pid);
    });
  }

  private loadTasks(pid: string): void {
    this.svc.listTasks(pid).subscribe({
      next: (t) => { this.tasks.set(t ?? []); this.syncOpenPopups(); },
      error: () => this.tasks.set([])
    });
  }

  /** Sửa task trong popup chi tiết → tải lại báo cáo + danh sách task. */
  onTaskChanged(): void {
    const pid = this.projectId();
    if (!pid) return;
    this.loadTasks(pid);
    const src$ = this.period() === 'weekly'
      ? this.svc.reportWeekly(pid, this.reportDate())
      : this.svc.reportDaily(pid, this.reportDate());
    src$.subscribe({ next: (r) => this.report.set(r) });
  }

  /**
   * Sau khi tải lại task: đồng bộ dữ liệu MỚI vào popup đang mở, giữ nguyên tập việc đang xem
   * (việc vừa đổi trạng thái vẫn nằm lại cho dễ theo dõi).
   */
  private syncOpenPopups(): void {
    const byId = new Map(this.tasks().map((t) => [t.id, t]));
    const d = this.detailModal();
    if (d) {
      this.detailModal.set({ ...d, items: d.items.map((i) => {
        const fresh = byId.get(i.taskId);
        return fresh ? { ...this.toItem(fresh), parentPath: i.parentPath } : i;
      }) });
    }
    const cur = this.taskDetail();
    if (cur) {
      const fresh = byId.get(cur.id);
      if (fresh) this.taskDetail.set(fresh);
    }
  }

  setPeriod(p: Period): void { this.period.set(p); }

  /** Tải file báo cáo (xlsx/docx) cho kỳ + ngày đang chọn. */
  exportReport(format: 'xlsx' | 'docx'): void {
    const url = this.svc.reportExportUrl(this.projectId(), this.period(), format, this.reportDate());
    const a = document.createElement('a');
    a.href = url;
    a.rel = 'noopener';
    document.body.appendChild(a);
    a.click();
    a.remove();
  }
  clampPct(v: number): number { return Math.max(0, Math.min(100, Math.round(v ?? 0))); }
  personPct(p: PersonStat): number { return p.total ? Math.round((p.done / p.total) * 100) : 0; }
  catDonePct(c: TypeStatusRow): number { return c.total ? Math.round((c.byStatus['DONE'] / c.total) * 100) : 0; }

  openPerson(p: PersonStat): void { this.openDetail('Công việc của ' + p.name, p.items, true); }
  /** Mở chi tiết việc TRONG KỲ của 1 người (từ bảng thống kê nhân sự). */
  openPersonRow(p: { name: string; items: ReportTaskItem[] }): void {
    this.openDetail('Công việc trong kỳ của ' + p.name, p.items, true);
  }

  typeColor(t: TaskType): string { return TYPE_META[t]?.color ?? 'var(--color-primary)'; }

  statusBadge(s: TaskStatus): string {
    switch (s) {
      case 'BACKLOG': return 'badge--neutral';
      case 'TODO': return 'badge--pending';
      case 'IN_PROGRESS': return 'badge--active';
      case 'IN_REVIEW': return 'badge--active';
      case 'DONE': return 'badge--done';
      default: return 'badge--neutral';
    }
  }
  statusLabel(s: TaskStatus): string {
    switch (s) {
      case 'BACKLOG': return 'Backlog';
      case 'TODO': return 'Cần làm';
      case 'IN_PROGRESS': return 'Đang làm';
      case 'IN_REVIEW': return 'Kiểm thử';
      case 'DONE': return 'Hoàn thành';
      default: return s;
    }
  }
  typeLabel(t: TaskType): string { return TYPE_META[t]?.short ?? t; }

  // ============ TRANG IN (HTML → PDF giống biểu mẫu khách hàng) ============

  /** Phân bố theo trạng thái (cho donut + chú thích). */
  readonly statusDist = computed(() => {
    const items = this.allItems();
    const total = items.length || 1;
    return STATUS_META.map((s) => {
      const count = items.filter((i) => i.status === s.key).length;
      return { label: s.label, color: s.color, count, pct: Math.round((count / total) * 1000) / 10 };
    });
  });

  /** Chuỗi conic-gradient dựng donut từ statusDist. */
  readonly donutGradient = computed(() => {
    const d = this.statusDist();
    const total = d.reduce((s, x) => s + x.count, 0) || 1;
    let acc = 0;
    const stops: string[] = [];
    for (const x of d) {
      const from = (acc / total) * 360;
      acc += x.count;
      const to = (acc / total) * 360;
      if (x.count > 0) stops.push(`${x.color} ${from}deg ${to}deg`);
    }
    if (!stops.length) stops.push('var(--color-border) 0deg 360deg');
    return `conic-gradient(${stops.join(', ')})`;
  });

  /** Số việc quá hạn (từ danh sách overdue). */
  readonly overdueCount = computed(() => this.report()?.overdue.length ?? 0);

  /** Các dòng TỔNG QUAN cho trang in (icon · nhãn · giá trị). */
  readonly printOverview = computed(() => {
    const o = this.report()?.overview;
    if (!o) return [] as { icon: string; label: string; value: string; tone?: string }[];
    const est = o.totalEstimate || 0;
    const doneEst = o.doneEstimate || 0;
    const estPct = est ? Math.round((doneEst / est) * 1000) / 10 : 0;
    return [
      { icon: '🗂️', label: 'Tổng số công việc', value: String(o.totalTasks) },
      { icon: '✅', label: 'Đã hoàn thành', value: String(o.doneTasks), tone: 'done' },
      { icon: '🔄', label: 'Đang làm', value: String(this.report()?.inProgress.length ?? 0), tone: 'doing' },
      { icon: '⛔', label: 'Quá hạn', value: String(o.overdueCount), tone: 'over' },
      { icon: '🐞', label: 'Lỗi (bug)', value: String(o.bugCount) },
      { icon: '📊', label: 'Ước lượng (%)', value: `${estPct}% (${doneEst}/${est} h)` }
    ];
  });

  /** Nhân sự cho trang in (dùng số liệu backend: Tổng/Xong/Đang làm/Trễ/%). */
  readonly printPeople = computed(() => this.report()?.byPerson ?? []);

  /** EPIC/Story + Đã hoàn thành: cắt bớt cho gọn trang in, ghi "… còn N mục khác". */
  private readonly PRINT_CAP = 14;
  readonly printEpic = computed(() => (this.report()?.epicStory ?? []).slice(0, this.PRINT_CAP));
  readonly printEpicMore = computed(() => Math.max(0, (this.report()?.epicStory?.length ?? 0) - this.PRINT_CAP));
  readonly printDone = computed(() => (this.report()?.done ?? []).slice(0, this.PRINT_CAP));
  readonly printDoneMore = computed(() => Math.max(0, (this.report()?.done?.length ?? 0) - this.PRINT_CAP));
  readonly printDoing = computed(() => (this.report()?.inProgress ?? []).slice(0, this.PRINT_CAP));
  readonly printDoingMore = computed(() => Math.max(0, (this.report()?.inProgress?.length ?? 0) - this.PRINT_CAP));
  readonly printUpcoming = computed(() => (this.report()?.upcoming ?? []).slice(0, this.PRINT_CAP));
  readonly printUpcomingMore = computed(() => Math.max(0, (this.report()?.upcoming?.length ?? 0) - this.PRINT_CAP));

  /** Tiêu đề + ngày cho header trang in. */
  readonly printTitle = computed(() =>
    (this.period() === 'weekly' ? 'BÁO CÁO TUẦN' : 'BÁO CÁO NGÀY') +
    (this.report()?.periodLabel ? ' — ' + this.report()!.periodLabel : ''));
  readonly printDateLabel = computed(() => {
    const iso = this.reportDate();
    const [y, m, d] = iso.split('-').map(Number);
    if (!y) return '';
    const dt = new Date(y, m - 1, d);
    const wd = ['Chủ nhật', 'Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7'][dt.getDay()];
    const p = (n: number) => String(n).padStart(2, '0');
    return `${p(d)}/${p(m)}/${y} · ${wd}`;
  });

  /** Mở trang in: bật lớp phủ → đổi tiêu đề tài liệu (tránh tiêu đề trang lọt vào header PDF) → in → khôi phục. */
  openPrint(): void {
    this.printMode.set(true);
    const prevTitle = document.title;
    const clean = (this.period() === 'weekly' ? 'Bao cao tuan' : 'Bao cao ngay')
      + (this.report()?.periodLabel ? ' - ' + this.report()!.periodLabel : '');
    setTimeout(() => {
      document.title = clean;
      const done = () => {
        this.printMode.set(false);
        document.title = prevTitle;
        window.removeEventListener('afterprint', done);
      };
      window.addEventListener('afterprint', done);
      window.print();
    }, 120);
  }
  closePrint(): void { this.printMode.set(false); }

  /** Màu chấm trạng thái cho trang in. */
  statusColor(s: TaskStatus): string {
    return STATUS_META.find((x) => x.key === s)?.color ?? '#94a3b8';
  }
  /** Cấp lồng Epic→Story (thụt lề trang in): từ parentPath "Epic: … › Story: …". */
  epicLevel(t: ReportTaskItem): number { return t.parentPath ? t.parentPath.split('›').length : 0; }

  /** Chữ cái đầu (avatar PIC trang in). */
  initials(name: string | null): string {
    if (!name) return '—';
    const parts = name.trim().split(/\s+/);
    const last = parts[parts.length - 1] ?? '';
    const first = parts.length > 1 ? parts[0] : '';
    return ((last[0] ?? '') + (first[0] ?? '')).toUpperCase() || name[0].toUpperCase();
  }
}
