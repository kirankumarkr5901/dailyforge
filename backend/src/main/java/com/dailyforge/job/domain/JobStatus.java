package com.dailyforge.job.domain;

/** The stage stepper (spec §8.7): Applied → Assessment → Interview (round n) → Offer, with three exits available at any point. */
public enum JobStatus {
    APPLIED,
    ASSESSMENT,
    INTERVIEW,
    OFFER,
    REJECTED,
    WITHDRAWN,
    GHOSTED
}
