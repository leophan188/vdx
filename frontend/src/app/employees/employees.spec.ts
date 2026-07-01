import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Employees } from './employees';
import { EmployeeService, Employee, EmployeeFilters } from '../core/employee.service';
import { ToastService } from '../shared/toast/toast.service';

function emp(empCode: string, fullName: string, status: string, deptCode: string, level: string): Employee {
  return {
    id: 'id-' + empCode, empCode, status, fullName, jobPosition: null, title: null,
    deptCode, unit: 'KKD', joinDate: null, birthDate: null, phone: null, contractType: null,
    bankAccount: null, bankName: null, level, userAccountId: null, external: false,
    orgUnitId: null, orgUnitName: null, positionId: null, positionTitle: null, roleNames: [],
    updatedAt: null, updatedBy: null, projects: [], totalEffort: 0
  };
}
const EMPS: Employee[] = [
  emp('0025', 'Lê Hữu Thanh', 'Đang làm việc', 'PDX', 'Expert'),
  emp('0191', 'Phạm Quang Long', 'Đang làm việc', 'PDX.2', 'Pre-senior'),
  emp('2402', 'Hoàng Lan Anh', 'Đã nghỉ việc', 'SDX', 'Pre-senior')
];

describe('Employees — thống kê & bộ lọc', () => {
  let lastFilters: EmployeeFilters | null;
  function setup() {
    lastFilters = null;
    TestBed.configureTestingModule({
      imports: [Employees],
      providers: [
        {
          provide: EmployeeService,
          useValue: {
            list: (f: EmployeeFilters) => { lastFilters = f; return of(EMPS); },
            logs: () => of([])
          }
        },
        { provide: ToastService, useValue: { info: () => {}, warning: () => {}, error: () => {}, success: () => {} } },
        provideRouter([])
      ]
    });
    const fixture = TestBed.createComponent(Employees);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('thống kê tách Đang làm việc / Đã nghỉ đúng', () => {
    const { cmp, fixture } = setup();
    fixture.detectChanges(); // ngOnInit -> reload -> rows
    expect(cmp.stats().total).toBe(3);
    expect(cmp.stats().active).toBe(2);
    expect(cmp.stats().inactive).toBe(1);
  });

  it('mặc định KHÔNG đồng bộ toàn phần (an toàn upload từng phần)', () => {
    const { cmp } = setup();
    expect(cmp.fullSync()).toBe(false);
  });

  it('reload truyền đúng tiêu chí lọc xuống service', () => {
    const { cmp, fixture } = setup();
    fixture.detectChanges();
    cmp.filterStatus = 'Đang làm việc';
    cmp.filterDept = 'PDX';
    cmp.reload();
    expect(lastFilters?.status).toBe('Đang làm việc');
    expect(lastFilters?.deptCode).toBe('PDX');
  });
});
