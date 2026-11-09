import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { searchEquipment } from '../../../api/fn/equipment/search-equipment';
import { listUsers } from '../../../api/fn/users/list-users';
import { createWorkOrder } from '../../../api/fn/work-orders/create-work-order';
import { Role } from '../../../core/auth/auth.service';
import { NotifyService } from '../../../core/notify.service';
import { equipment, fakeApi, fakeAuth, workOrderDetail } from '../../../../testing/fakes';
import { CancelDialog } from './cancel-dialog';
import { CompleteDialog } from './complete-dialog';
import { CreateWorkOrderDialog } from './create-work-order-dialog';
import { PlanDialog } from './plan-dialog';

/** Form dialogs: they validate input and close with a typed request body (or create the work order). */
describe('work order dialogs', () => {
  const dialogRef = { close: vi.fn() };
  let api: ReturnType<typeof fakeApi>;

  async function create<T>(component: Type<T>, role: Role = 'MANAGER') {
    dialogRef.close.mockReset();
    api = fakeApi([
      [listUsers, [{ id: 3, username: 'tech', fullName: 'Karim Haddad', role: 'TECHNICIAN' }]],
      [searchEquipment, { content: [equipment()], page: 0, size: 100, totalElements: 1, totalPages: 1 }],
      [createWorkOrder, workOrderDetail({ reference: 'OT-00009' })],
    ]);
    await TestBed.configureTestingModule({
      imports: [component],
      providers: [
        api.provider, fakeAuth(role).provider, provideNativeDateAdapter(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: 'OT-00007' },
        { provide: NotifyService, useValue: { success: vi.fn(), error: vi.fn() } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(component);
    await fixture.whenStable();
    return { fixture, component: fixture.componentInstance as Record<string, any> };
  }

  it('complete dialog requires a positive time and a resolution', async () => {
    const { fixture, component } = await create(CompleteDialog);
    expect(fixture.nativeElement.textContent).toContain('Terminer OT-00007');

    component['form'].setValue({ timeSpentMinutes: 0, resolution: '' });
    component['submit']();
    expect(dialogRef.close).not.toHaveBeenCalled();

    component['form'].setValue({ timeSpentMinutes: 45, resolution: 'Joint changé' });
    component['submit']();
    expect(dialogRef.close).toHaveBeenCalledWith({ timeSpentMinutes: 45, resolution: 'Joint changé' });
  });

  it('cancel dialog requires a reason', async () => {
    const { component } = await create(CancelDialog);

    component['submit']();
    expect(dialogRef.close).not.toHaveBeenCalled();

    component['form'].setValue({ reason: 'Doublon' });
    component['submit']();
    expect(dialogRef.close).toHaveBeenCalledWith({ reason: 'Doublon' });
  });

  it('plan dialog lists the technicians and returns an ISO date', async () => {
    const { fixture, component } = await create(PlanDialog);
    expect(api.callsOf(listUsers)).toEqual([{ role: 'TECHNICIAN' }]);

    component['submit']();
    expect(dialogRef.close).not.toHaveBeenCalled();

    component['form'].setValue({ assigneeId: 3, scheduledFor: new Date(2026, 10, 20) });
    component['submit']();
    expect(dialogRef.close).toHaveBeenCalledWith({ assigneeId: 3, scheduledFor: '2026-11-20' });
    expect(fixture.nativeElement.textContent).toContain('Planifier OT-00007');
  });

  it('create dialog sends the breakdown and closes with the created work order', async () => {
    const { component } = await create(CreateWorkOrderDialog, 'TECHNICIAN');
    expect(component['canManage']).toBe(false);

    component['form'].setValue({ type: 'CORRECTIVE', equipmentId: 10, title: 'Bruit anormal', description: '', priority: null });
    await component['submit']();

    expect(api.callsOf(createWorkOrder)).toEqual([{
      body: { type: 'CORRECTIVE', equipmentId: 10, title: 'Bruit anormal', description: undefined, priority: undefined },
    }]);
    expect(dialogRef.close).toHaveBeenCalledWith(expect.objectContaining({ reference: 'OT-00009' }));
  });

  it('create dialog refuses an incomplete form', async () => {
    const { component } = await create(CreateWorkOrderDialog);

    await component['submit']();

    expect(api.callsOf(createWorkOrder)).toEqual([]);
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
