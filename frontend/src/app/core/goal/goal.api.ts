import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateGoalPayload, Goal, GoalStatus, UpdateGoalPayload } from './goal.types';

@Injectable({ providedIn: 'root' })
export class GoalApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(status?: GoalStatus): Observable<Goal[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<Goal[]>(`${this.base}/goals`, { params });
  }

  create(payload: CreateGoalPayload): Observable<Goal> {
    return this.http.post<Goal>(`${this.base}/goals`, payload);
  }

  /** Edits an active goal. No If-Match: Goal deliberately carries no version (see the backend's V19). */
  update(id: string, payload: UpdateGoalPayload): Observable<Goal> {
    return this.http.patch<Goal>(`${this.base}/goals/${id}`, payload);
  }

  complete(id: string): Observable<Goal> {
    return this.http.post<Goal>(`${this.base}/goals/${id}/complete`, {});
  }

  reopen(id: string): Observable<Goal> {
    return this.http.post<Goal>(`${this.base}/goals/${id}/reopen`, {});
  }

  extend(id: string, newEndDate: string): Observable<Goal> {
    return this.http.post<Goal>(`${this.base}/goals/${id}/extend`, { newEndDate });
  }

  archive(id: string): Observable<Goal> {
    return this.http.patch<Goal>(`${this.base}/goals/${id}/archive`, {});
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/goals/${id}`);
  }
}
