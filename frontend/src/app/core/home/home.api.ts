import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import { DailySummary, DayDetail, HomeSummary, Quote } from './home.types';

@Injectable({ providedIn: 'root' })
export class HomeApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Omit `date` to ask for "today" as the server resolves it (spec §4.2). */
  summary(date?: LogicalDate): Observable<HomeSummary> {
    let params = new HttpParams();
    if (date) {
      params = params.set('date', date);
    }
    return this.http.get<HomeSummary>(`${this.base}/home/summary`, { params });
  }

  heatmap(from: LogicalDate, to: LogicalDate): Observable<DailySummary[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<DailySummary[]>(`${this.base}/insights/heatmap`, { params });
  }

  day(date: LogicalDate): Observable<DayDetail> {
    return this.http.get<DayDetail>(`${this.base}/insights/day/${date}`);
  }

  quoteOfTheDay(): Observable<Quote> {
    return this.http.get<Quote>(`${this.base}/quotes/today`);
  }
}
