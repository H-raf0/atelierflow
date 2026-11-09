import { backlogChart, downtimeChart, monthlyChart } from './charts';

describe('dashboard charts', () => {
  it('maps the backlog per priority', () => {
    const data = backlogChart([['Urgente', 2], ['Basse', 5]]);

    expect(data.labels).toEqual(['Urgente', 'Basse']);
    expect(data.datasets[0].data).toEqual([2, 5]);
  });

  it('stacks corrective and preventive work per month with French month names', () => {
    const data = monthlyChart([
      { month: '2026-10', corrective: 4, preventive: 6, downtimeHours: 12 },
      { month: '2026-11', corrective: 1, preventive: 2, downtimeHours: 3 },
    ]);

    expect(data.labels).toEqual(['oct. 26', 'nov. 26']);
    expect(data.datasets.map((d) => d.label)).toEqual(['Correctifs', 'Préventifs']);
    expect(data.datasets[1].data).toEqual([6, 2]);
  });

  it('shows downtime hours per machine', () => {
    const data = downtimeChart([
      { equipmentId: 1, code: 'PRS-001', name: 'Presse', workshop: 'Emboutissage', failures: 3, repairs: 3,
        downtimeHours: 14, mtbfHours: 113, mttrHours: 6, availability: 0.94 },
    ]);

    expect(data.labels).toEqual(['PRS-001']);
    expect(data.datasets[0].data).toEqual([14]);
  });
});
