#!/usr/bin/env bash
# ===========================================================================
# Chạy BACKEND LOCAL nối THẲNG MariaDB trên SERVER (không dùng H2 local nữa).
#
# CÁCH DÙNG:
#     ./run-local-serverdb.sh <server-ip> [port]
#   Ví dụ:
#     ./run-local-serverdb.sh 123.45.67.89
#     ./run-local-serverdb.sh db.congty.vn 3306
#
# ĐIỀU KIỆN: cổng 3306 của server phải nối được từ máy anh
#   (server đã publish 3306 ra ngoài + firewall cho IP của anh).
#
# FE vẫn `npm start` như thường → đăng nhập http://localhost:4200.
# ===========================================================================
set -euo pipefail

SERVER_HOST="${1:-}"
DB_PORT="${2:-3306}"
if [ -z "$SERVER_HOST" ]; then
  echo "Thiếu địa chỉ server. Dùng: ./run-local-serverdb.sh <server-ip> [port]"
  exit 1
fi

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"

# Nối thẳng DB server. Creds mặc định khớp .env (bpm/Admin@123) — đổi qua biến nếu cần.
export BPM_DB_URL="jdbc:mariadb://${SERVER_HOST}:${DB_PORT}/bpm"
export BPM_DB_USER="${BPM_DB_USER:-bpm}"
export BPM_DB_PASSWORD="${BPM_DB_PASSWORD:-Admin@123}"
# DB server MỚI (rỗng) -> update/true để tạo bảng. Nếu server đã khoá schema, đặt validate/false.
export BPM_DDL_AUTO="${BPM_DDL_AUTO:-update}"
export BPM_FLOWABLE_SCHEMA_UPDATE="${BPM_FLOWABLE_SCHEMA_UPDATE:-true}"
# Ghi đè log ra path ghi được ở máy local (prod mặc định /var/log/bpm — không ghi được trên Mac).
export BPM_LOG_FILE="${BPM_LOG_FILE:-/tmp/bpm-backend-serverdb.log}"

JAR="backend/target/bpm-platform-backend-0.1.0-SNAPSHOT.jar"
[ -f "$JAR" ] || { echo "Chưa có jar. Chạy: (cd backend && mvn -q -DskipTests package)"; exit 1; }

echo "→ Profile: prod | DB: $BPM_DB_URL | user: $BPM_DB_USER | ddl-auto: $BPM_DDL_AUTO"
if command -v nc >/dev/null 2>&1; then
  echo "→ Kiểm nối tới ${SERVER_HOST}:${DB_PORT} ..."
  if nc -z -w 4 "$SERVER_HOST" "$DB_PORT" 2>/dev/null; then
    echo "  ✓ Nối được cổng ${DB_PORT}."
  else
    echo "  ⚠ KHÔNG nối được ${SERVER_HOST}:${DB_PORT} — kiểm server đã mở 3306 + firewall chưa."
  fi
fi

exec java -jar "$JAR" --spring.profiles.active=prod
