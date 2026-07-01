# OnlyOffice Document Server — chạy local (Epic 3.10+)

## Yêu cầu: một container runtime
Máy hiện CHƯA có Docker. Hai lựa chọn trên macOS:

### A) Colima (CLI, nhẹ — khuyến nghị cho dev)
```bash
brew install colima docker docker-compose
colima start --cpu 2 --memory 4 --disk 30      # tạo VM Linux nhẹ (lần đầu ~vài phút)
```

### B) Docker Desktop (GUI, nặng hơn)
```bash
brew install --cask docker     # rồi mở Docker.app một lần để khởi động
```

## Khởi động OnlyOffice
```bash
docker compose -f ops/onlyoffice/docker-compose.yml up -d     # lần đầu pull ~2GB image
# Chờ ~30–60s rồi kiểm tra:
curl -f http://localhost:8082/healthcheck      # → true
open http://localhost:8082/                    # welcome page
```

## Thông số tích hợp (cho backend Epic 3.10)
| Mục | Giá trị local |
|---|---|
| Document Server URL | `http://localhost:8082` |
| JWT bật | `true` |
| JWT secret | `bpm-local-dev-secret` (đổi qua `ONLYOFFICE_JWT_SECRET`) |
| Callback từ OnlyOffice → backend | backend phải để OnlyOffice gọi được (cùng host; nếu OnlyOffice trong VM, dùng `host.docker.internal:8081`) |

> **Lưu ý mạng**: OnlyOffice (trong container/VM) cần truy cập ngược về backend để callback lưu tài liệu. Trên Colima/Docker Desktop, dùng `http://host.docker.internal:8081` làm địa chỉ callback; và backend phải tải được file tài liệu từ URL OnlyOffice truy cập được.

## Dừng / dọn
```bash
docker compose -f ops/onlyoffice/docker-compose.yml down       # giữ volume (dữ liệu)
docker compose -f ops/onlyoffice/docker-compose.yml down -v    # xóa cả volume
colima stop                                                    # tắt VM khi không dùng
```
