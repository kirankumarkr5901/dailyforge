import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

export type ChallengeStatus = 'ACTIVE' | 'REACHED' | 'COMPLETED' | 'ABANDONED';

/** Mirrors ChallengeService.View: already settled for today by the server. */
export interface Challenge {
  id: string;
  /** The daily habit that is this challenge's tick. */
  habitId: string;
  title: string;
  icon: string;
  lengthDays: number;
  bonusPoints: number;
  startedOn: LogicalDate;
  runStart: LogicalDate;
  /** How many times a missed day sent it back to day 1. */
  restarts: number;
  status: ChallengeStatus;
  dayNumber: number;
  doneInARow: number;
  doneToday: boolean;
}

export interface CreateChallengePayload {
  title: string;
  lengthDays: number;
  habitPoints: number;
  bonusPoints: number;
}

@Injectable({ providedIn: 'root' })
export class ChallengesApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<Challenge[]> {
    return this.http.get<Challenge[]>(`${this.base}/challenges`);
  }

  /** Structural: needs "Enable edit". Creates the challenge's daily habit too. */
  create(payload: CreateChallengePayload): Observable<Challenge> {
    return this.http.post<Challenge>(`${this.base}/challenges`, payload);
  }

  claim(id: string): Observable<{ challenge: Challenge; points: PointsEnvelope }> {
    return this.http.post<{ challenge: Challenge; points: PointsEnvelope }>(`${this.base}/challenges/${id}/claim`, {});
  }

  /** Structural: needs "Enable edit". The habit and its history stay. */
  abandon(id: string): Observable<Challenge> {
    return this.http.delete<Challenge>(`${this.base}/challenges/${id}`);
  }
}
