import { TestBed } from '@angular/core/testing';
import { DataGrid, GridColumn } from './data-grid';

const cols: GridColumn[] = [
  { key: 'name', header: 'Tên', sortable: true },
  { key: 'age', header: 'Tuổi', sortable: true }
];
const rows = [
  { name: 'Bình', age: 30 },
  { name: 'An', age: 25 },
  { name: 'Cường', age: 40 },
  { name: 'Dũng', age: 35 }
];

function makeGrid(pageSize = 2) {
  const fixture = TestBed.createComponent(DataGrid);
  fixture.componentRef.setInput('columns', cols);
  fixture.componentRef.setInput('rows', rows);
  fixture.componentRef.setInput('pageSize', pageSize);
  fixture.detectChanges();
  return fixture.componentInstance;
}

describe('DataGrid', () => {
  it('phân trang theo pageSize', () => {
    const g = makeGrid(2);
    expect(g.total()).toBe(4);
    expect(g.pageCount()).toBe(2);
    expect(g.pageRows().length).toBe(2);
    g.next();
    expect(g.page()).toBe(2);
    expect(g.rangeFrom()).toBe(3);
    expect(g.rangeTo()).toBe(4);
  });

  it('tìm kiếm lọc theo mọi cột và reset trang', () => {
    const g = makeGrid(2);
    g.next();
    g.onSearch('An');
    expect(g.page()).toBe(1);
    expect(g.total()).toBe(1);
    expect((g.pageRows()[0] as { name: string }).name).toBe('An');
  });

  it('sort cột tăng/giảm/tắt', () => {
    const g = makeGrid(10);
    const ageCol = cols[1];
    g.toggleSort(ageCol); // asc
    expect((g.pageRows()[0] as { age: number }).age).toBe(25);
    g.toggleSort(ageCol); // desc
    expect((g.pageRows()[0] as { age: number }).age).toBe(40);
    g.toggleSort(ageCol); // off -> giữ thứ tự gốc
    expect(g.sortKey()).toBeNull();
    expect((g.pageRows()[0] as { name: string }).name).toBe('Bình');
  });
});
