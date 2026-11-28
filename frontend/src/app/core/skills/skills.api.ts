import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

/** Mirrors the backend's SkillController records. */
export interface SkillLevel {
  number: number;
  title: string;
  drills: string[];
  goal: string;
}

export interface Skill {
  code: string;
  name: string;
  when: string;
  safety: string | null;
  /** The level being worked at, 1-based. */
  level: number;
  doneOnDate: boolean;
  /** Every day this skill's block was ever done. */
  daysDone: number;
  levels: SkillLevel[];
  /** Written by the AI goal planner, not one of the built-in three; can be removed. */
  custom: boolean;
}

export interface SkillBoard {
  date: LogicalDate;
  skills: Skill[];
}

export interface SkillWrite {
  board: SkillBoard;
  points: PointsEnvelope;
}

@Injectable({ providedIn: 'root' })
export class SkillsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Omit `date` for today in the user's own time zone. */
  board(date?: LogicalDate): Observable<SkillBoard> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<SkillBoard>(`${this.base}/skills`, { params });
  }

  /** Removes a skill the AI wrote; days already done keep their points. */
  removeCustom(code: string): Observable<SkillBoard> {
    return this.http.delete<SkillBoard>(`${this.base}/skills/${code}`);
  }

  setLevel(code: string, level: number): Observable<SkillBoard> {
    return this.http.put<SkillBoard>(`${this.base}/skills/${code}/level`, { level });
  }

  markDone(code: string, date: LogicalDate): Observable<SkillWrite> {
    return this.http.post<SkillWrite>(`${this.base}/skills/${code}/done`, { date });
  }

  markNotDone(code: string, date: LogicalDate): Observable<SkillWrite> {
    return this.http.delete<SkillWrite>(`${this.base}/skills/${code}/done`, { params: new HttpParams().set('date', date) });
  }
}
