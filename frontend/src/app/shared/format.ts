/**
 * Định dạng số có dấu chấm phân tách hàng nghìn (kiểu VN) — DÙNG CHUNG, không phụ thuộc locale ICU.
 * 5000000000 → "5.000.000.000". Trả '—' nếu null/undefined.
 */
export function formatThousands(n: number | null | undefined): string {
  if (n == null || isNaN(n as number)) return '—';
  const neg = n < 0;
  const [intPart, decPart] = Math.abs(n).toString().split('.');
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  return (neg ? '-' : '') + grouped + (decPart ? ',' + decPart : '');
}
