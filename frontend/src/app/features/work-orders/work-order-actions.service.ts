import { ComponentType } from '@angular/cdk/portal';
import { inject, Injectable } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { Api } from '../../api';
import { cancelWorkOrder } from '../../api/fn/work-orders/cancel-work-order';
import { completeWorkOrder } from '../../api/fn/work-orders/complete-work-order';
import { planWorkOrder } from '../../api/fn/work-orders/plan-work-order';
import { startWorkOrder } from '../../api/fn/work-orders/start-work-order';
import { unplanWorkOrder } from '../../api/fn/work-orders/unplan-work-order';
import { CancelWorkOrderRequest } from '../../api/models/cancel-work-order-request';
import { CompleteWorkOrderRequest } from '../../api/models/complete-work-order-request';
import { PlanWorkOrderRequest } from '../../api/models/plan-work-order-request';
import { WorkOrderDetail } from '../../api/models/work-order-detail';
import { NotifyService } from '../../core/notify.service';
import { BoardMove } from './board-moves';
import { CancelDialog } from './dialogs/cancel-dialog';
import { CompleteDialog } from './dialogs/complete-dialog';
import { PlanDialog } from './dialogs/plan-dialog';

/**
 * Executes the life cycle actions of a work order, asking for the required information first
 * (technician and date to plan, time and resolution to complete, reason to cancel).
 * Shared by the kanban board and the detail page.
 *
 * @returns the updated work order, or null if the user dismissed the dialog or the API refused
 */
@Injectable({ providedIn: 'root' })
export class WorkOrderActions {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);

  async run(move: BoardMove | 'cancel', workOrder: { id: number; reference: string }): Promise<WorkOrderDetail | null> {
    try {
      const updated = await this.execute(move, workOrder.id, workOrder.reference);
      if (updated) {
        this.notify.success(`${updated.reference} : ${CONFIRMATIONS[move]}`);
      }
      return updated;
    } catch (e) {
      this.notify.error(e);
      return null;
    }
  }

  private async execute(move: BoardMove | 'cancel', id: number, reference: string): Promise<WorkOrderDetail | null> {
    switch (move) {
      case 'plan': {
        const body = await this.ask<PlanWorkOrderRequest>(PlanDialog, reference);
        return body ? this.api.invoke(planWorkOrder, { id, body }) : null;
      }
      case 'complete': {
        const body = await this.ask<CompleteWorkOrderRequest>(CompleteDialog, reference);
        return body ? this.api.invoke(completeWorkOrder, { id, body }) : null;
      }
      case 'cancel': {
        const body = await this.ask<CancelWorkOrderRequest>(CancelDialog, reference);
        return body ? this.api.invoke(cancelWorkOrder, { id, body }) : null;
      }
      case 'start':
        return this.api.invoke(startWorkOrder, { id });
      case 'unplan':
        return this.api.invoke(unplanWorkOrder, { id });
    }
  }

  /** Opens a form dialog (given the work order reference) and resolves with its result, undefined if dismissed. */
  private ask<R>(component: ComponentType<unknown>, reference: string): Promise<R | undefined> {
    return firstValueFrom(this.dialog.open<unknown, string, R>(component, { data: reference }).afterClosed());
  }
}

const CONFIRMATIONS: Record<BoardMove | 'cancel', string> = {
  plan: 'planifié',
  unplan: 'remis à planifier',
  start: 'intervention démarrée',
  complete: 'terminé',
  cancel: 'annulé',
};
