#!/usr/bin/env bash
# ===========================================================================
# DEPLOY code LOCAL -> SERVER (git + docker compose build trên server).
#
# Luồng: commit sẵn -> push branch -> SSH server: pull + build image + restart.
# Dockerfile tự build (maven/npm bên trong) nên KHÔNG cần build jar/dist ở local.
#
# CÁCH DÙNG:
#     SSHPASS='<mật-khẩu-ssh>' ./deploy.sh            # không phải gõ mật khẩu
#     ./deploy.sh                                     # ssh sẽ hỏi mật khẩu
#   Tuỳ chọn (ghi đè mặc định qua biến môi trường):
#     DEPLOY_SERVER=pdx@10.8.0.60  DEPLOY_DIR=/home/pdx/bpm-vdx/vdx  ./deploy.sh
#     DEPLOY_SERVICES="backend frontend"  ./deploy.sh   # chỉ build service cần
# ===========================================================================
set -euo pipefail

SERVER="${DEPLOY_SERVER:-pdx@10.8.0.60}"
REMOTE_DIR="${DEPLOY_DIR:-/home/pdx/bpm-vdx/vdx}"
SERVICES="${DEPLOY_SERVICES:-backend frontend}"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

# 1) Bắt buộc đã commit sạch (deploy đúng thứ đã ghi lại).
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "⚠ Còn thay đổi CHƯA commit — hãy commit trước khi deploy:"; git status --short; exit 1
fi

echo "→ [1/3] Push $BRANCH lên origin..."
git push origin "$BRANCH"

# 2) Chọn cách chạy ssh (có SSHPASS -> sshpass; không -> ssh hỏi mật khẩu).
if [ -n "${SSHPASS:-}" ]; then
  SSH=(sshpass -e ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER")
else
  SSH=(ssh -o StrictHostKeyChecking=no -o ConnectTimeout=15 "$SERVER")
fi

echo "→ [2/3] Server $SERVER: pull + build ($SERVICES) + restart..."
"${SSH[@]}" bash -s <<REMOTE
set -e
cd "$REMOTE_DIR"
git fetch origin "$BRANCH"
git reset --hard "origin/$BRANCH"       # khớp đúng bản vừa push (bỏ mọi sửa tay trên server)
echo "  HEAD server: \$(git rev-parse --short HEAD)"
docker compose up -d --build $SERVICES
echo "--- trạng thái ---"
docker compose ps
REMOTE

echo "→ [3/3] Xong. FE: http://10.8.0.60:8085  |  branch: $BRANCH"
