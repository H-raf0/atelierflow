import { ChangeDetectionStrategy, Component, inject, resource, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Api } from '../../../api';
import { searchEquipment } from '../../../api/fn/equipment/search-equipment';
import { createWorkOrder } from '../../../api/fn/work-orders/create-work-order';
import { WorkOrderDetail } from '../../../api/models/work-order-detail';
import { AuthService } from '../../../core/auth/auth.service';
import { NotifyService } from '../../../core/notify.service';
import { PRIORITIES, PRIORITY_LABELS, Priority, TYPE_LABELS, WorkOrderType } from '../../../shared/labels';

/**
 * Report a breakdown (anyone) or create a preventive work order (managers). Without an explicit
 * priority, the server derives it from the machine criticality.
 */
@Component({
  selector: 'app-create-work-order-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Nouvel ordre de travail</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>Type</mat-label>
          <mat-select formControlName="type">
            <mat-option value="CORRECTIVE">{{ typeLabels.CORRECTIVE }} (panne)</mat-option>
            @if (canManage) {
              <mat-option value="PREVENTIVE">{{ typeLabels.PREVENTIVE }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Machine</mat-label>
          <mat-select formControlName="equipmentId" data-testid="wo-equipment">
            @for (equipment of equipment.value()?.content ?? []; track equipment.id) {
              <mat-option [value]="equipment.id">{{ equipment.code }} — {{ equipment.name }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Titre</mat-label>
          <input matInput formControlName="title" data-testid="wo-title">
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Description</mat-label>
          <textarea matInput rows="3" formControlName="description"></textarea>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Priorité</mat-label>
          <mat-select formControlName="priority">
            <mat-option [value]="null">Selon la criticité de la machine</mat-option>
            @for (priority of priorities; track priority) {
              <mat-option [value]="priority">{{ priorityLabels[priority] }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annuler</button>
        <button mat-flat-button type="submit" [disabled]="saving()" data-testid="wo-submit">Créer</button>
      </mat-dialog-actions>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateWorkOrderDialog {
  private readonly api = inject(Api);
  private readonly notify = inject(NotifyService);
  private readonly dialogRef = inject(MatDialogRef<CreateWorkOrderDialog, WorkOrderDetail>);

  protected readonly canManage = inject(AuthService).canManage();
  protected readonly typeLabels = TYPE_LABELS;
  protected readonly priorities = PRIORITIES;
  protected readonly priorityLabels = PRIORITY_LABELS;
  protected readonly saving = signal(false);
  protected readonly equipment = resource({
    loader: () => this.api.invoke(searchEquipment, { size: 100, sort: ['code,asc'] }),
  });

  protected readonly form = inject(FormBuilder).group({
    type: ['CORRECTIVE' as WorkOrderType, Validators.required],
    equipmentId: [null as number | null, Validators.required],
    title: ['', [Validators.required, Validators.maxLength(150)]],
    description: [''],
    priority: [null as Priority | null],
  });

  protected async submit(): Promise<void> {
    const value = this.form.getRawValue();
    if (this.form.invalid || value.equipmentId === null || !value.type || !value.title) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    try {
      const created = await this.api.invoke(createWorkOrder, {
        body: {
          type: value.type,
          equipmentId: value.equipmentId,
          title: value.title,
          description: value.description || undefined,
          priority: value.priority ?? undefined,
        },
      });
      this.notify.success(`${created.reference} créé`);
      this.dialogRef.close(created);
    } catch (e) {
      this.notify.error(e);
    } finally {
      this.saving.set(false);
    }
  }
}
