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
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

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

interface NativeBarcodeDetectorClass {
  new (o: { formats: string[] }): NativeBarcodeDetector;
  getSupportedFormats?(): Promise<string[]>;
}

/**
 * How long the built-in reader gets before ZXing takes over. Some Android phones have
 * the reader but lack the Google module behind it, so it runs and never finds anything.
 */
const NATIVE_GRACE_MS = 4000;

const FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e'];

/**
 * Scan a packaged food's barcode and log it from Open Food Facts, a free, open food
 * database. The camera reads the barcode with the browser's built-in detector where it
 * exists (Android Chrome) and otherwise with ZXing, loaded only when the scanner opens
 * (iPhone). The lookup goes through the DailyForge API, which asks Open Food Facts;
 * the phone never calls it directly. Typing the digits is the fallback.
 */
@Component({
  selector: 'df-barcode-scanner',
  imports: [
    FormsModule,
    DfButtonComponent,
    DfInputComponent,
    DfSheetComponent,
    DfStepperInputComponent,
  ],
  templateUrl: './barcode-scanner.component.html',
  styleUrl: './barcode-scanner.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BarcodeScannerComponent {
  private readonly api = inject(ProteinApi);
  private readonly toasts = inject(ToastService);

  readonly open = input.required<boolean>();
  readonly date = input.required<LogicalDate>();
  readonly closed = output<void>();
  /** A product was added to today: the page applies the day and points. */
  readonly logged = output<ProteinDayWrite>();
  /** A product was saved as a food: the page reloads its food list. */
  readonly foodSaved = output<void>();

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  protected readonly status = signal<
    'starting' | 'scanning' | 'looking' | 'found' | 'missing' | 'nocamera'
  >('starting');
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
      this.stream = await openCamera();
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

    const formats = await nativeFormats();
    if (formats.length) {
      const Detector = (window as unknown as { BarcodeDetector: NativeBarcodeDetectorClass })
        .BarcodeDetector;
      const detector = new Detector({ formats });
      let running = true;
      const startedAt = Date.now();
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
          if (Date.now() - startedAt > NATIVE_GRACE_MS) {
            // Nothing yet: the built-in reader may not work on this phone. ZXing reads too.
            running = false;
            void this.startZxing(video);
            return;
          }
          await new Promise((r) => setTimeout(r, 200));
        }
      };
      this.stopScan = () => (running = false);
      void loop();
    } else {
      await this.startZxing(video);
    }
  }

  /** ZXing, loaded on first use only: iPhone, and Android where the built-in reader fails. */
  private async startZxing(video: HTMLVideoElement): Promise<void> {
    if (this.status() !== 'scanning') return;
    const { BrowserMultiFormatReader } = await import('@zxing/browser');
    if (this.status() !== 'scanning') return;
    const reader = new BrowserMultiFormatReader();
    const controls = await reader.decodeFromVideoElement(video, (result) => {
      if (result && this.status() === 'scanning') {
        void this.lookup(result.getText());
      }
    });
    this.stopScan = () => controls.stop();
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

  /** The phone's camera app can't open while this page holds the camera. */
  protected releaseCamera(): void {
    this.stopCamera();
  }

  /** The fallback: a photo from the phone's camera app, which focuses far better than a web page can. */
  protected async onBarcodePhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.stopCamera();
    this.status.set('looking');
    const code = await readBarcodeFromPhoto(file);
    if (code) {
      void this.lookup(code);
      return;
    }
    this.toasts.show(
      'No barcode found in that photo. Get close so the barcode fills most of the picture.',
      {
        tone: 'penalty',
      },
    );
    this.status.set('missing');
  }

  protected scanAgain(): void {
    void this.start();
  }

  private async lookup(code: string): Promise<void> {
    this.stopCamera();
    this.status.set('looking');
    try {
      // Through our own API: the phone only ever talks to the server it already reaches.
      const p = await firstValueFrom(this.api.barcode(code));
      this.product.set({
        code: p.code,
        name: p.name,
        per100: {
          grams: p.proteinG,
          kcal: p.kcal,
          fatG: p.fatG,
          carbsG: p.carbsG,
          fiberG: p.fiberG,
          sugarG: p.sugarG,
        },
        servingG: p.servingG,
      });
      this.servingG.set(Math.round(p.servingG ?? 100) || 100);
      this.status.set('found');
    } catch (error) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.status.set('missing');
        return;
      }
      const message =
        error instanceof HttpErrorResponse && error.status === 0
          ? 'No connection. Check your signal and try again.'
          : ((error as HttpErrorResponse)?.error?.message ??
            'Could not look that up. Try again in a minute.');
      this.toasts.show(message, { tone: 'penalty' });
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
      this.toasts.show(
        'This product has no nutrition values on record. Add it as "Something else".',
      );
      return;
    }
    this.busy.set(true);
    try {
      this.logged.emit(
        await firstValueFrom(this.api.log(this.date(), this.nameWithServing(), amounts)),
      );
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
      await firstValueFrom(this.api.addFood(this.nameWithServing(), amounts, 'BARCODE'));
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

/**
 * The back camera, as sharp as a web page can get it. Phones with several back lenses
 * often hand a web page the ultra-wide one, which can't focus up close: so pick the
 * main back camera (the lowest-numbered one; Android names them "camera2 0, facing
 * back"), ask for up to 1080p with continuous autofocus, and zoom in a little so the
 * phone can be held far enough back to focus.
 */
async function openCamera(): Promise<MediaStream> {
  let stream = await getBackCamera();
  try {
    const main = await mainBackCameraId();
    const current = stream.getVideoTracks()[0]?.getSettings().deviceId;
    if (main && main !== current) {
      stream.getTracks().forEach((t) => t.stop());
      stream = await getBackCamera(main);
    }
  } catch {
    // Keep the camera we have.
  }
  await sharpen(stream.getVideoTracks()[0]);
  return stream;
}

async function getBackCamera(deviceId?: string): Promise<MediaStream> {
  const video: MediaTrackConstraints = deviceId
    ? { deviceId: { exact: deviceId }, width: { ideal: 1920 }, height: { ideal: 1080 } }
    : { facingMode: 'environment', width: { ideal: 1920 }, height: { ideal: 1080 } };
  try {
    return await navigator.mediaDevices.getUserMedia({ video, audio: false });
  } catch (e) {
    // A camera that refuses those sizes: ask for it at any size.
    if (e instanceof DOMException && e.name === 'OverconstrainedError') {
      return navigator.mediaDevices.getUserMedia({
        video: deviceId ? { deviceId: { exact: deviceId } } : { facingMode: 'environment' },
        audio: false,
      });
    }
    throw e;
  }
}

/** The main back camera's id, once camera access is granted (labels are empty before). */
async function mainBackCameraId(): Promise<string | null> {
  const cameras = (await navigator.mediaDevices.enumerateDevices()).filter(
    (d) => d.kind === 'videoinput',
  );
  const back = cameras.filter(
    (d) => /back|rear|environment/i.test(d.label) && !/front|user/i.test(d.label),
  );
  if (back.length < 2) return null;
  const notSpecial = back.filter((d) => !/ultra|wide|macro|tele|depth/i.test(d.label));
  const pool = notSpecial.length ? notSpecial : back;
  const number = (d: MediaDeviceInfo) => Number(/(\d+)/.exec(d.label)?.[1] ?? 99);
  return [...pool].sort((x, y) => number(x) - number(y))[0]?.deviceId ?? null;
}

/** Continuous autofocus and a little zoom, where the camera offers them. */
async function sharpen(track: MediaStreamTrack | undefined): Promise<void> {
  if (!track?.getCapabilities) return;
  const caps = track.getCapabilities() as {
    focusMode?: string[];
    zoom?: { min: number; max: number };
  };
  const advanced: Record<string, unknown> = {};
  if (caps.focusMode?.includes('continuous')) advanced['focusMode'] = 'continuous';
  if (caps.zoom && caps.zoom.max >= 1.5) advanced['zoom'] = Math.min(2, caps.zoom.max);
  if (Object.keys(advanced).length) {
    await track
      .applyConstraints({ advanced: [advanced as MediaTrackConstraintSet] })
      .catch(() => {});
  }
}

/**
 * Reads a barcode from a still photo (the phone's own camera app: full resolution and
 * proper focus). The built-in reader first, then ZXing. Null when there's none in it.
 */
async function readBarcodeFromPhoto(file: Blob): Promise<string | null> {
  const formats = await nativeFormats();
  if (formats.length) {
    try {
      const Detector = (window as unknown as { BarcodeDetector: NativeBarcodeDetectorClass })
        .BarcodeDetector;
      const bitmap = await createImageBitmap(file);
      const found = await new Detector({ formats }).detect(bitmap as unknown as HTMLVideoElement);
      bitmap.close();
      if (found.length) return found[0].rawValue;
    } catch {
      // Fall through to ZXing.
    }
  }
  const url = URL.createObjectURL(file);
  try {
    const { BrowserMultiFormatReader } = await import('@zxing/browser');
    const result = await new BrowserMultiFormatReader().decodeFromImageUrl(url);
    return result.getText();
  } catch {
    return null;
  } finally {
    URL.revokeObjectURL(url);
  }
}

/** The barcode formats the built-in reader says it can read, or none when it can't. */
async function nativeFormats(): Promise<string[]> {
  const Detector = (window as unknown as { BarcodeDetector?: NativeBarcodeDetectorClass })
    .BarcodeDetector;
  if (!Detector) return [];
  try {
    const supported = (await Detector.getSupportedFormats?.()) ?? FORMATS;
    return FORMATS.filter((f) => supported.includes(f));
  } catch {
    return [];
  }
}
