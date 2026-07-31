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

/**
 * HÔM NAY theo lịch MÁY NGƯỜI DÙNG, dạng yyyy-MM-dd cho <input type="date">.
 *
 * KHÔNG dùng new Date().toISOString().slice(0,10): toISOString trả giờ UTC, mà Việt Nam là
 * UTC+7 — từ 00:00 đến 07:00 giờ VN thì UTC vẫn đang ở NGÀY HÔM TRƯỚC. Ai chấm công ca đêm
 * hoặc mở máy sớm sẽ bị ghi lùi một ngày mà không hay biết.
 *
 * Gọi mỗi lần cần chứ đừng lưu vào hằng số lúc khởi tạo component — tab để mở qua nửa đêm
 * thì giá trị đó thành ngày hôm qua.
 */
export function todayIso(): string {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
