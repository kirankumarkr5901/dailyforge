/** Mirrors the backend's JobDtos (spec §8.7). */

import { LogicalDate } from '../time/logical-date';

export type JobSource = 'APPLIED' | 'REFERRAL_REQUESTED' | 'REFERRED' | 'RECRUITER';
export type JobStatus = 'APPLIED' | 'ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN' | 'GHOSTED';
/** Which kind of interview round — the backend caps technical at 3 and HR at 2. */
export type InterviewStage = 'TECHNICAL' | 'HR';
/**
 * How long a referral has been waiting, as what to do about it rather than as a colour.
 * The colour is this app's decision, made once in the referral list's own styles.
 */
export type ReferralState = 'WAITING' | 'FOLLOW_UP' | 'APPLY_DIRECTLY';

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
  /** The handle the referrer or the company portal gave you, to paste in later. */
  referralId: string | null;
  /** The day the referral was asked for — not appliedOn; the gap between them is the point. */
  referralRequestedOn: LogicalDate | null;
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
  referralId?: string;
  referralRequestedOn?: LogicalDate;
  note?: string;
  appliedOn: LogicalDate;
}

/** Every field optional: a PATCH changes only what it names. */
export interface UpdateApplicationPayload {
  company?: string;
  role?: string;
  roleId?: string;
  city?: string;
  jobUrl?: string;
  resumeVersion?: string;
  referrerName?: string;
  referralId?: string;
  referralRequestedOn?: LogicalDate;
  note?: string;
  nextFollowUpOn?: LogicalDate | null;
}

/** An application, plus the two facts that only make sense for a referral. */
export interface Referral {
  application: JobApplication;
  daysWaiting: number;
  state: ReferralState;
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
