import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Dumbbell, Flame, Footprints, Plus, Trash2 } from 'lucide-angular';

import { MotionChoice, MotionService } from '../../core/motion/motion.service';
import { ThemeChoice, ThemeService } from '../../core/theme/theme.service';
import { LogicalDate } from '../../core/time/logical-date';

import { DfButtonComponent } from '../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../shared/ui/df-card/df-card.component';
import { DfCheckboxComponent } from '../../shared/ui/df-checkbox/df-checkbox.component';
import { DfChipComponent } from '../../shared/ui/df-chip/df-chip.component';
import { DfDateStepperComponent } from '../../shared/ui/df-date-stepper/df-date-stepper.component';
import { DfEmptyStateComponent } from '../../shared/ui/df-empty-state/df-empty-state.component';
import { DfIconButtonComponent } from '../../shared/ui/df-icon-button/df-icon-button.component';
import { DfInputComponent } from '../../shared/ui/df-input/df-input.component';
import { DfScorePillComponent } from '../../shared/ui/df-score-pill/df-score-pill.component';
import { DfSelectComponent, DfSelectOption } from '../../shared/ui/df-select/df-select.component';
import { DfSheetComponent } from '../../shared/ui/df-sheet/df-sheet.component';
import { DfSkeletonComponent } from '../../shared/ui/df-skeleton/df-skeleton.component';
import { DfStepperInputComponent } from '../../shared/ui/df-stepper-input/df-stepper-input.component';
import { DfSwitchComponent } from '../../shared/ui/df-switch/df-switch.component';
import { ToastService } from '../../shared/ui/df-toast/toast.service';

type GalleryWidth = 'phone' | 'column' | 'full';

/**
 * The design gallery.
 *
 * This is a workbench, not a showcase. Every primitive appears in every state, in both
 * themes, at three widths, with reduced motion one click away — so a regression in any of
 * those is visible in seconds rather than discovered at M8.
 *
 * Read the page for what is missing: almost nothing here is warm, because almost nothing
 * here represents something the user earned. If this page ever looks orange, the heat
 * rule has leaked.
 */
