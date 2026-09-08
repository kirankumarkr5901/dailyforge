import { Injectable, Pipe, PipeTransform, inject } from '@angular/core';
import { STRINGS, StringKey } from './strings';

export type TParams = Record<string, string | number>;

@Injectable({ providedIn: 'root' })
export class I18nService {
  /**
   * Look up a string. Missing keys return the key itself rather than an empty space, so
   * a gap is visible in the UI during development instead of silently blank.
   */
  t(key: StringKey, params?: TParams): string {
    const template: string = STRINGS[key] ?? key;
    if (!params) {
      return template;
    }
    return template.replace(/\{(\w+)\}/g, (match, name: string) =>
      name in params ? String(params[name]) : match,
    );
  }
}

/** Template form: `{{ 'action.undo' | t }}`. */
@Pipe({ name: 't' })
export class TPipe implements PipeTransform {
  private readonly i18n = inject(I18nService);

  transform(key: StringKey, params?: TParams): string {
    return this.i18n.t(key, params);
  }
}
