import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { Check, LucideAngularModule } from 'lucide-angular';

import { FORGE_AREA_LABELS, ForgeQuest } from '../../../core/forge/forge.types';

/** One quest card: tap the ring to mark it done, tap again to undo (today only). */
@Component({
  selector: 'df-forge-quest',
  imports: [LucideAngularModule],
  templateUrl: './forge-quest.component.html',
  styleUrl: './forge-quest.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class]': "'area--' + quest().area.toLowerCase()", '[class.is-done]': 'quest().done' },
})
export class ForgeQuestComponent {
  readonly quest = input.required<ForgeQuest>();
  readonly busy = input(false);
  readonly toggled = output<void>();

  protected readonly checkIcon = Check;
  protected readonly areaLabels = FORGE_AREA_LABELS;
}
