package com.example.ddangddangddang.domain.notification.push;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 브라우저의 PushSubscription 그대로. (subscription.toJSON()의 endpoint, keys.p256dh, keys.auth)
 */
public record PushSubscriptionRequest(
        @NotBlank @Size(max = 1000) String endpoint,
        @NotBlank @Size(max = 100) String p256dh,
        @NotBlank @Size(max = 50) String auth
) {
}
