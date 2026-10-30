package com.example.ddangddangddang.domain.product.service;

import com.example.ddangddangddang.domain.product.dto.response.ProductImageUploadResponse;
import com.example.ddangddangddang.domain.product.exception.ProductErrorCode;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 상품 이미지(갤러리)와 상품 설명 속 이미지를 검사한다.
 * - 브라우저가 업로드 URL로 저장소에 바로 올린 뒤 그 키를 보낸다. 내 키인지, 실제로 올라갔는지, 형식·크기를 확인한다.
 *   이 상품에 이미 저장돼 있던 이미지는 다시 확인하지 않는다. 빠진 이미지는 커밋된 뒤 저장소에서 지운다.
 * - 설명은 글자 그대로 보여 주고, "![](키)" 한 줄만 그 자리에 이미지로 그린다. (HTML·링크는 쓰지 않아 XSS가 생기지 않는다)
 * 이미지는 product-images/{판매자 ID}/ 아래에 두고 누구나 읽을 수 있다. (infra/storage.tf)
 * 상품을 만들기 전에 올리므로 상품 ID가 아니라 판매자 ID로 나눈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductImageResolver {

    static final long IMAGE_MAX_BYTES = 10 * 1024 * 1024;
    static final Duration IMAGE_UPLOAD_TTL = Duration.ofMinutes(5);
    static final int MAX_IMAGES = 10;
    static final int MAX_DESCRIPTION_IMAGES = 20;
    private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );
    private static final Pattern IMAGE_FILE = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp|gif)");
    // 설명 속 이미지 줄: 앞뒤 공백을 빼고 한 줄 전체가 "![](키)"일 때만. (프론트 lib/product-description.ts와 같은 규칙)
    private static final Pattern DESCRIPTION_IMAGE_LINE = Pattern.compile("!\\[]\\(([^()\\s]+)\\)");

    private final FileStorage fileStorage;

    /** 상품 이미지 업로드 URL. 키에 판매자 ID를 넣어, 남이 올린 이미지를 가져다 쓰지 못하게 한다. */
    public ProductImageUploadResponse createUpload(Long sellerId, String contentType, long contentLength) {
        String extension = IMAGE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ProductErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
        if (contentLength > IMAGE_MAX_BYTES) {
            throw new BusinessException(ProductErrorCode.IMAGE_TOO_LARGE);
        }
        String key = imagePrefix(sellerId) + UUID.randomUUID() + "." + extension;
        String uploadUrl = fileStorage.presignUpload(key, contentType, contentLength, IMAGE_UPLOAD_TTL);
        return new ProductImageUploadResponse(uploadUrl, key, fileStorage.publicUrl(key), IMAGE_UPLOAD_TTL.toSeconds());
    }

    /**
     * 요청한 상품 이미지 순서를 검사해 저장할 목록으로 만든다. 같은 키가 두 번 오면 한 번만 남긴다.
     *
     * @param saved 이 상품에 지금 저장된 이미지 키(갤러리·설명). 다시 확인하지 않는다.
     */
    public List<String> resolveImages(Long sellerId, Collection<String> saved, List<String> requested) {
        List<String> keys = new ArrayList<>(new LinkedHashSet<>(requested));
        if (keys.size() > MAX_IMAGES) {
            throw new BusinessException(ProductErrorCode.TOO_MANY_IMAGES);
        }
        keys.forEach(key -> checkKey(sellerId, saved, key));
        return keys;
    }

    /**
     * 설명 속 이미지 줄을 검사한다. 내 키가 아니거나 올라가지 않은 이미지가 있으면 막는다.
     * 다만 이 기능 전에 저장된 설명에 원래 있던 "![](내 키가 아닌 것)" 줄은 그대로 둔다. (글자로만 보이고, 지우지도 않는다)
     * 막으면 그 상품은 설명을 고치기 전까지 다른 정보도 수정할 수 없게 된다.
     */
    public void checkDescription(Long sellerId, Collection<String> saved, String description) {
        // 장수는 이미지 줄 수로 센다. 같은 이미지를 여러 줄 넣어도 화면에는 줄마다 그려지기 때문이다.
        if (descriptionImageLineCount(description) > MAX_DESCRIPTION_IMAGES) {
            throw new BusinessException(ProductErrorCode.TOO_MANY_DESCRIPTION_IMAGES);
        }
        descriptionImageKeys(description).stream()
                .filter(key -> isOwnKey(sellerId, key) || !saved.contains(key))
                .forEach(key -> checkKey(sellerId, saved, key));
    }

    /** 이 판매자가 업로드 URL로 받은 모양의 키인지 (product-images/{판매자 ID}/{uuid}.{확장자}). 이런 키만 믿고, 그리고, 지운다. */
    public static boolean isOwnKey(Long sellerId, String key) {
        String prefix = imagePrefix(sellerId);
        return key != null && key.startsWith(prefix) && IMAGE_FILE.matcher(key.substring(prefix.length())).matches();
    }

    /** 설명에서 이미지 줄의 키를 순서대로(중복 없이) 뽑는다. */
    public static List<String> descriptionImageKeys(String description) {
        return List.copyOf(new LinkedHashSet<>(descriptionImageLines(description)));
    }

    /** 설명의 이미지 줄 수 (같은 키가 여러 줄이면 그만큼 센다) */
    static int descriptionImageLineCount(String description) {
        return descriptionImageLines(description).size();
    }

    private static List<String> descriptionImageLines(String description) {
        if (description == null || description.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>();
        for (String line : description.split("\\R")) {
            Matcher matcher = DESCRIPTION_IMAGE_LINE.matcher(line.strip());
            if (matcher.matches()) {
                keys.add(matcher.group(1));
            }
        }
        return keys;
    }

    /** 상품이 쓰는 이미지 키 전부 (갤러리 + 설명) */
    public static Set<String> usedKeys(Collection<String> imageKeys, String description) {
        Set<String> keys = new LinkedHashSet<>(imageKeys);
        keys.addAll(descriptionImageKeys(description));
        return keys;
    }

    /**
     * before에 있다가 after에서 빠진 이미지를 커밋된 뒤 지운다. (롤백되면 이전 이미지를 계속 써야 하므로)
     * 이 판매자의 키만 지운다. 예전 설명에 남의 키(다른 사람 프로필 사진 등)가 적혀 있었어도 그 파일은 건드리지 않는다.
     *
     * @param usedElsewhere 다른 상품(삭제·판매된 상품 포함)도 쓰는 키인지. 쓰면 지우지 않는다.
     */
    public void deleteRemovedImagesAfterCommit(Long sellerId, Set<String> before, Set<String> after, Predicate<String> usedElsewhere) {
        before.stream()
                .filter(key -> !after.contains(key))
                .filter(key -> isOwnKey(sellerId, key))
                .filter(key -> !usedElsewhere.test(key))
                .forEach(this::deleteAfterCommit);
    }

    /** 내 키인지 먼저 보고, 이 상품에 이미 저장된 키면 저장소 확인(존재·형식·크기)만 건너뛴다. */
    private void checkKey(Long sellerId, Collection<String> saved, String key) {
        if (!isOwnKey(sellerId, key)) {
            throw new BusinessException(ProductErrorCode.IMAGE_NOT_UPLOADED);
        }
        if (saved.contains(key)) {
            return;
        }
        FileStorage.StoredFile file = fileStorage.find(key)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.IMAGE_NOT_UPLOADED));
        if (file.size() > IMAGE_MAX_BYTES) {
            throw new BusinessException(ProductErrorCode.IMAGE_TOO_LARGE);
        }
        if (!IMAGE_EXTENSIONS.containsKey(file.contentType())) {
            throw new BusinessException(ProductErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }
    }

    private static String imagePrefix(Long sellerId) {
        return "product-images/" + sellerId + "/";
    }

    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("상품에서 뺀 이미지를 지우지 못했습니다. key={}", key, e);
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
