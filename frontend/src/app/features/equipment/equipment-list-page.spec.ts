import { ComponentFixture, TestBed } from '@angular/core/testing';
import { listWorkshops } from '../../api/fn/equipment/list-workshops';
import { searchEquipment } from '../../api/fn/equipment/search-equipment';
import { Role } from '../../core/auth/auth.service';
import { equipment, fakeApi, fakeAuth, fakeRealtime } from '../../../testing/fakes';
import { EquipmentListPage } from './equipment-list-page';

describe('EquipmentListPage', () => {
  let fixture: ComponentFixture<EquipmentListPage>;
  let element: HTMLElement;
  let api: ReturnType<typeof fakeApi>;
  let realtime: ReturnType<typeof fakeRealtime>;

  async function open(role: Role): Promise<void> {
    api = fakeApi([
      [searchEquipment, { content: [equipment(), equipment({ id: 11, code: 'TOUR-01', name: 'Tour CN', status: 'DOWN' })],
        page: 0, size: 20, totalElements: 2, totalPages: 1 }],
      [listWorkshops, ['Emboutissage', 'Usinage']],
    ]);
    realtime = fakeRealtime();
    await TestBed.configureTestingModule({
      imports: [EquipmentListPage],
      providers: [api.provider, fakeAuth(role).provider, realtime.provider],
    }).compileComponents();
    fixture = TestBed.createComponent(EquipmentListPage);
    element = fixture.nativeElement;
    await fixture.whenStable();
  }

  it('lists the machines returned by the server with French labels', async () => {
    await open('MANAGER');

    const rows = element.querySelectorAll('[data-testid="equipment-row"]');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('PRS-001');
    expect(rows[0].textContent).toContain('Critique');
    expect(rows[1].textContent).toContain('En panne');
    expect(api.callsOf(searchEquipment)[0]).toEqual(expect.objectContaining({ page: 0, size: 20, sort: ['code,asc'] }));
  });

  it('sends the search to the server after the user stops typing, from the first page', async () => {
    await open('MANAGER');
    vi.useFakeTimers();
    const search = element.querySelector<HTMLInputElement>('[data-testid="equipment-search"]')!;

    search.value = 'presse';
    search.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(300);
    vi.useRealTimers();
    await fixture.whenStable();

    expect(api.callsOf(searchEquipment).at(-1)).toEqual(expect.objectContaining({ q: 'presse', page: 0 }));
  });

  it('reloads when a machine changes status somewhere', async () => {
    await open('TECHNICIAN');
    const before = api.callsOf(searchEquipment).length;

    realtime.equipment.next({ equipmentId: 10, code: 'PRS-001', status: 'DOWN' });
    await fixture.whenStable();

    expect(api.callsOf(searchEquipment).length).toBe(before + 1);
  });

  it('shows edition controls according to the role', async () => {
    await open('TECHNICIAN');

    expect(element.querySelector('[data-testid="new-equipment"]')).toBeNull();
    expect(element.querySelector('button[mattooltip="Modifier"]')).toBeNull();
  });

  it('lets managers create machines', async () => {
    await open('MANAGER');

    expect(element.querySelector('[data-testid="new-equipment"]')).not.toBeNull();
  });
});
