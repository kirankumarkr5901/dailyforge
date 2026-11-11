/** Mirrors the backend's RewardDtos (spec §5.4, §8.9). */

import { PointsEnvelope } from '../points/points.types';
import { LogicalDate } from '../time/logical-date';

export type RewardTier = 'MICRO' | 'WEEKLY' | 'MONTHLY';

export interface Reward {
  id: string;
  /** Echoed back on edit so a stale device cannot overwrite a newer one. */
  version: number;
  name: string;
  cost: number;
  icon: string;
  tier: RewardTier;
  isRepeatable: boolean;
  /** The allowance per period, or null for no limit. The tier says how often it refreshes. */
  stock: number | null;
  /** How much of that allowance is left right now; null when there is no limit. */
  remaining: number | null;
  /** The day the allowance comes back; null when there is no limit. */
  refreshesOn: LogicalDate | null;
}

export interface CreateRewardPayload {
  name: string;
  cost: number;
  icon: string;
  tier: RewardTier;
  isRepeatable: boolean;
  stock?: number;
}

export interface RedeemResponse {
  redemptionId: string;
  points: PointsEnvelope;
}

export interface Redemption {
  id: string;
  rewardId: string;
  pointsSpent: number;
  redeemedAt: string;
  refunded: boolean;
}

export interface RefundResponse {
  points: PointsEnvelope;
}
