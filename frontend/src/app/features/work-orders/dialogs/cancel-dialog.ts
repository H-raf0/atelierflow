import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { CancelWorkOrderRequest } from '../../../api/models/cancel-work-order-request';

/** Cancel a work order that has not started; the reason is kept in the history. */
@Component({
  selector: 'app-cancel-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Annuler {{ reference }}</h2>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-dialog-content class="dialog-form">
        <mat-form-field appearance="outline">
          <mat-label>Motif</mat-label>
          <textarea matInput rows="3" formControlName="reason"></textarea>
        </mat-form-field>
      </mat-dialog-content>
      <mat-dialog-actions align="end">
        <button mat-button type="button" mat-dialog-close>Retour</button>
        <button mat-flat-button type="submit">Annuler l'OT</button>
      </mat-dialog-actions>
    </form>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CancelDialog {
  private readonly dialogRef = inject(MatDialogRef<CancelDialog, CancelWorkOrderRequest>);
  protected readonly reference = inject<string>(MAT_DIALOG_DATA);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    reason: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.dialogRef.close(this.form.getRawValue());
  }
}
