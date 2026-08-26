import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { Employees } from './employees';
import { EmployeeService, Employee, EmployeeFilters } from '../core/employee.service';
import { ToastService } from '../shared/toast/toast.service';

function emp(empCode: string, fullName: string, status: string, deptCode: string, level: string): Employee {
  return {
    id: 'id-' + empCode, empCode, status, fullName, jobPosition: null, title: null,
    deptCode, unit: 'KKD', joinDate: null, birthDate: null, leaveDate: null, phone: null, contractType: null,
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

  it('thâm niên tính theo lịch, người đã nghỉ dừng ở ngày nghỉ', () => {
    const { cmp } = setup();
    const left = { ...emp('9001', 'Đã nghỉ', 'Đã nghỉ việc', 'PDX', 'Senior'),
      joinDate: '15/01/2024', leaveDate: '18/02/2025' };
    expect(cmp.seniorityText(left)).toBe('1 năm 1 tháng 3 ngày');

    // Không có ngày nghỉ → lùi về lần cập nhật hồ sơ gần nhất.
    const noLeave = { ...emp('9002', 'Nghỉ cũ', 'Đã nghỉ việc', 'PDX', 'Senior'),
      joinDate: '01/03/2023', updatedAt: '2024-03-01T00:00:00Z' };
    expect(cmp.seniorityText(noLeave)).toBe('1 năm');

    // Phần bằng 0 bị bỏ hẳn, không ghi "0 tháng".
    const exact = { ...emp('9003', 'Tròn năm', 'Đang làm việc', 'PDX', 'Senior'), joinDate: '29/02/2024' };
    expect(cmp.seniorityText(exact)).not.toContain('0 tháng');

    // Thiếu ngày vào thì không bịa số.
    expect(cmp.seniorityText(emp('9004', 'Thiếu ngày', 'Đang làm việc', 'PDX', 'Senior'))).toBe('—');
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
