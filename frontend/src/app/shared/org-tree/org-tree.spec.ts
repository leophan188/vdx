import { TestBed } from '@angular/core/testing';
import { OrgTree } from './org-tree';
import { OrgUnit } from '../../core/org.service';

const units: OrgUnit[] = [
  { id: 'kkd', name: 'KKD', parentId: null },
  { id: 'pdx', name: 'PDX', parentId: 'kkd' },
  { id: 'pdx1', name: 'PDX.1', parentId: 'pdx' },
  { id: 'sdx', name: 'SDX', parentId: 'kkd' }
];

describe('OrgTree', () => {
  function setup() {
    TestBed.configureTestingModule({ imports: [OrgTree] });
    const fixture = TestBed.createComponent(OrgTree);
    fixture.componentRef.setInput('units', units);
    return fixture.componentInstance;
  }

  it('dựng cây DFS với cấp thụt lề đúng', () => {
    const cmp = setup();
    expect(cmp.nodes().map((n) => n.name)).toEqual(['KKD', 'PDX', 'PDX.1', 'SDX']);
    expect(cmp.nodes().map((n) => n.level)).toEqual([0, 1, 2, 1]);
  });

  it('gập node ẩn con cháu; chọn node phát selectedId', () => {
    const cmp = setup();
    expect(cmp.visible().length).toBe(4);
    cmp.toggle('pdx');               // gập PDX → ẩn PDX.1
    expect(cmp.visible().map((n) => n.name)).toEqual(['KKD', 'PDX', 'SDX']);
    cmp.select('sdx');
    expect(cmp.selectedId()).toBe('sdx');
    cmp.expandAll();
    expect(cmp.visible().length).toBe(4);
  });

  it('hasChildren đúng', () => {
    const cmp = setup();
    expect(cmp.hasChildren('kkd')).toBe(true);
    expect(cmp.hasChildren('pdx1')).toBe(false);
  });
});
