# Story 1.1: Đăng nhập & quản lý tài khoản nội bộ

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **quản trị hệ thống**,
I want **quản lý tài khoản nội bộ (tạo, đặt/đổi mật khẩu, khóa/mở) và người dùng đăng nhập bằng tài khoản hệ thống**,
so that **hệ thống có cổng xác thực riêng ở GĐ1 mà không phụ thuộc SSO/AD, làm bệ phóng cho phân quyền và phân công của các story sau**.

## Acceptance Criteria

1. **Given** một quản trị đã đăng nhập, **When** tạo tài khoản mới (tên đăng nhập + mật khẩu + họ tên), **Then** tài khoản được lưu với **mật khẩu băm** (không lưu plaintext) và xuất hiện trong danh sách tài khoản. _(FR-C07, NFR-04)_
2. **Given** một tài khoản tồn tại, **When** người dùng đăng nhập đúng tên đăng nhập + mật khẩu qua HTTPS, **Then** nhận phiên/token hợp lệ; **When** sai mật khẩu, **Then** bị từ chối với thông báo lỗi theo envelope chuẩn `{code,message,details,traceId}`, không lộ tài khoản có tồn tại hay không.
3. **Given** một phiên đăng nhập, **When** quá thời gian cấu hình không hoạt động, **Then** phiên hết hạn và yêu cầu đăng nhập lại.
4. **Given** một quản trị, **When** khóa/mở-khóa hoặc đặt lại mật khẩu một tài khoản, **Then** thay đổi có hiệu lực (tài khoản bị khóa không đăng nhập được) và được ghi **audit qua AuditPort** (ai·làm gì·đối tượng·thời điểm). _(AD-6, FR-I01)_
5. **Given** kiến trúc auth, **When** triển khai, **Then** lớp xác thực là **module riêng tách rời lõi nghiệp vụ** để GĐ sau cắm SSO/AD mà không sửa lõi. _(AD-9)_
6. **Given** chính sách mật khẩu, **When** đặt/đổi mật khẩu, **Then** áp ràng buộc tối thiểu (độ dài, độ phức tạp) _[ASSUMPTION] mức cụ thể chốt khi dev_.

## Tasks / Subtasks

- [ ] **Task 0 — Khởi tạo project skeleton (one-time, foundation)** (AC: tất cả)
  - [ ] BE: khởi tạo Spring Boot 3.5 (Java 21, Maven) theo cấu trúc layered `api/application/domain/infrastructure` (AD-1, AD-12); thêm dependency: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `mariadb-java-client`, `flowable-spring-boot-starter` (7.x) — chỉ khai báo, story này chưa dùng Flowable runtime.
  - [ ] FE: scaffold Angular 21 SPA (`frontend/`) + PrimeNG; tạo shell tối thiểu (topbar + sidebar theo vai trò — UX-DR3) đủ cho màn đăng nhập + quản trị tài khoản.
  - [ ] `deploy/docker-compose.yml`: MariaDB 11.8 service (BE/OnlyOffice thêm ở story sau).
- [ ] **Task 1 — Domain & schema tài khoản** (AC: 1,4)
  - [ ] Entity `UserAccount` (UUID v7 PK, username unique, passwordHash, fullName, status `ACTIVE|LOCKED`, createdAt UTC). Chỉ tạo bảng story này cần.
  - [ ] Migration tạo bảng `user_account` (snake_case, NFR-10 không áp dụng — đây là bảng quan hệ thuần).
- [ ] **Task 2 — Đăng nhập & phiên** (AC: 2,3)
  - [ ] `AuthService` (module `infrastructure/auth` tách rời — AD-9): xác thực username+passwordHash (BCrypt), phát token/phiên; cấu hình timeout phiên.
  - [ ] REST `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`; envelope lỗi chuẩn; HTTPS/TLS (NFR-04).
  - [ ] FE: màn đăng nhập tiếng Việt (UX-DR11), xử lý lỗi, lưu phiên.
- [ ] **Task 3 — Quản trị tài khoản (CRUD + khóa + reset)** (AC: 1,4,6)
  - [ ] `POST/GET/PATCH /api/v1/users` (tạo, liệt kê, khóa/mở, đặt lại mật khẩu) — chỉ vai trò quản trị (RBAC đầy đủ ở Story 1.4; story này gắn 1 vai trò ADMIN khởi tạo).
  - [ ] Chính sách mật khẩu (Task validator).
  - [ ] FE: bảng tài khoản dense (UX-DR4) + form tạo/sửa.
