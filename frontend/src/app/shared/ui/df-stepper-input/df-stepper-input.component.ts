import { ChangeDetectionStrategy, Component, computed, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { LucideAngularModule, Minus, Plus } from 'lucide-angular';

let nextId = 0;

/**
 * A numeric field with decrement and increment controls.
 *
 * This is the workhorse of logging — reps, sets, weight — so it is built for a thumb:
 * 44px targets, a keyboard-typable field between them, and clamping that silently
 * respects min and max rather than letting an impossible value reach the server.
 *
 * Decimals matter: weights are entered as 22.5, reps never are.
 */
@Component({
  selector: 'df-stepper-input',
  imports: [LucideAngularModule],
  templateUrl: './df-stepper-input.component.html',
  styleUrl: './df-stepper-input.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-stepper' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DfStepperInputComponent),
      multi: true,
    },
  ],
})
export class DfStepperInputComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly min = input(0);
  readonly max = input(999);
  readonly step = input(1);
  readonly decimals = input(0);
  readonly suffix = input<string | null>(null);
  readonly hint = input<string | null>(null);

  protected readonly minusIcon = Minus;
  protected readonly plusIcon = Plus;

  protected readonly fieldId = `df-stepper-${nextId++}`;
  protected readonly value = signal(0);
  protected readonly disabled = signal(false);

  protected readonly display = computed(() => this.value().toFixed(this.decimals()));
  protected readonly atMin = computed(() => this.value() <= this.min());
  protected readonly atMax = computed(() => this.value() >= this.max());

  private onChange: (value: number) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: number | null): void {
    this.value.set(this.clamp(value ?? this.min()));
  }

  registerOnChange(fn: (value: number) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected nudge(direction: 1 | -1): void {
    this.commit(this.value() + direction * this.step());
  }

  protected handleInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const parsed = Number.parseFloat(raw);
    if (!Number.isNaN(parsed)) {
      this.commit(parsed);
    }
  }

  /** Re-clamps on blur, so a typed out-of-range value corrects itself visibly. */
  protected handleBlur(event: Event): void {
    const input = event.target as HTMLInputElement;
    const parsed = Number.parseFloat(input.value);
    this.commit(Number.isNaN(parsed) ? this.min() : parsed);
    input.value = this.display();
    this.onTouched();
  }

  private commit(next: number): void {
    const clamped = this.clamp(next);
    this.value.set(clamped);
    this.onChange(clamped);
  }

  private clamp(value: number): number {
    const bounded = Math.min(this.max(), Math.max(this.min(), value));
    const factor = 10 ** this.decimals();
    return Math.round(bounded * factor) / factor;
  }
}
