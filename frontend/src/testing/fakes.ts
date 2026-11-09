import { computed, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { Api } from '../app/api';
import { EquipmentResponse } from '../app/api/models/equipment-response';
import { UserSummary } from '../app/api/models/user-summary';
import { WorkOrderDetail } from '../app/api/models/work-order-detail';
import { WorkOrderSummary } from '../app/api/models/work-order-summary';
import { AuthService, Role } from '../app/core/auth/auth.service';
import { EquipmentStatusEvent, RealtimeService } from '../app/core/realtime/realtime.service';

type ApiFn = (...args: never[]) => unknown;

/**
 * Fake of the generated client's `Api.invoke`: answers per API function, records the calls.
 * A response can be a value or a function of the request parameters.
 */
export function fakeApi(responses: [ApiFn, unknown][] = []) {
  const table = new Map<ApiFn, unknown>(responses);
  const invoke = vi.fn(async (fn: ApiFn, params?: unknown) => {
    if (!table.has(fn)) {
      throw new Error(`No fake response for ${fn.name}`);
    }
    const response = table.get(fn);
    return typeof response === 'function' ? (response as (p: unknown) => unknown)(params) : response;
  });
  return {
    provider: { provide: Api, useValue: { invoke } },
    invoke,
    respond: (fn: ApiFn, response: unknown) => table.set(fn, response),
    /** Parameters of the calls made with `fn`. */
    callsOf: (fn: ApiFn) => invoke.mock.calls.filter(([called]) => called === fn).map(([, params]) => params),
  };
}

export function fakeAuth(role: Role = 'MANAGER', id = 1) {
  const user = signal<UserSummary | null>({ id, username: 'user', fullName: 'Test User', role });
  const fake = {
    user,
    isAuthenticated: computed(() => user() !== null),
    canManage: computed(() => ['ADMIN', 'MANAGER'].includes(user()?.role ?? '')),
    isAdmin: computed(() => user()?.role === 'ADMIN'),
    hasRole: (...roles: Role[]) => roles.includes(user()?.role as Role),
    logout: vi.fn(),
  };
  return { provider: { provide: AuthService, useValue: fake }, ...fake };
}

export function fakeRealtime() {
  const workOrders = new Subject<WorkOrderSummary>();
  const equipment = new Subject<EquipmentStatusEvent>();
  const fake = { connected: signal(true), workOrders$: workOrders.asObservable(), equipment$: equipment.asObservable() };
  return { provider: { provide: RealtimeService, useValue: fake }, workOrders, equipment, connected: fake.connected };
}

export function workOrderSummary(overrides: Partial<WorkOrderSummary> = {}): WorkOrderSummary {
  return {
    id: 1,
    reference: 'OT-00001',
    type: 'CORRECTIVE',
    status: 'OPEN',
    priority: 'HIGH',
    title: 'Fuite hydraulique',
    equipmentId: 10,
    equipmentCode: 'PRS-001',
    equipmentName: 'Presse 200 t',
    assignee: null,
    reportedAt: '2026-11-02T08:00:00Z',
    scheduledFor: null,
    allowedTransitions: ['PLANNED', 'CANCELLED'],
    ...overrides,
  };
}

export function workOrderDetail(overrides: Partial<WorkOrderDetail> = {}): WorkOrderDetail {
  return {
    ...workOrderSummary(),
    description: 'Flaque sous la presse',
    equipmentWorkshop: 'Emboutissage',
    equipmentCriticality: 'CRITICAL',
    startedAt: null,
    completedAt: null,
    timeSpentMinutes: null,
    resolution: null,
    cancelledAt: null,
    cancellationReason: null,
    maintenancePlanId: null,
    createdAt: '2026-11-02T08:00:00Z',
    updatedAt: '2026-11-02T08:00:00Z',
    ...overrides,
  };
}

export function equipment(overrides: Partial<EquipmentResponse> = {}): EquipmentResponse {
  return {
    id: 10,
    code: 'PRS-001',
    name: 'Presse 200 t',
    workshop: 'Emboutissage',
    criticality: 'CRITICAL',
    status: 'OPERATIONAL',
    operatingHours: 12500,
    commissionedOn: '2019-03-01',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}
