import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import { DailySummary, DayDetail, HomeSummary, Quote, WeekReview, WeeklyCoachReview } from './home.types';

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

  /** The Monday-to-Sunday week containing `date`, in a few numbers. */
  week(date: LogicalDate): Observable<WeekReview> {
    return this.http.get<WeekReview>(`${this.base}/insights/week`, { params: new HttpParams().set('date', date) });
  }

  /** Null when AI is off or the week hasn't reached its Sunday yet (the server answers 204). */
  coach(date: LogicalDate): Observable<WeeklyCoachReview | null> {
    return this.http.get<WeeklyCoachReview | null>(`${this.base}/insights/coach`, { params: new HttpParams().set('date', date) });
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
