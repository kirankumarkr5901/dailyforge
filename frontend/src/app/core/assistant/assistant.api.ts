import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface ChatMessage {
  role: 'user' | 'assistant';
  text: string;
}

export type AssistantActionType =
  'LOG_FOOD' | 'ADD_WATER' | 'MARK_HABIT' | 'LOG_SETS' | 'LOG_RUN' | 'LOG_WEIGHT' | 'EXTEND_GOAL';

/** Something the assistant proposes; nothing happens until the user confirms it. */
export interface AssistantAction {
  type: AssistantActionType;
  summary: string;
  name: string;
  proteinG: number;
  kcal: number;
  fatG: number;
  carbsG: number;
  fiberG: number;
  sugarG: number;
  amount: number;
  weightKg: number;
  reps: number[];
  distanceKm: number;
  minutes: number;
  date: string;
}

export interface AssistantReply {
  answer: string;
  actions: AssistantAction[];
}

@Injectable({ providedIn: 'root' })
export class AssistantApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/assistant`;

  ask(messages: ChatMessage[]): Observable<AssistantReply> {
    return this.http.post<AssistantReply>(`${this.base}/ask`, { messages });
  }

  act(action: AssistantAction): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.base}/act`, action);
  }
}
