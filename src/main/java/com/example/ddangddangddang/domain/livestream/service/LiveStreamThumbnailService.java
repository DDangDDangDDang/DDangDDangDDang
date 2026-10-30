package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.ThumbnailUploadRequest;
import com.example.ddangddangddang.domain.livestream.dto.ThumbnailUploadResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 방송 카드 썸네일 (치지직처럼 지금 방송 화면).
 * 송출하는 판매자 브라우저가 1분마다 화면을 캡처해 저장소에 올리고(업로드 URL), 올린 키로 썸네일을 바꾼다.
 * 방송 카드(LiveStreamCard.thumbnailUrl)가 이 이미지를 보여 준다. 방송이 끝나도 마지막 썸네일은 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamThumbnailService {

    static final String CONTENT_TYPE = "image/jpeg";
    static final long MAX_BYTES = 1024 * 1024;
    static final Duration UPLOAD_TTL = Duration.ofMinutes(1);
    private static final Pattern THUMBNAIL_FILE = Pattern.compile("[0-9a-f-]{36}\\.jpg");

    private final LiveStreamRepository liveStreamRepository;
    private final FileStorage fileStorage;

    /** 썸네일 업로드 URL. 키에 방송 ID를 넣어, 다른 방송에 올린 이미지를 가져다 쓰지 못하게 한다. */
    @Transactional(readOnly = true)
    public ThumbnailUploadResponse createUpload(Long liveStreamId, Long sellerId, ThumbnailUploadRequest request) {
        findById(liveStreamId).validatePublishable(sellerId);
        if (!CONTENT_TYPE.equals(request.contentType()) || request.contentLength() > MAX_BYTES) {
            throw new BusinessException(LiveStreamErrorCode.INVALID_THUMBNAIL);
        }
        String key = prefix(liveStreamId) + UUID.randomUUID() + ".jpg";
        String uploadUrl = fileStorage.presignUpload(key, CONTENT_TYPE, request.contentLength(), UPLOAD_TTL);
        return new ThumbnailUploadResponse(uploadUrl, key, UPLOAD_TTL.toSeconds());
    }

    /** 올린 썸네일로 바꾸고, 앞의 썸네일은 커밋 뒤에 지운다. */
    @Transactional
    public void change(Long liveStreamId, Long sellerId, String thumbnailKey) {
        LiveStream liveStream = findById(liveStreamId);
        liveStream.validatePublishable(sellerId);
        String prefix = prefix(liveStreamId);
        if (!thumbnailKey.startsWith(prefix) || !THUMBNAIL_FILE.matcher(thumbnailKey.substring(prefix.length())).matches()) {
            throw new BusinessException(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);
        }
        FileStorage.StoredFile file = fileStorage.find(thumbnailKey)
                .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED));
        if (file.size() > MAX_BYTES || !CONTENT_TYPE.equals(file.contentType())) {
            fileStorage.delete(thumbnailKey);
            throw new BusinessException(LiveStreamErrorCode.INVALID_THUMBNAIL);
        }
        String previous = liveStream.getThumbnailKey();
        if (thumbnailKey.equals(previous)) {
            return;
        }
        if (liveStreamRepository.updateThumbnailKey(liveStreamId, thumbnailKey, previous) == 0) {
            // 다른 요청이 먼저 썸네일을 바꿨다. 이번에 올린 파일은 쓰지 않으므로 지운다.
            fileStorage.delete(thumbnailKey);
            throw new BusinessException(LiveStreamErrorCode.CONCURRENT_UPDATE);
        }
        if (previous != null) {
            deleteAfterCommit(previous);
        }
    }

    private LiveStream findById(Long liveStreamId) {
        return liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.NOT_FOUND));
    }

    private static String prefix(Long liveStreamId) {
        return "live-thumbnails/" + liveStreamId + "/";
    }

    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("지난 방송 썸네일을 지우지 못했습니다. key={}", key, e);
            }
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete.run();
            }
        });
    }
}
