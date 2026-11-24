package com.dailyforge.push.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * "Rest is over" as a real push, so it reaches the phone with the app in the background
 * or closed: a phone pauses a backgrounded page, and a timer inside it with it.
 *
 * <p>Held in memory, one per user: starting a new rest replaces the old one, and ending
 * the rest early cancels it. A restart of the server loses a pending rest, which is
 * acceptable for something a few minutes long; the in-app timer still runs.
 */
@Component
public class RestPushScheduler {

    private final TaskScheduler scheduler;
    private final PushSender sender;
    private final Map<UUID, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public RestPushScheduler(TaskScheduler scheduler, PushSender sender) {
        this.scheduler = scheduler;
        this.sender = sender;
    }

    public void schedule(UUID userId, int seconds) {
        cancel(userId);
        ScheduledFuture<?> future = scheduler.schedule(
                () -> {
                    pending.remove(userId);
                    sender.send(
                            userId,
                            new PushSender.Message("Rest is over", "Time for your next set.", "dailyforge-rest-timer", "/workout"),
                            120);
                },
                Instant.now().plusSeconds(seconds));
        pending.put(userId, future);
    }

    public void cancel(UUID userId) {
        ScheduledFuture<?> previous = pending.remove(userId);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    boolean isPending(UUID userId) {
        return pending.containsKey(userId);
    }
}
