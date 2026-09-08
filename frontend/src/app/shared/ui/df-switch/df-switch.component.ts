import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let nextId = 0;

/**
 * A two-state switch, for settings that take effect immediately.
 *
 * Built on a real checkbox input with `role="switch"` rather than a styled div, so it
 * is focusable, toggles on space, and participates in forms without extra work.
 */
@Component({
  selector: 'df-switch',
  templateUrl: './df-switch.component.html',
  styleUrl: './df-switch.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-switch' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DfSwitchComponent),
      multi: true,
    },
  ],
})
export class DfSwitchComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);

  protected readonly fieldId = `df-switch-${nextId++}`;
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
