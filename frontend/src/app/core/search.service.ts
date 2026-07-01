import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/** Một dòng kết quả tìm kiếm (nhân sự / dự án / tài khoản). */
export interface SearchItem {
  id: string;
  code: string | null;
  name: string;
  sub: string | null;
}

/** Kết quả tìm kiếm toàn cục, nhóm theo nguồn. */
export interface SearchResponse {
  employees: SearchItem[];
  projects: SearchItem[];
  accounts: SearchItem[];
  posts: SearchItem[];
}

/**
 * Tìm kiếm toàn cục (quick-jump topbar) — gọi /api/v1/search?q=...
 * Phân quyền theo nhóm xử lý ở backend (theo authorities). Dùng phiên (cookie) → withCredentials.
 */
@Injectable({ providedIn: 'root' })
export class SearchService {
  private http = inject(HttpClient);
  private readonly base = '/api/v1/search';

  search(q: string): Observable<SearchResponse> {
    const params = new HttpParams().set('q', q ?? '');
    return this.http.get<SearchResponse>(this.base, { params, withCredentials: true });
  }
}
