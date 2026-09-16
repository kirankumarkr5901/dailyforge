import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CreateApplicationPayload, JobApplication, JobMetrics, JobStatus, TransitionPayload } from './job.types';

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

  transition(id: string, payload: TransitionPayload): Observable<JobApplication> {
    return this.http.post<JobApplication>(`${this.base}/jobs/${id}/transition`, payload);
  }

  metrics(): Observable<JobMetrics> {
    return this.http.get<JobMetrics>(`${this.base}/jobs/metrics`);
  }
}
