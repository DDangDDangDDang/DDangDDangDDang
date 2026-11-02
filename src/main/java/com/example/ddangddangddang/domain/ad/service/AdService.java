package com.example.ddangddangddang.domain.ad.service;

import com.example.ddangddangddang.domain.ad.dto.AdCreateRequest;
import com.example.ddangddangddang.domain.ad.dto.AdImageUploadRequest;
import com.example.ddangddangddang.domain.ad.dto.AdImageUploadResponse;
import com.example.ddangddangddang.domain.ad.dto.AdResponse;
import com.example.ddangddangddang.domain.ad.entity.Ad;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;
import com.example.ddangddangddang.domain.ad.exception.AdErrorCode;
import com.example.ddangddangddang.domain.ad.repository.AdRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 배너 광고. 관리자가 이미지를 올려 등록하고 켜고 끈다. 화면은 자리별 켜진 광고를 받아 간다.
 *
 * 이미지 업로드는 다른 이미지와 같은 방식이다. (FileStorage: 운영은 S3, 로컬은 서버 디스크)
 * 1. createUpload로 업로드 URL을 받고 2. 브라우저가 그 URL로 올린 뒤 3. create로 키를 넘겨 등록한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdService {

    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    static final Duration UPLOAD_TTL = Duration.ofMinutes(10);
    static final String KEY_PREFIX = "ads/";
    /** 받는 이미지 형식과 저장할 확장자 */
    static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif");
    private static final Pattern IMAGE_FILE = Pattern.compile("[0-9a-f-]{36}\\.(jpg|png|webp|gif)");

    private final AdRepository adRepository;
    private final FileStorage fileStorage;

    /** 화면 배너: 이 자리의 켜진 광고 (먼저 등록한 순). 누구나 볼 수 있다 */
    public List<AdResponse> getActive(AdPlacement placement) {
        return adRepository.findByPlacementAndEnabledTrueOrderByIdAsc(placement).stream()
                .map(ad -> AdResponse.from(ad, fileStorage::publicUrl))
                .toList();
    }

    /** 관리자 목록. placement가 없으면 모든 자리 */
    public List<AdResponse> getAll(AdPlacement placement) {
        List<Ad> ads = placement == null
                ? adRepository.findAllByOrderByPlacementAscIdAsc()
                : adRepository.findByPlacementOrderByIdAsc(placement);
        return ads.stream().map(ad -> AdResponse.from(ad, fileStorage::publicUrl)).toList();
    }

    /** 광고 이미지 업로드 URL. 5MB 이하의 JPG·PNG·WEBP·GIF만 받는다 */
    public AdImageUploadResponse createUpload(AdImageUploadRequest request) {
        String extension = IMAGE_TYPES.get(request.contentType());
        if (extension == null || request.contentLength() > MAX_IMAGE_BYTES) {
            throw new BusinessException(AdErrorCode.INVALID_IMAGE);
        }
        String key = KEY_PREFIX + UUID.randomUUID() + "." + extension;
        String uploadUrl = fileStorage.presignUpload(key, request.contentType(), request.contentLength(), UPLOAD_TTL);
        return new AdImageUploadResponse(uploadUrl, key, fileStorage.publicUrl(key), UPLOAD_TTL.toSeconds());
    }

    /** 광고 등록. 올린 이미지가 실제로 있고 규칙에 맞는지, 링크가 http·https인지 확인한다. 켜진 상태로 만든다 */
    @Transactional
    public AdResponse create(AdCreateRequest request) {
        String imageKey = request.imageKey();
        if (!imageKey.startsWith(KEY_PREFIX) || !IMAGE_FILE.matcher(imageKey.substring(KEY_PREFIX.length())).matches()) {
            throw new BusinessException(AdErrorCode.IMAGE_NOT_UPLOADED);
        }
        FileStorage.StoredFile file = fileStorage.find(imageKey)
                .orElseThrow(() -> new BusinessException(AdErrorCode.IMAGE_NOT_UPLOADED));
        if (file.size() > MAX_IMAGE_BYTES || !IMAGE_TYPES.containsKey(file.contentType())) {
            fileStorage.delete(imageKey);
            throw new BusinessException(AdErrorCode.INVALID_IMAGE);
        }
        String linkUrl = validLink(request.linkUrl());

        Ad ad = adRepository.save(Ad.create(request.placement(), imageKey, linkUrl, request.altText().strip()));
        log.info("[광고] 등록 adId={}, placement={}", ad.getId(), ad.getPlacement());
        return AdResponse.from(ad, fileStorage::publicUrl);
    }

    /** 켜기·끄기 */
    @Transactional
    public AdResponse changeEnabled(Long adId, boolean enabled) {
        Ad ad = findById(adId);
        ad.changeEnabled(enabled);
        return AdResponse.from(ad, fileStorage::publicUrl);
    }

    /** 삭제. 이미지는 커밋 뒤에 지운다 (삭제가 롤백되면 이미지가 남아 있어야 하므로) */
    @Transactional
    public void delete(Long adId) {
        Ad ad = findById(adId);
        adRepository.delete(ad);
        deleteAfterCommit(ad.getImageKey());
        log.info("[광고] 삭제 adId={}", adId);
    }

    private Ad findById(Long adId) {
        return adRepository.findById(adId).orElseThrow(() -> new BusinessException(AdErrorCode.NOT_FOUND));
    }

    /**
     * 광고 링크 검사. http·https만 받는다.
     * javascript: 같은 주소가 들어가면 광고를 누른 사람의 브라우저에서 스크립트가 실행될 수 있다.
     */
    static String validLink(String raw) {
        String link = raw == null ? "" : raw.strip();
        try {
            URI uri = new URI(link);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                throw new BusinessException(AdErrorCode.INVALID_LINK);
            }
        } catch (URISyntaxException e) {
            throw new BusinessException(AdErrorCode.INVALID_LINK);
        }
        return link;
    }

    private void deleteAfterCommit(String key) {
        Runnable delete = () -> {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException e) {
                log.warn("삭제한 광고의 이미지를 지우지 못했습니다. key={}", key, e);
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
