import { WorkOrderSummary } from '../../api/models/work-order-summary';
import { Priority, WorkOrderStatus } from '../../shared/labels';

/** Columns of the kanban board (cancelled work orders are not displayed). */
export const BOARD_COLUMNS = ['OPEN', 'PLANNED', 'IN_PROGRESS', 'DONE'] as const;
export type BoardColumn = (typeof BOARD_COLUMNS)[number];

/** API action triggered by dropping a card into a column. */
export type BoardMove = 'plan' | 'unplan' | 'start' | 'complete';

const MOVE_TO: Record<BoardColumn, BoardMove> = {
  OPEN: 'unplan',
  PLANNED: 'plan',
  IN_PROGRESS: 'start',
  DONE: 'complete',
};

/**
 * The action a drop represents, or null if the move is illegal. The legal targets come from the
 * server (`allowedTransitions`), so the UI never duplicates the state machine rules.
 */
export function moveFor(card: WorkOrderSummary, target: BoardColumn): BoardMove | null {
  if (card.status === target || !card.allowedTransitions.includes(target)) {
    return null;
  }
  return MOVE_TO[target];
}

const PRIORITY_RANK: Record<Priority, number> = { URGENT: 0, HIGH: 1, MEDIUM: 2, LOW: 3 };

/** Most urgent first, then oldest first — the order the technicians should pick the work. */
export function byUrgency(a: WorkOrderSummary, b: WorkOrderSummary): number {
  return PRIORITY_RANK[a.priority] - PRIORITY_RANK[b.priority] || a.reportedAt.localeCompare(b.reportedAt);
}

/** Groups the cards by column, sorted by urgency. */
export function groupByColumn(cards: WorkOrderSummary[]): Record<BoardColumn, WorkOrderSummary[]> {
  const columns = { OPEN: [], PLANNED: [], IN_PROGRESS: [], DONE: [] } as Record<BoardColumn, WorkOrderSummary[]>;
  for (const card of cards) {
    if ((BOARD_COLUMNS as readonly WorkOrderStatus[]).includes(card.status)) {
      columns[card.status as BoardColumn].push(card);
    }
  }
  for (const column of BOARD_COLUMNS) {
    columns[column].sort(byUrgency);
  }
  return columns;
}

/** Inserts or replaces a card (by id): used for API responses and real-time events alike. */
export function upsert(cards: WorkOrderSummary[], card: WorkOrderSummary): WorkOrderSummary[] {
  const index = cards.findIndex((c) => c.id === card.id);
  return index === -1 ? [...cards, card] : cards.map((c, i) => (i === index ? card : c));
}
