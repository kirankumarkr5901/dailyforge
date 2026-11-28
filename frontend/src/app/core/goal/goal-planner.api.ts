import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LogicalDate } from '../time/logical-date';

export interface DraftGoal {
  title: string;
  description: string;
  kind: 'RUN_DISTANCE' | 'BODY_METRIC' | 'EXERCISE_TARGET' | 'CUSTOM';
  targetValue: number | null;
  exerciseName: string | null;
  targetDate: LogicalDate;
}

export interface DraftMilestone {
  title: string;
  dueDate: LogicalDate;
}

export interface DraftHabit {
  name: string;
  daysPerWeek: number;
}

export interface DraftSkill {
  name: string;
  when: string;
  safety: string;
  levels: { title: string; drills: string[]; goal: string }[];
}

export interface DraftWorkoutAdd {
  exerciseId: string;
  exerciseName: string;
  planDay: number;
  dayLabel: string;
  sets: number;
  repsMin: number;
  repsMax: number;
}

export interface GoalPlanDraft {
  request: string;
  summary: string;
  goal: DraftGoal;
  milestones: DraftMilestone[];
  habits: DraftHabit[];
  skill: DraftSkill | null;
  workoutAdds: DraftWorkoutAdd[];
}

export interface GoalMilestone {
  id: string;
  title: string;
  dueDate: LogicalDate;
  doneOn: LogicalDate | null;
}

export interface GoalReview {
  weekStart: LogicalDate;
  note: string;
  proposal: DraftMilestone[];
  /** PENDING waits for an answer; NONE was just a note. */
  status: 'PENDING' | 'ACCEPTED' | 'DISMISSED' | 'NONE';
}

export interface GoalPlan {
  goalId: string;
  milestones: GoalMilestone[];
  review: GoalReview | null;
}

/** The AI goal planner: draft, accept, milestones and the weekly re-tune. */
@Injectable({ providedIn: 'root' })
export class GoalPlannerApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/goals/ai`;

  draft(request: string, targetDate: LogicalDate): Observable<GoalPlanDraft> {
    return this.http.post<GoalPlanDraft>(`${this.base}/draft`, { request, targetDate });
  }

  accept(draft: GoalPlanDraft): Observable<{ goalId: string }> {
    return this.http.post<{ goalId: string }>(this.base, draft);
  }

  plans(): Observable<GoalPlan[]> {
    return this.http.get<GoalPlan[]>(`${this.base}/plans`);
  }

  toggleMilestone(id: string): Observable<GoalPlan[]> {
    return this.http.post<GoalPlan[]>(`${this.base}/milestones/${id}/toggle`, {});
  }

  answerReview(goalId: string, accept: boolean): Observable<GoalPlan[]> {
    return this.http.post<GoalPlan[]>(
      `${this.base}/${goalId}/review/${accept ? 'accept' : 'dismiss'}`,
      {},
    );
  }
}
