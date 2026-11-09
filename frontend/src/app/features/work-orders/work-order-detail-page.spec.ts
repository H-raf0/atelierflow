import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { addComment } from '../../api/fn/work-orders/add-comment';
import { getWorkOrder } from '../../api/fn/work-orders/get-work-order';
import { getWorkOrderHistory } from '../../api/fn/work-orders/get-work-order-history';
import { listComments } from '../../api/fn/work-orders/list-comments';
import { WorkOrderDetail } from '../../api/models/work-order-detail';
import { Role } from '../../core/auth/auth.service';
import { NotifyService } from '../../core/notify.service';
import { fakeApi, fakeAuth, fakeRealtime, workOrderDetail } from '../../../testing/fakes';
import { WorkOrderActions } from './work-order-actions.service';
import { WorkOrderDetailPage } from './work-order-detail-page';

describe('WorkOrderDetailPage', () => {
  let fixture: ComponentFixture<WorkOrderDetailPage>;
  let element: HTMLElement;
  let api: ReturnType<typeof fakeApi>;
  let realtime: ReturnType<typeof fakeRealtime>;
  const actions = { run: vi.fn() };

  async function open(workOrder: WorkOrderDetail, role: Role = 'MANAGER', userId = 1): Promise<void> {
    api = fakeApi([
      [getWorkOrder, workOrder],
      [getWorkOrderHistory, [
        { revision: 1, timestamp: '2026-11-02T08:00:00Z', username: 'chef', action: 'CREATED',
          changes: [{ field: 'status', from: null, to: 'OPEN' }] },
        { revision: 2, timestamp: '2026-11-02T09:00:00Z', username: 'chef', action: 'UPDATED',
          changes: [{ field: 'status', from: 'OPEN', to: 'PLANNED' }, { field: 'assignee', from: null, to: 'Karim Haddad' }] },
      ]],
      [listComments, [{ id: 1, author: { id: 2, username: 'tech', fullName: 'Karim Haddad', role: 'TECHNICIAN' },
        body: 'Pièce commandée', createdAt: '2026-11-02T10:00:00Z' }]],
      [addComment, {}],
    ]);
    realtime = fakeRealtime();
    await TestBed.configureTestingModule({
      imports: [WorkOrderDetailPage],
      providers: [
        provideRouter([]), api.provider, fakeAuth(role, userId).provider, realtime.provider,
        { provide: WorkOrderActions, useValue: actions },
        { provide: NotifyService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(WorkOrderDetailPage);
    fixture.componentRef.setInput('id', workOrder.id);
    element = fixture.nativeElement;
    await fixture.whenStable();
  }

  const actionButtons = () =>
    Array.from(element.querySelectorAll('[data-testid^="action-"]')).map((b) => b.getAttribute('data-testid'));

  beforeEach(() => actions.run.mockReset());

  it('shows the work order, its history in French and its comments', async () => {
    await open(workOrderDetail({ reference: 'OT-00042', title: 'Fuite vérin' }));

    expect(element.querySelector('h1')?.textContent).toContain('OT-00042');
    expect(element.textContent).toContain('PRS-001 — Presse 200 t');
    const history = element.querySelector('[data-testid="history"]')!.textContent!.replace(/\s+/g, ' ');
    expect(history).toContain('Statut : Ouvert → Planifié');
    expect(history).toContain('Technicien : — → Karim Haddad');
  });

  it('offers planning and cancellation to a manager on an open work order', async () => {
    await open(workOrderDetail({ allowedTransitions: ['PLANNED', 'CANCELLED'] }));

    expect(actionButtons()).toEqual(['action-plan', 'action-cancel']);
  });

  it('lets the assigned technician start, but nothing else', async () => {
    const assigned = workOrderDetail({
      status: 'PLANNED',
      allowedTransitions: ['OPEN', 'IN_PROGRESS', 'CANCELLED'],
      assignee: { id: 2, username: 'tech', fullName: 'Karim Haddad', role: 'TECHNICIAN' },
    });
    await open(assigned, 'TECHNICIAN', 2);

    expect(actionButtons()).toEqual(['action-start']);
  });

  it('offers no action to another technician', async () => {
    const assigned = workOrderDetail({
      status: 'IN_PROGRESS',
      allowedTransitions: ['DONE'],
      assignee: { id: 2, username: 'tech', fullName: 'Karim Haddad', role: 'TECHNICIAN' },
    });
    await open(assigned, 'TECHNICIAN', 3);

    expect(actionButtons()).toEqual([]);
  });

  it('runs an action then reloads the work order', async () => {
    await open(workOrderDetail({ allowedTransitions: ['PLANNED', 'CANCELLED'] }));
    actions.run.mockResolvedValue(workOrderDetail({ status: 'PLANNED' }));

    element.querySelector<HTMLButtonElement>('[data-testid="action-plan"]')!.click();
    await fixture.whenStable();

    expect(actions.run).toHaveBeenCalledWith('plan', expect.objectContaining({ id: 1 }));
    expect(api.callsOf(getWorkOrder).length).toBeGreaterThan(1);
  });

  it('reloads when another user changes this work order', async () => {
    await open(workOrderDetail({ id: 1 }));
    const before = api.callsOf(getWorkOrder).length;

    realtime.workOrders.next({ ...workOrderDetail({ id: 99 }) });
    realtime.workOrders.next({ ...workOrderDetail({ id: 1, status: 'PLANNED' }) });
    await fixture.whenStable();

    expect(api.callsOf(getWorkOrder).length).toBe(before + 1);
  });

  it('posts a comment', async () => {
    await open(workOrderDetail({ id: 1 }));
    const component = fixture.componentInstance as unknown as {
      commentForm: { setValue(v: { body: string }): void };
      postComment(): Promise<void>;
    };

    component.commentForm.setValue({ body: 'Roulement à changer' });
    await component.postComment();

    expect(api.callsOf(addComment)).toEqual([{ id: 1, body: { body: 'Roulement à changer' } }]);
  });
});
