import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom, map, Observable, shareReplay, finalize } from 'rxjs';
import { ApiConfiguration } from '../../api';
import { login as loginFn } from '../../api/fn/authentication/login';
import { logout as logoutFn } from '../../api/fn/authentication/logout';
import { refresh as refreshFn } from '../../api/fn/authentication/refresh';
import { TokenResponse } from '../../api/models/token-response';
import { UserSummary } from '../../api/models/user-summary';

export type Role = UserSummary['role'];

/** Renew the access token this long before it expires (WebSocket reconnections need a valid one). */
const EXPIRY_MARGIN_MS = 30_000;

/**
 * Session state, held in signals.
 *
 * - The access token lives in memory only (never in localStorage, where any injected script could read it).
 * - The refresh token is an HttpOnly cookie handled by the browser: JavaScript never sees it.
 * - Concurrent 401s trigger a single refresh request, shared by all the callers.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly config = inject(ApiConfiguration);
  private readonly router = inject(Router);

  private readonly session = signal<{ user: UserSummary; accessToken: string; expiresAt: number } | null>(null);
  private refreshInFlight$: Observable<string> | null = null;

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isAuthenticated = computed(() => this.session() !== null);
  /** Managers and administrators plan, assign and edit; technicians carry out their own work orders. */
  readonly canManage = computed(() => this.hasRole('ADMIN', 'MANAGER'));
  readonly isAdmin = computed(() => this.hasRole('ADMIN'));

  accessToken(): string | null {
    return this.session()?.accessToken ?? null;
  }

  hasRole(...roles: Role[]): boolean {
    const user = this.session()?.user;
    return user !== undefined && roles.includes(user.role);
  }

  async login(username: string, password: string): Promise<void> {
    const response = await firstValueFrom(
      loginFn(this.http, this.config.rootUrl, { body: { username, password } }).pipe(map((r) => r.body)),
    );
    this.open(response);
  }

  /** Exchanges the refresh cookie for a new access token; concurrent callers share one request. */
  refresh(): Observable<string> {
    this.refreshInFlight$ ??= refreshFn(this.http, this.config.rootUrl).pipe(
      map((response) => {
        this.open(response.body);
        return response.body.accessToken;
      }),
      finalize(() => (this.refreshInFlight$ = null)),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.refreshInFlight$;
  }

  /** A token valid for at least the next few seconds, renewed first if needed. */
  async validAccessToken(): Promise<string> {
    const current = this.session();
    if (current && current.expiresAt - Date.now() > EXPIRY_MARGIN_MS) {
      return current.accessToken;
    }
    return firstValueFrom(this.refresh());
  }

  /** Called once at startup: silently resumes the session if the refresh cookie is still valid. */
  async restoreSession(): Promise<void> {
    try {
      await firstValueFrom(this.refresh());
    } catch {
      // no session to resume: the guard will redirect to the login page
    }
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(logoutFn(this.http, this.config.rootUrl));
    } finally {
      this.session.set(null);
      await this.router.navigate(['/login']);
    }
  }

  /** The refresh token was rejected: the session is over, back to the login page. */
  sessionExpired(): void {
    this.session.set(null);
    void this.router.navigate(['/login'], { queryParams: { expired: true } });
  }

  private open(response: TokenResponse): void {
    this.session.set({
      user: response.user,
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresIn * 1000,
    });
  }
}
