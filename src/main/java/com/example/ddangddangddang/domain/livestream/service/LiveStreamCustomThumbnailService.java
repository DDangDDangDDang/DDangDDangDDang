package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livestream.dto.LiveStreamResponse;
import com.example.ddangddangddang.domain.livestream.dto.ThumbnailUploadRequest;
import com.example.ddangddangddang.domain.livestream.dto.ThumbnailUploadResponse;
import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.domain.livestream.repository.LiveStreamRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 판매자가 직접 올리는 방송 카드 썸네일.
 * 있으면 송출 화면 자동 캡처(LiveStreamThumbnailService)보다 먼저 카드에 보여 주고, 지우면 자동 캡처로 돌아간다. (LiveStream.cardThumbnailKey)
 * 브라우저가 업로드 URL로 저장소에 바로 올린 뒤 키로 확정한다. 방송 주인만, 종료 전(방송 예정·방송 중)에만 바꿀 수 있다.
 * 이미지는 live-thumbnails/{방송 ID}/custom/ 아래에 둔다. 자동 캡처의 키 규칙({방송 ID}/{uuid}.jpg)과 달라 자동 캡처가 이 키를 쓰지 못한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveStreamCustomThumbnailService {

    static final long MAX_BYTES = 2 * 1024 * 1024;
    static final Duration UPLOAD_TTL = Duration.ofMinutes(5);
    // 받는 이미지 형식과 저장할 때 붙이는 확장자. 로컬 저장소는 확장자로 형식을 정한다. (LocalFileStorage)
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp"
    );
    private static final Pattern FILE = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp)");

    private final LiveStreamRepository liveStreamRepository;
    private final FileStorage fileStorage;
    private final TransactionTemplate transactionTemplate;

    /** 썸네일 업로드 URL. 키에 방송 ID를 넣어, 다른 방송에 올린 이미지를 가져다 쓰지 못하게 한다. */
    @Transactional(readOnly = true)
    public ThumbnailUploadResponse createUpload(Long liveStreamId, Long sellerId, ThumbnailUploadRequest request) {
        findById(liveStreamId).validateOverlayEditable(sellerId);
        String extension = EXTENSIONS.get(request.contentType());
        if (extension == null || request.contentLength() > MAX_BYTES) {
            throw new BusinessException(LiveStreamErrorCode.INVALID_CUSTOM_THUMBNAIL);
        }
        String key = prefix(liveStreamId) + UUID.randomUUID() + "." + extension;
        String uploadUrl = fileStorage.presignUpload(key, request.contentType(), request.contentLength(), UPLOAD_TTL);
        return new ThumbnailUploadResponse(uploadUrl, key, UPLOAD_TTL.toSeconds());
    }

    /**
     * 올린 이미지를 썸네일로 쓴다. 이 방송의 키인지, 저장소에 실제로 올라갔는지, 형식·크기가 맞는지 다시 확인한다.
     * 저장소 조회(S3 HEAD)는 DB 커넥션을 잡지 않도록 트랜잭션 밖에서 하고, 트랜잭션 안에서 방송을 다시 읽어 바꾼다.
     * 앞에 올린 썸네일은 커밋된 뒤 지운다. (롤백되면 앞의 썸네일을 계속 써야 하므로)
     */
    public LiveStreamResponse change(Long liveStreamId, Long sellerId, String thumbnailKey) {
        LiveStream before = findById(liveStreamId);
        before.validateOverlayEditable(sellerId);
        String prefix = prefix(liveStreamId);
        if (!thumbnailKey.startsWith(prefix) || !FILE.matcher(thumbnailKey.substring(prefix.length())).matches()) {
            throw new BusinessException(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED);
        }
        boolean verified = !thumbnailKey.equals(before.getCustomThumbnailKey());
        if (verified) {
            FileStorage.StoredFile file = fileStorage.find(thumbnailKey)
                    .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.THUMBNAIL_NOT_UPLOADED));
            if (file.size() > MAX_BYTES || !EXTENSIONS.containsKey(file.contentType())) {
                throw new BusinessException(LiveStreamErrorCode.INVALID_CUSTOM_THUMBNAIL);
            }
        }
        return transactionTemplate.execute(status -> {
            LiveStream liveStream = findById(liveStreamId);
            // 이미 쓰던 키라 저장소 확인을 건너뛰었는데 그 사이 다른 창이 바꿨다면, 그 키의 파일은 이미 지워졌을 수 있다.
            if (!verified && !thumbnailKey.equals(liveStream.getCustomThumbnailKey())) {
                throw new BusinessException(LiveStreamErrorCode.CONCURRENT_UPDATE);
            }
            return replace(liveStream, sellerId, thumbnailKey);
        });
    }

    /** 직접 올린 썸네일을 지운다. 카드는 다시 송출 화면 자동 캡처를 쓴다. */
    @Transactional
    public LiveStreamResponse delete(Long liveStreamId, Long sellerId) {
        return replace(findById(liveStreamId), sellerId, null);
    }

    private LiveStreamResponse replace(LiveStream liveStream, Long sellerId, String key) {
        String previous = liveStream.changeCustomThumbnail(sellerId, key);
        if (!Objects.equals(previous, key)) {
            // 다른 요청(방송 정보 수정, 다른 창의 썸네일 변경)과 겹치면 한쪽만 성공시킨다. (낙관적 락)
            try {
                liveStreamRepository.saveAndFlush(liveStream);
            } catch (ObjectOptimisticLockingFailureException e) {
                throw new BusinessException(LiveStreamErrorCode.CONCURRENT_UPDATE, e);
            }
            if (previous != null) {
                deleteAfterCommit(previous);
            }
        }
        return LiveStreamResponse.from(liveStream, key == null ? null : fileStorage.publicUrl(key), publicUrl(liveStream.getVideoKey()));
    }

    private String publicUrl(String key) {
        return key == null ? null : fileStorage.publicUrl(key);
    }

    private LiveStream findById(Long liveStreamId) {
        return liveStreamRepository.findById(liveStreamId)
                .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.NOT_FOUND));
    }

    private static String prefix(Long liveStreamId) {
        return "live-thumbnails/" + liveStreamId + "/custom/";
    }

    // 파일을 못 지워도 썸네일 변경은 성공으로 둔다.
    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("앞에 올린 방송 썸네일을 지우지 못했습니다. key={}", key, e);
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
