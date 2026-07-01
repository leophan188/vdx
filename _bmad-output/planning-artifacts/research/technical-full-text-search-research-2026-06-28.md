---
title: "Nghiên cứu kỹ thuật — Nâng cấp Tìm kiếm Toàn văn (Full-Text Search) cho VMO DX"
type: technical-research
date: 2026-06-28
analyst: Mary (Business Analyst)
status: draft
language: vi
---

# Nâng cấp Tìm kiếm Toàn văn (Full-Text Search) — VMO DX

## 1. Tóm tắt điều hành (kết luận trước)

**Khuyến nghị: Giai đoạn 1 dùng `Hibernate Search 7` với backend `Lucene` nhúng.** Đây là điểm cân bằng tốt nhất cho ngữ cảnh VMO DX vì:

- **DB-agnostic** — chạy giống nhau trên **H2 (dev) và MariaDB (prod)**, tránh việc FULLTEXT của H2 ≠ MariaDB.
- **Nhúng (embedded), không thêm hạ tầng vận hành** — phù hợp quy mô nội bộ SME (~100–300 người), không cần dựng cụm search riêng.
- **Tự đồng bộ index từ JPA entity** (CRUD → index real-time) + có mass-indexer để nạp dữ liệu cũ.
- **Xếp hạng độ liên quan (relevance)** sẵn có; xử lý **tiếng Việt không dấu + có dấu** bằng analyzer (tokenizer + lowercase + asciiFolding).
- **Đường nâng cấp mượt**: khi dữ liệu/nhu cầu lớn, **đổi backend sang OpenSearch/Elasticsearch** mà **giữ nguyên annotation + API truy vấn** (Giai đoạn 2).

> Thay thế `findAll()`-scan hiện tại (không scale, không xếp hạng) bằng index Lucene. Giữ bộ chuẩn hoá không dấu (Normalizer + đ→d) ở phía truy vấn để khớp analyzer.

## 2. Bối cảnh & vấn đề

- Stack: Spring Boot 3.5 / Java 21 / JPA; DB **kép** H2-file (dev) + MariaDB (prod, Docker go-live).
- `SearchService` hiện **quét `findAll()` toàn bộ** rồi lọc `contains` (không dấu) — giới hạn 6/loại trên 4 nguồn: Nhân sự, Dự án, Tài khoản, Bài viết MXH; lọc quyền theo `FEAT_HR/PROJECT/ACCOUNTS/SOCIAL`.
- Hạn chế: **không scale** khi task/bài viết/tài liệu tăng; **không xếp hạng**; chưa có **trang kết quả đầy đủ**; cần **tiếng Việt có dấu + không dấu**.

## 3. So sánh phương án (đánh đổi)

| Tiêu chí | (A) MariaDB FULLTEXT | (B) Hibernate Search + Lucene (nhúng) | (C) Elasticsearch/OpenSearch | (D) PostgreSQL tsvector |
|---|---|---|---|---|
| Phù hợp stack hiện tại | Một phần (H2 dev khác cú pháp) | **Cao** (DB-agnostic) | Cao (nhưng thêm service) | Thấp (phải đổi DB) |
| Hạ tầng vận hành thêm | Không | **Không** (nhúng) | **Có** (cụm search) | Đổi DB |
| Tiếng Việt không dấu | Qua collation accent-insensitive; cần cột chuẩn hoá phụ cho chắc | **asciiFolding/ICU analyzer** | **vi_analyzer (CocCoc) + icu_folding** (mạnh nhất) | `unaccent` + cấu hình riêng |
| Xếp hạng độ liên quan | Có (điểm FULLTEXT) | **Có (Lucene scoring)** | **Rất tốt** | Có (`ts_rank`) |
| Đồng bộ dữ liệu | Tự (index trên cột) | **Tự động từ JPA** + mass-indexer | Cần pipeline đồng bộ | Trigger/cột generated |
| Độ phức tạp triển khai | Thấp–TB | **Trung bình** | Cao | Cao (đổi DB) |
| Khả năng mở rộng quy mô | TB | Cao | **Rất cao** | Cao |
| Rủi ro khoá công nghệ | Khoá MariaDB | **Thấp** (đổi backend được) | Thấp | Khoá Postgres |

**Lưu ý kỹ thuật quan trọng:**
- MariaDB/MySQL **ngram parser dành cho CJK** (ngôn ngữ không khoảng trắng) — **không phù hợp tiếng Việt** (tiếng Việt tách từ bằng khoảng trắng); với tiếng Việt dùng **word parser mặc định** + **collation accent-insensitive** + nên thêm **cột chuẩn hoá không dấu** để khớp cả "có dấu/không dấu". [MariaDB/MySQL docs]
- Hibernate Search là **cầu nối JPA ↔ Lucene/Elasticsearch**: `@Indexed` + `@FullTextField`, index tự cập nhật theo thao tác ORM; có thể đổi từ Lucene (local) sang Elasticsearch/OpenSearch **giữ nguyên code**. [Hibernate docs / reflectoring]
- Elasticsearch tiếng Việt: plugin **`elasticsearch-analysis-vietnamese` (`vi_analyzer`, CocCoc)** kết hợp **`icu_folding`/`asciiFolding`** (giữ cả bản có dấu lẫn không dấu qua `preserve_original`). [duydo/elasticsearch-analysis-vietnamese]
- PostgreSQL: `unaccent` (bỏ dấu) + `tsvector`/`ts_rank` + `pg_trgm` (mờ/typo) — mạnh nhưng **đòi đổi DB**, lệch hướng go-live MariaDB.

