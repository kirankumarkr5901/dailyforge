import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BodyDirection, BodyGoal, BodyMetric, BodyProfile, BodySex, BodySummary, LogWeightPayload } from './body.types';

@Injectable({ providedIn: 'root' })
export class BodyApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  summary(): Observable<BodySummary> {
    return this.http.get<BodySummary>(`${this.base}/body-metrics/summary`);
  }

  log(payload: LogWeightPayload): Observable<BodyMetric> {
    return this.http.post<BodyMetric>(`${this.base}/body-metrics`, payload);
  }

  profile(): Observable<BodyProfile> {
    return this.http.get<BodyProfile>(`${this.base}/body-metrics/profile`);
  }

  saveProfile(sex: BodySex | null, goal: BodyGoal | null): Observable<BodyProfile> {
    return this.http.put<BodyProfile>(`${this.base}/body-metrics/profile`, { sex, goal });
  }

  /** "Am I heading the right way?": kept by the server for the day. */
  direction(): Observable<BodyDirection> {
    return this.http.get<BodyDirection>(`${this.base}/body-metrics/direction`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/body-metrics/${id}`);
  }
}
