import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AiStatusService } from '../../../core/ai/ai-status.service';
import { BodyApi } from '../../../core/body/body.api';
import {
  BodyDirection,
  BodyGoal,
  BodyMetric,
  BodyProfile,
  BodySex,
} from '../../../core/body/body.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import {
  DfSelectComponent,
  DfSelectOption,
} from '../../../shared/ui/df-select/df-select.component';

/**
 * Body fat and direction on the Body page: the formula (male/female) and goal, the US
 * Navy body-fat estimate from waist, neck (and hips) with its change, and the AI's
 * "am I heading the right way?" read of the last eight weeks.
 */
@Component({
  selector: 'df-body-direction',
  imports: [FormsModule, DfButtonComponent, DfCardComponent, DfSelectComponent],
  templateUrl: './body-direction.component.html',
  styleUrl: './body-direction.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BodyDirectionComponent {
  private readonly api = inject(BodyApi);
  protected readonly ai = inject(AiStatusService);

  /** Reloads the estimates when new entries arrive. */
  readonly entries = input.required<BodyMetric[]>();

  protected readonly sexes: DfSelectOption[] = [
    { value: '', label: 'Choose' },
    { value: 'MALE', label: 'Male formula' },
    { value: 'FEMALE', label: 'Female formula (uses hips)' },
  ];
  protected readonly goals: DfSelectOption[] = [
    { value: '', label: 'Choose' },
    { value: 'LOSE_FAT', label: 'Lose fat' },
    { value: 'BUILD_MUSCLE', label: 'Build muscle' },
    { value: 'RECOMP', label: 'Both (recomp)' },
    { value: 'MAINTAIN', label: 'Maintain' },
  ];

  protected readonly profile = signal<BodyProfile | null>(null);
  protected readonly direction = signal<BodyDirection | null>(null);
  protected readonly checking = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly latest = computed(() => {
    const fat = this.profile()?.bodyFat ?? [];
    return fat.length ? fat[fat.length - 1] : null;
  });
  protected readonly change = computed(() => {
    const fat = this.profile()?.bodyFat ?? [];
    return fat.length >= 2 ? Math.round((fat[fat.length - 1].pct - fat[0].pct) * 10) / 10 : null;
  });

  constructor() {
    void this.ai.load();
    effect(() => {
      this.entries();
      untracked(() => void this.load());
    });
  }

  private async load(): Promise<void> {
    try {
      this.profile.set(await firstValueFrom(this.api.profile()));
    } catch {
      // The rest of the Body page works without it.
    }
  }

  protected async save(sex: string | null, goal: string | null): Promise<void> {
    const p = this.profile();
    try {
      this.profile.set(
        await firstValueFrom(
          this.api.saveProfile(
            ((sex ?? p?.sex) || null) as BodySex | null,
            ((goal ?? p?.goal) || null) as BodyGoal | null,
          ),
        ),
      );
      this.direction.set(null);
    } catch {
      this.error.set('That did not save. Try again.');
    }
  }

  protected async check(): Promise<void> {
    if (this.checking()) return;
    this.checking.set(true);
    this.error.set(null);
    try {
      this.direction.set(await firstValueFrom(this.api.direction()));
    } catch (e) {
      this.error.set(
        e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
          ? e.error.message
          : 'The check could not be done right now.',
      );
    } finally {
      this.checking.set(false);
    }
  }

  protected verdictLabel(v: BodyDirection['verdict']): string {
    return v === 'ON_TRACK'
      ? 'On track'
      : v === 'MIXED'
        ? 'Mixed signals'
        : v === 'OFF_TRACK'
          ? 'Off track'
          : 'Not enough data yet';
  }
}
