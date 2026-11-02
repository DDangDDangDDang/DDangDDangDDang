package com.example.ddangddangddang.global.storage;

import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 로컬 개발용 저장소. S3 없이 같은 흐름(업로드 URL 발급 → 브라우저가 PUT → 확인)을 서버 디스크로 흉내 낸다.
 * 업로드 URL은 한 번만 쓸 수 있는 무작위 토큰이고, 발급할 때 정한 형식·크기와 다르면 거절한다. (S3의 서명과 같은 역할)
 * 토큰은 메모리에만 두므로 서버를 다시 켜면 발급한 업로드 URL은 사라진다. 서버가 여러 대면 쓸 수 없다.
 */
public class LocalFileStorage implements FileStorage {

    public static final String URL_PREFIX = "/api/v1/local-storage";

    private final Path root;
    private final Clock clock;
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    public LocalFileStorage(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
    }

    @Override
    public String presignUpload(String key, String contentType, long contentLength, Duration ttl) {
        Instant now = clock.instant();
        tickets.values().removeIf(ticket -> ticket.expiresAt().isBefore(now));
        resolve(key);
        String token = UUID.randomUUID().toString();
        tickets.put(token, new Ticket(key, contentType, contentLength, now.plus(ttl)));
        return URL_PREFIX + "/uploads/" + token;
    }

    /**
     * 업로드 URL로 온 파일을 저장한다. 토큰은 성공·실패와 상관없이 한 번 쓰면 사라진다.
     */
    public UploadResult store(String token, String contentType, InputStream body) throws IOException {
        Ticket ticket = tickets.remove(token);
        if (ticket == null || ticket.expiresAt().isBefore(clock.instant())) {
            return UploadResult.NOT_FOUND;
        }
        if (!sameType(ticket.contentType(), contentType)) {
            return UploadResult.REJECTED;
        }
        // 정한 크기보다 1바이트 더 읽어 보고, 크기가 다르면 저장하지 않는다.
        byte[] bytes = body.readNBytes((int) Math.min(ticket.contentLength() + 1, Integer.MAX_VALUE));
        if (bytes.length != ticket.contentLength()) {
            return UploadResult.REJECTED;
        }
        Path path = resolve(ticket.key());
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
        return UploadResult.STORED;
    }

    /** 저장된 파일. 없으면 empty */
    public Optional<Path> file(String key) {
        Path path = resolve(key);
        return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
    }

    @Override
    public Optional<StoredFile> find(String key) {
        return file(key).map(path -> {
            try {
                return new StoredFile(Files.size(path), contentTypeOf(key).toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String publicUrl(String key) {
        return URL_PREFIX + "/files/" + key;
    }

    /** 파일 형식은 확장자로 정한다. (키를 만들 때 형식에 맞는 확장자를 붙인다) */
    public MediaType contentTypeOf(String key) {
        return MediaTypeFactory.getMediaType(key).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    // charset 같은 덧붙은 값은 보지 않고 형식(image/png)만 비교한다.
    private static boolean sameType(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        try {
            return MediaType.parseMediaType(expected).equalsTypeAndSubtype(MediaType.parseMediaType(actual));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    // 키에 ..을 넣어 저장 폴더 밖의 파일을 읽거나 쓰지 못하게 한다.
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("잘못된 파일 키입니다: " + key);
        }
        return path;
    }

    public enum UploadResult {
        STORED,
        NOT_FOUND,  // 없거나 만료된 업로드 URL
        REJECTED    // 발급할 때 정한 형식·크기와 다름
    }

    private record Ticket(String key, String contentType, long contentLength, Instant expiresAt) {
    }
}
