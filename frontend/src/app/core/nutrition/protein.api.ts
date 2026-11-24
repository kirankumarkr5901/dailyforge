import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';
import { NutritionAmounts, ProteinDay, ProteinDayWrite, ProteinFood } from './protein.types';

@Injectable({ providedIn: 'root' })
export class ProteinApi {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  /** Omit `date` for today in the user's own time zone. */
  day(date?: LogicalDate): Observable<ProteinDay> {
    const params = date ? new HttpParams().set('date', date) : undefined;
    return this.http.get<ProteinDay>(`${this.base}/protein/day`, { params });
  }

  log(date: LogicalDate, foodName: string, amounts: NutritionAmounts): Observable<ProteinDayWrite> {
    return this.http.post<ProteinDayWrite>(`${this.base}/protein/logs`, { date, foodName, ...amounts });
  }

  deleteLog(id: string): Observable<ProteinDayWrite> {
    return this.http.delete<ProteinDayWrite>(`${this.base}/protein/logs/${id}`);
  }

  /** Any of the three; a target left out stays as it is. */
  setTargets(targets: { targetG?: number; calorieBudget?: number; fatTargetG?: number }): Observable<ProteinDayWrite> {
    return this.http.put<ProteinDayWrite>(`${this.base}/protein/target`, targets);
  }

  /** Food list changes are structural: they need "Enable edit". */
  addFood(name: string, amounts: NutritionAmounts): Observable<ProteinFood> {
    return this.http.post<ProteinFood>(`${this.base}/protein/foods`, { name, ...amounts });
  }

  updateFood(id: string, patch: { name?: string; grams?: number; kcal?: number; fatG?: number }): Observable<ProteinFood> {
    return this.http.patch<ProteinFood>(`${this.base}/protein/foods/${id}`, patch);
  }

  deleteFood(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/protein/foods/${id}`);
  }
}
