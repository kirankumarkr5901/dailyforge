import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ToastService } from '../../shared/ui/df-toast/toast.service';
import { LogicalDate } from '../time/logical-date';
import { WorkoutOutboxService, isOffline } from './workout-outbox.service';
import { WorkoutsApi } from './workouts.api';

const noSignal = () => throwError(() => new HttpErrorResponse({ status: 0 }));
const refused = () => throwError(() => new HttpErrorResponse({ status: 403 }));

function pendingSet(clientId: string, reps: number) {
  return {
    kind: 'set' as const,
    clientId,
    label: `60 kg × ${reps}`,
    payload: {
      date: '2026-11-27' as LogicalDate,
      exerciseId: 'bench',
      weightMode: 'COMBINED' as const,
      enteredWeight: 60,
      reps,
      clientId,
    },
  };
}

describe('WorkoutOutboxService', () => {
  let api: { logSet: ReturnType<typeof vi.fn>; completeExercise: ReturnType<typeof vi.fn> };
  let toasts: { show: ReturnType<typeof vi.fn> };
  let outbox: WorkoutOutboxService;

  beforeEach(() => {
    localStorage.clear();
    api = { logSet: vi.fn(() => of({})), completeExercise: vi.fn(() => of({})) };
    toasts = { show: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        WorkoutOutboxService,
        { provide: WorkoutsApi, useValue: api },
        { provide: ToastService, useValue: toasts },
      ],
    });
    outbox = TestBed.inject(WorkoutOutboxService);
  });

  it('tells a missing connection apart from a refusal', () => {
    expect(isOffline(new HttpErrorResponse({ status: 0 }))).toBe(true);
    expect(isOffline(new HttpErrorResponse({ status: 403 }))).toBe(false);
    expect(isOffline(new Error('x'))).toBe(false);
  });

  it('keeps queued sets across a reload of the app', () => {
    api.logSet.mockImplementation(noSignal);
    outbox.enqueue(pendingSet('a', 8));

    const stored = JSON.parse(localStorage.getItem('dailyforge.workout-outbox') ?? '[]');
    expect(stored).toHaveLength(1);
    expect(stored[0].clientId).toBe('a');
  });

  it('sends in order, each with its own id, and empties the queue', async () => {
    api.logSet.mockImplementation(noSignal);
    outbox.enqueue(pendingSet('a', 8));
    outbox.enqueue(pendingSet('b', 6));

    api.logSet.mockImplementation(() => of({}));
    await outbox.flush();

    expect(api.logSet.mock.calls.map((c) => c[0].clientId)).toEqual(['a', 'b']);
    expect(outbox.pending()).toHaveLength(0);
  });

  it('stops at the first "no signal" and keeps the rest for later', async () => {
    api.logSet.mockImplementation(noSignal);
    outbox.enqueue(pendingSet('a', 8));
    outbox.enqueue(pendingSet('b', 6));

    await outbox.flush();

    expect(outbox.pending().map((p) => p.clientId)).toEqual(['a', 'b']);
  });

  it('drops a write the server refuses outright, and says so', async () => {
    api.logSet.mockImplementation(refused);
    outbox.enqueue(pendingSet('a', 8));

    await outbox.flush();

    expect(outbox.pending()).toHaveLength(0);
    expect(toasts.show).toHaveBeenCalled();
  });
});
