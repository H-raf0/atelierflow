import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import { cancelWorkOrder } from '../../api/fn/work-orders/cancel-work-order';
import { completeWorkOrder } from '../../api/fn/work-orders/complete-work-order';
import { planWorkOrder } from '../../api/fn/work-orders/plan-work-order';
import { startWorkOrder } from '../../api/fn/work-orders/start-work-order';
import { unplanWorkOrder } from '../../api/fn/work-orders/unplan-work-order';
import { NotifyService } from '../../core/notify.service';
import { fakeApi, workOrderDetail } from '../../../testing/fakes';
import { CompleteDialog } from './dialogs/complete-dialog';
import { PlanDialog } from './dialogs/plan-dialog';
import { WorkOrderActions } from './work-order-actions.service';

describe('WorkOrderActions', () => {
  let actions: WorkOrderActions;
  let api: ReturnType<typeof fakeApi>;
  const dialog = { open: vi.fn() };
  const notify = { success: vi.fn(), error: vi.fn() };
  const workOrder = { id: 5, reference: 'OT-00005' };

  beforeEach(() => {
    vi.clearAllMocks();
    api = fakeApi([
      [planWorkOrder, workOrderDetail({ reference: 'OT-00005', status: 'PLANNED' })],
      [unplanWorkOrder, workOrderDetail({ reference: 'OT-00005' })],
      [startWorkOrder, workOrderDetail({ reference: 'OT-00005', status: 'IN_PROGRESS' })],
      [completeWorkOrder, workOrderDetail({ reference: 'OT-00005', status: 'DONE' })],
      [cancelWorkOrder, workOrderDetail({ reference: 'OT-00005', status: 'CANCELLED' })],
    ]);
    TestBed.configureTestingModule({
      providers: [api.provider, { provide: MatDialog, useValue: dialog }, { provide: NotifyService, useValue: notify }],
    });
    actions = TestBed.inject(WorkOrderActions);
  });

  const dialogAnswers = (result: unknown) => dialog.open.mockReturnValue({ afterClosed: () => of(result) });

  it('asks for the technician and date before planning', async () => {
    dialogAnswers({ assigneeId: 3, scheduledFor: '2026-11-20' });

    const updated = await actions.run('plan', workOrder);

    expect(dialog.open).toHaveBeenCalledWith(PlanDialog, { data: 'OT-00005' });
    expect(api.callsOf(planWorkOrder)).toEqual([{ id: 5, body: { assigneeId: 3, scheduledFor: '2026-11-20' } }]);
    expect(updated?.status).toBe('PLANNED');
    expect(notify.success).toHaveBeenCalledWith('OT-00005 : planifié');
  });

  it('does nothing when the dialog is dismissed', async () => {
    dialogAnswers(undefined);

    expect(await actions.run('complete', workOrder)).toBeNull();
    expect(dialog.open).toHaveBeenCalledWith(CompleteDialog, { data: 'OT-00005' });
    expect(api.invoke).not.toHaveBeenCalled();
  });

  it('completes and cancels with the dialog values', async () => {
    dialogAnswers({ timeSpentMinutes: 30, resolution: 'Réparé' });
    await actions.run('complete', workOrder);
    dialogAnswers({ reason: 'Doublon' });
    await actions.run('cancel', workOrder);

    expect(api.callsOf(completeWorkOrder)).toEqual([{ id: 5, body: { timeSpentMinutes: 30, resolution: 'Réparé' } }]);
    expect(api.callsOf(cancelWorkOrder)).toEqual([{ id: 5, body: { reason: 'Doublon' } }]);
  });

  it('starts and unplans without asking anything', async () => {
    await actions.run('start', workOrder);
    await actions.run('unplan', workOrder);

    expect(dialog.open).not.toHaveBeenCalled();
    expect(api.callsOf(startWorkOrder)).toEqual([{ id: 5 }]);
    expect(api.callsOf(unplanWorkOrder)).toEqual([{ id: 5 }]);
  });

  it('reports a refusal of the API and returns null', async () => {
    const refusal = new HttpErrorResponse({ status: 403, error: { code: 'NOT_ASSIGNEE' } });
    api.respond(startWorkOrder, () => Promise.reject(refusal));

    expect(await actions.run('start', workOrder)).toBeNull();
    expect(notify.error).toHaveBeenCalledWith(refusal);
  });
});
