import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { StatCard } from '../../shared/stat-card/stat-card';
import { EmployeeChip } from '../../shared/employee-chip/employee-chip';
import { Modal } from '../../shared/modal/modal';
import { PrjTaskDetail } from '../task-detail/task-detail';
import { RoleStats } from '../role-stats/role-stats';
import { DataGrid, GridColumn } from '../../shared/data-grid/data-grid';
import { GridCellDirective } from '../../shared/data-grid/grid-cell.directive';
import { formatThousands } from '../../shared/format';
import { categoryStats, CatStat, catOf, effectiveHours, isOverdue, ownerOf, STATUS_META, TYPE_META, WORK_CATS, WorkCat } from '../work-stats';
import {
  ProjectService, Project, ProjectReport, ProjectStatus, TaskStatus, TaskType, ProjectTask,
  ProjectMember, ProjectActivityItem, WorkLog
} from '../../core/project.service';

interface StatusBar { status: TaskStatus; label: string; color: string; count: number; pct: number; }
interface TypeStat { type: TaskType; label: string; badge: string; count: number; }

/** Nhóm công việc theo trạng thái — giữ luôn DANH SÁCH để bấm số là mở popup chi tiết. */
type StatusBuckets = Record<TaskStatus, ProjectTask[]>;
/** Nhóm công việc theo loại (Công việc / Bug / Issue). */
type TypeBuckets = Record<WorkCat, ProjectTask[]>;

/** Một dòng "Thống kê theo loại" kèm danh sách công việc của từng ô số. */
interface CatRow extends CatStat {
  items: ProjectTask[];
  byStatus: StatusBuckets;
  overdueItems: ProjectTask[];
  /** Việc CÒN LẠI phải xử lý = tổng trừ Hoàn thành và Huỷ (Huỷ nằm ngoài phạm vi, không phải việc tồn). */
  openItems: ProjectTask[];
}

/** Một dòng bảng tổng hợp theo NHÂN SỰ (loại × trạng thái), mọi ô đều mở được popup. */
interface PersonRow {
  key: string;
  userId: string | null;
  name: string;
  unassigned: boolean;
  items: ProjectTask[];
  byType: TypeBuckets;
  byStatus: StatusBuckets;
  overdueItems: ProjectTask[];
  total: number;
  done: number;
  donePct: number;
  estHours: number;    // Σ giờ hiệu lực của việc đang giữ
  actualHours: number; // Σ giờ THỰC TẾ người này đã ghi (mọi vai)
}

/** Một dòng tiến độ EPIC / Story kèm số việc con xong / chưa xong. */
interface EpicStoryRow {
  id: string;
  code: string;
  title: string;
  type: TaskType;
  pct: number;
  level: number;
  items: ProjectTask[];
  doneItems: ProjectTask[];
  openItems: ProjectTask[];
}

/** Buckets rỗng cho 6 trạng thái. */
function emptyStatusBuckets(): StatusBuckets {
  return { BACKLOG: [], TODO: [], IN_PROGRESS: [], IN_REVIEW: [], DONE: [], CANCELLED: [] };
}
/** Buckets rỗng cho 3 loại công việc. */
function emptyTypeBuckets(): TypeBuckets {
  return { TASK: [], BUG: [], ISSUE: [] };
}

/**
 * Tab "Tổng quan" HỢP NHẤT (selector app-prj-overview).
 * Gộp tóm tắt dự án (get) + báo cáo (report: tiến độ, est/spent, bug, overdue,
 * byStatus/byType/byAssignee) + thống kê Task/Bug/Issue. Bố cục nhóm:
 * "Tiến độ" · "Phân bổ" · "Theo người".
 */
