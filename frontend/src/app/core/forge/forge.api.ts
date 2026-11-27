import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ForgeAiQuestion,
  ForgeAiTurn,
  ForgeMode,
  ForgeOverview,
  ForgeQuestChange,
  ForgeQuestionnaire,
  ForgeStartPayload,
} from './forge.types';

@Injectable({ providedIn: 'root' })
export class ForgeApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/forge`;

  overview(): Observable<ForgeOverview> {
    return this.http.get<ForgeOverview>(this.base);
  }

  questionnaire(): Observable<ForgeQuestionnaire> {
    return this.http.get<ForgeQuestionnaire>(`${this.base}/questionnaire`);
  }

  start(payload: ForgeStartPayload): Observable<ForgeOverview> {
    return this.http.post<ForgeOverview>(`${this.base}/program`, payload);
  }

  /** The AI interview: the answers so far in, the next question (or done) out. */
  aiInterview(turns: ForgeAiTurn[]): Observable<ForgeAiQuestion> {
    return this.http.post<ForgeAiQuestion>(`${this.base}/ai/interview`, { turns });
  }

  /** Starts a program the AI plans from the interview. */
  aiStart(turns: ForgeAiTurn[], mode: ForgeMode): Observable<ForgeOverview> {
    return this.http.post<ForgeOverview>(`${this.base}/ai/program`, { turns, mode });
  }

  abandon(): Observable<ForgeOverview> {
    return this.http.delete<ForgeOverview>(`${this.base}/program`);
  }

  complete(questId: string): Observable<ForgeQuestChange> {
    return this.http.post<ForgeQuestChange>(`${this.base}/quests/${questId}/completion`, {});
  }

  uncomplete(questId: string): Observable<ForgeQuestChange> {
    return this.http.delete<ForgeQuestChange>(`${this.base}/quests/${questId}/completion`);
  }
}
