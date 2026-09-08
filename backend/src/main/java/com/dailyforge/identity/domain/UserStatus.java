package com.dailyforge.identity.domain;

/** Mirrors the CHECK constraint on {@code app_user.status}. */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED
}
