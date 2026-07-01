import {
  Component, ElementRef, HostListener, OnInit, OnDestroy, computed, inject, signal, viewChild
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, switchMap, map } from 'rxjs/operators';
import { EmployeeChip } from '../employee-chip/employee-chip';
import { SearchService, SearchResponse, SearchItem } from '../../core/search.service';

/** Độ trễ debounce gõ phím (ms) + số ký tự tối thiểu trước khi gọi API. */
const DEBOUNCE_MS = 250;
const MIN_LEN = 2;

/**
 * Tìm kiếm toàn cục (quick-jump topbar): ô input + dropdown kết quả nhóm theo
 * Nhân sự / Dự án / Tài khoản. Debounce 250ms, tối thiểu 2 ký tự.
 * Điều hướng: nhân sự → /employees, dự án → /projects/{id}, tài khoản → /accounts.
 * Phím ⌘K/Ctrl+K để focus; Esc + click ngoài để đóng.
 */
@Component({
  selector: 'app-global-search',
  imports: [EmployeeChip],
  templateUrl: './global-search.html'
})
export class GlobalSearch implements OnInit, OnDestroy {
  private svc = inject(SearchService);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);

  private readonly inputRef = viewChild<ElementRef<HTMLInputElement>>('searchInput');

  /** Nội dung ô gõ (để hiển thị nhãn "đang gõ"/clear). */
  readonly query = signal('');
  readonly open = signal(false);
  readonly loading = signal(false);
  readonly results = signal<SearchResponse>({ employees: [], projects: [], accounts: [], posts: [] });

  /** Tổng số kết quả mọi nhóm. */
  readonly total = computed(() => {
    const r = this.results();
    return r.employees.length + r.projects.length + r.accounts.length + r.posts.length;
  });

  /** Truy vấn đủ dài để tìm (đồng bộ điều kiện với backend). */
  readonly hasQuery = computed(() => this.query().trim().length >= MIN_LEN);

  /** Không có kết quả: đã gõ đủ, không còn tải, mà tổng = 0. */
  readonly empty = computed(() => this.hasQuery() && !this.loading() && this.total() === 0);

  private readonly term$ = new Subject<string>();
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.term$.pipe(
      debounceTime(DEBOUNCE_MS),
      map(s => s.trim()),
      distinctUntilChanged(),
      switchMap((q) => {
        if (q.length < MIN_LEN) {
          this.loading.set(false);
          this.results.set({ employees: [], projects: [], accounts: [], posts: [] });
          return [];
        }
        this.loading.set(true);
        return this.svc.search(q);
      })
    ).subscribe({
      next: (r: SearchResponse) => { this.results.set(r); this.loading.set(false); },
      error: () => { this.results.set({ employees: [], projects: [], accounts: [], posts: [] }); this.loading.set(false); }
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onInput(value: string): void {
    this.query.set(value);
    this.open.set(true);
    this.term$.next(value);
  }

  onFocus(): void {
    if (this.hasQuery() || this.total() > 0) this.open.set(true);
  }

  /** Điều hướng theo loại + reset ô. */
  goEmployee(_: SearchItem): void { this.navigate('/employees'); }
  goProject(it: SearchItem): void { this.navigate('/projects/' + it.id); }
  goAccount(_: SearchItem): void { this.navigate('/accounts'); }
  goPost(_: SearchItem): void { this.navigate('/home'); }

  private navigate(url: string): void {
    this.router.navigateByUrl(url);
    this.reset();
  }

  private reset(): void {
    this.query.set('');
    this.results.set({ employees: [], projects: [], accounts: [], posts: [] });
    this.loading.set(false);
    this.open.set(false);
  }

  /** Esc trên ô: đóng dropdown (giữ nội dung). */
  onEsc(): void { this.open.set(false); }

  /** Click ra ngoài component → đóng. */
  @HostListener('document:click', ['$event'])
  onDocClick(ev: MouseEvent): void {
    if (!this.host.nativeElement.contains(ev.target as Node)) this.open.set(false);
  }

  /** Phím tắt ⌘K / Ctrl+K → focus ô tìm kiếm. */
  @HostListener('document:keydown', ['$event'])
  onKeydown(ev: KeyboardEvent): void {
    if ((ev.metaKey || ev.ctrlKey) && (ev.key === 'k' || ev.key === 'K')) {
      ev.preventDefault();
      this.open.set(true);
      this.inputRef()?.nativeElement.focus();
    }
  }
}
