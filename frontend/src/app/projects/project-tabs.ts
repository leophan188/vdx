/** Cấu hình TAB của một dự án (dùng chung cho shell sidebar + project-workspace). */
export interface PrjTab { key: string; label: string; icon: string; feature?: string; group: string; }

export const PROJECT_TABS: PrjTab[] = [
  { key: 'overview', label: 'Tổng quan', icon: '📊', group: '' }, // luôn hiện; đứng đầu
  // Nhóm CÔNG VIỆC
  { key: 'kanban', label: 'Kanban', icon: '📋', feature: 'PRJ_KANBAN', group: 'Công việc' },
  { key: 'backlog', label: 'Backlog', icon: '🗂️', feature: 'PRJ_BACKLOG', group: 'Công việc' },
  { key: 'bugs', label: 'Bug / Issue', icon: '🐞', feature: 'PRJ_BUGS', group: 'Công việc' },
  { key: 'timeline', label: 'Timeline', icon: '📅', feature: 'PRJ_TIMELINE', group: 'Công việc' },
  // Nhóm THEO DÕI & BÁO CÁO
  { key: 'timesheet', label: 'Timesheet', icon: '⏱️', feature: 'PRJ_TIMESHEET', group: 'Theo dõi & Báo cáo' },
  { key: 'log', label: 'Log', icon: '📜', feature: 'PRJ_LOG', group: 'Theo dõi & Báo cáo' },
  { key: 'diary', label: 'Nhật ký', icon: '📔', feature: 'PRJ_DIARY', group: 'Theo dõi & Báo cáo' },
  { key: 'reports-period', label: 'Báo cáo ngày/tuần', icon: '🗓️', feature: 'PRJ_REPORTS', group: 'Theo dõi & Báo cáo' },
  // Nhóm QUẢN LÝ
  { key: 'members', label: 'Thành viên', icon: '👥', feature: 'PRJ_MEMBERS', group: 'Quản lý' },
];

export interface PrjTabGroup { group: string; tabs: PrjTab[]; }

/** Gom tab theo nhóm, giữ thứ tự ('' = nhóm không tiêu đề, đứng đầu). */
export function groupPrjTabs(tabs: PrjTab[]): PrjTabGroup[] {
  const out: PrjTabGroup[] = [];
  for (const t of tabs) {
    let g = out.find((x) => x.group === t.group);
    if (!g) { g = { group: t.group, tabs: [] }; out.push(g); }
    g.tabs.push(t);
  }
  return out;
}
