import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ifMatch } from '../sync/if-match';
import {
  CreateApplicationPayload,
  JobApplication,
  JobMetrics,
  JobStatus,
  Referral,
  TransitionPayload,
  UpdateApplicationPayload,
} from './job.types';

@Injectable({ providedIn: 'root' })
export class JobApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(status?: JobStatus): Observable<JobApplication[]> {
    let params = new HttpParams();
    if (status) {
      params = params.set('status', status);
    }
    return this.http.get<JobApplication[]>(`${this.base}/jobs`, { params });
  }

  create(payload: CreateApplicationPayload): Observable<JobApplication> {
    return this.http.post<JobApplication>(`${this.base}/jobs`, payload);
  }

  /**
   * Edits an application. `version` is the copy being edited — the server refuses the
   * write if another device has changed it since (see StaleWrite on the backend).
   */
  update(id: string, payload: UpdateApplicationPayload, version?: number): Observable<JobApplication> {
    return this.http.patch<JobApplication>(`${this.base}/jobs/${id}`, payload, ifMatch(version));
  }

  /** Referrals only, oldest ask first, each with how long it has been waiting. */
  referrals(): Observable<Referral[]> {
    return this.http.get<Referral[]>(`${this.base}/jobs/referrals`);
  }

  transition(id: string, payload: TransitionPayload): Observable<JobApplication> {
    return this.http.post<JobApplication>(`${this.base}/jobs/${id}/transition`, payload);
  }

  metrics(): Observable<JobMetrics> {
    return this.http.get<JobMetrics>(`${this.base}/jobs/metrics`);
  }
}
