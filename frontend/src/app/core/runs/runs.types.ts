/** Mirrors the backend's RunDtos (spec §6, §8.5). */

import { LogicalDate } from '../time/logical-date';
import { PointsEnvelope } from '../points/points.types';

export type RunType = 'LONG' | 'INTERVAL' | 'TEMPO';

export type Bracket = 'D5K' | 'D10K' | 'D15K' | 'D21K' | 'D25K' | 'D42K' | 'D50K';

export interface Run {
  id: string;
  /** Echoed back on edit so a stale device cannot overwrite a newer one. */
  version: number;
  date: LogicalDate;
  distanceMeters: number;
  durationSeconds: number;
  type: RunType;
  paceSecPerKm: number;
  note: string | null;
  feltEffort: number | null;
}

export interface LogRunPayload {
  date: LogicalDate;
  distanceMeters: number;
  durationSeconds: number;
  type?: RunType;
  note?: string;
  feltEffort?: number;
}

export interface RunWriteResponse {
  run: Run;
  points: PointsEnvelope;
}

export interface DeleteRunResponse {
  points: PointsEnvelope;
}

export interface RunRecords {
  lifetimeRunPoints: number;
  topByDistance: Run[];
  topByPace: Run[];
  byBracket: Record<Bracket, Run[]>;
}
