import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LedgerEntry, Page, PointsCategory, PointsEnvelope, ScoreSnapshot } from './points.types';

@Injectable({ providedIn: 'root' })
export class PointsApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  snapshot(): Observable<ScoreSnapshot> {
    return this.http.get<ScoreSnapshot>(`${this.base}/points/snapshot`);
  }

  ledger(page = 0, size = 20, category?: PointsCategory): Observable<Page<LedgerEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<Page<LedgerEntry>>(`${this.base}/points/ledger`, { params });
  }

  recalculate(): Observable<ScoreSnapshot> {
    return this.http.post<ScoreSnapshot>(`${this.base}/points/recalculate`, {});
  }

  /** Only reachable when the backend runs off the `prod` profile (see PointsDebugController). */
  debugAward(
    category: PointsCategory,
    ruleCode: string,
    amount: number,
    description: string,
  ): Observable<PointsEnvelope> {
    return this.http.post<PointsEnvelope>(`${this.base}/points/debug/award`, {
      category,
      ruleCode,
      amount,
      description,
    });
  }
}
