import { DatePipe, LowerCasePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, numberAttribute, resource, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTabsModule } from '@angular/material/tabs';
import { RouterLink } from '@angular/router';
import { filter } from 'rxjs';
import { Api } from '../../api';
import { addComment } from '../../api/fn/work-orders/add-comment';
import { getWorkOrder } from '../../api/fn/work-orders/get-work-order';
import { getWorkOrderHistory } from '../../api/fn/work-orders/get-work-order-history';
import { listComments } from '../../api/fn/work-orders/list-comments';
import { AuthService } from '../../core/auth/auth.service';
import { NotifyService } from '../../core/notify.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import {
  CRITICALITY_LABELS,
  HISTORY_FIELD_LABELS,
  historyValueLabel,
  PRIORITY_LABELS,
  STATUS_LABELS,
  TYPE_LABELS,
} from '../../shared/labels';
import { BoardMove } from './board-moves';
import { WorkOrderActions } from './work-order-actions.service';

interface ActionButton {
  move: BoardMove | 'cancel';
  label: string;
  icon: string;
}

/** Work order page: details, available actions, comments and the audit trail (who changed what, when). */
@Component({
  selector: 'app-work-order-detail-page',
  imports: [
    DatePipe, LowerCasePipe, RouterLink, ReactiveFormsModule, MatCardModule, MatButtonModule, MatIconModule, MatTabsModule,
    MatFormFieldModule, MatInputModule, MatProgressBarModule,
  ],
  templateUrl: './work-order-detail-page.html',
  styleUrl: './work-order-detail-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkOrderDetailPage {
  private readonly api = inject(Api);
  private readonly actions = inject(WorkOrderActions);
  private readonly notify = inject(NotifyService);
  protected readonly auth = inject(AuthService);

  /** Route parameter `:id`, bound by the router. */
  readonly id = input.required({ transform: numberAttribute });

  protected readonly labels = {
    status: STATUS_LABELS, priority: PRIORITY_LABELS, type: TYPE_LABELS, criticality: CRITICALITY_LABELS,
    field: HISTORY_FIELD_LABELS,
  };
  protected readonly historyValue = historyValueLabel;

  protected readonly workOrder = resource({
    params: () => ({ id: this.id() }),
    loader: ({ params }) => this.api.invoke(getWorkOrder, params),
  });
  protected readonly history = resource({
    params: () => ({ id: this.id() }),
    loader: ({ params }) => this.api.invoke(getWorkOrderHistory, params),
  });
  protected readonly comments = resource({
    params: () => ({ id: this.id() }),
    loader: ({ params }) => this.api.invoke(listComments, params),
  });

  /** Buttons for the transitions the server allows now and the user's role permits. */
  protected readonly availableActions = computed<ActionButton[]>(() => {
    const workOrder = this.workOrder.value();
    if (!workOrder) {
      return [];
    }
    const allowed = workOrder.allowedTransitions;
    const manager = this.auth.canManage();
    const mine = workOrder.assignee?.id === this.auth.user()?.id;
    const buttons: ActionButton[] = [];
    if (manager && allowed.includes('PLANNED')) buttons.push({ move: 'plan', label: 'Planifier', icon: 'event' });
    if (allowed.includes('IN_PROGRESS') && (manager || mine)) {
      buttons.push({ move: 'start', label: 'Démarrer', icon: 'play_arrow' });
    }
    if (allowed.includes('DONE') && (manager || mine)) buttons.push({ move: 'complete', label: 'Terminer', icon: 'task_alt' });
    if (manager && allowed.includes('OPEN')) buttons.push({ move: 'unplan', label: 'Désassigner', icon: 'undo' });
    if (manager && allowed.includes('CANCELLED')) buttons.push({ move: 'cancel', label: 'Annuler', icon: 'block' });
    return buttons;
  });

  protected readonly posting = signal(false);
  protected readonly commentForm = inject(FormBuilder).nonNullable.group({
    body: ['', [Validators.required, Validators.maxLength(2000)]],
  });

  constructor() {
    // someone else changed this work order: reload it so the page never shows stale data
    inject(RealtimeService)
      .workOrders$.pipe(
        filter((event) => event.id === this.id()),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.refresh());
  }

  protected async run(button: ActionButton): Promise<void> {
    const workOrder = this.workOrder.value();
    if (workOrder && (await this.actions.run(button.move, workOrder))) {
      this.refresh();
    }
  }

  protected async postComment(): Promise<void> {
    if (this.commentForm.invalid) {
      return;
    }
    this.posting.set(true);
    try {
      await this.api.invoke(addComment, { id: this.id(), body: this.commentForm.getRawValue() });
      this.commentForm.reset();
      this.comments.reload();
    } catch (e) {
      this.notify.error(e);
    } finally {
      this.posting.set(false);
    }
  }

  private refresh(): void {
    this.workOrder.reload();
    this.history.reload();
  }
}
