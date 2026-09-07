/** Mirrors the backend's ActivityDtos (spec §6 "activity"). */

import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

export type ActivityPolarity = 'POSITIVE' | 'NEGATIVE';

export interface ActivityType {
  id: string;
  name: string;
  polarity: ActivityPolarity;
  points: number;
  icon: string;
  sortOrder: number;
}

export interface CreateActivityTypePayload {
  name: string;
  polarity: ActivityPolarity;
  points: number;
  icon: string;
}

export interface ActivityLog {
  id: string;
  activityTypeId: string;
  occurredOn: LogicalDate;
  count: number;
  note: string | null;
  createdAt: string;
}

export interface LogActivityPayload {
  date: LogicalDate;
  count?: number;
  note?: string;
}

export interface LogWriteResponse {
  log: ActivityLog;
  points: PointsEnvelope;
}

export interface DeleteLogResponse {
  points: PointsEnvelope;
}
