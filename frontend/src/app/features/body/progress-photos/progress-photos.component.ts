import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Camera, ChevronLeft, ChevronRight, LucideAngularModule, Trash2 } from 'lucide-angular';

import { Photo, PhotosApi } from '../../../core/photos/photos.api';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/**
 * Progress photos on the Body page: take or pick one (dated today), see them as a
 * grid, tap one to view it large, step through, or delete it. Private: every image is
 * shown through a short-lived link from the server; nothing in storage is public.
 */
@Component({
  selector: 'df-progress-photos',
  imports: [LucideAngularModule, DfButtonComponent, DfCardComponent, DfSheetComponent],
  templateUrl: './progress-photos.component.html',
  styleUrl: './progress-photos.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgressPhotosComponent {
  private readonly api = inject(PhotosApi);
  private readonly toasts = inject(ToastService);

  /** Today, for dating a new photo. */
  readonly today = input.required<LogicalDate>();

  protected readonly cameraIcon = Camera;
  protected readonly prevIcon = ChevronLeft;
  protected readonly nextIcon = ChevronRight;
  protected readonly deleteIcon = Trash2;
  protected readonly formatLong = formatLong;

  protected readonly enabled = signal<boolean | null>(null);
  protected readonly photos = signal<Photo[]>([]);
  protected readonly uploading = signal(false);
  protected readonly viewing = signal<number | null>(null);
  protected readonly current = computed(() => {
    const i = this.viewing();
    return i === null ? null : (this.photos()[i] ?? null);
  });

  constructor() {
    void this.load();
  }

  private async load(): Promise<void> {
    try {
      const list = await firstValueFrom(this.api.list());
      this.enabled.set(list.enabled);
      this.photos.set(list.photos);
    } catch {
      this.enabled.set(false);
    }
  }

  protected async onPicked(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.uploading()) return;
    this.uploading.set(true);
    try {
      await this.api.upload(file, this.today());
      await this.load();
      this.toasts.show('Photo saved.');
    } catch {
      this.toasts.show('Could not save that photo. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.uploading.set(false);
    }
  }

  protected step(delta: number): void {
    const i = this.viewing();
    if (i === null) return;
    const next = i + delta;
    if (next >= 0 && next < this.photos().length) this.viewing.set(next);
  }

  protected async remove(photo: Photo): Promise<void> {
    try {
      await firstValueFrom(this.api.delete(photo.id));
      this.viewing.set(null);
      this.photos.update((list) => list.filter((p) => p.id !== photo.id));
      this.toasts.show('Photo deleted.');
    } catch {
      this.toasts.show('Could not delete that photo. Try again.', { tone: 'penalty' });
    }
  }
}
