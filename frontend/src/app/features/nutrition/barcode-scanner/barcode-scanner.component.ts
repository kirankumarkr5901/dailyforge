import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';

import { SessionStore } from '../../../core/auth/session.store';
import { ProteinApi } from '../../../core/nutrition/protein.api';
import { NutritionAmounts, ProteinDayWrite } from '../../../core/nutrition/protein.types';
import { LogicalDate } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfInputComponent } from '../../../shared/ui/df-input/df-input.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { DfStepperInputComponent } from '../../../shared/ui/df-stepper-input/df-stepper-input.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/** A product from Open Food Facts, values per 100 g. */
interface Product {
  code: string;
  name: string;
  per100: NutritionAmounts;
  servingG: number | null;
}

/** The browser's own barcode reader, where it exists (Chrome on Android). */
interface NativeBarcodeDetector {
  detect(source: HTMLVideoElement): Promise<{ rawValue: string }[]>;
}

const FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e'];

/**
 * Scan a packaged food's barcode and log it from Open Food Facts, a free, open food
 * database. The camera reads the barcode with the browser's built-in detector where it
 * exists (Android Chrome) and otherwise with ZXing, loaded only when the scanner opens
 * (iPhone). The lookup goes straight from the phone to Open Food Facts with plain
 * fetch, so no DailyForge login ever travels with it. Typing the digits is the fallback.
 */
