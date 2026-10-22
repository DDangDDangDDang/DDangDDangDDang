package com.example.ddangddangddang.domain.notification.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브라우저의 PushSubscription 그대로. (subscription.toJSON()의 endpoint, keys.p256dh, keys.auth)
 */
public record PushSubscriptionRequest(
        @NotBlank @Size(min = 1, max = 1000) String endpoint,
        @NotBlank @Size(min = 1, max = 100) String p256dh,
        @NotBlank @Size(min = 1, max = 50) String auth
) {
}
