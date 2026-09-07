package com.dailyforge.reward.domain;

/**
 * How often a reward is meant to be earned (owner feedback): MICRO is a small daily
 * treat, WEEKLY and MONTHLY progressively bigger splurges — grouping the list so a
 * 5-point treat and a 2000-point reward do not sit undifferentiated together.
 */
public enum RewardTier {
    MICRO,
    WEEKLY,
    MONTHLY
}
