import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { LucideAngularModule, Plus, X } from 'lucide-angular';
import { RestTimerService } from './rest-timer.service';

/** The floating rest-timer pill (spec §8.3 [ADD]). Renders nothing while not running. */
@Component({
  selector: 'df-rest-timer',
  imports: [LucideAngularModule],
  templateUrl: './rest-timer.component.html',
  styleUrl: './rest-timer.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RestTimerComponent {
  protected readonly timer = inject(RestTimerService);
  protected readonly plusIcon = Plus;
  protected readonly closeIcon = X;

  protected readonly display = computed(() => {
    const seconds = this.timer.secondsLeft() ?? 0;
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  });

  protected addThirty(): void {
    this.timer.adjust(30);
  }

  protected dismiss(): void {
    this.timer.dismiss();
  }
}
