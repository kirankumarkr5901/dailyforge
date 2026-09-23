package com.dailyforge.workout.domain;

/**
 * Spec §6, extended with CABLE (owner feedback — cable machines are a distinct, common
 * gym equipment type spec's original list omitted). Drives both the PR type-factor
 * (§5.4, see V14__add_cable_equipment.sql for CABLE's own factor) and the equipment
 * chip colour (§9.2).
 */
public enum Equipment {
    DUMBBELL,
    BARBELL,
    BODYWEIGHT,
    MACHINE,
    CABLE,
    NONE
}
