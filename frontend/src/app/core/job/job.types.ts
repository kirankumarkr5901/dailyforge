/** Mirrors the backend's JobDtos (spec §8.7). */

import { LogicalDate } from '../time/logical-date';

export type JobSource = 'APPLIED' | 'REFERRAL_REQUESTED' | 'REFERRED' | 'RECRUITER';
export type JobStatus = 'APPLIED' | 'ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN' | 'GHOSTED';
/** Which kind of interview round — the backend caps technical at 3 and HR at 2. */
export type InterviewStage = 'TECHNICAL' | 'HR';

export interface JobApplication {
  id: string;
  /** Echoed back on edit so a stale device cannot overwrite a newer one. */
  version: number;
  company: string;
  role: string;
  roleId: string | null;
  city: string | null;
  jobUrl: string | null;
  resumeVersion: string | null;
  source: JobSource;
  referrerName: string | null;
  status: JobStatus;
  currentRound: number;
  nextFollowUpOn: LogicalDate | null;
  note: string | null;
  appliedOn: LogicalDate;
  /** Which kind of interview this is in, or last reached. */
  interviewStage: InterviewStage | null;
  /** Only set when status is REJECTED — where the rejection came from, for the label. */
  rejectedFromStatus: JobStatus | null;
  rejectedFromStage: InterviewStage | null;
  rejectedFromRound: number | null;
}

export interface CreateApplicationPayload {
  company: string;
  role: string;
  roleId?: string;
  city?: string;
  jobUrl?: string;
  resumeVersion?: string;
  source: JobSource;
  referrerName?: string;
  note?: string;
  appliedOn: LogicalDate;
}

export interface TransitionPayload {
  toStatus: JobStatus;
  roundNumber?: number;
  interviewStage?: InterviewStage;
  note?: string;
  occurredOn: LogicalDate;
}

export interface JobMetrics {
  countsByStatus: Partial<Record<JobStatus, number>>;
  responseRate: number;
  averageDaysToFirstResponse: number | null;
  interviewsPerApplication: number;
  needsFollowUp: JobApplication[];
}
