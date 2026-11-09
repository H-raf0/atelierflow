import { ChangeDetectionStrategy, Component, inject, resource } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Api } from '../../../api';
import { listUsers } from '../../../api/fn/users/list-users';
import { PlanWorkOrderRequest } from '../../../api/models/plan-work-order-request';
import { toIsoDate } from '../../../shared/dates';

/** Assign a technician and a date (OPEN → PLANNED). */
@Component({
  selector: 'app-plan-dialog',
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatSelectModule, MatInputModule, MatDatepickerModule,
    MatButtonModule,
  ],
  template: `
    <h2 mat-dialog-title>Planifier {{ reference }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>Technicien</mat-label>
          <mat-select formControlName="assigneeId" data-testid="assignee">
            @for (technician of technicians.value() ?? []; track technician.id) {
              <mat-option [value]="technician.id">{{ technician.fullName }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Date prévue</mat-label>
          <input matInput [matDatepicker]="picker" formControlName="scheduledFor">
          <mat-datepicker-toggle matIconSuffix [for]="picker" />
          <mat-datepicker #picker />
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annuler</button>
        <button mat-flat-button type="submit" data-testid="confirm-plan">Planifier</button>
      </mat-dialog-actions>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlanDialog {
  private readonly api = inject(Api);
  private readonly dialogRef = inject(MatDialogRef<PlanDialog, PlanWorkOrderRequest>);
  protected readonly reference = inject<string>(MAT_DIALOG_DATA);

  protected readonly technicians = resource({ loader: () => this.api.invoke(listUsers, { role: 'TECHNICIAN' }) });
  protected readonly form = inject(FormBuilder).group({
    assigneeId: [null as number | null, Validators.required],
    scheduledFor: [new Date() as Date | null, Validators.required],
  });

  protected submit(): void {
    const { assigneeId, scheduledFor } = this.form.getRawValue();
    if (this.form.invalid || assigneeId === null || scheduledFor === null) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close({ assigneeId, scheduledFor: toIsoDate(scheduledFor) });
  }
}
