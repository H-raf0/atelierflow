import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, resource, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { debounceTime, map } from 'rxjs';
import { Api } from '../../api';
import { deleteEquipment } from '../../api/fn/equipment/delete-equipment';
import { listWorkshops } from '../../api/fn/equipment/list-workshops';
import { searchEquipment } from '../../api/fn/equipment/search-equipment';
import { EquipmentResponse } from '../../api/models/equipment-response';
import { AuthService } from '../../core/auth/auth.service';
import { NotifyService } from '../../core/notify.service';
import { RealtimeService } from '../../core/realtime/realtime.service';
import {
  CRITICALITIES,
  CRITICALITY_LABELS,
  Criticality,
  EQUIPMENT_STATUSES,
  EQUIPMENT_STATUS_LABELS,
  EquipmentStatus,
} from '../../shared/labels';
import { EquipmentFormDialog, EquipmentFormDialogData } from './equipment-form-dialog';

/** Machine fleet: searched, filtered, sorted and paged by the server. */
@Component({
  selector: 'app-equipment-list-page',
  imports: [
    ReactiveFormsModule, DecimalPipe, MatTableModule, MatSortModule, MatPaginatorModule, MatFormFieldModule,
    MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatProgressBarModule, MatTooltipModule,
  ],
  templateUrl: './equipment-list-page.html',
  styleUrl: './equipment-list-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentListPage {
  private readonly api = inject(Api);
  private readonly dialog = inject(MatDialog);
  private readonly notify = inject(NotifyService);
  protected readonly auth = inject(AuthService);

  protected readonly criticalities = CRITICALITIES;
  protected readonly statuses = EQUIPMENT_STATUSES;
  protected readonly criticalityLabels = CRITICALITY_LABELS;
  protected readonly statusLabels = EQUIPMENT_STATUS_LABELS;
  protected readonly columns = ['code', 'name', 'workshop', 'criticality', 'status', 'operatingHours', 'actions'];

  protected readonly filters = inject(FormBuilder).nonNullable.group({
    q: '',
    workshop: '',
    criticality: '' as Criticality | '',
    status: '' as EquipmentStatus | '',
  });
  /** Filter values as a signal; typing is debounced so a search does not fire on every key stroke. */
  private readonly filterValues = toSignal(
    this.filters.valueChanges.pipe(debounceTime(300), map(() => this.filters.getRawValue())),
    { initialValue: this.filters.getRawValue() },
  );
  protected readonly page = signal({ index: 0, size: 20 });
  protected readonly sort = signal<Sort>({ active: 'code', direction: 'asc' });

  /** Reloads automatically whenever a filter, the page or the sort changes (signals as parameters). */
  protected readonly equipment = resource({
    params: () => ({ filters: this.filterValues(), page: this.page(), sort: this.sort() }),
    loader: ({ params }) =>
      this.api.invoke(searchEquipment, {
        q: params.filters.q || undefined,
        workshop: params.filters.workshop || undefined,
        criticality: params.filters.criticality || undefined,
        status: params.filters.status || undefined,
        page: params.page.index,
        size: params.page.size,
        sort: params.sort.direction ? [`${params.sort.active},${params.sort.direction}`] : undefined,
      }),
  });
  protected readonly workshops = resource({ loader: () => this.api.invoke(listWorkshops) });

  constructor() {
    this.filters.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.page.update((p) => ({ ...p, index: 0 })));
    // a machine broke down or was repaired somewhere: refresh the list
    inject(RealtimeService).equipment$.pipe(takeUntilDestroyed()).subscribe(() => this.equipment.reload());
  }

  /** Typed accessors: mat-table rows are untyped (`any`) in templates. */
  protected criticalityLabel(equipment: EquipmentResponse): string {
    return CRITICALITY_LABELS[equipment.criticality];
  }

  protected statusLabel(equipment: EquipmentResponse): string {
    return EQUIPMENT_STATUS_LABELS[equipment.status];
  }

  protected onPage(event: PageEvent): void {
    this.page.set({ index: event.pageIndex, size: event.pageSize });
  }

  protected onSort(sort: Sort): void {
    this.sort.set(sort);
  }

  protected openForm(equipment?: EquipmentResponse): void {
    const data: EquipmentFormDialogData = { equipment, workshops: this.workshops.value() ?? [] };
    this.dialog
      .open(EquipmentFormDialog, { data })
      .afterClosed()
      .subscribe((saved) => {
        if (saved) {
          this.equipment.reload();
          this.workshops.reload();
        }
      });
  }

  protected async remove(equipment: EquipmentResponse): Promise<void> {
    if (!confirm(`Supprimer définitivement la machine ${equipment.code} ?`)) {
      return;
    }
    try {
      await this.api.invoke(deleteEquipment, { id: equipment.id });
      this.notify.success(`Machine ${equipment.code} supprimée`);
      this.equipment.reload();
    } catch (e) {
      this.notify.error(e);
    }
  }
}
