import { CdkDrag, CdkDragDrop, CdkDropList } from '@angular/cdk/drag-drop';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { getBoard } from '../../api/fn/work-orders/get-board';
import { WorkOrderSummary } from '../../api/models/work-order-summary';
import { fakeApi, fakeAuth, fakeRealtime, workOrderDetail, workOrderSummary } from '../../../testing/fakes';
import { BoardColumn } from './board-moves';
import { BoardPage } from './board-page';
import { WorkOrderActions } from './work-order-actions.service';

describe('BoardPage', () => {
  let fixture: ComponentFixture<BoardPage>;
  let element: HTMLElement;
  let realtime: ReturnType<typeof fakeRealtime>;
  const actions = { run: vi.fn() };

  beforeEach(async () => {
    actions.run.mockReset();
    realtime = fakeRealtime();
    const api = fakeApi([[getBoard, [
      workOrderSummary({ id: 1, reference: 'OT-00001' }),
      workOrderSummary({ id: 2, reference: 'OT-00002', status: 'IN_PROGRESS', allowedTransitions: ['DONE'] }),
    ]]]);
    await TestBed.configureTestingModule({
      imports: [BoardPage],
      providers: [
        provideRouter([]), api.provider, fakeAuth('TECHNICIAN').provider, realtime.provider,
        { provide: WorkOrderActions, useValue: actions },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(BoardPage);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  const column = (status: BoardColumn) => element.querySelector(`[data-testid="column-${status}"]`)!;
  const page = () => fixture.componentInstance as unknown as {
    onDrop(event: CdkDragDrop<BoardColumn, BoardColumn, WorkOrderSummary>): Promise<void>;
    canDrop(drag: CdkDrag<WorkOrderSummary>, drop: CdkDropList<BoardColumn>): boolean;
  };
  const drop = (card: WorkOrderSummary, target: BoardColumn, isPointerOverContainer = true) =>
    ({ item: { data: card }, container: { data: target }, isPointerOverContainer }) as
      CdkDragDrop<BoardColumn, BoardColumn, WorkOrderSummary>;

  it('shows each card in the column of its status', () => {
    expect(column('OPEN').textContent).toContain('OT-00001');
    expect(column('IN_PROGRESS').textContent).toContain('OT-00002');
    expect(column('DONE').textContent).toContain('Aucun OT');
    expect(element.textContent).toContain('Vous pouvez démarrer et terminer les OT qui vous sont assignés');
  });

  it('moves a card when another user changes it (WebSocket event)', async () => {
    realtime.workOrders.next(workOrderSummary({ id: 1, reference: 'OT-00001', status: 'PLANNED' }));
    await fixture.whenStable();

    expect(column('PLANNED').textContent).toContain('OT-00001');
    expect(column('OPEN').textContent).not.toContain('OT-00001');
  });

  it('defers push events while a card is being dragged', async () => {
    const board = fixture.componentInstance as unknown as { dragStarted(): void; dragEnded(): void };

    board.dragStarted();
    realtime.workOrders.next(workOrderSummary({ id: 3, reference: 'OT-00003' }));
    await fixture.whenStable();
    expect(column('OPEN').textContent).not.toContain('OT-00003');

    board.dragEnded();
    await fixture.whenStable();
    expect(column('OPEN').textContent).toContain('OT-00003');
  });

  it('adds cards created elsewhere', async () => {
    realtime.workOrders.next(workOrderSummary({ id: 3, reference: 'OT-00003' }));
    await fixture.whenStable();

    expect(column('OPEN').textContent).toContain('OT-00003');
  });

  it('only accepts legal drop targets', () => {
    const card = workOrderSummary();
    const target = (status: BoardColumn) => ({ data: status }) as CdkDropList<BoardColumn>;

    expect(page().canDrop({ data: card } as CdkDrag<WorkOrderSummary>, target('PLANNED'))).toBe(true);
    expect(page().canDrop({ data: card } as CdkDrag<WorkOrderSummary>, target('DONE'))).toBe(false);
  });

  it('runs the matching action on drop and applies the result', async () => {
    actions.run.mockResolvedValue(workOrderDetail({ id: 1, reference: 'OT-00001', status: 'PLANNED' }));

    await page().onDrop(drop(workOrderSummary({ id: 1 }), 'PLANNED'));
    await fixture.whenStable();

    expect(actions.run).toHaveBeenCalledWith('plan', expect.objectContaining({ id: 1 }));
    expect(column('PLANNED').textContent).toContain('OT-00001');
  });

  it('ignores a release outside the column the card was last allowed into', async () => {
    await page().onDrop(drop(workOrderSummary({ id: 1 }), 'PLANNED', false));

    expect(actions.run).not.toHaveBeenCalled();
  });

  it('leaves the card in place when the action is dismissed or refused', async () => {
    actions.run.mockResolvedValue(null);

    await page().onDrop(drop(workOrderSummary({ id: 1 }), 'PLANNED'));
    await page().onDrop(drop(workOrderSummary({ id: 1 }), 'DONE'));
    await fixture.whenStable();

    expect(actions.run).toHaveBeenCalledTimes(1);
    expect(column('OPEN').textContent).toContain('OT-00001');
  });
});
