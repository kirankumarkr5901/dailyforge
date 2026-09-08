import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { LucideAngularModule, LucideIconData } from 'lucide-angular';

/**
 * An empty state always proposes a next action (spec §9.6). `title` and `body` are
 * required, and the action is projected in, so writing one without a way forward takes
 * deliberate effort.
 */
@Component({
  selector: 'df-empty-state',
  imports: [LucideAngularModule],
  templateUrl: './df-empty-state.component.html',
  styleUrl: './df-empty-state.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-empty-state' },
})
export class DfEmptyStateComponent {
  readonly icon = input<LucideIconData | null>(null);
  readonly title = input.required<string>();
  readonly body = input.required<string>();
}
