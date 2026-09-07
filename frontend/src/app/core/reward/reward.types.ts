/** Mirrors the backend's RewardDtos (spec §5.4, §8.9). */

import { PointsEnvelope } from '../points/points.types';

export interface Reward {
  id: string;
  name: string;
  cost: number;
  icon: string;
  isRepeatable: boolean;
  stock: number | null;
}

export interface CreateRewardPayload {
  name: string;
  cost: number;
  icon: string;
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
