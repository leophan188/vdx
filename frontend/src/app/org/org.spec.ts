import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Org } from './org';
import { OrgService, OrgUnit } from '../core/org.service';

const units: OrgUnit[] = [
  { id: 't', name: 'Tập đoàn', parentId: null },
  { id: 'b', name: 'Ban A', parentId: 't' },
  { id: 'v', name: 'Vụ A1', parentId: 'b' }
];

describe('Org', () => {
  function setup(svc: Partial<OrgService>) {
    TestBed.configureTestingModule({
      imports: [Org],
      providers: [{ provide: OrgService, useValue: svc }, provideRouter([])]
    });
    const fixture = TestBed.createComponent(Org);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('chọn đơn vị → chi tiết cha/con đúng', () => {
    const { cmp, fixture } = setup({ all: () => of(units) } as Partial<OrgService>);
    fixture.detectChanges();
    cmp.selectedUnitId.set('b');
    expect(cmp.selectedUnit()?.name).toBe('Ban A');
    expect(cmp.parentName(cmp.selectedUnit()!)).toBe('Tập đoàn');
    expect(cmp.childCount('b')).toBe(1); // Vụ A1
    expect(cmp.childCount('t')).toBe(1); // Ban A
  });

  it('tạo đơn vị gọi service rồi reload', () => {
    let createdWith: { name: string; parentId: string | null } | null = null;
    const svc = {
      all: () => of(units),
      create: (name: string, parentId: string | null) => {
        createdWith = { name, parentId };
        return of({ id: 'x', name, parentId });
      }
    } as unknown as Partial<OrgService>;
    const { cmp } = setup(svc);
    cmp.newName = 'Vụ Mới';
    cmp.newParentId = 'b';
    cmp.create();
    expect(createdWith).toEqual({ name: 'Vụ Mới', parentId: 'b' });
  });
});