@Component({
  selector: 'df-barcode-scanner',
  imports: [FormsModule, DfButtonComponent, DfInputComponent, DfSheetComponent, DfStepperInputComponent],
  templateUrl: './barcode-scanner.component.html',
  styleUrl: './barcode-scanner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarcodeScannerComponent {
  private readonly api = inject(ProteinApi);
  private readonly toasts = inject(ToastService);
  protected readonly session = inject(SessionStore);

  readonly open = input.required<boolean>();
  readonly date = input.required<LogicalDate>();
  readonly closed = output<void>();
  /** A product was added to today: the page applies the day and points. */
  readonly logged = output<ProteinDayWrite>();
  /** A product was saved as a food: the page reloads its food list. */
  readonly foodSaved = output<void>();

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  protected readonly status = signal<'starting' | 'scanning' | 'looking' | 'found' | 'missing' | 'nocamera'>('starting');
  protected readonly product = signal<Product | null>(null);
  protected readonly servingG = signal(100);
  protected readonly manualCode = signal('');
  protected readonly busy = signal(false);

  /** The product's values for the chosen serving, rounded to whole numbers. */
  protected readonly serving = computed<NutritionAmounts | null>(() => {
    const p = this.product();
    if (!p) return null;
    const f = this.servingG() / 100;
    const r = (v: number) => Math.round(v * f);
    return {
      grams: r(p.per100.grams),
      kcal: r(p.per100.kcal),
      fatG: r(p.per100.fatG),
      carbsG: r(p.per100.carbsG),
      fiberG: r(p.per100.fiberG),
      sugarG: r(p.per100.sugarG),
    };
  });

  private stream: MediaStream | null = null;
  private stopScan: (() => void) | null = null;

  constructor() {
    effect(() => {
      const open = this.open();
      untracked(() => (open ? void this.start() : this.stopCamera()));
    });
    inject(DestroyRef).onDestroy(() => this.stopCamera());
  }

  private async start(): Promise<void> {
    this.product.set(null);
    this.manualCode.set('');
    this.status.set('starting');
    try {
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
    } catch {
      this.status.set('nocamera');
      return;
    }
    // The <video> is in the sheet's template; wait a frame for it to exist.
    await new Promise((r) => requestAnimationFrame(r));
    const video = this.video()?.nativeElement;
    if (!video || !this.stream) return;
    video.srcObject = this.stream;
    await video.play().catch(() => {});
    this.status.set('scanning');

    const Detector = (window as unknown as { BarcodeDetector?: new (o: { formats: string[] }) => NativeBarcodeDetector })
      .BarcodeDetector;
    if (Detector) {
      const detector = new Detector({ formats: FORMATS });
      let running = true;
      const loop = async () => {
        while (running && this.status() === 'scanning') {
          try {
            const found = await detector.detect(video);
            if (found.length > 0) {
              void this.lookup(found[0].rawValue);
              return;
            }
          } catch {
            // A frame that could not be read; try the next one.
          }
          await new Promise((r) => setTimeout(r, 250));
        }
      };
      this.stopScan = () => (running = false);
      void loop();
    } else {
      // iPhone and others: ZXing, loaded on first use only.
      const { BrowserMultiFormatReader } = await import('@zxing/browser');
      const reader = new BrowserMultiFormatReader();
      const controls = await reader.decodeFromVideoElement(video, (result) => {
        if (result && this.status() === 'scanning') {
          void this.lookup(result.getText());
        }
      });
      this.stopScan = () => controls.stop();
    }
  }

  private stopCamera(): void {
    this.stopScan?.();
    this.stopScan = null;
    this.stream?.getTracks().forEach((t) => t.stop());
    this.stream = null;
  }

  protected lookupManual(): void {
    const code = this.manualCode().replace(/\D/g, '');
    if (code.length < 8) {
      this.toasts.show('Type the 8 to 13 digits under the barcode.');
      return;
    }
    void this.lookup(code);
  }

  protected scanAgain(): void {
    void this.start();
  }

  private async lookup(code: string): Promise<void> {
    this.stopCamera();
    this.status.set('looking');
    try {
      // Plain fetch, not HttpClient: the auth interceptor must never see this request.
      const res = await fetch(
        `https://world.openfoodfacts.org/api/v2/product/${encodeURIComponent(code)}.json?fields=product_name,brands,nutriments,serving_quantity`,
      );
      const body = await res.json();
      if (body.status !== 1 || !body.product) {
        this.status.set('missing');
        return;
      }
      const p = body.product;
      const n = p.nutriments ?? {};
      const num = (v: unknown) => (typeof v === 'number' && isFinite(v) ? v : Number(v) || 0);
      const name = [p.brands?.split(',')[0]?.trim(), p.product_name?.trim()].filter(Boolean).join(' ') || `Product ${code}`;
      this.product.set({
        code,
        name: name.slice(0, 50),
        per100: {
          grams: num(n['proteins_100g']),
          kcal: num(n['energy-kcal_100g']),
          fatG: num(n['fat_100g']),
          carbsG: num(n['carbohydrates_100g']),
          fiberG: num(n['fiber_100g']),
          sugarG: num(n['sugars_100g']),
        },
        servingG: p.serving_quantity ? num(p.serving_quantity) : null,
      });
      this.servingG.set(Math.round(p.serving_quantity ? num(p.serving_quantity) : 100) || 100);
      this.status.set('found');
    } catch {
      this.toasts.show('Could not reach Open Food Facts. Check your connection.', { tone: 'penalty' });
      this.status.set('missing');
    }
  }

  private nameWithServing(): string {
    return `${this.product()!.name} ${this.servingG()} g`.slice(0, 60);
  }

  protected async addToToday(): Promise<void> {
    const amounts = this.serving();
    if (!amounts || this.busy()) return;
    if (!amounts.grams && !amounts.kcal && !amounts.fatG) {
      this.toasts.show('This product has no nutrition values on record. Add it as "Something else".');
      return;
    }
    this.busy.set(true);
    try {
      this.logged.emit(await firstValueFrom(this.api.log(this.date(), this.nameWithServing(), amounts)));
      this.closed.emit();
    } catch {
      this.toasts.show('That did not save. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected async saveAsFood(): Promise<void> {
    const amounts = this.serving();
    if (!amounts || this.busy()) return;
    this.busy.set(true);
    try {
      await firstValueFrom(this.api.addFood(this.nameWithServing(), amounts));
      this.foodSaved.emit();
      this.toasts.show('Saved to your foods.');
    } catch {
      this.toasts.show('Could not save it as a food. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected close(): void {
    this.stopCamera();
    this.closed.emit();
  }
}
