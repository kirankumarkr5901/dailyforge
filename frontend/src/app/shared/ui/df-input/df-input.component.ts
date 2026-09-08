import { ChangeDetectionStrategy, Component, forwardRef, input, signal } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';

let nextId = 0;

/**
 * A labelled text input.
 *
 * The label is required and always rendered — no placeholder-as-label, which disappears
 * the moment someone starts typing and leaves the field unidentifiable.
 *
 * The error message is wired through `aria-describedby` and `aria-invalid` so it is
 * announced rather than merely coloured.
 */
@Component({
  selector: 'df-input',
  templateUrl: './df-input.component.html',
  styleUrl: './df-input.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'df-input' },
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => DfInputComponent),
      multi: true,
    },
  ],
})
export class DfInputComponent implements ControlValueAccessor {
  readonly label = input.required<string>();
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);
  readonly type = input<'text' | 'email' | 'password' | 'search' | 'url'>('text');
  readonly placeholder = input('');
  readonly autocomplete = input('off');
  /** Rendered inside the field, e.g. "kg". Decorative: never the only label. */
  readonly suffix = input<string | null>(null);

  protected readonly fieldId = `df-input-${nextId++}`;
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

  protected handleInput(event: Event): void {
    const next = (event.target as HTMLInputElement).value;
    this.value.set(next);
    this.onChange(next);
  }

  protected handleBlur(): void {
    this.onTouched();
  }

  protected describedBy(): string | null {
    const ids: string[] = [];
    if (this.error()) {
      ids.push(`${this.fieldId}-error`);
    } else if (this.hint()) {
      ids.push(`${this.fieldId}-hint`);
    }
    return ids.length ? ids.join(' ') : null;
  }
}
