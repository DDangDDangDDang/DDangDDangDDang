package com.example.ddangddangddang.domain.livestream.service;

import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.livestream.dto.OverlayBoxRequest;
import com.example.ddangddangddang.domain.livestream.dto.OverlayImageUploadResponse;
import com.example.ddangddangddang.domain.livestream.entity.OverlayBox;
import com.example.ddangddangddang.domain.livestream.entity.OverlayTextSize;
import com.example.ddangddangddang.domain.livestream.entity.OverlayTextStyle;
import com.example.ddangddangddang.domain.livestream.exception.LiveStreamErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 판매자가 방송 화면에 넣는 텍스트·이미지 박스를 검사하고 저장할 모양으로 만든다.
 * - 텍스트 박스: 앞뒤 공백을 지우고, 비어 있으면 안 되고, 방송 제목처럼 연락처·외부 링크 같은 금지 내용을 막는다.
 * - 이미지 박스: 브라우저가 업로드 URL로 저장소에 바로 올린 뒤 그 키를 보낸다. 이 방송의 키인지, 실제로 올라갔는지, 형식·크기를 확인한다.
 *   이미 저장돼 있던 이미지는 다시 확인하지 않는다. 빠진 이미지는 커밋된 뒤 저장소에서 지운다.
 * 이미지는 overlay-images/{방송 ID}/ 아래에 두고 누구나 읽을 수 있다. (infra/storage.tf)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverlayBoxResolver {

    static final long IMAGE_MAX_BYTES = 5 * 1024 * 1024;
    static final Duration IMAGE_UPLOAD_TTL = Duration.ofMinutes(5);
    static final int DEFAULT_IMAGE_WIDTH = 25;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );
    private static final Pattern IMAGE_FILE = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp|gif)");

    private final FileStorage fileStorage;
    private final ChatContentFilter contentFilter;

    /** 이미지 박스용 업로드 URL. 키에 방송 ID를 넣어, 다른 방송에 올린 이미지를 가져다 쓰지 못하게 한다. */
    public OverlayImageUploadResponse createImageUpload(Long liveStreamId, String contentType, long contentLength) {
        String extension = IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(LiveStreamErrorCode.UNSUPPORTED_OVERLAY_IMAGE_TYPE);
        }
        if (contentLength > IMAGE_MAX_BYTES) {
            throw new BusinessException(LiveStreamErrorCode.OVERLAY_IMAGE_TOO_LARGE);
        }
        String key = imagePrefix(liveStreamId) + UUID.randomUUID() + "." + extension;
        String uploadUrl = fileStorage.presignUpload(key, contentType, contentLength, IMAGE_UPLOAD_TTL);
        return new OverlayImageUploadResponse(uploadUrl, key, fileStorage.publicUrl(key), IMAGE_UPLOAD_TTL.toSeconds());
    }

    /**
     * 요청한 박스를 저장할 박스로 바꾼다. 순서는 그대로다. (뒤에 있는 박스가 화면 위에 그려진다)
     *
     * @param current 지금 저장된 박스. 그대로 남는 이미지는 저장소를 다시 확인하지 않는다.
     */
    public List<OverlayBox> resolve(Long liveStreamId, List<OverlayBox> current, List<OverlayBoxRequest> requests) {
        Map<String, OverlayBox> savedImages = current.stream()
                .filter(box -> box.imageKey() != null)
                .collect(Collectors.toMap(OverlayBox::imageKey, Function.identity(), (a, b) -> a));
        List<OverlayBox> boxes = new ArrayList<>(requests.size());
        for (OverlayBoxRequest request : requests) {
            boxes.add(switch (request.type()) {
                case TEXT -> toTextBox(request);
                case IMAGE -> toImageBox(liveStreamId, request, savedImages);
            });
        }
        return boxes;
    }

    /** before에 있다가 after에서 빠진 이미지를 커밋된 뒤 지운다. (롤백되면 이전 박스를 계속 써야 하므로) */
    public void deleteRemovedImagesAfterCommit(List<OverlayBox> before, List<OverlayBox> after) {
        Set<String> kept = after.stream().map(OverlayBox::imageKey).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> removed = new HashSet<>();
        before.stream().map(OverlayBox::imageKey).filter(Objects::nonNull).filter(key -> !kept.contains(key)).forEach(removed::add);
        removed.forEach(this::deleteAfterCommit);
    }

    private OverlayBox toTextBox(OverlayBoxRequest request) {
        String text = request.text() == null ? "" : request.text().strip();
        if (text.isEmpty()) {
            throw new BusinessException(LiveStreamErrorCode.OVERLAY_TEXT_REQUIRED);
        }
        contentFilter.findViolation(text).ifPresent(violation -> {
            throw new BusinessException(LiveStreamErrorCode.CONTENT_NOT_ALLOWED, violation.getMessage());
        });
        OverlayTextStyle style = request.textStyle() == null ? OverlayTextStyle.PLAIN : request.textStyle();
        OverlayTextSize size = request.textSize() == null ? OverlayTextSize.MEDIUM : request.textSize();
        return OverlayBox.text(request.x(), request.y(), text, style, size);
    }

    private OverlayBox toImageBox(Long liveStreamId, OverlayBoxRequest request, Map<String, OverlayBox> savedImages) {
        String key = request.imageKey();
        int width = request.width() == null ? DEFAULT_IMAGE_WIDTH : request.width();
        if (key == null || key.isBlank()) {
            throw new BusinessException(LiveStreamErrorCode.OVERLAY_IMAGE_NOT_UPLOADED);
        }
        OverlayBox saved = savedImages.get(key);
        if (saved != null) {
            return OverlayBox.image(request.x(), request.y(), key, saved.imageUrl(), width);
        }
        String prefix = imagePrefix(liveStreamId);
        if (!key.startsWith(prefix) || !IMAGE_FILE.matcher(key.substring(prefix.length())).matches()) {
            throw new BusinessException(LiveStreamErrorCode.OVERLAY_IMAGE_NOT_UPLOADED);
        }
        FileStorage.StoredFile file = fileStorage.find(key)
                .orElseThrow(() -> new BusinessException(LiveStreamErrorCode.OVERLAY_IMAGE_NOT_UPLOADED));
        if (file.size() > IMAGE_MAX_BYTES) {
            throw new BusinessException(LiveStreamErrorCode.OVERLAY_IMAGE_TOO_LARGE);
        }
        if (!IMAGE_EXTENSIONS.containsKey(file.contentType())) {
            throw new BusinessException(LiveStreamErrorCode.UNSUPPORTED_OVERLAY_IMAGE_TYPE);
        }
        return OverlayBox.image(request.x(), request.y(), key, fileStorage.publicUrl(key), width);
    }

    private static String imagePrefix(Long liveStreamId) {
        return "overlay-images/" + liveStreamId + "/";
    }

    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("방송 화면에서 뺀 이미지를 지우지 못했습니다. key={}", key, e);
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
