import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { Hammer } from 'lucide-angular';

import { DfEmptyStateComponent } from '../../shared/ui/df-empty-state/df-empty-state.component';

/**
 * Stands in for a screen a later milestone builds.
 *
 * It says plainly which milestone owns it rather than showing a spinner or an empty box,
 * because "not built yet" and "broken" look identical otherwise — and the shell around
 * it is real, so the navigation is worth exercising now.
 */
@Component({
  selector: 'df-placeholder',
  imports: [DfEmptyStateComponent],
  templateUrl: './placeholder.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PlaceholderComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly icon = Hammer;

  protected readonly title = toSignal(
    this.route.data.pipe(map((data) => (data['title'] as string) ?? 'Not built yet')),
    { initialValue: 'Not built yet' },
  );

  protected readonly body = toSignal(
    this.route.data.pipe(map((data) => (data['body'] as string) ?? '')),
    { initialValue: '' },
  );
}
