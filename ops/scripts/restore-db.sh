#!/usr/bin/env bash
# KHÔI PHỤC cơ sở dữ liệu BPM từ file backup (.sql.gz) — Story 5.4.
# CẢNH BÁO: GHI ĐÈ dữ liệu hiện tại của DB đích. Chỉ chạy có chủ đích (DR / môi trường staging).
set -euo pipefail

FILE="${1:?Cách dùng: restore-db.sh <đường-dẫn/bpm_YYYYMMDD_HHMMSS.sql.gz>}"
DB="${BPM_DB_NAME:-bpm}"
HOST="${BPM_DB_HOST:-localhost}"
PORT="${BPM_DB_PORT:-3306}"
USER="${BPM_DB_USER:-bpm}"

: "${BPM_DB_PASSWORD:?Hãy đặt biến môi trường BPM_DB_PASSWORD}"
[[ -s "$FILE" ]] || { echo "Không thấy file backup: $FILE" >&2; exit 1; }

echo "[restore] SẼ GHI ĐÈ DB '$DB'@$HOST từ: $FILE"
read -r -p "Gõ 'YES' để xác nhận: " ok
[[ "$ok" == "YES" ]] || { echo "Hủy."; exit 1; }

# Dừng ứng dụng trước khi khôi phục (xem runbook). Khôi phục:
gunzip -c "$FILE" | mariadb -h "$HOST" -P "$PORT" -u "$USER" -p"$BPM_DB_PASSWORD" "$DB"
echo "[restore] OK. Khởi động lại ứng dụng và kiểm chứng /actuator/health/readiness."
