package com.dailyforge.identity.domain;

import java.util.List;

/**
 * What another module is allowed to know about "who are the users". Points' daily
 * rollover job needs to enumerate active users and their zones without ever touching
 * {@code UserRepository} directly — modules call each other through service interfaces
 * only, never through their repositories.
 */
public interface UserDirectory {

    List<UserZone> listActive();
}
