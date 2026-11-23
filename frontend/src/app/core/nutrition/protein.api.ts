import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import { ProteinDay, ProteinDayWrite, ProteinFood } from './protein.types';

@Injectable({ providedIn: 'root' })
export class ProteinApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Omit `date` for today in the user's own time zone. */
  day(date?: LogicalDate): Observable<ProteinDay> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<ProteinDay>(`${this.base}/protein/day`, { params });
  }

  log(date: LogicalDate, foodName: string, grams: number): Observable<ProteinDayWrite> {
    return this.http.post<ProteinDayWrite>(`${this.base}/protein/logs`, { date, foodName, grams });
  }

  deleteLog(id: string): Observable<ProteinDayWrite> {
    return this.http.delete<ProteinDayWrite>(`${this.base}/protein/logs/${id}`);
  }

  setTarget(targetG: number): Observable<ProteinDayWrite> {
    return this.http.put<ProteinDayWrite>(`${this.base}/protein/target`, { targetG });
  }

  /** Food list changes are structural: they need "Enable edit". */
  addFood(name: string, grams: number): Observable<ProteinFood> {
    return this.http.post<ProteinFood>(`${this.base}/protein/foods`, { name, grams });
  }

  updateFood(id: string, patch: { name?: string; grams?: number }): Observable<ProteinFood> {
    return this.http.patch<ProteinFood>(`${this.base}/protein/foods/${id}`, patch);
  }

  deleteFood(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/protein/foods/${id}`);
  }
}
