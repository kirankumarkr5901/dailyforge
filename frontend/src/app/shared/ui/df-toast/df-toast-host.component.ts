import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { LucideAngularModule, X } from 'lucide-angular';
import { ToastService } from './toast.service';

/**
 * Renders the toast stack. Mounted once, in the app shell.
 *
 * `role="status"` with `aria-live="polite"` means the message is announced without
 * stealing focus — important, because the user is usually mid-task when one appears.
 */
@Component({
  selector: 'df-toast-host',
  imports: [LucideAngularModule],
  templateUrl: './df-toast-host.component.html',
  styleUrl: './df-toast-host.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-toast-host' },
})
export class DfToastHostComponent {
  protected readonly toasts = inject(ToastService);
  protected readonly closeIcon = X;
}
