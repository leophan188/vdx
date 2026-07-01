import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Positions } from './positions';
import { PositionService, Position } from '../core/position.service';
import { OrgService, OrgUnit } from '../core/org.service';
import { AuthService, UserAccount } from '../core/auth.service';

const units: OrgUnit[] = [{ id: 'u1', name: 'Vụ A', parentId: null }];
const users: UserAccount[] = [{ id: 'usr1', username: 'a', fullName: 'Anh A', email: null, phone: null, status: 'ACTIVE', role: 'USER', roleCode: null }];
const positions: Position[] = [{ id: 'p1', title: 'Vụ trưởng', orgUnitId: 'u1', currentHolderUserId: null }];

describe('Positions', () => {
  function setup(pos: Partial<PositionService>) {
    TestBed.configureTestingModule({
      imports: [Positions],
      providers: [
        { provide: PositionService, useValue: { all: () => of(positions), ...pos } },
        { provide: OrgService, useValue: { all: () => of(units) } },
        { provide: AuthService, useValue: { listUsers: () => of(users) } },
        provideRouter([])
      ]
    });
    const fixture = TestBed.createComponent(Positions);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('tải đơn vị và người dùng khi khởi tạo', () => {
    const { cmp, fixture } = setup({ byOrgUnit: () => of(positions) } as Partial<PositionService>);
    fixture.detectChanges();
    expect(cmp.units().length).toBe(1);
    expect(cmp.users().length).toBe(1);
  });

  it('gán người giữ gọi service rồi reload', () => {
    let assignedWith: { id: string; userId: string } | null = null;
    const svc = {
      byOrgUnit: () => of(positions),
      assign: (id: string, userId: string) => {
        assignedWith = { id, userId };
        return of(void 0);
      }
    } as unknown as Partial<PositionService>;
    const { cmp, fixture } = setup(svc);
    fixture.detectChanges();
    cmp.selectedUnitId.set('u1');
    cmp.assign(positions[0], 'usr1');
    expect(assignedWith).toEqual({ id: 'p1', userId: 'usr1' });
  });

  it('userName trả "(trống)" khi null', () => {
    const { cmp } = setup({ byOrgUnit: () => of(positions) } as Partial<PositionService>);
    expect(cmp.userName(null)).toBe('(trống)');
  });
});
