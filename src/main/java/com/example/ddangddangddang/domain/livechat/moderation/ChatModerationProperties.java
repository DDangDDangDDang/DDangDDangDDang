package com.example.ddangddangddang.domain.livechat.moderation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * 채팅 모더레이션 설정. 모든 값에 기본값이 있어 application.yml에 적지 않아도 동작한다.
 *
 * <pre>
 * chat.moderation.banned-words   전송 전에 막을 금칙어 (환경변수 CHAT_MODERATION_BANNED_WORDS=단어1,단어2)
 * chat.moderation.ai.enabled     AI 검토 사용 여부. 기본 false (환경변수 CHAT_MODERATION_AI_ENABLED=true)
 *                                켜려면 ANTHROPIC_API_KEY 환경변수도 필요하다.
 * chat.moderation.ai.model       검토 모델. 기본 claude-opus-5-5 (환경변수 CHAT_MODERATION_AI_MODEL)
 *                                비용을 줄이려면 claude-haiku-5-5 (Opus 5.5보다 약 40배 저렴, 품질은 평가 세트로 비교 후 결정)
 * chat.moderation.ai.effort      추론 강도 (low / medium / high). 단순 분류라 기본 low
 * chat.moderation.ai.interval    방송별로 모은 채팅을 검토하는 주기. 기본 3초
 * chat.moderation.ai.batch-size  한 번에 검토할 최대 메시지 수. 기본 50
 * chat.moderation.ai.max-pending 방송별로 쌓아 둘 수 있는 최대 메시지 수. 넘으면 오래된 것부터 버린다. 기본 500
 * </pre>
 */
@ConfigurationProperties(prefix = "chat.moderation")
public record ChatModerationProperties(List<String> bannedWords, Ai ai) {

    public ChatModerationProperties {
        bannedWords = bannedWords == null ? List.of() : List.copyOf(bannedWords);
        ai = ai == null ? new Ai(false, null, null, null, 0, 0) : ai;
    }

    public record Ai(boolean enabled, String model, String effort, Duration interval, int batchSize, int maxPending) {

        public Ai {
            model = model == null || model.isBlank() ? "claude-opus-5-5" : model;
            effort = effort == null || effort.isBlank() ? "low" : effort;
            interval = interval == null ? Duration.ofSeconds(3) : interval;
            batchSize = batchSize <= 0 ? 50 : batchSize;
            maxPending = maxPending <= 0 ? 500 : maxPending;
        }
    }
}