@Component({
  selector: 'df-dev-ui',
  imports: [
    ReactiveFormsModule,
    DfButtonComponent,
    DfCardComponent,
    DfCheckboxComponent,
    DfChipComponent,
    DfDateStepperComponent,
    DfEmptyStateComponent,
    DfIconButtonComponent,
    DfInputComponent,
    DfScorePillComponent,
    DfSelectComponent,
    DfSheetComponent,
    DfSkeletonComponent,
    DfStepperInputComponent,
    DfSwitchComponent,
  ],
  templateUrl: './dev-ui.component.html',
  styleUrl: './dev-ui.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DevUiComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly motion = inject(MotionService);
  protected readonly toasts = inject(ToastService);

  protected readonly plusIcon = Plus;
  protected readonly trashIcon = Trash2;
  protected readonly dumbbellIcon = Dumbbell;
  protected readonly runIcon = Footprints;
  protected readonly flameIcon = Flame;

  protected readonly width = signal<GalleryWidth>('column');

  private readonly pill = viewChild.required(DfScorePillComponent);
  protected readonly score = signal(4821);

  /**
   * A fixed date, not today's. The gallery must not call `new Date()` for a logical
   * date any more than the app may (spec §4.2) — the user's today comes from the server.
   */
  protected readonly demoToday: LogicalDate = '2026-03-12';
  protected readonly demoDate = signal<LogicalDate>('2026-03-12');

  protected readonly sheetOpen = signal(false);

  protected readonly nameControl = new FormControl('Bench press');
  protected readonly emptyControl = new FormControl('');
  protected readonly disabledControl = new FormControl({ value: 'Locked', disabled: true });
  protected readonly repsControl = new FormControl(8);
  protected readonly weightControl = new FormControl(42.5);
  protected readonly strictControl = new FormControl(true);
  protected readonly doneControl = new FormControl(true);
  protected readonly pendingControl = new FormControl(false);
  protected readonly typeControl = new FormControl('LONG');

  protected readonly runTypes: readonly DfSelectOption[] = [
    { value: 'LONG', label: 'Long' },
    { value: 'INTERVAL', label: 'Interval' },
    { value: 'TEMPO', label: 'Tempo' },
  ];

  protected readonly themeChoices: readonly ThemeChoice[] = ['SYSTEM', 'LIGHT', 'DARK'];
  protected readonly motionChoices: readonly MotionChoice[] = ['SYSTEM', 'FULL', 'REDUCED'];
  protected readonly widthChoices: readonly GalleryWidth[] = ['phone', 'column', 'full'];

  protected readonly widthLabel = computed(
    () => ({ phone: '360', column: '560', full: 'Full' })[this.width()],
  );

  /**
   * The same display thresholds the score pill uses, so the specimens below and the
   * pill in the bar cannot drift apart.
   */
  protected readonly heatThresholds = [0, 25, 100, 300] as const;

  /** Four amounts chosen to land on four different steps, so the ramp is visible. */
  protected readonly rampDemo = [
    { amount: 12, label: 'a habit tick' },
    { amount: 40, label: 'a short run' },
    { amount: 150, label: 'a half marathon' },
    { amount: 400, label: 'a marathon' },
  ];

  protected stepFor(amount: number): 1 | 2 | 3 | 4 {
    const value = Math.abs(amount);
    const [, two, three, four] = this.heatThresholds;
    if (value >= four) return 4;
    if (value >= three) return 3;
    if (value >= two) return 2;
    return 1;
  }

  /** Colour tokens rendered as swatches, so a bad value is visible rather than theoretical. */
  protected readonly surfaceTokens = [
    '--surface',
    '--surface-raised',
    '--surface-sunken',
    '--surface-inset',
  ];
  protected readonly inkTokens = ['--ink', '--ink-muted', '--ink-faint'];
  protected readonly lineTokens = ['--line', '--line-strong'];
  protected readonly earnedTokens = [
    '--earned-0',
    '--earned-1',
    '--earned-2',
    '--earned-3',
    '--earned-4',
    '--earned-5',
  ];
  protected readonly semanticTokens = ['--done', '--penalty', '--info', '--focus'];
  protected readonly equipmentTokens = [
    '--equip-barbell',
    '--equip-dumbbell',
    '--equip-machine',
    '--equip-bodyweight',
    '--equip-cardio',
  ];

  protected readonly typeScale = [
    { token: '--text-3xl', label: '46 — display' },
    { token: '--text-2xl', label: '34 — page title' },
    { token: '--text-xl', label: '26 — section' },
    { token: '--text-lg', label: '20 — card heading' },
    { token: '--text-base', label: '16 — body' },
    { token: '--text-sm', label: '14 — secondary' },
    { token: '--text-xs', label: '12 — meta' },
  ];

  protected setTheme(choice: ThemeChoice): void {
    this.theme.set(choice);
  }

  protected setMotion(choice: MotionChoice): void {
    this.motion.set(choice);
  }

  /** Mimics an award round trip: the server returns a new total, the pill counts to it. */
  protected award(delta: number): void {
    const from = this.score();
    const to = from + delta;
    this.score.set(to);
    this.pill().award(from, to);
  }

  protected showNeutralToast(): void {
    this.toasts.show('Plan saved.');
  }

  protected showEarnedToast(amount = 12): void {
    this.toasts.show(`Logged. +${amount} points.`, {
      tone: 'earned',
      heatStep: this.stepFor(amount),
      actionLabel: 'Undo',
      action: () => this.award(-amount),
    });
  }

  protected showPenaltyToast(): void {
    this.toasts.show('Cold shower missed. −10 points.', { tone: 'penalty' });
  }
}
