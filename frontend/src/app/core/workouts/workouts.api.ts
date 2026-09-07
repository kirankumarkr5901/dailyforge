import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import {
  CreateExercisePayload,
  DeleteSetResponse,
  Exercise,
  ExerciseHistoryEntry,
  LogSetPayload,
  PlanExercise,
  SetWriteResponse,
  UpdateSetPayload,
  WorkoutPlan,
  WorkoutSession,
} from './workouts.types';

@Injectable({ providedIn: 'root' })
export class WorkoutsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  searchExercises(query?: string): Observable<Exercise[]> {
    let params = new HttpParams();
    if (query) {
      params = params.set('q', query);
    }
    return this.http.get<Exercise[]>(`${this.base}/exercises`, { params });
  }

  createExercise(payload: CreateExercisePayload): Observable<Exercise> {
    return this.http.post<Exercise>(`${this.base}/exercises`, payload);
  }

  deleteExercise(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/exercises/${id}`);
  }

  exerciseHistory(id: string): Observable<ExerciseHistoryEntry[]> {
    return this.http.get<ExerciseHistoryEntry[]>(`${this.base}/exercises/${id}/history`);
  }

  plans(): Observable<WorkoutPlan[]> {
    return this.http.get<WorkoutPlan[]>(`${this.base}/workout-plans`);
  }

  archivedPlans(): Observable<WorkoutPlan[]> {
    return this.http.get<WorkoutPlan[]>(`${this.base}/workout-plans/archived`);
  }

  unarchivePlan(id: string): Observable<WorkoutPlan> {
    return this.http.post<WorkoutPlan>(`${this.base}/workout-plans/${id}/unarchive`, {});
  }

  createPlan(name: string, dayCount: number): Observable<WorkoutPlan> {
    return this.http.post<WorkoutPlan>(`${this.base}/workout-plans`, { name, dayCount });
  }

  updatePlan(id: string, patch: { name?: string; isActive?: boolean }): Observable<WorkoutPlan> {
    return this.http.patch<WorkoutPlan>(`${this.base}/workout-plans/${id}`, patch);
  }

  archivePlan(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/workout-plans/${id}`);
  }

  relabelDay(planId: string, dayIndex: number, label: string): Observable<WorkoutPlan> {
    return this.http.patch<WorkoutPlan>(`${this.base}/workout-plans/${planId}/days/${dayIndex}`, { label });
  }

  addPlanExercise(
    planId: string,
    exerciseId: string,
    dayIndex: number,
    targetSets?: number | null,
    targetReps?: number | null,
  ): Observable<PlanExercise> {
    return this.http.post<PlanExercise>(`${this.base}/workout-plans/${planId}/exercises`, {
      exerciseId,
      dayIndex,
      targetSets,
      targetReps,
    });
  }

  removePlanExercise(planId: string, planExerciseId: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/workout-plans/${planId}/exercises/${planExerciseId}`);
  }

  moveExercise(planId: string, planExerciseId: string, toDayIndex: number): Observable<PlanExercise> {
    return this.http.post<PlanExercise>(`${this.base}/workout-plans/${planId}/exercises/${planExerciseId}/move`, {
      toDayIndex,
    });
  }

  updatePlanExercise(
    planId: string,
    planExerciseId: string,
    patch: { targetSets?: number | null; targetReps?: number | null; notes?: string | null },
  ): Observable<PlanExercise> {
    return this.http.patch<PlanExercise>(`${this.base}/workout-plans/${planId}/exercises/${planExerciseId}`, patch);
  }

  session(date: LogicalDate, planId?: string | null, dayIndex?: number | null): Observable<WorkoutSession> {
    let params = new HttpParams().set('date', date);
    if (planId) {
      params = params.set('planId', planId);
    }
    if (dayIndex != null) {
      params = params.set('dayIndex', dayIndex);
    }
    return this.http.get<WorkoutSession>(`${this.base}/workouts/session`, { params });
  }

  logSet(payload: LogSetPayload): Observable<SetWriteResponse> {
    return this.http.post<SetWriteResponse>(`${this.base}/workouts/sets`, payload);
  }

  updateSet(id: string, payload: UpdateSetPayload): Observable<SetWriteResponse> {
    return this.http.patch<SetWriteResponse>(`${this.base}/workouts/sets/${id}`, payload);
  }

  deleteSet(id: string): Observable<DeleteSetResponse> {
    return this.http.delete<DeleteSetResponse>(`${this.base}/workouts/sets/${id}`);
  }
}
