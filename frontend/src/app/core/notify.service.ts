import { inject, Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { describeError } from '../shared/errors';

/** Short feedback messages at the bottom of the screen. */
@Injectable({ providedIn: 'root' })
export class NotifyService {
  private readonly snackBar = inject(MatSnackBar);

  success(message: string): void {
    this.snackBar.open(message, undefined, { duration: 3000 });
  }

  error(error: unknown): void {
    this.snackBar.open(describeError(error), 'Fermer', { duration: 6000, panelClass: 'snack-error' });
  }
}
