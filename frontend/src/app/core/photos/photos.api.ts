import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';

export interface Photo {
  id: string;
  takenOn: LogicalDate;
  /** A one-hour view link straight to the image in storage. */
  url: string;
}

export interface PhotoList {
  /** False when the server has no photo storage set up. */
  enabled: boolean;
  photos: Photo[];
}

const MAX_SIDE = 1600;
const MAX_BYTES = 3 * 1024 * 1024;

/** Shrinks a camera photo to at most 1600 px on its long side, as JPEG. */
async function shrink(file: File): Promise<Blob> {
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, MAX_SIDE / Math.max(bitmap.width, bitmap.height));
  const canvas = document.createElement('canvas');
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  bitmap.close();
  for (const quality of [0.82, 0.7, 0.55]) {
    const blob = await new Promise<Blob | null>((r) => canvas.toBlob(r, 'image/jpeg', quality));
    if (blob && blob.size <= MAX_BYTES) return blob;
  }
  throw new Error('too-large');
}

/**
 * Progress photos. The image goes from the phone straight to storage with a short-lived
 * upload link, using plain fetch so the DailyForge login never travels with it; the API
 * only hands out the link and records the upload.
 */
@Injectable({ providedIn: 'root' })
export class PhotosApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<PhotoList> {
    return this.http.get<PhotoList>(`${this.base}/photos`);
  }

  async upload(file: File, takenOn: LogicalDate): Promise<Photo> {
    const blob = await shrink(file);
    const { key, uploadUrl } = await firstValueFrom(
      this.http.post<{ key: string; uploadUrl: string }>(`${this.base}/photos/upload-url`, { sizeBytes: blob.size }),
    );
    const res = await fetch(uploadUrl, { method: 'PUT', body: blob, headers: { 'Content-Type': 'image/jpeg' } });
    if (!res.ok) throw new Error(`upload-${res.status}`);
    return firstValueFrom(this.http.post<Photo>(`${this.base}/photos`, { key, takenOn }));
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/photos/${id}`);
  }
}
