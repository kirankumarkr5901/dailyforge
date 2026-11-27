import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';

export interface CoachAlert {
  exerciseId: string;
  exercise: string;
  type: 'PLATEAU' | 'DELOAD' | 'SWAP';
  message: string;
  /** For a SWAP: the exercise to try instead; otherwise empty. */
  swapTo: string;
}

export interface CoachGap {
  muscle: string;
  /** Sets done this week so far. */
  sets: number;
  message: string;
}

export interface Coaching {
  note: string;
  alerts: CoachAlert[];
  gaps: CoachGap[];
}

@Injectable({ providedIn: 'root' })
export class WorkoutCoachApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Kept by the server for the rest of the day, so asking again is free. */
  coach(planId: string, dayIndex: number, date: LogicalDate | null): Observable<Coaching> {
    let params = new HttpParams().set('planId', planId).set('dayIndex', dayIndex);
    if (date) params = params.set('date', date);
    return this.http.get<Coaching>(`${this.base}/workouts/coach`, { params });
  }
}
