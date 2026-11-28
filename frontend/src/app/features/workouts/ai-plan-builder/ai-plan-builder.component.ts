import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { PlanDraft, WorkoutPlanAiApi } from '../../../core/workouts/workout-plan-ai.api';
import { WorkoutPlan } from '../../../core/workouts/workouts.types';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import {
  DfSelectComponent,
  DfSelectOption,
} from '../../../shared/ui/df-select/df-select.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

const EQUIPMENT = [
  { value: 'BARBELL', label: 'Barbell' },
  { value: 'DUMBBELL', label: 'Dumbbells' },
  { value: 'MACHINE', label: 'Machines' },
  { value: 'CABLE', label: 'Cables' },
  { value: 'BODYWEIGHT', label: 'Bodyweight' },
];

/**
 * "Build me a workout plan", inside the plan manager: a few answers, a draft to look
 * over (drop any exercise), then saved as a new plan that isn't active until switched
 * to. Exercises already in the library, and above all ones already logged, are reused
 * so their cards keep their history.
 */
@Component({
  selector: 'df-ai-plan-builder',
  imports: [
    FormsModule,
    DfButtonComponent,
    DfInputComponent,
    DfSelectComponent,
    DfStepperInputComponent,
  ],
  templateUrl: './ai-plan-builder.component.html',
  styleUrl: './ai-plan-builder.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AiPlanBuilderComponent {
  private readonly api = inject(WorkoutPlanAiApi);
  private readonly toasts = inject(ToastService);

  /** A plan was saved: the manager reloads and shows it. */
  readonly created = output<WorkoutPlan>();

  protected readonly equipmentOptions = EQUIPMENT;
  protected readonly goals: DfSelectOption[] = [
    { value: 'build muscle', label: 'Build muscle' },
    { value: 'get stronger', label: 'Get stronger' },
    { value: 'lose fat while keeping muscle', label: 'Lose fat, keep muscle' },
    { value: 'general fitness', label: 'General fitness' },
  ];
  protected readonly levels: DfSelectOption[] = [
    { value: 'beginner (under 1 year)', label: 'Beginner' },
    { value: 'intermediate (1-3 years)', label: 'Intermediate' },
    { value: 'advanced (3+ years)', label: 'Advanced' },
  ];

  protected readonly open = signal(false);
  protected readonly days = signal(4);
  protected readonly minutes = signal(60);
  protected readonly equipment = signal<string[]>(EQUIPMENT.map((e) => e.value));
  protected readonly goal = signal('build muscle');
  protected readonly experience = signal('intermediate (1-3 years)');
  protected readonly limits = signal('');
  protected readonly drafting = signal(false);
  protected readonly saving = signal(false);
  protected readonly draft = signal<PlanDraft | null>(null);

  protected toggleEquipment(value: string): void {
    this.equipment.update((list) =>
      list.includes(value) ? list.filter((v) => v !== value) : [...list, value],
    );
  }

  protected async makeDraft(): Promise<void> {
    if (this.drafting()) return;
    this.drafting.set(true);
    try {
      this.draft.set(
        await firstValueFrom(
          this.api.draft({
            daysPerWeek: this.days(),
            equipment: this.equipment(),
            goal: this.goal(),
            experience: this.experience(),
            sessionMinutes: this.minutes(),
            limits: this.limits().trim(),
          }),
        ),
      );
    } catch (e) {
      this.toasts.show(errorMessage(e) ?? "Couldn't draft a plan right now. Try again.", {
        tone: 'penalty',
      });
    } finally {
      this.drafting.set(false);
    }
  }

  protected rename(name: string): void {
    this.draft.update((d) => (d ? { ...d, name } : d));
  }

  protected remove(dayIndex: number, exerciseIndex: number): void {
    this.draft.update((d) =>
      d
        ? {
            ...d,
            days: d.days
              .map((day, i) =>
                i === dayIndex
                  ? { ...day, exercises: day.exercises.filter((_, j) => j !== exerciseIndex) }
                  : day,
              )
              .filter((day) => day.exercises.length > 0),
          }
        : d,
    );
  }

  protected async save(): Promise<void> {
    const d = this.draft();
    if (!d || this.saving() || d.days.length === 0) return;
    this.saving.set(true);
    try {
      const plan = await firstValueFrom(this.api.save(d));
      this.toasts.show(`"${plan.name}" saved. Make it active when you're ready to switch.`);
      this.draft.set(null);
      this.open.set(false);
      this.created.emit(plan);
    } catch (e) {
      this.toasts.show(errorMessage(e) ?? 'Could not save the plan. Try again.', {
        tone: 'penalty',
      });
    } finally {
      this.saving.set(false);
    }
  }

  protected restSeconds(s: number): string {
    return s >= 60 ? `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')} rest` : `${s}s rest`;
  }
}

function errorMessage(e: unknown): string | null {
  return e instanceof HttpErrorResponse && typeof e.error?.message === 'string'
    ? e.error.message
    : null;
}
