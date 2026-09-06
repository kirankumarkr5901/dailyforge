import { DOCUMENT, Injectable, inject } from '@angular/core';

const GIS_SRC = 'https://accounts.google.com/gsi/client';

interface GoogleCredentialResponse {
  credential: string;
}

/**
 * Loads Google Identity Services, on demand.
 *
 * The script is fetched the first time a sign-in sheet with Google enabled actually
 * opens — not at startup. Most visits never open it, and a third-party script on the
 * critical path of an offline-capable PWA is a cost paid by everyone for a feature used
 * by some.
 *
 * What comes back is an ID token, which is worth nothing on its own: the backend
 * verifies it against Google's public keys before any account is created or matched.
 */
@Injectable({ providedIn: 'root' })
export class GoogleSignInService {
  private readonly document = inject(DOCUMENT);
  private loading: Promise<void> | null = null;

  async renderButton(
    container: HTMLElement,
    clientId: string,
    onCredential: (idToken: string) => void,
  ): Promise<void> {
    await this.load();

    const google = (this.document.defaultView as GoogleWindow | undefined)?.google;
    if (!google) {
      throw new Error('Google Identity Services did not load');
    }

    google.accounts.id.initialize({
      client_id: clientId,
      callback: (response: GoogleCredentialResponse) => onCredential(response.credential),
    });

    // Google renders its own button because its branding rules require it; the sheet
    // gives it a slot sized to match our own controls rather than restyling it.
    google.accounts.id.renderButton(container, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      text: 'continue_with',
      shape: 'rectangular',
      width: container.clientWidth || 320,
    });
  }

  private load(): Promise<void> {
    if (this.loading) {
      return this.loading;
    }

    this.loading = new Promise<void>((resolve, reject) => {
      const existing = this.document.querySelector<HTMLScriptElement>(`script[src="${GIS_SRC}"]`);
      if (existing) {
        resolve();
        return;
      }

      const script = this.document.createElement('script');
      script.src = GIS_SRC;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => {
        // Let the next attempt try again rather than caching the failure forever.
        this.loading = null;
        reject(new Error('Could not load Google Identity Services'));
      };
      this.document.head.appendChild(script);
    });

    return this.loading;
  }
}

interface GoogleWindow extends Window {
  google?: {
    accounts: {
      id: {
        initialize(config: { client_id: string; callback: (r: GoogleCredentialResponse) => void }): void;
        renderButton(container: HTMLElement, options: Record<string, unknown>): void;
      };
    };
  };
}