@Component({
  selector: 'app-prj-overview',
  standalone: true,
  imports: [StatCard, EmployeeChip, Modal, DataGrid, GridCellDirective, PrjTaskDetail, RoleStats],
  templateUrl: './overview.html',
  styles: [`
    .ov2 { display: grid; gap: var(--space-4); }

    /* Header */
    .ov2__head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between;
      gap: var(--space-3); padding: var(--space-4) var(--space-5); border: 1px solid var(--color-border);
      border-radius: var(--radius-lg); background: var(--color-surface); }
    .ov2__title { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; margin: 0 0 var(--space-2); font-size: 1.35rem; }
    .ov2__code { font-size: .8rem; font-weight: 600; color: var(--color-text-muted); background: var(--color-surface-alt);
      padding: 2px 8px; border-radius: var(--radius-sm); }
    .ov2__meta { display: flex; flex-wrap: wrap; gap: var(--space-4); color: var(--color-text-muted); font-size: var(--text-sm); }
    .ov2__meta b { color: var(--color-text); font-weight: 600; }

    /* Panel trên: tiến độ + lưới thẻ */
    .ov2__top { display: grid; gap: var(--space-3); grid-template-columns: minmax(240px, 300px) 1fr; align-items: stretch; }
    @media (max-width: 720px) { .ov2__top { grid-template-columns: 1fr; } }
    .ov2__progress { padding: var(--space-5); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); display: flex; flex-direction: column; gap: var(--space-2); justify-content: center; }
    .ov2__progress-label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: 600; }
    .ov2__progress-pct { font-size: 2.4rem; font-weight: 800; color: var(--color-primary); line-height: 1; }
    .ov2__progress-sub { font-size: var(--text-xs); color: var(--color-text-muted); }
    .ov2__bar { height: 10px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; margin-top: var(--space-1); }
    .ov2__bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg, var(--status-active), var(--status-done)); transition: width .3s; }
    .ov2__stats { display: grid; gap: var(--space-3); grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); }
    /* Thẻ Hoàn thành có thanh tiến trình + số done/tổng */
    .ov2__prog-card { padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; gap: 6px; }
    .ov2__prog-label { font-size: var(--text-sm); color: var(--color-text-muted); font-weight: 600; }
    .ov2__prog-pct { font-size: 2rem; font-weight: 800; line-height: 1; color: var(--color-primary); }
    .ov2__prog-pct--alt { color: var(--color-info, var(--status-active)); }
    .ov2__prog-bar { height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .ov2__prog-fill { height: 100%; border-radius: 999px; background: var(--color-primary); }
    .ov2__prog-fill--alt { background: var(--color-info, var(--status-active)); }
    .ov2__prog-pct--effort { color: var(--status-pending); }
    .ov2__prog-pct--effort.is-over { color: var(--overdue, #e5484d); }
    .ov2__prog-fill--effort { background: var(--status-pending); }
    .ov2__prog-fill--effort.is-over { background: var(--overdue, #e5484d); }
    .ov2__prog-warn { font-size: var(--text-xs); color: var(--overdue, #e5484d); font-weight: 600; }
    .ov2__prog-sub { font-size: var(--text-xs); color: var(--color-text-muted); }

    .ov2__h { margin: var(--space-2) 0 0; font-size: 1rem; font-weight: var(--weight-semibold); }

    /* Thẻ theo loại (Công việc / Bug / Issue) */
    .ov2__cats { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); }
    .ov2__cat { padding: var(--space-4); border: 1px solid var(--color-border); border-radius: var(--radius-lg);
      background: var(--color-surface); box-shadow: var(--shadow-sm); display: flex; flex-direction: column; gap: var(--space-3); }
    .ov2__cat-head { display: flex; align-items: center; gap: var(--space-2); }
    .ov2__cat-ico { font-size: 1.1rem; }
    .ov2__cat-name { font-weight: var(--weight-semibold); }
    .ov2__cat-total { margin-left: auto; font-size: 1.8rem; font-weight: 800; line-height: 1; color: var(--cat, var(--color-primary)); font-variant-numeric: tabular-nums; }
    .ov2__cat-bar { height: 7px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .ov2__cat-fill { height: 100%; border-radius: 999px; background: var(--cat, var(--status-done)); }
    .ov2__cat-sub { font-size: var(--text-xs); color: var(--color-text-muted); }
    .ov2__cat-list { list-style: none; margin: 0; padding: 0; display: grid; gap: 2px; }
    .ov2__cat-list li { display: block; }
    .ov2__cat-list b { font-variant-numeric: tabular-nums; }
    .ov2__cat-link { align-self: flex-start; margin-top: 2px; border: 0; background: none; color: var(--color-primary);
      cursor: pointer; font: inherit; font-size: var(--text-sm); font-weight: 600; padding: 0; }
    .ov2__cat-link:hover { text-decoration: underline; }

    /* Panel chung + thông tin dự án */
    .ov2__panel { padding: var(--space-5); border: 1px solid var(--color-border); border-radius: var(--radius-lg); background: var(--color-surface); }
    .ov2__panel-h { margin: 0 0 var(--space-4); font-size: 1rem; font-weight: var(--weight-semibold); }
    .ov2__info { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); }
    .ov2__info-k { font-size: var(--text-xs); color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .03em; }
    .ov2__info-v { font-weight: 600; margin-top: 2px; }

    /* 2 cột đáy */
    .ov2__two { display: grid; gap: var(--space-4); grid-template-columns: 1fr 1fr; }
    @media (max-width: 860px) { .ov2__two { grid-template-columns: 1fr; } }

    .ov2__act { display: flex; flex-direction: column; }
    .ov2__act-row { display: flex; gap: var(--space-2); padding: var(--space-2) 0; border-bottom: 1px solid var(--color-border); font-size: var(--text-sm); }
    .ov2__act-row:last-child { border-bottom: 0; }
    .ov2__act-ico { flex: 0 0 auto; }
    .ov2__act-body { flex: 1; min-width: 0; }
    .ov2__act-main { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ov2__act-main b { font-weight: 600; }
    .ov2__act-time { color: var(--color-text-muted); font-size: var(--text-xs); }

    /* Bug/Issue theo nhân sự (thay panel Thành viên) */
    .ov2__bugcols { display: grid; gap: var(--space-4); grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); }
    .ov2__bugcol-title { margin: 0 0 var(--space-2); font-size: var(--text-sm); font-weight: var(--weight-semibold); }
    .ov2__bugrow { display: flex; align-items: center; justify-content: space-between; gap: var(--space-2);
      padding: 5px 10px; border-radius: var(--radius-md); background: var(--color-surface-alt); margin-bottom: 3px; font-size: var(--text-sm); }
    .ov2__bugrank { color: var(--color-text-muted); font-size: var(--text-xs); margin-right: 2px; }
    .ov2__bugcount { font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; min-width: 26px; text-align: center;
      background: color-mix(in srgb, var(--overdue, #e5484d) 15%, transparent); color: var(--overdue, #e5484d);
      padding: 0 8px; border-radius: 999px; font-size: var(--text-xs); }

    .ov2__mem { display: flex; flex-direction: column; }
    .ov2__mem-row { display: flex; align-items: center; gap: var(--space-2); padding: var(--space-2) 0; border-bottom: 1px solid var(--color-border); }
    .ov2__mem-row:last-child { border-bottom: 0; }
    .ov2__mem-role { margin-left: auto; }
    .ov2__mem-md { color: var(--color-text-muted); font-size: var(--text-xs); min-width: 56px; text-align: right; }

    .ov2__empty { color: var(--color-text-muted); font-style: italic; font-size: var(--text-sm); padding: var(--space-2) 0; }
    .ov2__loading { padding: var(--space-6); text-align: center; color: var(--color-text-muted); }
    .ov2__actions { display: flex; gap: var(--space-2); align-items: flex-start; }

    /* Tiến độ EPIC / Story */
    .ov2__es-row { display: flex; align-items: center; gap: var(--space-3); padding: 6px 0; border-bottom: 1px solid var(--color-border); }
    .ov2__es-row:last-child { border-bottom: 0; }
    .ov2__es-row--head { color: var(--color-text-muted); font-size: var(--text-xs); font-weight: var(--weight-semibold);
      text-transform: uppercase; letter-spacing: .02em; }
    .ov2__es-title { flex: 1; min-width: 0; display: flex; align-items: center; gap: var(--space-2); }
    .ov2__es-name { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ov2__es-bar { width: 160px; flex: 0 0 auto; height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .ov2__es-fill { height: 100%; border-radius: 999px; background: var(--status-done); }
    .ov2__es-pct { flex: 0 0 auto; min-width: 44px; text-align: right; font-variant-numeric: tabular-nums; }
    .ov2__es-nums { flex: 0 0 auto; display: flex; align-items: center; }
    .ov2__es-nums > * { flex: 0 0 122px; text-align: center; white-space: nowrap; }
    /* Pill LOẠI (Epic/Story) — màu theo loại, dùng chung với popup chi tiết */
    .ov2__tag { flex: 0 0 auto; font-size: var(--text-xs); font-weight: 700; padding: 1px 8px; border-radius: 999px;
      white-space: nowrap; color: var(--tb-color, var(--color-primary));
      background: color-mix(in srgb, var(--tb-color, var(--color-primary)) 14%, transparent); }
    .ov2__es-bar-head { flex: 0 0 auto; width: 216px; text-align: right; }
    .ov2__hint { font-size: var(--text-xs); font-weight: var(--weight-regular, 400); color: var(--color-text-muted); }
    .ov2__note { margin: calc(var(--space-2) * -1) 0 0; font-size: var(--text-xs); color: var(--color-text-muted); line-height: 1.5; }
    .ov2__note b { color: var(--color-text); font-weight: 600; }

    /* ===== Ô SỐ bấm được → popup danh sách công việc ===== */
    .ov2__num { border: 0; background: none; padding: 1px 6px; border-radius: var(--radius-sm); cursor: pointer;
      font: inherit; font-weight: var(--weight-semibold); font-variant-numeric: tabular-nums; color: var(--color-primary); }
    .ov2__num:hover { background: var(--color-primary-soft, var(--color-surface-alt)); text-decoration: underline; }
    .ov2__num--done { color: var(--status-done); }
    .ov2__num--open { color: var(--status-pending); }
    .ov2__num--overdue { color: var(--overdue, #e5484d); }
    .ov2__zero { padding: 1px 6px; color: var(--color-text-muted); opacity: .5; font-variant-numeric: tabular-nums; }

    /* ===== Bảng tổng hợp theo nhân sự (nhân sự × loại × trạng thái) ===== */
    .ov2__mx { overflow-x: auto; }
    .ov2__mx-inner { display: grid; gap: 2px; min-width: 1180px; }
    .ov2__mrow { display: grid;
      grid-template-columns: minmax(180px, 1.8fr) repeat(9, minmax(52px, .8fr)) minmax(52px, .7fr) repeat(3, minmax(62px, .8fr)) minmax(104px, 1fr);
      align-items: center; gap: var(--space-1); padding: 5px var(--space-3); border-radius: var(--radius-md); font-size: var(--text-sm); }
    .ov2__mrow > span:not(.ov2__mname) { text-align: center; }
    .ov2__mrow--body { background: var(--color-surface-alt); }
    .ov2__mrow--head { color: var(--color-text-muted); font-size: var(--text-xs);
      font-weight: var(--weight-semibold); text-transform: uppercase; letter-spacing: .02em; }
    .ov2__mrow--group { padding-bottom: 0; }
    .ov2__mrow--group span { border-radius: var(--radius-sm); }
    .ov2__mrow--group .ov2__mgrp { background: var(--color-surface-alt); padding: 2px 0; }
    .ov2__mrow--sum { background: var(--color-primary-soft, var(--color-surface-alt)); font-weight: var(--weight-semibold); }
    .ov2__mname { display: inline-flex; align-items: center; gap: var(--space-2); min-width: 0;
      font-weight: var(--weight-medium); overflow: hidden; }
    .ov2__mname span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ov2__msep { border-left: 1px solid var(--color-border); }
    .ov2__var--over { color: var(--overdue, #e5484d); font-weight: var(--weight-semibold); }
    .ov2__var--under { color: var(--status-done); font-weight: var(--weight-semibold); }
    .ov2__mpct { display: flex; align-items: center; gap: var(--space-2); justify-content: center; }
    .ov2__mpct-bar { flex: 1; max-width: 70px; height: 6px; border-radius: 999px; background: var(--color-border); overflow: hidden; }
    .ov2__mpct-fill { display: block; height: 100%; border-radius: 999px; background: var(--status-done); }
    .ov2__mpct-val { min-width: 34px; text-align: right; font-size: var(--text-xs); color: var(--color-text-muted); }

    /* Danh sách trạng thái trong thẻ theo loại — cả dòng bấm được */
    .ov2__cat-btn { display: flex; align-items: center; justify-content: space-between; width: 100%; gap: var(--space-2);
      padding: 5px 10px; border: 0; border-radius: var(--radius-md); background: var(--color-surface-alt);
      font: inherit; font-size: var(--text-sm); color: inherit; text-align: left; cursor: pointer; }
    .ov2__cat-btn:hover:not(:disabled) { background: var(--color-primary-soft, var(--color-border)); }
    .ov2__cat-btn:disabled { cursor: default; opacity: .65; }
    .ov2__cat-btn b { font-variant-numeric: tabular-nums; }
    /* "Đang mở" là con số tổng hợp của cả thẻ (không phải một trạng thái) → tách khỏi nhóm bên dưới. */
    .ov2__cat-btn--open { font-weight: var(--weight-semibold); margin-bottom: var(--space-1);
      border-bottom: 1px solid var(--color-border); border-radius: var(--radius-md) var(--radius-md) 0 0; }
    .ov2__cat-btn--open b { color: var(--cat, var(--color-primary)); }
    .ov2__cat-total-btn { margin-left: auto; border: 0; background: none; padding: 0; cursor: pointer;
      font: inherit; font-size: 1.8rem; font-weight: 800; line-height: 1; color: var(--cat, var(--color-primary));
      font-variant-numeric: tabular-nums; }
    .ov2__cat-total-btn:hover { text-decoration: underline; }

    /* Bộ lọc LOẠI công việc trong popup */
    .ov2__d-filters { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
    .ov2__d-filters-lbl { font-size: var(--text-xs); color: var(--color-text-muted); margin-right: 2px; }
    .ov2__d-chip { border: 1px solid var(--color-border); background: var(--color-surface); cursor: pointer;
      font: inherit; font-size: var(--text-xs); font-weight: 600; color: var(--color-text-muted);
      padding: 3px 9px; border-radius: 999px; white-space: nowrap; }
    .ov2__d-chip b { font-variant-numeric: tabular-nums; opacity: .75; }
    .ov2__d-chip:hover { border-color: var(--tb-color, var(--color-primary)); color: var(--tb-color, var(--color-primary)); }
    .ov2__d-chip.is-active { border-color: var(--tb-color, var(--color-primary)); color: var(--tb-color, var(--color-primary));
      background: color-mix(in srgb, var(--tb-color, var(--color-primary)) 12%, transparent); }

    /* Ô chi tiết trong popup */
    .ov2__d-type { font-size: var(--text-xs); font-weight: 700; padding: 1px 7px; border-radius: 999px; white-space: nowrap;
      color: var(--tb-color, var(--color-primary)); background: color-mix(in srgb, var(--tb-color, var(--color-primary)) 14%, transparent); }
    .ov2__d-code { border: 0; cursor: pointer; font: inherit; font-size: var(--text-xs); font-weight: 700; }
    .ov2__d-code:hover { text-decoration: underline; }
    /* Tiêu đề công việc — bấm để mở chi tiết */
    .ov2__d-open { display: block; width: 100%; border: 0; background: none; padding: 0; cursor: pointer;
      font: inherit; color: inherit; text-align: left; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ov2__d-open:hover { color: var(--color-primary); text-decoration: underline; }
    .ov2__d-parent { font-size: var(--text-xs); color: var(--color-text-muted); overflow: hidden;
      text-overflow: ellipsis; white-space: nowrap; }
    .ov2__d-pct { display: flex; align-items: center; gap: var(--space-2); }
    .ov2__d-bar { flex: 1; min-width: 48px; height: 8px; border-radius: 999px; background: var(--color-surface-alt); overflow: hidden; }
    .ov2__d-fill { height: 100%; border-radius: 999px; background: var(--status-done); }
    .ov2__d-val { font-size: var(--text-xs); color: var(--color-text-muted); min-width: 32px; text-align: right; }

    /* ===== TRANG IN / PDF Tổng quan (báo cáo khách) ===== */
    .rp-overlay { position: fixed; inset: 0; z-index: 1000; overflow: auto; background: #5b6472;
      padding: 20px 12px 40px; display: flex; flex-direction: column; align-items: center; }
    .ovp-bar { position: sticky; top: 0; z-index: 2; width: 210mm; max-width: 100%; display: flex; align-items: center; gap: 10px;
      padding: 8px 12px; margin-bottom: 14px; background: #1f2937; color: #e5e7eb; border-radius: 8px; font-size: 12px; }
    .rp-page { width: 210mm; max-width: 100%; background: #fff; box-shadow: 0 6px 30px rgba(0,0,0,.35); padding: 12mm 12mm 14mm; }
    .ovp { color: #1f2937; font-family: system-ui, "Segoe UI", Roboto, sans-serif; font-size: 12px; }
    .ovp__head { background: #1e3a5f; color: #fff; border-radius: 8px; padding: 12px 16px; text-align: center; margin-bottom: 14px; }
    /* 3 thẻ số liệu màu — đồng bộ mẫu PDF báo cáo ngày/tuần */
    .ovp__cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 14px; }
    .ovp__card { border-radius: 10px; padding: 10px 12px; text-align: center; border: 1.5px solid;
      -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .ovp__card-lbl { font-size: 11px; font-weight: 800; padding: 4px 0; border-radius: 6px; color: #fff; margin: -10px -12px 8px; }
    .ovp__card-num { font-size: 30px; font-weight: 800; line-height: 1; }
    .ovp__card--done { border-color: #2ea05a; background: #eef8f1; color: #1e7e42; }
    .ovp__card--done .ovp__card-lbl { background: #2ea05a; }
    .ovp__card--doing { border-color: #1e50a0; background: #eef2fb; color: #1e50a0; }
    .ovp__card--doing .ovp__card-lbl { background: #1e50a0; }
    .ovp__card--total { border-color: #1e3a5f; background: #eef1f6; color: #1e3a5f; }
    .ovp__card--total .ovp__card-lbl { background: #1e3a5f; }
    .ovp__title { font-size: 18px; font-weight: 800; letter-spacing: .3px; }
    .ovp__proj { margin-top: 4px; font-size: 12px; opacity: .92; }
    .ovp__progress { display: flex; align-items: center; gap: 16px; border: 1px solid #e5e7eb; border-radius: 8px; padding: 12px 16px; margin-bottom: 14px; }
    .ovp__pct { font-size: 30px; font-weight: 800; color: #1e50a0; line-height: 1; }
    .ovp__psub { font-size: 11px; color: #64748b; margin-top: 4px; }
    .ovp__bar { flex: 1; height: 12px; border-radius: 999px; background: #edf0f4; overflow: hidden; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .ovp__bar-fill { height: 100%; border-radius: 999px; background: linear-gradient(90deg,#2563eb,#22c55e); }
    .ovp__h { background: #1e3a5f; color: #fff; font-size: 12px; font-weight: 800; padding: 5px 10px; border-radius: 6px; margin: 14px 0 8px;
      -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    .ovp__info { width: 100%; border-collapse: collapse; }
    .ovp__info th { background: #f1f5f9; text-align: left; font-weight: 700; color: #475569; padding: 6px 10px; border: 1px solid #e2e8f0; width: 18%; white-space: nowrap; }
    .ovp__info td { padding: 6px 10px; border: 1px solid #e2e8f0; font-weight: 600; }
    .ovp__stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
    .ovp__stat { border: 1px solid #e5e7eb; border-radius: 8px; padding: 8px 12px; display: flex; align-items: baseline; justify-content: space-between; }
    .ovp__stat-l { color: #64748b; font-size: 11px; }
    .ovp__stat b { font-size: 18px; font-weight: 800; }
    .ovp__cat { width: 100%; border-collapse: collapse; }
    .ovp__cat th { background: #eef2f7; color: #475569; font-size: 10px; text-transform: uppercase; font-weight: 800; padding: 6px; border: 1px solid #e2e8f0; text-align: center; }
    .ovp__cat td { padding: 6px; border: 1px solid #eef0f2; text-align: center; }
    .ovp__cat .l { text-align: left; }
    .ovp__cat th:last-child, .ovp__cat td:last-child { min-width: 96px; }
    .ovp__es td.l { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 0; }
    .ovp__es-tag { display: inline-block; font-size: 8px; font-weight: 800; padding: 1px 5px; border-radius: 4px;
      margin-right: 6px; background: #e3ecf9; color: #1e50a0; }
    .ovp__es-tag--epic { background: #efe6fb; color: #6b3fb0; }
    .ovp__pbar { position: relative; height: 14px; border-radius: 999px; background: #edf0f4; overflow: hidden; }
    .ovp__pbar-fill { position: absolute; left: 0; top: 0; height: 100%; border-radius: 999px; background: #3fbf6a; }
    .ovp__pbar span { position: relative; z-index: 1; font-size: 9px; font-weight: 700; color: #14532d; line-height: 14px; padding-right: 6px; display: block; text-align: right; }
    .ovp__foot { margin-top: 16px; text-align: center; font-size: 10px; color: #94a3b8; }
  `]
})
export class PrjOverview {
  private svc = inject(ProjectService);

