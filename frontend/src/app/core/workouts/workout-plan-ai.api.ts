import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { WorkoutPlan } from './workouts.types';

export interface PlanRequest {
  daysPerWeek: number;
  equipment: string[];
  goal: string;
  experience: string;
  sessionMinutes: number;
  limits: string;
}

export interface DraftExercise {
  /** Null: not in the library yet; created when the plan is saved. */
  exerciseId: string | null;
  name: string;
  muscle: string;
  equipment: string;
  sets: number;
  repsMin: number;
  repsMax: number;
  restSeconds: number;
}

export interface DraftDay {
  label: string;
  exercises: DraftExercise[];
}

export interface PlanDraft {
  name: string;
  summary: string;
  days: DraftDay[];
}

/** "Build me a workout plan": a draft to look over, then saved as a new plan. */
@Injectable({ providedIn: 'root' })
export class WorkoutPlanAiApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/workout-plans/ai`;

  draft(request: PlanRequest): Observable<PlanDraft> {
    return this.http.post<PlanDraft>(`${this.base}/draft`, request);
  }

  save(draft: PlanDraft): Observable<WorkoutPlan> {
    return this.http.post<WorkoutPlan>(this.base, draft);
  }
}
