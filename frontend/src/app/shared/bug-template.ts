/**
 * Mẫu Mô tả lỗi dùng chung: gộp "các bước thực hiện / kết quả thực tế / kết quả mong đợi" vào 1 trường Mô tả.
 * Form báo lỗi chỉ còn 1 ô Mô tả (rộng), điền sẵn khung này khi tạo bug/issue mới.
 */
export const BUG_DESCRIPTION_TEMPLATE =
  'I. Các bước thực hiện\n1.\n2.\n3.\n\nII. Kết quả thực tế\n\n\nIII. Kết quả mong đợi\n';

interface BugLikeFields {
  description?: string | null;
  stepsToReproduce?: string | null;
  actualResult?: string | null;
  expectedResult?: string | null;
}

/**
 * Khi SỬA một bug cũ (đã tách 3 trường), gộp nội dung cũ vào Mô tả để không mất dữ liệu — chỉ gộp 1 lần.
 * Trả về chuỗi Mô tả đã gộp (nếu Mô tả đã chứa khung "I. Các bước" thì giữ nguyên).
 */
export function mergeBugFieldsIntoDescription(t: BugLikeFields): string {
  const desc = (t.description ?? '').trim();
  const steps = (t.stepsToReproduce ?? '').trim();
  const actual = (t.actualResult ?? '').trim();
  const expected = (t.expectedResult ?? '').trim();
  if (!steps && !actual && !expected) return t.description ?? '';
  if (desc.includes('I. Các bước')) return t.description ?? '';
  const parts: string[] = [];
  parts.push('I. Các bước thực hiện\n' + (steps || ''));
  parts.push('II. Kết quả thực tế\n' + (actual || ''));
  parts.push('III. Kết quả mong đợi\n' + (expected || ''));
  return (desc ? desc + '\n\n' : '') + parts.join('\n\n');
}