  readonly projectId = input.required<string>();
  /** Tăng khi task được sửa ở popup chi tiết → tải lại DỮ LIỆU mà không dựng lại component,
   *  nhờ vậy bộ lọc / nhóm đang gập / trang / vị trí cuộn giữ nguyên như trước khi mở popup. */
  readonly refreshKey = input(0);
  /** Chuyển sang tab khác (link "Xem chi tiết →"). */
  readonly openTab = output<string>();

  readonly project = signal<Project | null>(null);
  readonly report = signal<ProjectReport | null>(null);
  readonly tasks = signal<ProjectTask[]>([]);
  readonly members = signal<ProjectMember[]>([]);
  readonly activity = signal<ProjectActivityItem[]>([]);
  readonly loading = signal(true);
  /** Giờ THỰC TẾ toàn dự án — để đối chiếu est vs thực tế theo người. */
  readonly workLogs = signal<WorkLog[]>([]);
  /** userId → tổng giờ đã ghi (mọi vai). */
  readonly actualByUser = computed(() => {
    const m = new Map<string, number>();
    for (const w of this.workLogs()) {
      m.set(w.userId, (m.get(w.userId) ?? 0) + (w.hours || 0));
    }
    return m;
  });
  /** Tổng giờ thực tế của dự án + độ lệch so với est. */
  readonly actualTotal = computed(() =>
    Math.round(this.workLogs().reduce((a, w) => a + (w.hours || 0), 0) * 10) / 10);
  /**
   * % CÔNG SỨC ĐÃ TIÊU = giờ thực tế đã ghi / tổng est dự án.
   * Cố tình TÁCH khỏi "% hoàn thành": % hoàn thành đo KHỐI LƯỢNG đã bàn giao (trọng số est),
   * chỉ số này đo CÔNG SỨC đã bỏ ra. Gộp hai thứ vào một số thì làm việc kém hiệu quả lại
   * làm % đẹp lên — task est 4h tốn 8h sẽ đẩy tiến độ tăng dù chẳng giao thêm gì.
   * Đọc cặp: công sức 80% mà khối lượng mới 50% là đang ăn vào dự toán.
   */
  readonly effortPct = computed(() => {
    const est = this.report()?.totalEstimate ?? 0;
    return est > 0 ? Math.round((this.actualTotal() / est) * 100) : 0;
  });
  /** Tiêu công sức nhanh hơn tốc độ bàn giao ít nhất 10 điểm → cảnh báo. */
  readonly effortOverrun = computed(() => this.effortPct() - this.pct() >= 10);

