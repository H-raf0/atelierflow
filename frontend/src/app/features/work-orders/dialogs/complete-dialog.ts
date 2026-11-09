import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CompleteWorkOrderRequest } from '../../../api/models/complete-work-order-request';

/** Close the intervention (IN_PROGRESS → DONE): time spent and what was done are mandatory. */
@Component({
  selector: 'app-complete-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Terminer {{ reference }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>Temps passé (minutes)</mat-label>
          <input matInput type="number" min="1" formControlName="timeSpentMinutes" data-testid="time-spent">
          @if (form.controls.timeSpentMinutes.invalid) {
            <mat-error>Entre 1 et 10 080 minutes</mat-error>
          }
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Travaux réalisés</mat-label>
          <textarea matInput rows="4" formControlName="resolution" data-testid="resolution"></textarea>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Annuler</button>
        <button mat-flat-button type="submit" data-testid="confirm-complete">Terminer</button>
      </mat-dialog-actions>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CompleteDialog {
  private readonly dialogRef = inject(MatDialogRef<CompleteDialog, CompleteWorkOrderRequest>);
  protected readonly reference = inject<string>(MAT_DIALOG_DATA);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    timeSpentMinutes: [60, [Validators.required, Validators.min(1), Validators.max(10_080)]],
    resolution: ['', [Validators.required, Validators.maxLength(4000)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.getRawValue());
  }
}
