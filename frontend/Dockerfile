# syntax=docker/dockerfile:1
# ============================================================================
# BPM Platform Frontend — Dockerfile multi-stage (GO-LIVE)
# Stage 1: build Angular production (npm ci + ng build, mặc định = production).
# Stage 2: phục vụ tĩnh bằng nginx:alpine, proxy /api + /actuator sang backend.
# Output build (theo angular.json): dist/frontend/browser/
# ============================================================================

# ---------- Stage 1: BUILD ----------
FROM node:22-alpine AS build
WORKDIR /app

# Cache layer cài đặt: copy package*.json trước.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# angular.json đặt defaultConfiguration: production → 'npm run build' đã là prod.
RUN npm run build

# ---------- Stage 2: RUNTIME (nginx) ----------
FROM nginx:alpine AS runtime

# Cấu hình nginx phục vụ SPA + proxy API.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy nội dung đã build (thư mục browser/) vào webroot.
COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/

EXPOSE 80

# nginx:alpine có sẵn wget cho healthcheck.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