  readonly estVariancePct = computed(() => {
    const est = this.report()?.totalEstimate ?? 0;
    return est > 0 ? Math.round(((this.actualTotal() - est) / est) * 100) : 0;
  });

  /** Xuất báo cáo Tổng quan cho khách (Excel + PDF) — KHÔNG có người thực hiện & trễ hạn. */
  readonly exporting = signal(false);
  readonly printMode = signal(false);
  exportExcel(): void {
    if (this.exporting()) return;
    this.exporting.set(true);
    this.svc.exportOverview(this.projectId()).subscribe({
      next: (b) => { ProjectService.downloadBlob(b, 'tong-quan-' + (this.project()?.code || 'du-an') + '.xlsx'); this.exporting.set(false); },
      error: () => { this.exporting.set(false); }
    });
  }
  openPrint(): void { this.printMode.set(true); setTimeout(() => this.printNow(), 150); }
  printNow(): void {
    const prev = document.title;
    document.title = 'Tong quan - ' + (this.project()?.code || 'du an');
    const done = () => { document.title = prev; window.removeEventListener('afterprint', done); };
    window.addEventListener('afterprint', done);
    window.print();
  }
  /** "Chưa làm" = Cần làm + Backlog (gộp cho báo cáo khách). */
  notStarted(c: CatStat): number { return c.todo + c.backlog; }

