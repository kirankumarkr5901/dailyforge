import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { ChevronDown, LucideAngularModule } from 'lucide-angular';

let nextId = 0;

export interface DfSelectOption {
  value: string;
  label: string;
  disabled?: boolean;
}

/**
 * A styled native `<select>`.
 *
 * Native on purpose: on a phone this opens the platform picker, which is faster and more
 * familiar than any custom listbox, and it costs nothing in accessibility. A custom
 * overlay would be a downgrade dressed as craft.
 */
@Component({
  selector: 'df-select',
  imports: [LucideAngularModule],
  templateUrl: './df-select.component.html',
  styleUrl: './df-select.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-select' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DfSelectComponent),
      multi: true,
    },
  ],
})
export class DfSelectComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly options = input.required<readonly DfSelectOption[]>();
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);

  protected readonly chevron = ChevronDown;
  protected readonly fieldId = `df-select-${nextId++}`;
  protected readonly value = signal('');
  protected readonly disabled = signal(false);

  private onChange: (value: string) => void = () => {};
  private onTouched: () => void = () => {};

  writeValue(value: string | null): void {
    this.value.set(value ?? '');
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled.set(isDisabled);
  }

  protected handleChange(event: Event): void {
    const next = (event.target as HTMLSelectElement).value;
    this.value.set(next);
    this.onChange(next);
    this.onTouched();
  }
}
