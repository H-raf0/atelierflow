import { ChartConfiguration } from 'chart.js';
import { EquipmentKpi } from '../../api/models/equipment-kpi';
import { MonthlyKpi } from '../../api/models/monthly-kpi';

type BarData = ChartConfiguration<'bar'>['data'];

const COLORS = {
  corrective: '#dc2626',
  preventive: '#2563eb',
  priority: ['#dc2626', '#ea580c', '#ca8a04', '#0284c7'],
  downtime: '#ea580c',
};

/** Pure mapping functions from API data to Chart.js datasets (kept out of the component, unit tested). */
export function backlogChart(entries: [label: string, count: number][]): BarData {
  return {
    labels: entries.map(([label]) => label),
    datasets: [{ data: entries.map(([, count]) => count), backgroundColor: COLORS.priority, label: 'OT à traiter' }],
  };
}

export function monthlyChart(months: MonthlyKpi[]): BarData {
  const formatter = new Intl.DateTimeFormat('fr-FR', { month: 'short', year: '2-digit' });
  return {
    labels: months.map((m) => formatter.format(new Date(`${m.month}-01T00:00:00`))),
    datasets: [
      { label: 'Correctifs', data: months.map((m) => m.corrective), backgroundColor: COLORS.corrective },
      { label: 'Préventifs', data: months.map((m) => m.preventive), backgroundColor: COLORS.preventive },
    ],
  };
}

export function downtimeChart(machines: EquipmentKpi[]): BarData {
  return {
    labels: machines.map((m) => m.code),
    datasets: [{ label: "Heures d'arrêt", data: machines.map((m) => m.downtimeHours), backgroundColor: COLORS.downtime }],
  };
}