  /** Hoạt động gần đây (8 mục mới nhất). */
  readonly recentActivity = computed(() => this.activity().slice(0, 8));
  /** Khách hàng suy từ tiền tố CHỮ của mã dự án (VCB26118 → VCB); '—' nếu không có. */
  readonly customer = computed(() => {
    const code = this.project()?.code ?? '';
    const m = code.match(/^[A-Za-z]+/);
    return m ? m[0].toUpperCase() : '—';
  });

  // ===================== SỐ LIỆU BẤM ĐƯỢC (popup chi tiết) =====================

  /** Cột trạng thái / loại dùng chung cho các bảng tổng hợp. */
  readonly statusMetaAll = STATUS_META;
  readonly workCats = WORK_CATS;
  /** Thứ tự hiển thị trong thẻ theo loại: xong trước, huỷ cuối. */
  readonly catListMeta = (['DONE', 'IN_PROGRESS', 'IN_REVIEW', 'TODO', 'BACKLOG', 'CANCELLED'] as TaskStatus[])
    .map((k) => STATUS_META.find((m) => m.key === k)!);

  /** Công việc THỰC (Task/Sub-task/Bug/Issue) — nền cho mọi bảng thống kê; bỏ Epic/Story (cấp nhóm). */
  readonly workItems = computed(() => this.tasks().filter((t) => catOf(t.type) !== null));

  /**
   * Popup danh sách công việc chi tiết — mở khi bấm vào một ô số.
   * `byPerson` = popup đã lọc sẵn theo 1 nhân sự → ẩn cột "Người làm" cho đỡ thừa.
   */
  readonly detailModal = signal<{ title: string; items: ProjectTask[]; byPerson: boolean } | null>(null);
  /** Loại công việc đang lọc trong popup ('ALL' = tất cả). */
  readonly detailType = signal<TaskType | 'ALL'>('ALL');

  /** Mở popup; bỏ qua nếu ô số bằng 0 (không có gì để xem). */
  openDetail(title: string, items: ProjectTask[], byPerson = false): void {
    if (!items.length) return;
    this.detailType.set('ALL'); // mỗi lần mở là lọc lại từ đầu
    this.detailModal.set({ title: `${title} — ${items.length} việc`, items, byPerson });
  }
  closeDetail(): void { this.detailModal.set(null); this.detailType.set('ALL'); }

  /** Chip lọc theo loại trong popup: chỉ liệt kê những loại THỰC SỰ có trong danh sách, kèm số đếm. */
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
  readonly detailRows = computed<ProjectTask[]>(() => {
    const items = this.detailModal()?.items ?? [];
    const t = this.detailType();
    return t === 'ALL' ? items : items.filter((i) => i.type === t);
  });

  /**
   * Cột hiển thị: bỏ "Người làm" khi popup lọc theo người VÀ mọi dòng đều do đúng người đó thực hiện.
   * Ô "Kiểm thử" gom theo người kiểm thử/người log nên assignee là các dev khác nhau —
   * lúc đó phải GIỮ cột để còn biết ai đã làm việc đang chờ verify.
   */
  readonly detailColsShown = computed<GridColumn[]>(() => {
    const d = this.detailModal();
    if (!d?.byPerson) return this.detailCols;
    const first = d.items[0]?.assigneeUserId ?? null;
    const sameAssignee = d.items.every((i) => (i.assigneeUserId ?? null) === first);
    return sameAssignee ? this.detailCols.filter((c) => c.key !== 'assigneeName') : this.detailCols;
  });

