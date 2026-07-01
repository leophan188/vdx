import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { Audit } from './audit';
import { AuditService, AuditEvent } from '../core/audit.service';

const events: AuditEvent[] = [
  {
    id: 'e1',
    action: 'TASK_ASSIGNED',
    objectType: 'TaskAssignment',
    objectId: 'a1',
    actor: 'admin',
    detail: 'task=t1',
    createdAt: '2026-06-24T10:00:00Z'
  }
];

describe('Audit', () => {
  function setup(svc: Partial<AuditService>) {
    TestBed.configureTestingModule({
      imports: [Audit],
      providers: [{ provide: AuditService, useValue: svc }]
    });
    const fixture = TestBed.createComponent(Audit);
    return { fixture, cmp: fixture.componentInstance };
  }

  it('tra vết theo đối tượng', () => {
    const { cmp } = setup({ trail: () => of(events) } as Partial<AuditService>);
    cmp.mode = 'object';
    cmp.objectType = 'TaskAssignment';
    cmp.objectId = 'a1';
    cmp.search();
    expect(cmp.events().length).toBe(1);
    expect(cmp.searched()).toBe(true);
  });

  it('tra vết theo nhiệm vụ', () => {
    let askedTask: string | null = null;
    const svc = {
      trailForTask: (taskId: string) => {
        askedTask = taskId;
        return of(events);
      }
    } as unknown as Partial<AuditService>;
    const { cmp } = setup(svc);
    cmp.mode = 'task';
    cmp.taskId = 't1';
    cmp.search();
    expect(askedTask).toBe('t1');
    expect(cmp.events().length).toBe(1);
  });
});
