#!/usr/bin/env bash
# ===========================================================================
# Chạy BACKEND LOCAL nối THẲNG MariaDB trên SERVER qua SSH tunnel.
#
# BƯỚC 1 — mở tunnel ở 1 terminal khác (giữ chạy), thay <user>/<server-ip>:
#     ssh -N -L 13306:127.0.0.1:3306 <user>@<server-ip>
#   (cổng 13306 ở máy local -> 127.0.0.1:3306 trên server, nơi MariaDB
#    publish loopback theo docker-compose. Server phải đã `docker compose up -d mariadb`.)
#
# BƯỚC 2 — chạy script này ở terminal khác (thư mục gốc repo):
#     ./run-local-serverdb.sh
#
# FE vẫn `npm start` như thường (proxy /api -> 8081). Đăng nhập http://localhost:4200.
# ===========================================================================
set -euo pipefail

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}"
export PATH="$JAVA_HOME/bin:$PATH"

# Cổng tunnel ở máy local (khớp lệnh `ssh -L` bước 1).
TUNNEL_PORT="${TUNNEL_PORT:-13306}"

# Kết nối DB server qua tunnel. Creds mặc định khớp .env (bpm/Admin@123) — đổi qua biến nếu cần.
export BPM_DB_URL="jdbc:mariadb://127.0.0.1:${TUNNEL_PORT}/bpm"
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
echo "→ Kiểm tunnel: nc -z 127.0.0.1 $TUNNEL_PORT ..."
if command -v nc >/dev/null 2>&1 && ! nc -z 127.0.0.1 "$TUNNEL_PORT" 2>/dev/null; then
  echo "⚠ Không thấy cổng $TUNNEL_PORT. Đã mở SSH tunnel ở bước 1 chưa?"
fi

exec java -jar "$JAR" --spring.profiles.active=prod
