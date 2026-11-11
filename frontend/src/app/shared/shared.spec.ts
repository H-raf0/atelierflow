import { HttpErrorResponse } from '@angular/common/http';
import { daysAgo, toIsoDate } from './dates';
import { describeError } from './errors';
import { historyValueLabel } from './labels';

describe('describeError', () => {
  const problem = (status: number, error: unknown) => new HttpErrorResponse({ status, error });

  it('translates business rule codes', () => {
    expect(describeError(problem(409, { code: 'INVALID_TRANSITION' }))).toContain("n'est pas autorisé");
    expect(describeError(problem(403, { code: 'NOT_ASSIGNEE' }))).toBe("Cet ordre de travail n'est pas assigné à vous.");
  });

  it('lists invalid fields', () => {
    const message = describeError(problem(400, {
      code: 'VALIDATION_FAILED',
      violations: [{ field: 'code', message: 'must not be blank' }],
    }));

    expect(message).toBe('Champs invalides : code (must not be blank)');
  });

  it('falls back on the server detail, then on the status', () => {
    expect(describeError(problem(409, { code: 'UNKNOWN', detail: 'Something specific' }))).toBe('Something specific');
    expect(describeError(problem(500, null))).toBe('Erreur 500.');
  });

  it('handles network failures and non-HTTP errors', () => {
    expect(describeError(problem(0, null))).toContain('injoignable');
    expect(describeError(new Error('boom'))).toBe('Une erreur inattendue est survenue.');
  });
});

describe('labels and dates', () => {
  it('renders history values in French', () => {
    expect(historyValueLabel('status', 'IN_PROGRESS')).toBe('En cours');
    expect(historyValueLabel('priority', 'URGENT')).toBe('Urgente');
    expect(historyValueLabel('title', 'Fuite')).toBe('Fuite');
    expect(historyValueLabel('assignee', null)).toBe('—');
    expect(historyValueLabel('scheduledFor', '2026-11-20')).toBe('20/11/2026');
  });

  it('formats local dates as ISO days', () => {
    expect(toIsoDate(new Date(2026, 0, 5))).toBe('2026-01-05');
    expect(daysAgo(10, new Date(2026, 10, 11))).toBe('2026-11-01');
  });
});
