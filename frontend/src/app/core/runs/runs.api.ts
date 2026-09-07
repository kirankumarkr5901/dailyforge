import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import { DeleteRunResponse, LogRunPayload, Run, RunRecords, RunWriteResponse } from './runs.types';

@Injectable({ providedIn: 'root' })
export class RunsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  list(from?: LogicalDate, to?: LogicalDate): Observable<Run[]> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to) params = params.set('to', to);
    return this.http.get<Run[]>(`${this.base}/runs`, { params });
  }

  log(payload: LogRunPayload): Observable<RunWriteResponse> {
    return this.http.post<RunWriteResponse>(`${this.base}/runs`, payload);
  }

  update(id: string, payload: LogRunPayload): Observable<RunWriteResponse> {
    return this.http.patch<RunWriteResponse>(`${this.base}/runs/${id}`, payload);
  }

  delete(id: string): Observable<DeleteRunResponse> {
    return this.http.delete<DeleteRunResponse>(`${this.base}/runs/${id}`);
  }

  records(): Observable<RunRecords> {
    return this.http.get<RunRecords>(`${this.base}/runs/records`);
  }
}
