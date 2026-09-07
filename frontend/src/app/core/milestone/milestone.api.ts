import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import { MilestoneRecap, RecapPeriod } from './milestone.types';

@Injectable({ providedIn: 'root' })
export class MilestoneApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Omit `date` to anchor on "today" as the server resolves it, same contract as every other board. */
  recap(period: RecapPeriod, date?: LogicalDate): Observable<MilestoneRecap> {
    let params = new HttpParams().set('period', period);
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<MilestoneRecap>(`${this.base}/milestones/recap`, { params });
  }
}
