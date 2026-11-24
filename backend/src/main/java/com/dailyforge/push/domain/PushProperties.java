package com.dailyforge.push.domain;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The server's VAPID key pair, as unpadded base64url (public: 65-byte uncompressed P-256
 * point; private: 32-byte scalar), and the contact the push services may use.
 *
 * <p>Blank keys switch push off: the app says notifications are unavailable rather than
 * failing. Set VAPID_PUBLIC_KEY and VAPID_PRIVATE_KEY in the environment to switch it on.
 */
@ConfigurationProperties(prefix = "dailyforge.push")
public record PushProperties(String vapidPublicKey, String vapidPrivateKey, String subject) {

    public PushProperties {
        vapidPublicKey = vapidPublicKey == null ? "" : vapidPublicKey.trim();
        vapidPrivateKey = vapidPrivateKey == null ? "" : vapidPrivateKey.trim();
        subject = subject == null || subject.isBlank() ? "mailto:notifications@dailyforge.app" : subject.trim();
    }

    public boolean enabled() {
        return !vapidPublicKey.isEmpty() && !vapidPrivateKey.isEmpty();
    }
}
