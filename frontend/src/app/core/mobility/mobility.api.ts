import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

/** Mirrors the backend's MobilityController records. */
export interface MobilityMove {
  name: string;
  seconds: number;
  cue: string;
}

export interface MobilityFlow {
  code: string;
  name: string;
  when: string;
  totalSeconds: number;
  doneToday: boolean;
  timesDone: number;
  moves: MobilityMove[];
}

export interface MobilityBoard {
  date: LogicalDate;
  flows: MobilityFlow[];
}

export interface MobilityCompletion {
  board: MobilityBoard;
  points: PointsEnvelope;
}

@Injectable({ providedIn: 'root' })
export class MobilityApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  board(): Observable<MobilityBoard> {
    return this.http.get<MobilityBoard>(`${this.base}/mobility`);
  }

  /** Called when the last move's countdown runs out. Pays once per flow per day. */
  complete(code: string): Observable<MobilityCompletion> {
    return this.http.post<MobilityCompletion>(`${this.base}/mobility/${code}/complete`, {});
  }
}
