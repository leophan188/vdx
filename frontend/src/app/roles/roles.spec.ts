import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Roles } from './roles';
import { RoleService, Role } from '../core/role.service';
import { PositionService } from '../core/position.service';

const roles: Role[] = [{ code: 'ADMIN', name: 'Quản trị', permissions: ['ALL'] }];

describe('Roles', () => {
  function setup(roleSvc: Partial<RoleService>) {
    TestBed.configureTestingModule({
      imports: [Roles],
      providers: [
        { provide: RoleService, useValue: roleSvc },
        { provide: PositionService, useValue: { all: () => of([]) } },
        provideRouter([])
      ]
    });
    const fixture = TestBed.createComponent(Roles);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('tải vai trò khi khởi tạo', () => {
    const { cmp, fixture } = setup({ list: () => of(roles) } as Partial<RoleService>);
    fixture.detectChanges();
    expect(cmp.roles().length).toBe(1);
  });

  it('tạo vai trò tách quyền theo dấu phẩy', () => {
    let created: { code: string; name: string; perms: string[] } | null = null;
    const svc = {
      list: () => of(roles),
      create: (code: string, name: string, permissions: string[]) => {
        created = { code, name, perms: permissions };
        return of({ code, name, permissions });
      }
    } as unknown as Partial<RoleService>;
    const { cmp } = setup(svc);
    cmp.newCode = 'ORG_MANAGER';
    cmp.newName = 'Quản lý tổ chức';
    cmp.newPerms = 'ORG_ADMIN, POSITION_ADMIN ,  ';
    cmp.create();
    expect(created).toEqual({ code: 'ORG_MANAGER', name: 'Quản lý tổ chức', perms: ['ORG_ADMIN', 'POSITION_ADMIN'] });
  });
});
