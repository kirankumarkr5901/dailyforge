import { TestBed } from '@angular/core/testing';
import { PendingActionService } from './pending-action.service';

describe('PendingActionService', () => {
  let service: PendingActionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(PendingActionService);
  });

  it('replays the captured action once and then forgets it', async () => {
    let runs = 0;
    service.capture({ description: 'Logged.', run: () => void runs++ });

    await service.replay();
    expect(runs).toBe(1);

    // A second replay must not repeat the write — that is how one tap becomes two sets.
    await service.replay();
    expect(runs).toBe(1);
  });

  it('awaits an async action before clearing', async () => {
    let finished = false;
    service.capture({
      description: 'Logged.',
      run: async () => {
        await Promise.resolve();
        finished = true;
      },
    });

    await service.replay();
    expect(finished).toBe(true);
  });

  it('holds only the newest action, so a stale one cannot fire later', async () => {
    const ran: string[] = [];
    service.capture({ description: 'first', run: () => ran.push('first') });
    service.capture({ description: 'second', run: () => ran.push('second') });

    await service.replay();

    expect(ran).toEqual(['second']);
  });

  it('is safe to replay when nothing was captured', async () => {
    await expect(service.replay()).resolves.toBeUndefined();
  });

  it('discards without running', async () => {
    let ran = false;
    service.capture({ description: 'x', run: () => (ran = true) });

    service.discard();
    await service.replay();

    expect(ran).toBe(false);
    expect(service.pending()).toBeNull();
  });
});
