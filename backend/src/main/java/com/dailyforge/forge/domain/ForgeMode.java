package com.dailyforge.forge.domain;

/**
 * NORMAL forgives a missed day (the streak resets, nothing else). HARD pays more XP per
 * quest and takes XP away for every quest left undone when a day closes. Neither mode
 * ever touches points beyond what a completed quest pays.
 */
public enum ForgeMode {
    NORMAL,
    HARD
}
