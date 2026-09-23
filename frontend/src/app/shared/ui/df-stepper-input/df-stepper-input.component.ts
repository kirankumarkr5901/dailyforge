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
 * Decimals matter: weights are entered as 22.5, reps never are. `decimals` is a
 * precision ceiling for rounding, not a format to force on every value — a whole-number
 * weight like 60 displays as "60", not "60.0"; a typed 62.5 is not reformatted back to
 * "62.5" on every keystroke while the field is still focused, only once it settles on
 * blur or a nudge. The field never invents precision the user did not ask for.
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

  /** What the input actually shows. Only re-synced from `value` on blur or a nudge —
   * never on every keystroke, so typing "62.5" is never fought mid-digit. */
  protected readonly display = signal('0');
  protected readonly atMin = computed(() => this.value() <= this.min());
  protected readonly atMax = computed(() => this.value() >= this.max());

  private onChange: (value: number) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: number | null): void {
    const clamped = this.clamp(value ?? this.min());
    this.value.set(clamped);
    this.display.set(this.naturalDisplay(clamped));
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
    this.display.set(this.naturalDisplay(this.value()));
  }

  /** Live text only — the numeric value updates so the parent sees it as the user
   * types, but the field's own displayed text is left alone until blur. */
  protected handleInput(event: Event): void {
    const raw = (event.target as HTMLInputElement).value;
    const parsed = Number.parseFloat(raw);
    if (!Number.isNaN(parsed)) {
      const clamped = this.clamp(parsed);
      this.value.set(clamped);
      this.onChange(clamped);
    }
  }

  /** Re-clamps and reformats on blur, so a typed out-of-range value corrects itself
   * visibly and a stray trailing "." or extra zero settles to a clean number. */
  protected handleBlur(event: Event): void {
    const input = event.target as HTMLInputElement;
    const parsed = Number.parseFloat(input.value);
    this.commit(Number.isNaN(parsed) ? this.min() : parsed);
    this.display.set(this.naturalDisplay(this.value()));
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

  /** A whole number reads as "60", not "60.0" — decimals only appear when the value
   * actually has them, up to the field's own precision ceiling. */
  private naturalDisplay(value: number): string {
    return String(Number(value.toFixed(this.decimals())));
  }
}
