package com.dailyforge.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that changes the <i>structure</i> of the app — creating, editing,
 * reordering or removing a habit, a workout plan, an exercise, an activity type, a
 * reward, a goal, or starting/ending a Forge 66 program. Such a request is refused while
 * the user's "Enable edit" setting is off (owner request).
 *
 * Logging effort — ticking a habit, logging a set or a run, redeeming a reward — is
 * never marked: the whole point of the lock is that logging stays effortless while the
 * things being logged against stay put.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface StructuralChange {}
