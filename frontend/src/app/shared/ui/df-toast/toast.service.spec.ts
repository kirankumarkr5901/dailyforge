import { TestBed } from '@angular/core/testing';
import { ToastService } from './toast.service';

describe('ToastService', () => {
  let service: ToastService;

  beforeEach(() => {
    vi.useFakeTimers();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ToastService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('shows a toast and dismisses it after the undo window', () => {
    service.show('Logged. +12 points.', { actionLabel: 'Undo', action: () => {} });
    expect(service.toasts().length).toBe(1);

    vi.advanceTimersByTime(5999);
    expect(service.toasts().length).toBe(1);

    vi.advanceTimersByTime(2);
    expect(service.toasts().length).toBe(0);
  });

  it('runs the action and dismisses when invoked', () => {
    let undone = false;
    const id = service.show('Logged.', { actionLabel: 'Undo', action: () => (undone = true) });

    service.invoke(id);

    expect(undone).toBe(true);
    expect(service.toasts().length).toBe(0);
  });

  it('holds the window open while paused, then resumes a full window', () => {
    const id = service.show('Logged.');

    vi.advanceTimersByTime(3000);
    service.pause(id);
    vi.advanceTimersByTime(60_000);
    expect(service.toasts().length).toBe(1);

    service.resume(id);
    vi.advanceTimersByTime(5999);
    expect(service.toasts().length).toBe(1);
    vi.advanceTimersByTime(2);
    expect(service.toasts().length).toBe(0);
  });

  it('keeps at most three toasts, dropping the oldest', () => {
    service.show('One');
    service.show('Two');
    service.show('Three');
    service.show('Four');

    expect(service.toasts().map((toast) => toast.message)).toEqual(['Two', 'Three', 'Four']);
  });

  it('gives each toast its own timer, so a later log does not cut an earlier one short', () => {
    service.show('First');
    vi.advanceTimersByTime(3000);
    service.show('Second');

    vi.advanceTimersByTime(3001);
    // The first has expired on its own schedule; the second still has time left.
    expect(service.toasts().map((toast) => toast.message)).toEqual(['Second']);
  });

  it('does nothing when an already dismissed toast is invoked', () => {
    const id = service.show('Gone');
    service.dismiss(id);
    expect(() => service.invoke(id)).not.toThrow();
    expect(service.toasts().length).toBe(0);
  });
});