  // ----- Chi tiết công việc (mở chồng lên popup danh sách) -----
  readonly taskDetail = signal<ProjectTask | null>(null);
  readonly taskDetailOpen = signal(false);
  /** Bấm 1 dòng trong popup danh sách → mở chi tiết task/bug kiểu Jira. */
  openTask(t: ProjectTask): void {
    this.taskDetail.set(t);
    this.taskDetailOpen.set(true);
  }
  closeTask(): void {
    this.taskDetailOpen.set(false);
    this.taskDetail.set(null);
  }
  /** Sửa task trong popup chi tiết → tải lại số liệu tổng quan. */
  onTaskChanged(): void { this.load(this.projectId()); }

  /** Thống kê RIÊNG BIỆT Task / Bug / Issue — kèm danh sách từng ô để mở popup. */
  readonly catStats = computed<CatRow[]>(() => {
    const items = this.tasks();
    return categoryStats(items).map((c) => {
      const list = items.filter((t) => catOf(t.type) === c.key);
      const byStatus = emptyStatusBuckets();
      for (const t of list) byStatus[t.status].push(t);
      return {
        ...c, items: list, byStatus,
        overdueItems: list.filter((t) => isOverdue(t.dueDate, t.status)),
        openItems: list.filter((t) => t.status !== 'DONE' && t.status !== 'CANCELLED')
      };
    });
  });

  /**
   * Bảng tổng hợp theo NHÂN SỰ: mỗi người × loại công việc × trạng thái.
   * Gom theo CHỦ HIỆN TẠI (xem ownerOf): việc ở Kiểm thử tính cho người kiểm thử /
   * người log, chứ không nằm mãi ở dev đã bàn giao. Mỗi việc chỉ thuộc ĐÚNG MỘT người
   * tại một thời điểm → cộng các dòng lại vẫn đúng bằng tổng công việc dự án.
   * Người chưa gán gom về một dòng và luôn xếp cuối.
   */
  readonly personRows = computed<PersonRow[]>(() => {
    const map = new Map<string, PersonRow>();
    for (const t of this.workItems()) {
      const own = ownerOf(t);
      const key = own.id || own.name || '__none__';
      let row = map.get(key);
      if (!row) {
        row = {
          key, userId: own.id, name: own.name || '— Chưa gán —',
          unassigned: !own.id && !own.name,
          items: [], byType: emptyTypeBuckets(), byStatus: emptyStatusBuckets(), overdueItems: [],
          total: 0, done: 0, donePct: 0, estHours: 0, actualHours: 0
        };
        map.set(key, row);
      }
      row.items.push(t);
      const c = catOf(t.type);
      if (c) row.byType[c].push(t);
      row.byStatus[t.status].push(t);
      if (isOverdue(t.dueDate, t.status)) row.overdueItems.push(t);
    }
    // Người CÓ LIÊN QUAN nhưng đang giữ 0 việc (vd QA đã verify xong hết, hoặc dev vừa bàn
    // giao hết) vẫn phải có dòng — nếu không họ biến mất khỏi bảng dù đóng góp rất nhiều.
    for (const t of this.workItems()) {
      for (const p of [
        { id: t.assigneeUserId, name: t.assigneeName },
        { id: t.testerUserId ?? null, name: t.testerName ?? null },
        { id: t.reporterUserId ?? null, name: t.reporterName ?? null }
      ]) {
        if (!p.id && !p.name) continue;
        const key = p.id || p.name || '__none__';
        if (map.has(key)) continue;
        map.set(key, {
          key, userId: p.id ?? null, name: p.name || '— Chưa gán —', unassigned: false,
          items: [], byType: emptyTypeBuckets(), byStatus: emptyStatusBuckets(), overdueItems: [],
          total: 0, done: 0, donePct: 0, estHours: 0, actualHours: 0
        });
      }
    }

    const actual = this.actualByUser();
    const rows = [...map.values()];
    for (const r of rows) {
      r.total = r.items.length;
      r.done = r.byStatus.DONE.length;
      const scope = r.total - r.byStatus.CANCELLED.length; // Huỷ ngoài phạm vi % hoàn thành
      r.donePct = scope > 0 ? Math.round((r.done / scope) * 100) : 0;
      r.estHours = Math.round(r.items
        .filter((t) => t.status !== 'CANCELLED')
        .reduce((a, t) => a + effectiveHours(t), 0) * 10) / 10;
      r.actualHours = Math.round((actual.get(r.userId ?? '__none__') ?? 0) * 10) / 10;
    }
    return rows.sort((a, b) =>
      (a.unassigned ? 1 : 0) - (b.unassigned ? 1 : 0) || b.total - a.total || a.name.localeCompare(b.name, 'vi')
    );
  });

  /** Dòng "Tổng cộng" của bảng nhân sự (gộp mọi người). */
  readonly personTotal = computed<PersonRow>(() => {
    const all = this.workItems();
    const byType = emptyTypeBuckets();
    const byStatus = emptyStatusBuckets();
    for (const t of all) {
      const c = catOf(t.type);
      if (c) byType[c].push(t);
      byStatus[t.status].push(t);
    }
    const total = all.length;
    const scope = total - byStatus.CANCELLED.length;
    return {
      key: '__total__', userId: null, name: 'Tổng cộng', unassigned: false,
      items: all, byType, byStatus, overdueItems: all.filter((t) => isOverdue(t.dueDate, t.status)),
      estHours: 0, actualHours: 0, // dòng Σ lấy số tổng từ report + work log, không cộng lại từ dòng con
      total, done: byStatus.DONE.length,
      donePct: scope > 0 ? Math.round((byStatus.DONE.length / scope) * 100) : 0
    };
  });

