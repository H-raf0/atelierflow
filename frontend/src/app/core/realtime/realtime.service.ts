import { DestroyRef, effect, inject, Injectable, InjectionToken, signal } from '@angular/core';
import { Client, IMessage, StompConfig } from '@stomp/stompjs';
import { Observable, Subject } from 'rxjs';
import { WorkOrderSummary } from '../../api/models/work-order-summary';
import { EquipmentStatus } from '../../shared/labels';
import { AuthService } from '../auth/auth.service';

export interface EquipmentStatusEvent {
  equipmentId: number;
  code: string;
  status: EquipmentStatus;
}

/** Creates the STOMP client; replaced by a fake in unit tests (no real WebSocket in jsdom). */
export const STOMP_CLIENT_FACTORY = new InjectionToken<(config: StompConfig) => Client>('STOMP_CLIENT_FACTORY', {
  providedIn: 'root',
  factory: () => (config) => new Client(config),
});

/**
 * STOMP connection to the backend, open while the user is signed in.
 *
 * Push events are discrete occurrences, hence exposed as RxJS streams (signals hold a current value,
 * they are not meant to carry a sequence of events). The connection state is a signal for the UI.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private readonly createClient = inject(STOMP_CLIENT_FACTORY);
  private readonly workOrderEvents = new Subject<WorkOrderSummary>();
  private readonly equipmentEvents = new Subject<EquipmentStatusEvent>();
  private client: Client | null = null;

  readonly connected = signal(false);
  readonly workOrders$: Observable<WorkOrderSummary> = this.workOrderEvents.asObservable();
  readonly equipment$: Observable<EquipmentStatusEvent> = this.equipmentEvents.asObservable();

  constructor() {
    // connect on sign-in, disconnect on sign-out
    effect(() => (this.auth.isAuthenticated() ? this.connect() : this.disconnect()));
    inject(DestroyRef).onDestroy(() => this.disconnect());
  }

  private connect(): void {
    if (this.client) {
      return;
    }
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    const client = this.createClient({
      brokerURL: `${scheme}://${location.host}/ws`,
      reconnectDelay: 5000,
      // browsers cannot add headers to the WebSocket handshake: the JWT goes in the STOMP CONNECT frame,
      // fetched (and renewed if needed) before every (re)connection
      beforeConnect: async (stomp) => {
        stomp.connectHeaders = { Authorization: `Bearer ${await this.auth.validAccessToken()}` };
      },
      onConnect: () => {
        this.connected.set(true);
        client.subscribe('/topic/work-orders', (message) => this.workOrderEvents.next(parse(message)));
        client.subscribe('/topic/equipment', (message) => this.equipmentEvents.next(parse(message)));
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });
    client.activate();
    this.client = client;
  }

  private disconnect(): void {
    void this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
  }
}

function parse<T>(message: IMessage): T {
  return JSON.parse(message.body) as T;
}
