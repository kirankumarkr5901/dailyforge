/** Mirrors the backend's JobDtos (spec §8.7). */

import { LogicalDate } from '../time/logical-date';

export type JobSource = 'APPLIED' | 'REFERRAL_REQUESTED' | 'REFERRED' | 'RECRUITER';
export type JobStatus = 'APPLIED' | 'ASSESSMENT' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN' | 'GHOSTED';

export interface JobApplication {
  id: string;
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
  /** Only set when status is REJECTED — the stage the rejection came from. */
  rejectedFromStatus: JobStatus | null;
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
