import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, input, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Camera, ChevronLeft, ChevronRight, CloudUpload, LucideAngularModule, Trash2 } from 'lucide-angular';

import { SessionStore } from '../../../core/auth/session.store';
import { LocalPhotosStore } from '../../../core/photos/local-photos.store';
import { PhotosApi, shrink } from '../../../core/photos/photos.api';
import { LogicalDate, formatLong } from '../../../core/time/logical-date';
import { DfButtonComponent } from '../../../shared/ui/df-button/df-button.component';
import { DfCardComponent } from '../../../shared/ui/df-card/df-card.component';
import { DfSheetComponent } from '../../../shared/ui/df-sheet/df-sheet.component';
import { ToastService } from '../../../shared/ui/df-toast/toast.service';

/** One photo on screen, wherever it is kept. */
interface ShownPhoto {
  id: string;
  takenOn: LogicalDate;
  url: string;
  /** Kept on this phone only (no server storage, or not uploaded yet). */
  local: boolean;
  /** The image itself, for a phone-only photo, so it can be uploaded later. */
  blob?: Blob;
}

/**
 * Progress photos on the Body page. With photo storage on the server they go to the
 * cloud (private, short-lived links). Without it they are kept on this phone instead,
 * clearly labelled, and can be moved to the cloud once storage is set up. Either way:
 * take or pick one (dated today), see them as a grid, view large, step through, delete.
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
  private readonly local = inject(LocalPhotosStore);
  private readonly session = inject(SessionStore);
  private readonly toasts = inject(ToastService);

  /** Today, for dating a new photo. */
  readonly today = input.required<LogicalDate>();

  protected readonly cameraIcon = Camera;
  protected readonly prevIcon = ChevronLeft;
  protected readonly nextIcon = ChevronRight;
  protected readonly deleteIcon = Trash2;
  protected readonly uploadIcon = CloudUpload;
  protected readonly formatLong = formatLong;

  /** Whether the server stores photos; null until known. */
  protected readonly cloud = signal<boolean | null>(null);
  protected readonly photos = signal<ShownPhoto[]>([]);
  protected readonly busy = signal(false);
  protected readonly viewing = signal<number | null>(null);
  protected readonly current = computed(() => {
    const i = this.viewing();
    return i === null ? null : (this.photos()[i] ?? null);
  });
  protected readonly hasLocal = computed(() => this.photos().some((p) => p.local));
  /** Photos can be added when either the cloud or this phone can keep them. */
  protected readonly canAdd = computed(() => this.cloud() === true || this.local.supported());

  /** Object URLs for phone-only photos; released when they are replaced or the card goes. */
  private objectUrls: string[] = [];

  constructor() {
    void this.load();
    inject(DestroyRef).onDestroy(() => this.releaseUrls());
  }

  private releaseUrls(): void {
    this.objectUrls.forEach((u) => URL.revokeObjectURL(u));
    this.objectUrls = [];
  }

  private async load(): Promise<void> {
    let cloudPhotos: ShownPhoto[] = [];
    try {
      const list = await firstValueFrom(this.api.list());
      this.cloud.set(list.enabled);
      cloudPhotos = list.photos.map((p) => ({ ...p, local: false }));
    } catch {
      this.cloud.set(false);
    }
    let localPhotos: ShownPhoto[] = [];
    const userId = this.session.user()?.id;
    if (userId && this.local.supported()) {
      try {
        this.releaseUrls();
        localPhotos = (await this.local.list(userId)).map((p) => {
          const url = URL.createObjectURL(p.blob);
          this.objectUrls.push(url);
          return { id: p.id, takenOn: p.takenOn, url, local: true, blob: p.blob };
        });
      } catch {
        // Browser storage unavailable (private mode): only cloud photos show.
      }
    }
    this.photos.set([...cloudPhotos, ...localPhotos].sort((a, b) => (a.takenOn < b.takenOn ? 1 : a.takenOn > b.takenOn ? -1 : 0)));
  }

  protected async onPicked(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.busy()) return;
    this.busy.set(true);
    try {
      if (this.cloud()) {
        await this.api.upload(file, this.today());
        this.toasts.show('Photo saved.');
      } else {
        const userId = this.session.user()?.id;
        if (!userId) return;
        await this.local.add(userId, this.today(), await shrink(file));
        this.toasts.show('Photo saved on this phone.');
      }
      await this.load();
    } catch {
      this.toasts.show('Could not save that photo. Try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  /** Moves a phone-only photo to the cloud, once the server has storage. */
  protected async uploadLocal(photo: ShownPhoto): Promise<void> {
    if (!photo.blob || this.busy()) return;
    this.busy.set(true);
    try {
      await this.api.upload(photo.blob, photo.takenOn, true);
      await this.local.remove(photo.id);
      this.viewing.set(null);
      await this.load();
      this.toasts.show('Moved to the cloud.');
    } catch {
      this.toasts.show('Could not upload it. Check your connection and try again.', { tone: 'penalty' });
    } finally {
      this.busy.set(false);
    }
  }

  protected step(delta: number): void {
    const i = this.viewing();
    if (i === null) return;
    const next = i + delta;
    if (next >= 0 && next < this.photos().length) this.viewing.set(next);
  }

  protected async remove(photo: ShownPhoto): Promise<void> {
    try {
      if (photo.local) {
        await this.local.remove(photo.id);
      } else {
        await firstValueFrom(this.api.delete(photo.id));
      }
      this.viewing.set(null);
      this.photos.update((list) => list.filter((p) => p.id !== photo.id));
      this.toasts.show('Photo deleted.');
    } catch {
      this.toasts.show('Could not delete that photo. Try again.', { tone: 'penalty' });
    }
  }
}
