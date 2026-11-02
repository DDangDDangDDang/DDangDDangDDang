package com.example.ddangddangddang.global.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageTest {

    private static final String KEY = "profile-images/1/a.png";

    @TempDir
    Path dir;

    private MutableClock clock;
    private LocalFileStorage storage;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-28T00:00:00Z"));
        storage = new LocalFileStorage(dir, clock);
    }

    @Test
    @DisplayName("업로드 URL로 정한 형식·크기의 파일을 올리면 저장되고, 크기·형식을 확인할 수 있다")
    void storeAndFind() throws IOException {
        String token = tokenOf(storage.presignUpload(KEY, "image/png", 3, Duration.ofMinutes(5)));

        assertThat(storage.store(token, "image/png", body(3))).isEqualTo(LocalFileStorage.UploadResult.STORED);

        assertThat(storage.find(KEY)).contains(new FileStorage.StoredFile(3, "image/png"));
        assertThat(storage.publicUrl(KEY)).isEqualTo("/api/v1/local-storage/files/" + KEY);
    }

    @Test
    @DisplayName("업로드 URL은 한 번만 쓸 수 있다")
    void oneTime() throws IOException {
        String token = tokenOf(storage.presignUpload(KEY, "image/png", 3, Duration.ofMinutes(5)));
        storage.store(token, "image/png", body(3));

        assertThat(storage.store(token, "image/png", body(3))).isEqualTo(LocalFileStorage.UploadResult.NOT_FOUND);
    }

    @Test
    @DisplayName("형식이나 크기가 정한 것과 다르면 저장하지 않는다")
    void rejectMismatch() throws IOException {
        String typeToken = tokenOf(storage.presignUpload(KEY, "image/png", 3, Duration.ofMinutes(5)));
        String sizeToken = tokenOf(storage.presignUpload(KEY, "image/png", 3, Duration.ofMinutes(5)));

        assertThat(storage.store(typeToken, "text/html", body(3))).isEqualTo(LocalFileStorage.UploadResult.REJECTED);
        assertThat(storage.store(sizeToken, "image/png", body(4))).isEqualTo(LocalFileStorage.UploadResult.REJECTED);
        assertThat(storage.find(KEY)).isEmpty();
    }

    @Test
    @DisplayName("만료된 업로드 URL은 쓸 수 없다")
    void expired() throws IOException {
        String token = tokenOf(storage.presignUpload(KEY, "image/png", 3, Duration.ofMinutes(5)));
        clock.instant = clock.instant.plus(Duration.ofMinutes(6));

        assertThat(storage.store(token, "image/png", body(3))).isEqualTo(LocalFileStorage.UploadResult.NOT_FOUND);
    }

    @Test
    @DisplayName("저장 폴더 밖을 가리키는 키는 거절한다")
    void pathTraversal() {
        assertThatThrownBy(() -> storage.presignUpload("../outside.png", "image/png", 3, Duration.ofMinutes(5)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.find("profile-images/../../outside.png"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("지우면 더 이상 찾을 수 없고, 없는 파일을 지워도 오류가 아니다")
    void delete() throws IOException {
        String token = tokenOf(storage.presignUpload(KEY, "image/png", 3, Duration.ofMinutes(5)));
        storage.store(token, "image/png", body(3));

        storage.delete(KEY);
        storage.delete(KEY);

        assertThat(storage.find(KEY)).isEmpty();
    }

    private static String tokenOf(String uploadUrl) {
        return uploadUrl.substring(uploadUrl.lastIndexOf('/') + 1);
    }

    private static ByteArrayInputStream body(int size) {
        return new ByteArrayInputStream(new byte[size]);
    }

    private static class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
