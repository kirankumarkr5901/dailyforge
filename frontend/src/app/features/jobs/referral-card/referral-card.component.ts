import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Check, Copy, ExternalLink, LucideAngularModule } from 'lucide-angular';

import { Referral, ReferralState } from '../../../core/job/job.types';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfIconButtonComponent } from '../../../shared/ui/df-icon-button/df-icon-button.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * What to do about a referral that has been waiting this long.
 *
 * The backend decides the state; the wording is this app's to write, which is why
 * ReferralState carries no copy of its own. Each line names an action rather than
 * describing a colour, because "orange" tells you nothing you can act on.
 */
const SUGGESTIONS: Record<ReferralState, string> = {
  WAITING: 'Give them a little time — this has not been out long.',
  FOLLOW_UP: 'Worth a polite nudge. A short message beats waiting in silence.',
  APPLY_DIRECTLY: 'Stop waiting on this one. Apply directly — a referral can still land afterwards.',
};

/** The short form on the pill, where there is only room for the verdict. */
const STATE_LABELS: Record<ReferralState, string> = {
  WAITING: 'Waiting',
  FOLLOW_UP: 'Follow up',
  APPLY_DIRECTLY: 'Apply now',
};

/**
 * One referral request: who you asked, for what, how long ago, and what to do next.
 *
 * The copy buttons are the point of the card. A referral ID lives in somebody's chat
 * message and has to be retyped into an application form days later, which is exactly
 * the sort of thing that gets mistyped — so it is one tap to the clipboard rather than
 * a value to squint at.
 */
@Component({
  selector: 'df-referral-card',
  imports: [LucideAngularModule, DfCardComponent, DfIconButtonComponent],
  templateUrl: './referral-card.component.html',
  styleUrl: './referral-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': '"referral-card--" + referral().state.toLowerCase().replace("_", "-")' },
})
export class ReferralCardComponent {
  private readonly toasts = inject(ToastService);

  readonly referral = input.required<Referral>();

  protected readonly copyIcon = Copy;
  protected readonly openIcon = ExternalLink;
  protected readonly doneIcon = Check;

  protected readonly app = computed(() => this.referral().application);

  protected readonly stateLabel = computed(() => STATE_LABELS[this.referral().state]);
  protected readonly suggestion = computed(() => SUGGESTIONS[this.referral().state]);

  /** "Asked today" reads better than "0 days waiting", and one day is not "1 days". */
  protected readonly waitedLabel = computed(() => {
    const days = this.referral().daysWaiting;
    if (!this.app().referralRequestedOn) {
      return 'No date recorded';
    }
    if (days === 0) {
      return 'Asked today';
    }
    return days === 1 ? '1 day waiting' : `${days} days waiting`;
  });

  protected async copy(value: string | null, what: string): Promise<void> {
    if (!value) {
      return;
    }
    try {
      await navigator.clipboard.writeText(value);
      this.toasts.show(`${what} copied.`);
    } catch {
      // Clipboard access is refused outside a secure context, and on some browsers
      // without a user gesture it has already been consumed by. Saying so beats a
      // button that silently does nothing.
      this.toasts.show(`Could not copy the ${what.toLowerCase()}. Select it and copy manually.`);
    }
  }
}
