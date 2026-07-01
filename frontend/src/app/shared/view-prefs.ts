/**
 * Lưu/đọc cấu hình hiển thị của màn (bộ lọc loại task…) vào localStorage — DÙNG LẠI ở backlog, timeline…
 * để lần sau vào không phải tích chọn lại. Lỗi (private mode…) → trả fallback, không vỡ.
 */
export function loadPref<T>(key: string, fallback: T): T {
  try {
    const v = localStorage.getItem(key);
    return v == null ? fallback : (JSON.parse(v) as T);
  } catch {
    return fallback;
  }
}

export function savePref(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    /* bỏ qua (vd chế độ riêng tư) */
  }
}
