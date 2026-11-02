package com.example.ddangddangddang.global.storage;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;

/**
 * 로컬 저장소(LocalFileStorage)의 업로드·내려받기. S3 대신 이 서버가 받는다.
 * 브라우저가 토큰 없이 부르므로 SecurityConfig에서 열어 둔다. API 명세에는 넣지 않는다. (프론트는 받은 URL을 그대로 쓴다)
 */
@Hidden
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
@RequestMapping(LocalFileStorage.URL_PREFIX)
public class LocalStorageController {

    private final LocalFileStorage storage;

    @PutMapping("/uploads/{token}")
    public ResponseEntity<Void> upload(@PathVariable String token,
                                       @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                                       HttpServletRequest request) throws IOException {
        return switch (storage.store(token, contentType, request.getInputStream())) {
            case STORED -> ResponseEntity.ok().build();
            case NOT_FOUND -> ResponseEntity.notFound().build();
            case REJECTED -> ResponseEntity.badRequest().build();
        };
    }

    @GetMapping("/files/{*key}")
    public ResponseEntity<Resource> download(@PathVariable String key) {
        String normalized = key.startsWith("/") ? key.substring(1) : key;
        try {
            return storage.file(normalized)
                    .map(path -> ResponseEntity.ok()
                            .contentType(storage.contentTypeOf(normalized))
                            // 키에 무작위 값이 들어 있어 같은 키의 내용이 바뀌지 않는다.
                            .cacheControl(CacheControl.maxAge(Duration.ofDays(365)))
                            .<Resource>body(new FileSystemResource(path)))
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
