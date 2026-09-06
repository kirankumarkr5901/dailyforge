package com.dailyforge.identity.domain;

import com.dailyforge.common.time.DayService;
import com.dailyforge.identity.repo.UserRepository;
import com.dailyforge.identity.repo.UserSettingsRepository;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDirectoryService implements UserDirectory {

    private final UserRepository users;
    private final UserSettingsRepository settings;
    private final DayService dayService;

    public UserDirectoryService(UserRepository users, UserSettingsRepository settings, DayService dayService) {
        this.users = users;
        this.settings = settings;
        this.dayService = dayService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserZone> listActive() {
        return users.findAll().stream()
                .filter(User::isActive)
                .map(
                        user -> {
                            ZoneId zone =
                                    settings
                                            .findById(user.getId())
                                            .map(s -> dayService.zoneOf(s.getTimeZone()))
                                            .orElse(ZoneId.of("UTC"));
                            return new UserZone(user.getId(), zone);
                        })
                .toList();
    }
}
