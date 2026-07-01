#!/usr/bin/env bash
# Backup NHẤT QUÁN cơ sở dữ liệu BPM (MariaDB) — Story 5.4.
# Dùng --single-transaction để có snapshot nhất quán (InnoDB) mà KHÔNG khóa bảng → không gián đoạn vận hành.
# Cron gợi ý: 0 1 * * *  (1h sáng mỗi ngày)
set -euo pipefail

DB="${BPM_DB_NAME:-bpm}"
HOST="${BPM_DB_HOST:-localhost}"
PORT="${BPM_DB_PORT:-3306}"
USER="${BPM_DB_USER:-bpm}"
OUT_DIR="${BPM_BACKUP_DIR:-/var/backups/bpm}"
RETENTION_DAYS="${BPM_BACKUP_RETENTION_DAYS:-14}"
TS="$(date +%Y%m%d_%H%M%S)"
FILE="$OUT_DIR/bpm_${TS}.sql.gz"

: "${BPM_DB_PASSWORD:?Hãy đặt biến môi trường BPM_DB_PASSWORD}"
mkdir -p "$OUT_DIR"

echo "[backup] $DB@$HOST:$PORT → $FILE"
mariadb-dump --single-transaction --quick --routines --triggers --events \
  -h "$HOST" -P "$PORT" -u "$USER" -p"$BPM_DB_PASSWORD" "$DB" | gzip -9 > "$FILE"

# Kiểm tra file backup không rỗng (an toàn tối thiểu)
if [[ ! -s "$FILE" ]]; then
  echo "[backup] LỖI: file backup rỗng!" >&2
  exit 1
fi
echo "[backup] OK: $(du -h "$FILE" | cut -f1)  $FILE"

# Dọn backup cũ hơn N ngày
find "$OUT_DIR" -name 'bpm_*.sql.gz' -mtime +"$RETENTION_DAYS" -print -delete
echo "[backup] Đã dọn backup > ${RETENTION_DAYS} ngày."
