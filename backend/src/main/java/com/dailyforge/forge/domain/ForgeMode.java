package com.dailyforge.forge.domain;

/**
 * NORMAL forgives a missed day (the streak resets, nothing else). HARD pays more XP per
 * quest, but a day missed or left partial ends the run: it restarts at day 1. What was
 * already earned, XP and points alike, is kept. Neither mode ever touches points beyond
 * what a completed quest pays.
 */
public enum ForgeMode {
    NORMAL,
    HARD
}
