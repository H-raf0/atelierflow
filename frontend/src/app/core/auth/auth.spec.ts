import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { provideApiConfiguration } from '../../api';
import { TokenResponse } from '../../api/models/token-response';
import { authGuard, roleGuard } from './auth.guard';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

function token(accessToken: string, role: TokenResponse['user']['role'] = 'MANAGER'): TokenResponse {
  return {
    accessToken,
    tokenType: 'Bearer',
    expiresIn: 900,
    user: { id: 7, username: 'chef', fullName: 'Sophie Bernard', role },
  };
}

describe('authentication', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideApiConfiguration(''),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });

  afterEach(() => httpTesting.verify());

  async function signIn(role: TokenResponse['user']['role'] = 'MANAGER'): Promise<void> {
    const login = auth.login('chef', 'secret');
    const request = httpTesting.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'chef', password: 'secret' });
    request.flush(token('access-1', role));
    await login;
  }

  describe('AuthService', () => {
    it('opens a session on login and exposes the role', async () => {
      expect(auth.isAuthenticated()).toBe(false);

      await signIn('MANAGER');

      expect(auth.isAuthenticated()).toBe(true);
      expect(auth.user()?.fullName).toBe('Sophie Bernard');
      expect(auth.canManage()).toBe(true);
      expect(auth.isAdmin()).toBe(false);
    });

    it('technicians cannot manage', async () => {
      await signIn('TECHNICIAN');

      expect(auth.canManage()).toBe(false);
      expect(auth.hasRole('TECHNICIAN')).toBe(true);
    });

    it('shares a single refresh request between concurrent callers', async () => {
      const first = firstValueFrom(auth.refresh());
      const second = firstValueFrom(auth.refresh());

      httpTesting.expectOne('/api/auth/refresh').flush(token('access-2'));

      expect(await first).toBe('access-2');
      expect(await second).toBe('access-2');
    });

    it('restoreSession resumes silently or stays signed out', async () => {
      const failed = auth.restoreSession();
      httpTesting.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });
      await failed;
      expect(auth.isAuthenticated()).toBe(false);

      const resumed = auth.restoreSession();
      httpTesting.expectOne('/api/auth/refresh').flush(token('access-3'));
      await resumed;
      expect(auth.accessToken()).toBe('access-3');
    });

    it('returns the current token while valid, renews it otherwise', async () => {
      await signIn();
      expect(await auth.validAccessToken()).toBe('access-1');

      vi.useFakeTimers();
      vi.setSystemTime(Date.now() + 15 * 60_000);
      const renewed = auth.validAccessToken();
      httpTesting.expectOne('/api/auth/refresh').flush(token('access-4'));
      expect(await renewed).toBe('access-4');
      vi.useRealTimers();
    });

    it('logout revokes the session server-side and goes back to the login page', async () => {
      await signIn();

      const logout = auth.logout();
      httpTesting.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
      await logout;

      expect(auth.isAuthenticated()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/login']);
    });
  });

  describe('authInterceptor', () => {
    it('adds the bearer token to API calls but not to authentication endpoints', async () => {
      await signIn();

      http.get('/api/equipment').subscribe();
      const request = httpTesting.expectOne('/api/equipment');
      expect(request.request.headers.get('Authorization')).toBe('Bearer access-1');
      request.flush({});

      http.get('/assets/logo.svg').subscribe();
      expect(httpTesting.expectOne('/assets/logo.svg').request.headers.has('Authorization')).toBe(false);
    });

    it('refreshes an expired token once and replays the request', async () => {
      await signIn();
      const response = firstValueFrom(http.get<{ ok: boolean }>('/api/work-orders/board'));

      httpTesting.expectOne('/api/work-orders/board').flush(null, { status: 401, statusText: 'Unauthorized' });
      httpTesting.expectOne('/api/auth/refresh').flush(token('access-2'));
      const replay = httpTesting.expectOne('/api/work-orders/board');
      expect(replay.request.headers.get('Authorization')).toBe('Bearer access-2');
      replay.flush({ ok: true });

      expect(await response).toEqual({ ok: true });
    });

    it('ends the session when the refresh token is rejected', async () => {
      await signIn();
      const response = firstValueFrom(http.get('/api/kpis/summary'));

      httpTesting.expectOne('/api/kpis/summary').flush(null, { status: 401, statusText: 'Unauthorized' });
      httpTesting.expectOne('/api/auth/refresh').flush(null, { status: 401, statusText: 'Unauthorized' });

      await expect(response).rejects.toMatchObject({ status: 401 });
      expect(auth.isAuthenticated()).toBe(false);
      expect(router.navigate).toHaveBeenCalledWith(['/login'], { queryParams: { expired: true } });
    });

    it('does not try to refresh on other errors', async () => {
      await signIn();
      const response = firstValueFrom(http.post('/api/work-orders/1/start', null));

      httpTesting.expectOne('/api/work-orders/1/start').flush({ code: 'NOT_ASSIGNEE' }, { status: 403, statusText: 'Forbidden' });

      await expect(response).rejects.toMatchObject({ status: 403 });
      expect(auth.isAuthenticated()).toBe(true);
    });
  });

  describe('guards', () => {
    const runGuard = (guard: typeof authGuard, url = '/board') =>
      TestBed.runInInjectionContext(() => guard({} as never, { url } as never));

    it('sends anonymous users to the login page with a return URL', () => {
      const result = runGuard(authGuard) as UrlTree;

      expect(router.serializeUrl(result)).toBe('/login?returnUrl=%2Fboard');
    });

    it('lets signed-in users in and filters by role', async () => {
      await signIn('TECHNICIAN');

      expect(runGuard(authGuard)).toBe(true);
      expect(runGuard(roleGuard('TECHNICIAN'))).toBe(true);
      expect(router.serializeUrl(runGuard(roleGuard('ADMIN', 'MANAGER')) as UrlTree)).toBe('/');
    });
  });
});
