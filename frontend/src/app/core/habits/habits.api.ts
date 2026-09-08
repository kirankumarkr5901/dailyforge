import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ifMatch } from '../sync/if-match';
import { LogicalDate } from '../time/logical-date';
import {
  BonusPreview,
  CreateHabitPayload,
  Habit,
  HabitBoard,
  HabitLogResponse,
  UpdateHabitPayload,
} from './habits.types';

@Injectable({ providedIn: 'root' })
export class HabitsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<Habit[]> {
    return this.http.get<Habit[]>(`${this.base}/habits`);
  }

  create(payload: CreateHabitPayload): Observable<Habit> {
    return this.http.post<Habit>(`${this.base}/habits`, payload);
  }

  /** `version` is the copy being edited; the server refuses the write if it has moved on. */
  update(id: string, payload: UpdateHabitPayload, version?: number): Observable<Habit> {
    return this.http.patch<Habit>(`${this.base}/habits/${id}`, payload, ifMatch(version));
  }

  reorder(orderedIds: string[]): Observable<Habit[]> {
    return this.http.patch<Habit[]>(`${this.base}/habits/order`, { orderedIds });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/habits/${id}`);
  }

  /** Omit `date` to ask for "today" as the server resolves it (spec §4.2). */
  board(date?: LogicalDate): Observable<HabitBoard> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<HabitBoard>(`${this.base}/habits/board`, { params });
  }

  /** Anonymous-readable (spec §8.2) — used live while composing a new habit. */
  bonusPreview(baseBonus?: number, bonusMultiplier?: number): Observable<BonusPreview> {
    let params = new HttpParams();
    if (baseBonus != null) {
      params = params.set('baseBonus', baseBonus);
    }
    if (bonusMultiplier != null) {
      params = params.set('bonusMultiplier', bonusMultiplier);
    }
    return this.http.get<BonusPreview>(`${this.base}/habits/bonus-preview`, { params });
  }

  log(id: string, date: LogicalDate): Observable<HabitLogResponse> {
    return this.http.post<HabitLogResponse>(`${this.base}/habits/${id}/logs`, { date });
  }

  unlog(id: string, date: LogicalDate): Observable<HabitLogResponse> {
    return this.http.delete<HabitLogResponse>(`${this.base}/habits/${id}/logs/${date}`);
  }
}
