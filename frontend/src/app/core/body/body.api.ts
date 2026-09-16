import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { BodyMetric, BodySummary, LogWeightPayload } from './body.types';

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

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/body-metrics/${id}`);
  }
}
