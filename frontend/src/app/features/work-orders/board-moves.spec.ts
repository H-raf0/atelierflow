import { WorkOrderSummary } from '../../api/models/work-order-summary';
import { byUrgency, groupByColumn, moveFor, upsert } from './board-moves';

function card(overrides: Partial<WorkOrderSummary> = {}): WorkOrderSummary {
  return {
    id: 1,
    reference: 'OT-00001',
    type: 'CORRECTIVE',
    status: 'OPEN',
    priority: 'MEDIUM',
    title: 'Fuite',
    equipmentId: 1,
    equipmentCode: 'PRS-001',
    equipmentName: 'Presse',
    assignee: null,
    reportedAt: '2026-11-02T08:00:00Z',
    scheduledFor: null,
    allowedTransitions: ['PLANNED', 'CANCELLED'],
    ...overrides,
  };
}

describe('board moves', () => {
  it('maps a drop to the action of the target column when the server allows it', () => {
    expect(moveFor(card(), 'PLANNED')).toBe('plan');
    expect(moveFor(card({ status: 'PLANNED', allowedTransitions: ['OPEN', 'IN_PROGRESS', 'CANCELLED'] }), 'OPEN'))
      .toBe('unplan');
    expect(moveFor(card({ status: 'PLANNED', allowedTransitions: ['OPEN', 'IN_PROGRESS', 'CANCELLED'] }), 'IN_PROGRESS'))
      .toBe('start');
    expect(moveFor(card({ status: 'IN_PROGRESS', allowedTransitions: ['DONE'] }), 'DONE')).toBe('complete');
  });

  it('refuses moves the server does not allow and drops on the same column', () => {
    expect(moveFor(card(), 'IN_PROGRESS')).toBeNull();
    expect(moveFor(card(), 'DONE')).toBeNull();
    expect(moveFor(card(), 'OPEN')).toBeNull();
    expect(moveFor(card({ status: 'DONE', allowedTransitions: [] }), 'OPEN')).toBeNull();
  });

  it('groups cards by column, most urgent then oldest first, hiding cancelled ones', () => {
    const cards = [
      card({ id: 1, priority: 'LOW' }),
      card({ id: 2, priority: 'URGENT', reportedAt: '2026-11-03T08:00:00Z' }),
      card({ id: 3, priority: 'URGENT', reportedAt: '2026-11-01T08:00:00Z' }),
      card({ id: 4, status: 'DONE' }),
      card({ id: 5, status: 'CANCELLED' }),
    ];

    const columns = groupByColumn(cards);

    expect(columns.OPEN.map((c) => c.id)).toEqual([3, 2, 1]);
    expect(columns.DONE.map((c) => c.id)).toEqual([4]);
    expect(columns.PLANNED).toEqual([]);
    expect(Object.values(columns).flat().some((c) => c.id === 5)).toBe(false);
  });

  it('orders by priority before date', () => {
    expect(byUrgency(card({ priority: 'HIGH' }), card({ priority: 'LOW' }))).toBeLessThan(0);
    expect(byUrgency(card({ reportedAt: '2026-11-05T00:00:00Z' }), card())).toBeGreaterThan(0);
  });

  it('upserts cards by id without mutating the list', () => {
    const cards = [card({ id: 1 }), card({ id: 2 })];

    const replaced = upsert(cards, card({ id: 2, status: 'PLANNED' }));
    const added = upsert(cards, card({ id: 3 }));

    expect(replaced.find((c) => c.id === 2)?.status).toBe('PLANNED');
    expect(replaced).toHaveLength(2);
    expect(added.map((c) => c.id)).toEqual([1, 2, 3]);
    expect(cards[1].status).toBe('OPEN');
  });
});
