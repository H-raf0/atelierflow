import { DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, resource, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { debounceTime } from 'rxjs';
import { Api } from '../../api';
import { getEquipmentKpis } from '../../api/fn/kp-is/get-equipment-kpis';
import { getKpiSummary } from '../../api/fn/kp-is/get-kpi-summary';
import { getMonthlyKpis } from '../../api/fn/kp-is/get-monthly-kpis';
import { RealtimeService } from '../../core/realtime/realtime.service';
import { daysAgo } from '../../shared/dates';
import { PRIORITIES, PRIORITY_LABELS } from '../../shared/labels';
import { backlogChart, downtimeChart, monthlyChart } from './charts';

/** Reliability dashboard. Chart.js is registered here only, so it is not part of the initial bundle. */
@Component({
  selector: 'app-dashboard-page',
  imports: [
    BaseChartDirective, DecimalPipe, PercentPipe, MatCardModule, MatButtonToggleModule, MatIconModule,
    MatProgressBarModule, MatTooltipModule,
  ],
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './dashboard-page.html',
  styleUrl: './dashboard-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  private readonly api = inject(Api);

  protected readonly periods = [30, 90, 180];
  protected readonly periodDays = signal(90);
  private readonly period = computed(() => ({ from: daysAgo(this.periodDays() - 1) }));

  protected readonly summary = resource({
    params: this.period,
    loader: ({ params }) => this.api.invoke(getKpiSummary, params),
  });
  protected readonly equipment = resource({
    params: this.period,
    loader: ({ params }) => this.api.invoke(getEquipmentKpis, params),
  });
  protected readonly monthly = resource({
    loader: () => this.api.invoke(getMonthlyKpis, { months: 6 }),
  });

  protected readonly backlogChart = computed<ChartConfiguration<'bar'>['data'] | null>(() => {
    const summary = this.summary.value();
    return summary ? backlogChart(PRIORITIES.map((p) => [PRIORITY_LABELS[p], summary.backlogByPriority[p] ?? 0])) : null;
  });
  protected readonly monthlyChart = computed(() => {
    const months = this.monthly.value();
    return months ? monthlyChart(months) : null;
  });
  protected readonly downtimeChart = computed(() => {
    const machines = this.equipment.value();
    return machines ? downtimeChart(machines.filter((m) => m.downtimeHours > 0).slice(0, 8)) : null;
  });

  protected readonly barOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    // work orders are counted: no half ticks
    scales: { y: { beginAtZero: true, ticks: { precision: 0 } } },
  };
  protected readonly stackedOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    scales: { x: { stacked: true }, y: { stacked: true, beginAtZero: true, ticks: { precision: 0 } } },
  };
  protected readonly horizontalOptions: ChartConfiguration<'bar'>['options'] = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
  };

  constructor() {
    // indicators move when work orders change: refresh at most once every few seconds
    inject(RealtimeService)
      .workOrders$.pipe(debounceTime(3000), takeUntilDestroyed())
      .subscribe(() => {
        this.summary.reload();
        this.equipment.reload();
        this.monthly.reload();
      });
  }
}
