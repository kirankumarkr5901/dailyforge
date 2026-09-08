package com.dailyforge.identity.domain;

import java.time.ZoneId;
import java.util.UUID;

/** One user's id and time zone — enough for another module to reason about their day. */
public record UserZone(UUID userId, ZoneId zone) {}