  /**
   * Tiến độ % EPIC/Story theo THỨ TỰ CÂY (Story nằm dưới Epic cha) — kèm level để thụt lề
   * và số việc con XONG / CHƯA XONG (đếm mọi hậu duệ là Task/Sub-task/Bug/Issue).
   */
  readonly epicStoryRows = computed<EpicStoryRow[]>(() => {
    const all = this.tasks();
    // Con trực tiếp của MỌI task — để gom hậu duệ là công việc thực.
    const kids = new Map<string, ProjectTask[]>();
    for (const t of all) {
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

    const es = all.filter((t) => t.type === 'EPIC' || t.type === 'STORY');
    const esIds = new Set(es.map((t) => t.id));
    const childrenOf = new Map<string, ProjectTask[]>();
    for (const t of es) {
      const k = t.parentId && esIds.has(t.parentId) ? t.parentId : '';
      (childrenOf.get(k) ?? childrenOf.set(k, []).get(k)!).push(t);
    }
    const out: EpicStoryRow[] = [];
    const walk = (parentKey: string, level: number) => {
      for (const t of childrenOf.get(parentKey) ?? []) {
        const items = descWork(t.id);
        out.push({
          id: t.id, code: t.code, title: t.title, type: t.type,
          pct: Math.max(0, Math.min(100, Math.round(t.progressPct ?? 0))), level,
          items,
          doneItems: items.filter((i) => i.status === 'DONE'),
          openItems: items.filter((i) => i.status !== 'DONE' && i.status !== 'CANCELLED')
        });
        walk(t.id, level + 1);
      }
    };
    walk('', 0);
    return out;
  });

  /** Độ lệch giờ thực tế so với est của một người: "+25%" là vượt, "−10%" là dưới ước lượng. */
  varianceText(p: PersonRow): string {
    if (!p.estHours || !p.actualHours) return '—';
    const v = Math.round(((p.actualHours - p.estHours) / p.estHours) * 100);
    return (v > 0 ? '+' : '') + v + '%';
  }
  varianceClass(p: PersonRow): string {
    if (!p.estHours || !p.actualHours) return 'ov2__zero';
    return p.actualHours > p.estHours ? 'ov2__var--over' : 'ov2__var--under';
  }

  /** Nhãn/màu cho popup + bảng. */
  readonly detailCols: GridColumn[] = [
    { key: 'code', header: 'Mã', width: '84px', sortable: true },
    { key: 'type', header: 'Loại', width: '104px' },
    { key: 'title', header: 'Công việc', sortable: true },
    { key: 'assigneeName', header: 'Người làm', width: '190px' },
    { key: 'status', header: 'Trạng thái', width: '132px' },
    { key: 'dueDate', header: 'Hạn', align: 'center', width: '124px', sortable: true },
    { key: 'progressPct', header: '% HT', width: '110px', sortable: true }
  ];
  typeColor(t: TaskType): string { return TYPE_META[t]?.color ?? 'var(--color-primary)'; }
  typeLabel(t: TaskType): string { return TYPE_META[t]?.short ?? t; }
  clampPct(v: number | null | undefined): number { return Math.max(0, Math.min(100, Math.round(v ?? 0))); }
  /** Chuỗi cha (Epic › Story › Task cha) để hiện dưới tiêu đề trong popup. */
  parentPath(t: ProjectTask): string { return (t.parentChain ?? []).map((p) => p.title).join(' › '); }
  taskStatusLabel(s: TaskStatus): string { return STATUS_META.find((m) => m.key === s)?.label ?? s; }
  taskStatusBadge(s: TaskStatus): string {
    switch (s) {
      case 'BACKLOG': return 'badge--neutral';
      case 'TODO': return 'badge--pending';
      case 'IN_PROGRESS': return 'badge--active';
      case 'IN_REVIEW': return 'badge--active'; // không có badge--info trong design system
      case 'DONE': return 'badge--done';
      case 'CANCELLED': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }

  /** Bug/Issue của dự án — để kiểm soát chất lượng theo nhân sự. */
  private readonly bugList = computed(() => this.tasks().filter((t) => t.type === 'BUG' || t.type === 'ISSUE'));
  readonly bugCount = computed(() => this.bugList().length);
  /** Tester ĐÃ log bug (nhóm theo người tạo/report). */
  readonly bugByReporter = computed(() => this.rankBugs('reporter'));
  /** Dev BỊ log bug (nhóm theo người thực hiện). */
  readonly bugByAssignee = computed(() => this.rankBugs('assignee'));
  private rankBugs(kind: 'reporter' | 'assignee'): { name: string; count: number }[] {
    const map = new Map<string, { name: string; count: number }>();
    for (const b of this.bugList()) {
      const id = kind === 'reporter' ? b.reporterUserId : b.assigneeUserId;
      const name = kind === 'reporter' ? b.reporterName : b.assigneeName;
      const key = id || name || '__none__';
      const cur = map.get(key) ?? { name: name || '— Không rõ —', count: 0 };
      cur.count++;
      map.set(key, cur);
    }
    return [...map.values()].sort((a, b) => b.count - a.count);
  }

  /** % hoàn thành theo EST (ưu tiên report, fallback Project.completionPct). */
  readonly pct = computed(() =>
    Math.max(0, Math.min(100, Math.round(this.report()?.completionPct ?? this.project()?.completionPct ?? 0)))
  );
  /** % hoàn thành theo SỐ LƯỢNG task lá (đếm). */
  readonly taskPct = computed(() => {
    const r = this.report();
    return r && r.leafTasks ? Math.round((r.leafDoneTasks / r.leafTasks) * 100) : 0;
  });

  /** Đang làm = IN_PROGRESS + IN_REVIEW (cho thẻ số liệu báo cáo). */
  readonly doingCount = computed(() => {
    const b = (this.report()?.byStatus ?? {}) as Record<string, number>;
    return (b['IN_PROGRESS'] || 0) + (b['IN_REVIEW'] || 0);
  });

  /** Est done/tổng — làm tròn cho gọn thẻ (tránh tràn "83.45 / 948.45"). */
  readonly estValue = computed(() => {
    const r = this.report();
    return r ? Math.round(r.doneEstimate) + ' / ' + Math.round(r.totalEstimate) : '—';
  });

  readonly dateRange = computed(() => {
    const p = this.project();
    if (!p) return '—';
    const s = p.startDate || '?';
    const d = p.dueDate || '?';
    return s === '?' && d === '?' ? '—' : `${s} → ${d}`;
  });

  // ----- byStatus → thanh ngang -----
  private readonly statusMeta: { status: TaskStatus; label: string; color: string }[] = [
    { status: 'BACKLOG', label: 'Backlog', color: 'var(--color-text-muted)' },
    { status: 'TODO', label: 'To Do', color: 'var(--status-pending)' },
    { status: 'IN_PROGRESS', label: 'In Progress', color: 'var(--status-active)' },
    { status: 'IN_REVIEW', label: 'Testing', color: 'var(--color-info)' },
    { status: 'DONE', label: 'Done', color: 'var(--status-done)' },
    { status: 'CANCELLED', label: 'Cancelled', color: 'var(--status-cancel)' }
  ];

  readonly statusBars = computed<StatusBar[]>(() => {
    const r = this.report();
    if (!r) return [];
    const max = Math.max(1, ...this.statusMeta.map((m) => r.byStatus[m.status] ?? 0));
    return this.statusMeta.map((m) => {
      const count = r.byStatus[m.status] ?? 0;
      return { ...m, count, pct: Math.round((count / max) * 100) };
    });
  });

  // ----- byType → badge + số -----
  private readonly typeMeta: { type: TaskType; label: string; badge: string }[] = [
    { type: 'EPIC', label: 'Epic', badge: 'badge--active' },
    { type: 'STORY', label: 'Story', badge: 'badge--done' },
    { type: 'TASK', label: 'Task', badge: 'badge--neutral' },
    { type: 'SUBTASK', label: 'Subtask', badge: 'badge--neutral' },
    { type: 'BUG', label: 'Bug', badge: 'badge--cancel' },
    { type: 'ISSUE', label: 'Issue', badge: 'badge--pending' }
  ];

  readonly typeStats = computed<TypeStat[]>(() => {
    const r = this.report();
    if (!r) return [];
    return this.typeMeta
      .map((m) => ({ type: m.type, label: m.label, badge: m.badge, count: r.byType[m.type] ?? 0 }))
      .filter((t) => t.count > 0);
  });

  // ----- byAssignee → data-grid -----
  readonly assigneeRows = computed(() => {
    const r = this.report();
    if (!r) return [];
    return r.byAssignee.map((a) => {
      const scope = a.total - a.cancel; // Huỷ ngoài phạm vi % hoàn thành
      return { ...a, donePct: scope > 0 ? Math.round((a.done / scope) * 100) : 0 };
    });
  });

  constructor() {
    effect(() => {
      const id = this.projectId();
      this.refreshKey();          // sửa task ở popup → tải lại dữ liệu, KHÔNG dựng lại màn
      if (id) this.load(id);
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.svc.get(id).subscribe({
      next: (p) => this.project.set(p),
      error: () => this.project.set(null)
    });
    this.svc.report(id).subscribe({
      next: (r) => { this.report.set(r); this.loading.set(false); },
      error: () => { this.report.set(null); this.loading.set(false); }
    });
    this.svc.listTasks(id).subscribe({
      next: (t) => { this.tasks.set(t ?? []); this.syncOpenPopups(); },
      error: () => this.tasks.set([])
    });
    this.svc.listMembers(id).subscribe({
      next: (m) => this.members.set(m ?? []),
      error: () => this.members.set([])
    });
    // Khoảng rộng để lấy TOÀN BỘ giờ đã ghi của dự án (API lọc theo ngày).
    this.svc.listProjectWorkLogs(id, '2000-01-01', '2100-12-31').subscribe({
      next: (w) => this.workLogs.set(w ?? []),
      error: () => this.workLogs.set([])
    });
    this.svc.projectActivity(id).subscribe({
      next: (a) => this.activity.set(a ?? []),
      error: () => this.activity.set([])
    });
  }

  /**
   * Sau khi tải lại danh sách task: đồng bộ dữ liệu MỚI vào các popup đang mở
   * (danh sách chi tiết + chi tiết công việc) để không hiện số liệu cũ.
   * Giữ nguyên TẬP việc đang xem — việc vừa đổi trạng thái vẫn nằm lại cho dễ theo dõi.
   */
  private syncOpenPopups(): void {
    const byId = new Map(this.tasks().map((t) => [t.id, t]));
    const d = this.detailModal();
    if (d) this.detailModal.set({ ...d, items: d.items.map((i) => byId.get(i.id) ?? i) });
    const cur = this.taskDetail();
    if (cur) {
      const fresh = byId.get(cur.id);
      if (fresh) this.taskDetail.set(fresh);
    }
  }

  /** Nhãn hành động cho dòng hoạt động (nhận cả DIARY từ nhật ký dự án). */
  actionLabel(a: string): string {
    switch (a) {
      case 'CREATED': return 'tạo';
      case 'STATUS': return 'đổi trạng thái';
      case 'ASSIGN': return 'gán người';
      case 'EDIT': return 'sửa';
      case 'COMMENT': return 'bình luận';
      case 'ATTACH': return 'đính kèm';
      case 'SPENT': return 'ghi giờ';
      case 'DIARY': return 'ghi nhật ký';
      default: return String(a).toLowerCase();
    }
  }
  actionIcon(a: string): string {
    switch (a) {
      case 'CREATED': return '✨';
      case 'STATUS': return '🔄';
      case 'ASSIGN': return '👤';
      case 'EDIT': return '✏️';
      case 'COMMENT': return '💬';
      case 'ATTACH': return '📎';
      case 'SPENT': return '⏱️';
      case 'DIARY': return '📔';
      default: return '•';
    }
  }
  /** Thời gian tương đối gọn: "5 phút trước", "2 giờ trước", "3 ngày trước". */
  relTime(iso: string | null): string {
    if (!iso) return '';
    const t = Date.parse(iso);
    if (isNaN(t)) return '';
    const diff = Date.now() - t;
    const min = Math.floor(diff / 60000);
    if (min < 1) return 'vừa xong';
    if (min < 60) return min + ' phút trước';
    const hr = Math.floor(min / 60);
    if (hr < 24) return hr + ' giờ trước';
    const day = Math.floor(hr / 24);
    if (day < 30) return day + ' ngày trước';
    const mon = Math.floor(day / 30);
    return mon + ' tháng trước';
  }

  /** Làm tròn số nguyên (dùng cho giờ est hiển thị). */
  rnd(n: number | null | undefined): number { return Math.round(n ?? 0); }

  /** Ngân sách (VND) — phân tách hàng nghìn (helper chung), '—' nếu trống. */
  formatBudget(n: number | null | undefined): string {
    return (n == null) ? '—' : formatThousands(n) + ' ₫';
  }
  /** Nỗ lực MM — 2 chữ số thập phân, '0' nếu trống. */
  formatMM(n: number | null | undefined): string {
    return (n == null ? 0 : n).toLocaleString('vi-VN', { maximumFractionDigits: 2 });
  }
  /** Chênh lệch tuyệt đối nỗ lực thực tế vs kế hoạch (MM), làm tròn 2 chữ số. */
  effortVariance(p: Project): number {
    const plan = p.plannedEffortMm ?? 0;
    return Math.round(Math.abs(p.totalEffortMM - plan) * 100) / 100;
  }

  statusLabel(s: ProjectStatus | undefined): string {
    switch (s) {
      case 'PLANNING': return 'Lập kế hoạch';
      case 'ACTIVE': return 'Đang thực hiện';
      case 'ON_HOLD': return 'Tạm dừng';
      case 'DONE': return 'Done';
      case 'CANCELLED': return 'Cancelled';
      default: return '—';
    }
  }

  statusBadge(s: ProjectStatus | undefined): string {
    switch (s) {
      case 'PLANNING': return 'badge--pending';
      case 'ACTIVE': return 'badge--active';
      case 'ON_HOLD': return 'badge--neutral';
      case 'DONE': return 'badge--done';
      case 'CANCELLED': return 'badge--cancel';
      default: return 'badge--neutral';
    }
  }
}
