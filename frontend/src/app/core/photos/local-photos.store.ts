import { Injectable } from '@angular/core';

import { LogicalDate } from '../time/logical-date';

/** A progress photo kept on this phone only. */
export interface LocalPhoto {
  id: string;
  /** Whose photo: a phone shared by two accounts shows each only their own. */
  userId: string;
  takenOn: LogicalDate;
  blob: Blob;
  createdAt: number;
}

const DB = 'dailyforge-photos';
const STORE = 'photos';

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE, { keyPath: 'id' });
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

function run<T>(mode: IDBTransactionMode, work: (store: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  return open().then(
    (db) =>
      new Promise<T>((resolve, reject) => {
        const tx = db.transaction(STORE, mode);
        const req = work(tx.objectStore(STORE));
        tx.oncomplete = () => {
          db.close();
          resolve(req.result);
        };
        tx.onerror = () => {
          db.close();
          reject(tx.error);
        };
      }),
  );
}

/**
 * Progress photos kept on the phone, in IndexedDB, for when the server has no photo
 * storage. They never leave the phone: clearing the browser's data, uninstalling the app
 * or changing phones loses them, which the screen says plainly. The browser is asked to
 * keep this storage persistent, so it is not evicted to free space.
 */
@Injectable({ providedIn: 'root' })
export class LocalPhotosStore {
  supported(): boolean {
    return typeof indexedDB !== 'undefined';
  }

  async list(userId: string): Promise<LocalPhoto[]> {
    const all = await run<LocalPhoto[]>('readonly', (s) => s.getAll() as IDBRequest<LocalPhoto[]>);
    return all.filter((p) => p.userId === userId);
  }

  async add(userId: string, takenOn: LogicalDate, blob: Blob): Promise<void> {
    // Best effort: ask for persistent storage the first time a photo is kept here.
    void navigator.storage?.persist?.();
    const photo: LocalPhoto = {
      id: globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`,
      userId,
      takenOn,
      blob,
      createdAt: Date.now(),
    };
    await run('readwrite', (s) => s.put(photo));
  }

  async remove(id: string): Promise<void> {
    await run('readwrite', (s) => s.delete(id));
  }
}