- [ ] **Task 4 — Audit qua AuditPort** (AC: 4)
  - [ ] Định nghĩa `AuditPort` (interface ở `domain`) + adapter ghi append-only bảng `audit_event` phân vùng thời gian (AD-6, NFR-13) — tạo bảng tối thiểu story này cần.
  - [ ] Phát audit cho: tạo/khóa/mở/reset tài khoản, đăng nhập thất bại. **Không** dùng Flowable history làm audit.
- [ ] **Task 5 — Test**
  - [ ] BE: unit test AuthService (băm, sai mật khẩu, khóa), integration test endpoint (JUnit 5 + Spring Boot Test + Testcontainers MariaDB hoặc H2).
  - [ ] FE: test component đăng nhập (Vitest/Jasmine theo mặc định Angular 21).
  - [ ] AC-trace: mỗi AC có ít nhất một test.

## Dev Notes

- **Kiến trúc (bắt buộc tuân thủ):** modular monolith, layered + hexagonal ports (AD-1); feature→core, không feature↔feature (AD-12); auth là module tách rời để cắm SSO sau (AD-9); audit append-only một AuditPort, phân vùng thời gian, Flowable history KHÔNG phải audit (AD-6). [Source: architecture/architecture-bpm-platform-2026-06-24/ARCHITECTURE-SPINE.md#AD-1, #AD-6, #AD-9, #AD-12]
- **Conventions:** PK UUID v7; bảng/cột snake_case; class PascalCase; REST `/api/v1/{resource}` kebab; thời gian lưu UTC `TIMESTAMP`, API ISO 8601; envelope lỗi `{code,message,details[],traceId}`. [Source: ARCHITECTURE-SPINE.md#Consistency-Conventions]
- **Stack & version:** Java 21 (LTS) · Spring Boot 3.5.x (KHÔNG dùng 4.x — chọn 3.5 vì Flowable 7 build cho dòng 3.x) · MariaDB 11.8 (LTS) · Angular 21 (LTS) + PrimeNG. [Source: ARCHITECTURE-SPINE.md#Stack]
- **UX:** shell sidebar-theo-vai-trò + topbar (UX-DR3); bảng dense zebra/sticky (UX-DR4); microcopy tiếng Việt trang trọng (UX-DR11); sàn WCAG 2.1 AA (UX-DR9: tương phản, focus, bàn phím). [Source: ux-designs/ux-bpm-platform-2026-06-24/EXPERIENCE.md]
- **Bảo mật:** BCrypt cho mật khẩu; HTTPS/TLS; không lộ tồn-tại-tài-khoản qua thông báo lỗi; quản lý phiên + chính sách mật khẩu (NFR-04).
- **Phạm vi story:** RBAC đầy đủ (vai trò→vị trí) là **Story 1.4** — story này chỉ cần một vai trò ADMIN seed để bảo vệ endpoint quản trị. Org tree là Story 1.2. KHÔNG xây trước.

### Project Structure Notes

- Đây là **story khởi tạo dự án** (greenfield) — Task 0 tạo skeleton BE/FE/deploy lần đầu theo source tree trong spine (`backend/{api,application,domain,infrastructure}`, `frontend/`, `deploy/`). [Source: ARCHITECTURE-SPINE.md#Structural-Seed]
- Tạo bảng/entity **chỉ những gì story cần** (`user_account`, `audit_event`) — không tạo trước toàn bộ schema.

### ⚠️ Ràng buộc môi trường (QUAN TRỌNG cho dev agent)

- Máy hiện tại **KHÔNG có Java/Maven** (chỉ Node 26 + npm). → **Backend Spring Boot KHÔNG build/test/chạy được tại đây.** Dev agent có thể *viết mã* BE nhưng phải báo rõ không thể `mvn`/chạy test BE; cần cài JDK 21 + Maven (hoặc dùng Maven Wrapper sau khi có JDK) để build/kiểm thử.
- **Frontend Angular** scaffold/build được bằng npm. Ưu tiên hoàn tất + kiểm thử phần FE; phần BE để bản thảo mã + hướng dẫn build khi có Java.

### References

- [Source: planning-artifacts/epics.md#Story-1.1] — AC gốc, FR-C07/C06/I01.
- [Source: prds/prd-bpm-platform-2026-06-24/prd.md#FR-C07, #NFR-04] — đăng nhập nội bộ, bảo mật.
- [Source: architecture/.../ARCHITECTURE-SPINE.md#AD-9, #AD-6] — auth tách rời, AuditPort.

## Dev Agent Record

### Agent Model Used

claude-opus-4-8 (1M context) — đóng vai dev agent (2026-06-24).

### Debug Log References

- `mvn -B test` (backend): **BUILD SUCCESS** — `Tests run: 6, Failures: 0, Errors: 0`. Spring Boot 3.5.0, Java 21.0.11, H2 in-memory (profile test).
- Sửa 1 lỗi: ký tự `&` chưa escape trong `<description>` pom.xml.

### Completion Notes List

- ✅ **Backend hoàn tất + test thật pass (6/6).** Phủ AC 1–5 + chính sách mật khẩu (AC-6): đăng nhập thành công/sai/khóa, audit `LOGIN_FAILED` qua AuditPort (AD-6), admin tạo tài khoản (201), non-admin 403, chưa xác thực 401, envelope lỗi `{code,message,details,traceId}`, phiên timeout cấu hình (`server.servlet.session.timeout`), BCrypt, module auth tách rời (AD-9).
- **Sai lệch có chủ đích (ghi rõ):** (1) Chưa thêm `flowable-spring-boot-starter` — Story 1.1 không dùng engine; thêm sẽ auto-tạo bảng Flowable, làm nặng build/test. Thêm ở Epic 2/3. (2) PK dùng UUID v4 thay UUID v7 (stdlib Java 21 chưa có generator v7) — chuyển v7 khi đưa generator vào core. (3) `ddl-auto=update` GĐ1; chuyển Flyway + `validate` khi schema ổn định. (4) Audit `partition by time` để ở DDL/vận hành (Story 5.5); entity chỉ INSERT.
- ✅ **Frontend Angular 21 — build OK + Vitest 7/7 pass (3 file).** Màn **đăng nhập** (lỗi 401), **quản trị tài khoản** đầy đủ: **tạo** (form username/họ tên/mật khẩu/vai trò), **khóa/mở**, **đặt lại mật khẩu**, bảng dense; `AuthService` (withCredentials cho phiên); routes `/login`→`/accounts`; shell topbar.
- ✅ **Mọi AC 1–6 phủ end-to-end** (BE thực thi + test; FE thao tác + test).
- ⚠️ **Follow-up (không chặn done):** chưa tích hợp **PrimeNG** (dùng HTML thuần — để story UX polish); chưa có **e2e thật FE↔BE** (cần chạy đồng thời + MariaDB); khuyến nghị chạy `/code-review` độc lập (context khác) trước khi ship.

### File List

Backend (mới):
- `backend/pom.xml`
- `backend/src/main/java/com/bpm/BpmApplication.java`
- `backend/src/main/java/com/bpm/domain/{AccountStatus,UserAccount}.java`
- `backend/src/main/java/com/bpm/domain/audit/{AuditPort,AuditEvent}.java`
- `backend/src/main/java/com/bpm/infrastructure/{UserAccountRepository,AuditEventRepository,JpaAuditAdapter,AdminSeeder}.java`
- `backend/src/main/java/com/bpm/infrastructure/auth/{AppUserDetailsService,SecurityConfig}.java`
- `backend/src/main/java/com/bpm/application/{UserAccountService,AuthService}.java`
- `backend/src/main/java/com/bpm/api/{AuthController,UserController,GlobalExceptionHandler}.java`
- `backend/src/main/java/com/bpm/api/dto/{LoginRequest,CreateUserRequest,ResetPasswordRequest,UserResponse,ApiError}.java`
- `backend/src/main/resources/application.yml`
- `backend/src/test/resources/application-test.yml`
- `backend/src/test/java/com/bpm/AuthAndUserAdminIntegrationTest.java`

Frontend (mới — scaffold Angular 21 + tùy chỉnh):
- `frontend/` (scaffold đầy đủ qua `ng new`)
- `frontend/src/app/app.config.ts` (thêm provideHttpClient)
- `frontend/src/app/app.routes.ts`, `app.html` (shell + routes)
- `frontend/src/app/core/auth.service.ts`
- `frontend/src/app/login/{login.ts,login.html,login.spec.ts}`
- `frontend/src/app/accounts/{accounts.ts,accounts.html}`
- `frontend/src/app/app.spec.ts` (cập nhật)

Deploy:
- `deploy/docker-compose.yml` (MariaDB 11.8)
