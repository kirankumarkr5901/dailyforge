import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ActivityLog,
  ActivityType,
  CreateActivityTypePayload,
  DeleteLogResponse,
  LogActivityPayload,
  LogWriteResponse,
} from './activity.types';

@Injectable({ providedIn: 'root' })
export class ActivityApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(): Observable<ActivityType[]> {
    return this.http.get<ActivityType[]>(`${this.base}/activities`);
  }

  create(payload: CreateActivityTypePayload): Observable<ActivityType> {
    return this.http.post<ActivityType>(`${this.base}/activities`, payload);
  }

  archive(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/activities/${id}`);
  }

  recentLogs(): Observable<ActivityLog[]> {
    return this.http.get<ActivityLog[]>(`${this.base}/activity-logs`);
  }

  log(activityTypeId: string, payload: LogActivityPayload): Observable<LogWriteResponse> {
    return this.http.post<LogWriteResponse>(`${this.base}/activities/${activityTypeId}/logs`, payload);
  }

  deleteLog(logId: string): Observable<DeleteLogResponse> {
    return this.http.delete<DeleteLogResponse>(`${this.base}/activity-logs/${logId}`);
  }
}
