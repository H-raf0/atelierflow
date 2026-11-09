import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, linkedSignal, resource } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Api } from '../../api';
import { getBoard } from '../../api/fn/work-orders/get-board';
import { WorkOrderSummary } from '../../api/models/work-order-summary';
import { AuthService } from '../../core/auth/auth.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { PRIORITY_LABELS, STATUS_LABELS, TYPE_LABELS } from '../../shared/labels';
import { BOARD_COLUMNS, BoardColumn, groupByColumn, moveFor, upsert } from './board-moves';
import { CreateWorkOrderDialog } from './dialogs/create-work-order-dialog';
import { WorkOrderActions } from './work-order-actions.service';

/**
 * Kanban board of the work orders. Moving a card calls the matching life cycle action; columns only
 * accept legal moves (from `allowedTransitions`). Changes made by other users arrive over WebSocket.
 */
@Component({
  selector: 'app-board-page',
  imports: [
    CdkDropListGroup, CdkDropList, CdkDrag, DatePipe, RouterLink, MatButtonModule, MatIconModule,
    MatProgressBarModule, MatTooltipModule,
  ],
  templateUrl: './board-page.html',
  styleUrl: './board-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BoardPage {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly actions = inject(WorkOrderActions);
  protected readonly auth = inject(AuthService);

  protected readonly columns = BOARD_COLUMNS;
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly typeLabels = TYPE_LABELS;

  private readonly board = resource({ loader: () => this.api.invoke(getBoard) });
  /** Local copy of the cards: reset by every (re)load, then patched by actions and push events. */
  private readonly cards = linkedSignal<WorkOrderSummary[]>(() => this.board.value() ?? []);
  protected readonly byColumn = computed(() => groupByColumn(this.cards()));
  protected readonly loading = this.board.isLoading;
  protected readonly error = this.board.error;

  constructor() {
    inject(RealtimeService)
      .workOrders$.pipe(takeUntilDestroyed())
      .subscribe((card) => this.cards.update((cards) => upsert(cards, card)));
  }

  /** CDK predicate: a column lights up as a drop target only if the move is legal. */
  protected readonly canDrop = (drag: CdkDrag<WorkOrderSummary>, drop: CdkDropList<BoardColumn>): boolean =>
    moveFor(drag.data, drop.data) !== null;

  protected async onDrop(event: CdkDragDrop<BoardColumn, BoardColumn, WorkOrderSummary>): Promise<void> {
    const card = event.item.data;
    const move = moveFor(card, event.container.data);
    if (!move) {
      return;
    }
    const updated = await this.actions.run(move, card);
    if (updated) {
      this.cards.update((cards) => upsert(cards, updated));
    }
  }

  protected newWorkOrder(): void {
    this.dialog
      .open(CreateWorkOrderDialog)
      .afterClosed()
      .subscribe((created?: WorkOrderSummary) => {
        if (created) {
          this.cards.update((cards) => upsert(cards, created));
        }
      });
  }

  protected reload(): void {
    this.board.reload();
  }
}
