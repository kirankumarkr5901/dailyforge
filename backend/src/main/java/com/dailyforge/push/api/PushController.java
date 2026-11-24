package com.dailyforge.push.api;

import com.dailyforge.common.security.CurrentUser;
import com.dailyforge.push.domain.DailyReminderService;
import com.dailyforge.push.domain.PushSender;
import com.dailyforge.push.domain.RestPushScheduler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Phone notifications: subscribing a device, the daily reminder time, and rest-over pushes. */
@RestController
@RequestMapping("/api/v1/push")
public class PushController {

    private final PushSender sender;
    private final RestPushScheduler rest;
    private final DailyReminderService reminders;
    private final CurrentUser currentUser;

    public PushController(
            PushSender sender, RestPushScheduler rest, DailyReminderService reminders, CurrentUser currentUser) {
        this.sender = sender;
        this.rest = rest;
        this.reminders = reminders;
        this.currentUser = currentUser;
    }

    /** {@code enabled} false means the server has no VAPID keys set: notifications are off. */
    public record ConfigResponse(boolean enabled, String publicKey, LocalTime reminderTime, int devices) {}

    public record Keys(@NotBlank String p256dh, @NotBlank String auth) {}

    public record SubscribeRequest(@NotBlank String endpoint, @NotNull @Valid Keys keys) {}

    public record UnsubscribeRequest(@NotBlank String endpoint) {}

    /** {@code time} null turns the daily reminder off. */
    public record ReminderRequest(LocalTime time) {}

    public record RestRequest(@NotNull @Min(1) @Max(900) Integer seconds) {}

    @GetMapping("/config")
    public ConfigResponse config() {
        UUID userId = currentUser.require();
        return new ConfigResponse(
                sender.enabled(), sender.publicKey(), reminders.reminderTime(userId), sender.subscriptions(userId).size());
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@Valid @RequestBody SubscribeRequest request) {
        UUID userId = currentUser.require();
        sender.subscribe(userId, new PushSender.Subscription(request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        reminders.refresh(userId);
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@Valid @RequestBody UnsubscribeRequest request) {
        UUID userId = currentUser.require();
        sender.unsubscribe(userId, request.endpoint());
        reminders.refresh(userId);
    }

    @PutMapping("/reminder")
    public ConfigResponse setReminder(@RequestBody ReminderRequest request) {
        UUID userId = currentUser.require();
        reminders.setReminderTime(userId, request.time());
        return config();
    }

    /** Sends a test notification to every device of this user, right now. */
    @PostMapping("/test")
    public java.util.Map<String, Integer> test() {
        int delivered = sender.send(
                currentUser.require(),
                new PushSender.Message("DailyForge", "Notifications are working on this device.", "dailyforge-test", "/settings"),
                300);
        return java.util.Map.of("delivered", delivered);
    }

    /** A rest started: push "Rest is over" when it ends, replacing any rest already pending. */
    @PostMapping("/rest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void scheduleRest(@Valid @RequestBody RestRequest request) {
        rest.schedule(currentUser.require(), request.seconds());
    }

    /** The rest ended early (Done, or a new set logged): no push. */
    @DeleteMapping("/rest")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelRest() {
        rest.cancel(currentUser.require());
    }
}
