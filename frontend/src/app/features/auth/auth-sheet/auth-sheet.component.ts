import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from '../../../core/api/api.types';
import { AuthSheetService } from '../../../core/auth/auth-sheet.service';
import { GoogleSignInService } from '../../../core/auth/google-sign-in.service';
import { PendingActionService } from '../../../core/auth/pending-action.service';
import { SessionStore } from '../../../core/auth/session.store';
import { TPipe } from '../../../core/i18n/i18n.service';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Sign in or create an account.
 *
 * A sheet rather than a page, because it has to be able to appear over whatever the user
 * was doing — spec §4.1 requires that an anonymous write opens this and then replays the
 * action, which is only possible if the surrounding screen survives.
 */
@Component({
  selector: 'df-auth-sheet',
  imports: [ReactiveFormsModule, TPipe, DfSheetComponent, DfInputComponent, DfButtonComponent],
  templateUrl: './auth-sheet.component.html',
  styleUrl: './auth-sheet.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthSheetComponent {
  private readonly session = inject(SessionStore);
  private readonly pending = inject(PendingActionService);
  private readonly googleSignIn = inject(GoogleSignInService);
  private readonly toasts = inject(ToastService);

  protected readonly sheet = inject(AuthSheetService);
  protected readonly capabilities = this.session.capabilities;

  protected readonly submitting = signal(false);
  protected readonly formError = signal<string | null>(null);

  private readonly googleSlot = viewChild<ElementRef<HTMLElement>>('googleSlot');
  private googleRendered = false;

  protected readonly isSignup = computed(() => this.sheet.mode() === 'signup');

  protected readonly reasonKey = computed(() => {
    switch (this.sheet.reason()) {
      case 'write':
        return 'auth.reason.write' as const;
      case 'expired':
        return 'auth.reason.expired' as const;
      default:
        return 'auth.reason.manual' as const;
    }
  });

  protected readonly form = new FormGroup({
    displayName: new FormControl('', { nonNullable: true }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(10)],
    }),
  });

  constructor() {
    effect(() => {
      // Rendering Google's button needs the slot to exist, which only happens once the
      // sheet is open. Doing it here keeps the component from caring about timing.
      const open = this.sheet.isOpen();
      const clientId = this.capabilities().googleEnabled ? this.googleClientId() : null;
      const slot = this.googleSlot();

      if (open && clientId && slot && !this.googleRendered) {
        this.googleRendered = true;
        void this.renderGoogle(slot.nativeElement, clientId);
      }
      if (!open) {
        this.formError.set(null);
      }
    });
  }

  protected toggleMode(): void {
    this.formError.set(null);
    this.sheet.setMode(this.isSignup() ? 'login' : 'signup');
  }

  protected async submit(): Promise<void> {
    if (this.submitting()) {
      return;
    }

    this.formError.set(null);
    const { email, password, displayName } = this.form.getRawValue();

    if (this.form.controls.email.invalid || this.form.controls.password.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.isSignup() && !displayName.trim()) {
      this.form.controls.displayName.markAsTouched();
      return;
    }

    this.submitting.set(true);
    try {
      if (this.isSignup()) {
        await this.session.signup({
          email,
          password,
          displayName: displayName.trim(),
          timeZone: SessionStore.browserTimeZone(),
        });
      } else {
        await this.session.login({ email, password });
      }
      await this.finish();
    } catch (error) {
      this.formError.set(this.messageFor(error));
    } finally {
      this.submitting.set(false);
    }
  }

  private async renderGoogle(slot: HTMLElement, clientId: string): Promise<void> {
    try {
      await this.googleSignIn.renderButton(slot, clientId, (idToken) =>
        void this.completeGoogle(idToken),
      );
    } catch {
      // A blocked or failed third-party script must not take the password form with it.
      this.googleRendered = false;
    }
  }

  private async completeGoogle(idToken: string): Promise<void> {
    this.submitting.set(true);
    this.formError.set(null);
    try {
      await this.session.loginWithGoogle(idToken);
      await this.finish();
    } catch (error) {
      this.formError.set(this.messageFor(error));
    } finally {
      this.submitting.set(false);
    }
  }

  /** Closes the sheet and runs whatever the user was doing when they were interrupted. */
  private async finish(): Promise<void> {
    this.form.reset();
    this.sheet.close();

    const held = this.pending.pending();
    await this.pending.replay();

    if (held) {
      this.toasts.show(held.description);
    }
  }

  private googleClientId(): string | null {
    // Injected at build time per environment; absent means the feature is off.
    const configured = (globalThis as { __DF_GOOGLE_CLIENT_ID__?: string }).__DF_GOOGLE_CLIENT_ID__;
    return configured && configured.length > 0 ? configured : null;
  }

  /**
   * Maps the backend's stable error code to copy. The server's own message is never
   * shown, so wording stays a frontend decision (spec §4.7).
   */
  private messageFor(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      const body = error.error as ApiError | null;
      switch (body?.code) {
        case 'AUTH_INVALID_CREDENTIALS':
          return 'That email and password do not match.';
        case 'AUTH_EMAIL_TAKEN':
          return 'That email already has an account. Sign in instead.';
        case 'VALIDATION_FAILED':
          return body.message || 'Check the form and try again.';
        case 'OUT_OF_RANGE':
          return 'Too many attempts. Wait a minute and try again.';
        default:
          if (error.status === 0) {
            return 'No connection. Check your network and try again.';
          }
          return 'Something went wrong on our side. Try again.';
      }
    }
    return 'Something went wrong. Try again.';
  }
}
