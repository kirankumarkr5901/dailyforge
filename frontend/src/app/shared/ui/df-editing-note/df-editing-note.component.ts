import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Lock, LucideAngularModule } from 'lucide-angular';

/**
 * Stands in for the create/edit controls a screen hides while "Enable edit" is off, so
 * the missing button reads as a choice the user made — with the way back one tap away —
 * rather than as a feature that vanished.
 */
@Component({
  selector: 'df-editing-note',
  imports: [LucideAngularModule, RouterLink],
  templateUrl: './df-editing-note.component.html',
  styleUrl: './df-editing-note.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DfEditingNoteComponent {
  /** What would be changeable, e.g. "add or change habits". */
  readonly what = input.required<string>();

  protected readonly lockIcon = Lock;
}
