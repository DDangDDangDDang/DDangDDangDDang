package com.example.ddangddangddang.domain.livebot.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 라이브 봇. 관리자가 만든 로그인할 수 없는 판매자 계정(userId)과 그 계정이 내보낼 녹화 영상(videoKey).
 * 관리자가 켜면 이 계정의 방송을 만들어 바로 시작하고, 시청 화면은 영상을 방송 시작 시각에 맞춰 반복 재생한다.
 * 카메라 방송은 할 수 없다. (봇 계정으로는 로그인할 수 없어 스튜디오에 들어갈 수 없음)
 */
@Getter
@Entity
@Table(name = "live_bots")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LiveBot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 봇 계정 (User.createBot) */
    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 녹화 영상의 저장소 키 (live-bots/uuid.확장자). 화면 주소는 FileStorage.publicUrl로 만든다 */
    @Column(name = "video_key", nullable = false, length = 200)
    private String videoKey;

    private LiveBot(Long userId, String videoKey) {
        this.userId = userId;
        this.videoKey = videoKey;
    }

    public static LiveBot create(Long userId, String videoKey) {
        return new LiveBot(userId, videoKey);
    }
}
