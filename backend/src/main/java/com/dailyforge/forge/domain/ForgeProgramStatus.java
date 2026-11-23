package com.dailyforge.forge.domain;

public enum ForgeProgramStatus {
    ACTIVE,
    COMPLETED,
    ABANDONED,
    /** Hard mode: a day was missed or left partial, so this run ended and a new one began at day 1. */
    RESTARTED
}
