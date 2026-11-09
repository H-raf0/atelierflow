import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Api } from '../../api';
import { createEquipment } from '../../api/fn/equipment/create-equipment';
import { updateEquipment } from '../../api/fn/equipment/update-equipment';
import { EquipmentResponse } from '../../api/models/equipment-response';
import { NotifyService } from '../../core/notify.service';
import { toIsoDate } from '../../shared/dates';
import { CRITICALITIES, CRITICALITY_LABELS, Criticality } from '../../shared/labels';

export interface EquipmentFormDialogData {
  equipment?: EquipmentResponse;
  workshops: string[];
}

/** Create or edit a machine. The code and hour counter can only be set at creation. */
@Component({
  selector: 'app-equipment-form-dialog',
  imports: [
    ReactiveFormsModule, MatDialogModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatDatepickerModule,
    MatButtonModule,
  ],
  templateUrl: './equipment-form-dialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EquipmentFormDialog {
  private readonly api = inject(Api);
  private readonly notify = inject(NotifyService);
  private readonly dialogRef = inject(MatDialogRef<EquipmentFormDialog, boolean>);
  protected readonly data = inject<EquipmentFormDialogData>(MAT_DIALOG_DATA);

  protected readonly isEdit = this.data.equipment !== undefined;
  protected readonly criticalities = CRITICALITIES;
  protected readonly criticalityLabels = CRITICALITY_LABELS;
  protected readonly saving = signal(false);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    code: [this.data.equipment?.code ?? '', [Validators.required, Validators.pattern(/^[A-Z0-9][A-Z0-9-]{2,19}$/)]],
    name: [this.data.equipment?.name ?? '', [Validators.required, Validators.maxLength(120)]],
    workshop: [this.data.equipment?.workshop ?? '', [Validators.required, Validators.maxLength(60)]],
    criticality: [(this.data.equipment?.criticality ?? 'MEDIUM') as Criticality, Validators.required],
    operatingHours: [this.data.equipment?.operatingHours ?? 0, [Validators.required, Validators.min(0)]],
    commissionedOn: [this.data.equipment?.commissionedOn ? new Date(this.data.equipment.commissionedOn) : null as Date | null],
  });

  constructor() {
    if (this.isEdit) {
      this.form.controls.code.disable();
      this.form.controls.operatingHours.disable();
    }
  }

  protected async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const commissionedOn = value.commissionedOn ? toIsoDate(value.commissionedOn) : undefined;
    this.saving.set(true);
    try {
      if (this.data.equipment) {
        await this.api.invoke(updateEquipment, {
          id: this.data.equipment.id,
          body: { name: value.name, workshop: value.workshop, criticality: value.criticality, commissionedOn },
        });
      } else {
        await this.api.invoke(createEquipment, {
          body: { ...value, commissionedOn },
        });
      }
      this.notify.success(`Machine ${value.code} enregistrée`);
      this.dialogRef.close(true);
    } catch (e) {
      this.notify.error(e);
    } finally {
      this.saving.set(false);
    }
  }
}
