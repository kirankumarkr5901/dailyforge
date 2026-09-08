import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let nextId = 0;

/**
 * A checkbox with a drawn tick (spec §9.4: "checkmarks draw").
 *
 * The tick is an SVG path animated by stroke-dashoffset, which stays on the compositor
 * and costs nothing. Under reduced motion the path is simply present at full length —
 * the state still changes, it just does not travel.
 */
@Component({
  selector: 'df-checkbox',
  templateUrl: './df-checkbox.component.html',
  styleUrl: './df-checkbox.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-checkbox' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DfCheckboxComponent),
      multi: true,
    },
  ],
})
export class DfCheckboxComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);

  protected readonly fieldId = `df-checkbox-${nextId++}`;
  protected readonly checked = signal(false);
  protected readonly disabled = signal(false);

  private onChange: (value: boolean) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: boolean | null): void {
    this.checked.set(!!value);
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected toggle(event: Event): void {
    const next = (event.target as HTMLInputElement).checked;
    this.checked.set(next);
    this.onChange(next);
    this.onTouched();
  }
}
