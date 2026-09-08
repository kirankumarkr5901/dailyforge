import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { AuthApi } from '../../core/auth/auth.api';
import { AuthSheetService } from '../../core/auth/auth-sheet.service';
import { SessionStore } from '../../core/auth/session.store';
import { TPipe } from '../../core/i18n/i18n.service';
import { MotionChoice, MotionService } from '../../core/motion/motion.service';
import { OnboardingService } from '../../core/onboarding/onboarding.service';
import { ThemeChoice, ThemeService } from '../../core/theme/theme.service';
import { DfButtonComponent } from '../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../shared/ui/df-card/df-card.component';
import { DfEmptyStateComponent } from '../../shared/ui/df-empty-state/df-empty-state.component';
import { DfSelectComponent, DfSelectOption } from '../../shared/ui/df-select/df-select.component';
import { DfStepperInputComponent } from '../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../shared/ui/df-toast/toast.service';
import { RestTimerService } from '../workouts/rest-timer/rest-timer.service';
import { LucideAngularModule, Settings as SettingsIcon } from 'lucide-angular';

/**
 * Settings.
 *
 * Theme and motion apply immediately and locally — they are how the interface behaves,
 * and waiting for a round trip to change them would feel broken. The time zone and unit
 * system are server state, because streaks resolve through them and every device must
 * agree.
 */
@Component({
  selector: 'df-settings',
  imports: [
    FormsModule,
    TPipe,
    LucideAngularModule,
    DfCardComponent,
    DfSelectComponent,
    DfStepperInputComponent,
    DfButtonComponent,
    DfEmptyStateComponent,
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SettingsComponent {
  private readonly api = inject(AuthApi);
  private readonly toasts = inject(ToastService);

  protected readonly session = inject(SessionStore);
  protected readonly theme = inject(ThemeService);
  protected readonly motion = inject(MotionService);
  protected readonly authSheet = inject(AuthSheetService);
  protected readonly onboarding = inject(OnboardingService);
  protected readonly restTimer = inject(RestTimerService);

  protected readonly settingsIcon = SettingsIcon;
  protected readonly saving = signal(false);

  protected readonly themeOptions: readonly DfSelectOption[] = [
    { value: 'SYSTEM', label: 'System' },
    { value: 'LIGHT', label: 'Light' },
    { value: 'DARK', label: 'Dark' },
  ];

  protected readonly motionOptions: readonly DfSelectOption[] = [
    { value: 'SYSTEM', label: 'System' },
    { value: 'FULL', label: 'Full' },
    { value: 'REDUCED', label: 'Reduced' },
  ];

  protected readonly unitOptions: readonly DfSelectOption[] = [
    { value: 'METRIC', label: 'Metric (kg, km)' },
    { value: 'IMPERIAL', label: 'Imperial (lb, mi)' },
  ];

  /**
   * The full IANA list is thousands of entries; a select of that size is unusable. The
   * browser's own zone is offered plus a short common set, and anything else can be set
   * through the API until a searchable picker exists.
   */
  protected readonly timeZoneOptions = computed<readonly DfSelectOption[]>(() => {
    const current = this.session.user()?.settings.timeZone ?? SessionStore.browserTimeZone();
    const common = [
      SessionStore.browserTimeZone(),
      'UTC',
      'Asia/Kolkata',
      'Europe/London',
      'Europe/Berlin',
      'America/New_York',
      'America/Los_Angeles',
      'Australia/Sydney',
    ];
    const unique = Array.from(new Set([current, ...common]));
    return unique.map((zone) => ({ value: zone, label: zone }));
  });

  protected readonly currentTimeZone = computed(
    () => this.session.user()?.settings.timeZone ?? SessionStore.browserTimeZone(),
  );

  protected readonly currentUnits = computed(
    () => this.session.user()?.settings.unitSystem ?? 'METRIC',
  );

  protected setTheme(value: string): void {
    this.theme.set(value as ThemeChoice);
    void this.persist({ theme: value });
  }

  protected setMotion(value: string): void {
    this.motion.set(value as MotionChoice);
  }

  protected setTimeZone(value: string): void {
    void this.persist({ timeZone: value });
  }

  protected setUnits(value: string): void {
    void this.persist({ unitSystem: value });
  }

  protected setRestTimerDefault(seconds: number): void {
    this.restTimer.setDefault(seconds);
  }

  protected replayTour(): void {
    this.onboarding.replay();
  }

  protected signIn(): void {
    this.authSheet.open('manual');
  }

  protected async signOut(): Promise<void> {
    await this.session.logout();
  }

  /** Server-side settings only. Anonymous visitors keep their local preferences. */
  private async persist(patch: Record<string, unknown>): Promise<void> {
    if (!this.session.isAuthenticated()) {
      return;
    }

    this.saving.set(true);
    try {
      const settings = await firstValueFrom(
        this.api.updateSettings(patch, this.session.user()?.settings.version),
      );
      const user = this.session.user();
      if (user) {
        this.session.patchUser({ ...user, settings });
      }
      this.toasts.show('Saved.');
    } catch {
      this.toasts.show('That did not save. Check your connection and try again.', {
        tone: 'penalty',
      });
    } finally {
      this.saving.set(false);
    }
  }
}