## 4. Xử lý tiếng Việt (có dấu + không dấu)

Chiến lược chung cho mọi phương án: **index 2 dạng** — nguyên bản (có dấu) và đã bỏ dấu (asciiFolding/`đ→d`, lowercase). Truy vấn cũng chuẩn hoá tương ứng → gõ "dau the thang" ra "Đậu Thế Thắng", gõ "Đậu" vẫn ra. Với Hibernate Search: định nghĩa custom analyzer `standard tokenizer + lowercase + asciiFolding` cho field bỏ dấu, và một field giữ nguyên dấu.

## 5. Phạm vi & phân quyền

- **Mở rộng nguồn**: gắn `@Indexed` cho thêm **Task dự án** và **Tài liệu (OnlyOffice)** ngoài 4 nguồn hiện có.
- **Lọc theo quyền**: index thêm field "phạm vi/owner" (vd projectId, deptCode, hidden) rồi **lọc ở truy vấn** theo `FEAT_*`/membership; hoặc hậu-lọc kết quả như hiện tại (đơn giản hơn, đủ cho quy mô nhỏ). Bài viết ẩn loại khỏi index hoặc gắn cờ.

## 6. Đồng bộ index

- **Hibernate Search**: tự index khi entity được tạo/sửa/xoá qua JPA (real-time); chạy **mass indexer** một lần để nạp dữ liệu hiện có; lưu index Lucene trên đĩa (cấu hình thư mục, sao lưu cùng dữ liệu).
- Với entity sửa ngoài JPA (vd seeder ghi thẳng) cần gọi reindex thủ công.

## 7. Lộ trình tăng dần (chống over-engineering)

- **Giai đoạn 1 (1–2 tuần, ít rủi ro):** Thêm Hibernate Search (Lucene local). Annotate 4–6 entity. Thay `SearchService` quét tay bằng truy vấn index có **xếp hạng**. Thêm **trang kết quả đầy đủ** + **deep-link bài viết** (mở đúng bài). Giữ phân quyền hậu-lọc.
- **Giai đoạn 2 (chỉ khi cần scale/nhu cầu nâng cao):** Đổi backend Hibernate Search sang **OpenSearch/Elasticsearch** + `vi_analyzer` + `icu_folding`; bổ sung gợi ý (autocomplete), highlight, đồng nghĩa. **Không đổi tầng nghiệp vụ.**

**Phương án dự phòng nếu muốn ZERO dependency mới:** MariaDB FULLTEXT + cột `search_text` chuẩn hoá + `MATCH…AGAINST` (BOOLEAN, prefix `term*`); nhưng phải xử lý riêng cho H2 dev (giữ fallback `findAll()` theo profile) — kém gọn hơn Hibernate Search.

## 8. Rủi ro & lưu ý

- Index Lucene trên đĩa cần đưa vào **chiến lược sao lưu** + dung lượng; container cần volume bền.
- Dữ liệu ghi **ngoài JPA** (seeder/SQL trực tiếp) sẽ **lệch index** → cần reindex.
- Quy mô hiện tại nhỏ → **không vội Elasticsearch** (tránh chi phí vận hành thừa). Quyết định Giai đoạn 2 theo ngưỡng dữ liệu/độ trễ thực đo.

## 9. Bước tiếp theo đề xuất

Chuyển sang **Product Brief (CB)** cho hạng mục "Tìm kiếm toàn văn GĐ1 — Hibernate Search" để dev triển khai, hoặc bàn giao trực tiếp cho pha implement.

## Nguồn tham khảo

- MariaDB FULLTEXT / ngram: https://mariadb.com/docs/server/ha-and-performance/optimization-and-tuning/optimization-and-indexes/full-text-indexes/full-text-index-overview ; https://dev.mysql.com/doc/refman/8.0/en/fulltext-search-ngram.html
- Hibernate Search (Lucene/Elasticsearch): https://hibernate.org/search/documentation/getting-started/ ; https://reflectoring.io/hibernate-search/ ; https://docs.jboss.org/hibernate/stable/search/getting-started/orm/en-US/html_single/
- Elasticsearch tiếng Việt: https://github.com/duydo/elasticsearch-analysis-vietnamese ; https://www.elastic.co/guide/en/elasticsearch/plugins/current/analysis.html
- PostgreSQL FTS tiếng Việt: https://www.postgresql.org/docs/current/unaccent.html ; https://blog.tuando.me/vietnamese-full-text-search-on-postgresql
