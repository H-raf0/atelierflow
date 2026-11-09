import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { describeError } from '../../shared/errors';

/** Accounts created by the backend "demo" profile (also listed in the README). */
export const DEMO_ACCOUNTS = [
  { label: 'Administrateur', username: 'admin', password: 'Demo-Atelier-2026' },
  { label: 'Responsable', username: 'responsable', password: 'Demo-Atelier-2026' },
  { label: 'Technicien', username: 'technicien', password: 'Demo-Atelier-2026' },
] as const;

@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Query parameters, bound by the router (withComponentInputBinding). */
  readonly returnUrl = input<string>('/');
  readonly expired = input<string>();

  protected readonly demoAccounts = DEMO_ACCOUNTS;
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Typed reactive form: `form.value.username` is a `string`, not `any`. */
  protected readonly form = inject(FormBuilder).nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });

  protected fill(account: (typeof DEMO_ACCOUNTS)[number]): void {
    this.form.setValue({ username: account.username, password: account.password });
  }

  protected async submit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    try {
      const { username, password } = this.form.getRawValue();
      await this.auth.login(username, password);
      await this.router.navigateByUrl(this.returnUrl());
    } catch (e) {
      this.error.set(describeError(e));
    } finally {
      this.loading.set(false);
    }
  }
}
