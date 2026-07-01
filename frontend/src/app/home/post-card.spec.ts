import { TestBed } from '@angular/core/testing';
import { PostCard } from './post-card';
import { PostService, PostView, MediaView } from '../core/post.service';
import { ToastService } from '../shared/toast/toast.service';

function media(n: number): MediaView[] {
  return Array.from({ length: n }, (_, i): MediaView => ({
    id: 'm' + i, kind: 'IMAGE', name: 'img' + i, contentType: 'image/png', size: 1, url: '/x/' + i
  }));
}
function post(n: number): PostView {
  return {
    id: 'p1', authorId: 'u1', authorName: 'Người A', body: 'nội dung', scope: 'ALL', orgUnitId: null, topic: null,
    category: 'ANNOUNCEMENT', categoryLabel: 'Thông báo', subjectUserId: null,
    pinned: false, hidden: false, createdAt: '2026-06-27T00:00:00Z', likeCount: 0, liked: false,
    commentCount: 0, media: media(n), comments: [], mentions: []
  };
}

describe('PostCard — lưới ảnh + lightbox', () => {
  function setup(n: number) {
    TestBed.configureTestingModule({
      imports: [PostCard],
      providers: [
        { provide: PostService, useValue: {} },
        { provide: ToastService, useValue: {} }
      ]
    });
    const fixture = TestBed.createComponent(PostCard);
    fixture.componentRef.setInput('post', post(n));
    fixture.componentRef.setInput('isAdmin', false);
    return fixture.componentInstance;
  }

  it('collage kiểu FB: render tối đa 5 ô và đếm số ảnh dư (+N)', () => {
    const cmp = setup(8);
    expect(cmp.tiles().length).toBe(5);
    expect(cmp.tileCount()).toBe(5);
    expect(cmp.moreCount()).toBe(3);
  });

  it('không có ô dư khi số ảnh ≤ 5', () => {
    const cmp = setup(4);
    expect(cmp.tiles().length).toBe(4);
    expect(cmp.tileCount()).toBe(4);
    expect(cmp.moreCount()).toBe(0);
  });

  it('lightbox mở/đóng và chuyển ảnh vòng tròn', () => {
    const cmp = setup(3);
    expect(cmp.lightboxItem()).toBeNull();
    cmp.openLightbox(2);
    expect(cmp.lightboxItem()?.id).toBe('m2');
    cmp.nextMedia(); // 2 -> 0 (quay vòng)
    expect(cmp.lightboxItem()?.id).toBe('m0');
    cmp.prevMedia(); // 0 -> 2 (quay vòng ngược)
    expect(cmp.lightboxItem()?.id).toBe('m2');
    cmp.closeLightbox();
    expect(cmp.lightboxItem()).toBeNull();
  });
});

describe('PostCard — bình luận nhiều cấp + @mention', () => {
  function cmt(id: string, parentId: string | null, body = 'x'): any {
    return { id, authorId: 'u1', authorName: 'A', body, edited: false, mine: false, editable: false,
      createdAt: '2026-06-27T00:00:00Z', parentId, mentions: [] };
  }
  function setupWith(comments: any[]) {
    TestBed.configureTestingModule({
      imports: [PostCard],
      providers: [{ provide: PostService, useValue: {} }, { provide: ToastService, useValue: {} }]
    });
    const fixture = TestBed.createComponent(PostCard);
    const p = post(0);
    (p as any).comments = comments;
    fixture.componentRef.setInput('post', p);
    fixture.componentRef.setInput('isAdmin', false);
    return fixture.componentInstance;
  }

  it('dựng cây từ danh sách phẳng theo parentId, có độ sâu', () => {
    const cmp = setupWith([cmt('c1', null), cmt('c2', 'c1'), cmt('c3', 'c2'), cmt('c4', null)]);
    const tree = cmp.commentTree();
    expect(tree.length).toBe(2);                 // 2 gốc
    expect(tree[0].children[0].id).toBe('c2');   // c2 con c1
    expect(tree[0].children[0].children[0].id).toBe('c3'); // c3 con c2
    expect(tree[0].children[0].depth).toBe(1);
    expect(tree[0].children[0].children[0].depth).toBe(2);
  });

  it('tô sáng @Tên khớp mentions thành span.ochome-mention', () => {
    const cmp = setupWith([]);
    const html = cmp.renderBody('Chào @Lê Quang Minh nhé', [{ userId: 'u1', name: 'Lê Quang Minh' }] as any);
    expect(String((html as any).changingThisBreaksApplicationSecurity ?? html))
      .toContain('ochome-mention');
  });
});
