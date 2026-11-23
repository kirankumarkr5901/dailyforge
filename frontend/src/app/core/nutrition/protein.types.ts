/** Mirrors the backend's ProteinDtos. */

import { LogicalDate } from '../time/logical-date';
import { PointsEnvelope } from '../points/points.types';

export interface ProteinFood {
  id: string;
  name: string;
  grams: number;
}

export interface ProteinEntry {
  id: string;
  foodName: string;
  grams: number;
}

export interface ProteinDay {
  date: LogicalDate;
  targetG: number;
  totalG: number;
  entries: ProteinEntry[];
  foods: ProteinFood[];
}

export interface ProteinDayWrite {
  day: ProteinDay;
  points: PointsEnvelope;
}
