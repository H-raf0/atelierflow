import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { IMessage, StompConfig } from '@stomp/stompjs';
import { fakeAuth, fakeRealtime, workOrderSummary } from '../../testing/fakes';
import { AuthService } from './auth/auth.service';
import { Shell } from './layout/shell';
import { NotifyService } from './notify.service';
import { RealtimeService, STOMP_CLIENT_FACTORY } from './realtime/realtime.service';

describe('RealtimeService', () => {
  const authenticated = signal(false);
  let config: StompConfig | undefined;
  const subscriptions = new Map<string, (message: IMessage) => void>();
  const client = {
    activate: vi.fn(),
    deactivate: vi.fn(),
    subscribe: vi.fn((topic: string, callback: (message: IMessage) => void) => subscriptions.set(topic, callback)),
  };

  beforeEach(() => {
    vi.clearAllMocks();
    authenticated.set(false);
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: { isAuthenticated: authenticated, validAccessToken: async () => 'jwt-1' } },
        { provide: STOMP_CLIENT_FACTORY, useValue: (c: StompConfig) => ((config = c), client) },
      ],
    });
  });

  it('connects on sign-in with the JWT in the CONNECT frame and streams the pushed events', async () => {
    const realtime = TestBed.inject(RealtimeService);
    const received: unknown[] = [];
    realtime.workOrders$.subscribe((event) => received.push(event));

    authenticated.set(true);
    TestBed.tick();
    expect(client.activate).toHaveBeenCalled();

    const stomp = { connectHeaders: {} } as { connectHeaders: Record<string, string> };
    await config!.beforeConnect!(stomp as never);
    expect(stomp.connectHeaders).toEqual({ Authorization: 'Bearer jwt-1' });

    config!.onConnect!({} as never);
    expect(realtime.connected()).toBe(true);
    subscriptions.get('/topic/work-orders')!({ body: JSON.stringify(workOrderSummary({ id: 4 })) } as IMessage);
    expect(received).toEqual([expect.objectContaining({ id: 4 })]);
  });

  it('disconnects on sign-out', () => {
    const realtime = TestBed.inject(RealtimeService);
    authenticated.set(true);
    TestBed.tick();

    authenticated.set(false);
    TestBed.tick();

    expect(client.deactivate).toHaveBeenCalled();
    expect(realtime.connected()).toBe(false);
  });
});

describe('Shell', () => {
  it('shows the user, the real-time state and signs out', async () => {
    const auth = fakeAuth('TECHNICIAN');
    const realtime = fakeRealtime();
    await TestBed.configureTestingModule({
      imports: [Shell],
      providers: [provideRouter([]), auth.provider, realtime.provider],
    }).compileComponents();
    const fixture = TestBed.createComponent(Shell);
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;

    expect(element.textContent).toContain('Test User');
    expect(element.textContent).toContain('Temps réel');
    expect(element.querySelectorAll('a[mat-list-item]')).toHaveLength(3);

    realtime.connected.set(false);
    await fixture.whenStable();
    expect(element.textContent).toContain('Hors ligne');
  });
});

describe('NotifyService', () => {
  it('shows success and translated error messages', () => {
    const snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({ providers: [{ provide: MatSnackBar, useValue: snackBar }] });
    const notify = TestBed.inject(NotifyService);

    notify.success('Enregistré');
    notify.error(new Error('boom'));

    expect(snackBar.open).toHaveBeenCalledWith('Enregistré', undefined, { duration: 3000 });
    expect(snackBar.open).toHaveBeenCalledWith('Une erreur inattendue est survenue.', 'Fermer', expect.anything());
  });
});
