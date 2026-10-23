package com.example.ddangddangddang.domain.notification.service;

import com.example.ddangddangddang.domain.notification.dto.NotificationSettingResponse;
import com.example.ddangddangddang.domain.notification.entity.NotificationCategory;
import com.example.ddangddangddang.domain.notification.entity.NotificationMute;
import com.example.ddangddangddang.domain.notification.repository.NotificationMuteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 알림 종류(묶음)별 켜기·끄기. 기본은 모두 켜짐이고, 끈 묶음만 저장한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSettingService {

    private final NotificationMuteRepository muteRepository;
    private final TransactionTemplate transactionTemplate;

    public List<NotificationSettingResponse> getSettings(Long userId) {
        Set<NotificationCategory> muted = muteRepository.findByUserId(userId).stream()
                .map(NotificationMute::getCategory)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationCategory.class)));
        return Arrays.stream(NotificationCategory.values())
                .map(category -> NotificationSettingResponse.of(category, !muted.contains(category)))
                .toList();
    }

    /** 한 묶음을 켜거나 끈다. 이미 그 상태면 그대로 둔다. (버튼 연타·다른 탭) */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<NotificationSettingResponse> update(Long userId, NotificationCategory category, boolean enabled) {
        if (enabled) {
            transactionTemplate.executeWithoutResult(status -> muteRepository.unmute(userId, category));
        } else if (!muteRepository.existsByUserIdAndCategory(userId, category)) {
            try {
                transactionTemplate.executeWithoutResult(status -> muteRepository.saveAndFlush(NotificationMute.create(userId, category)));
            } catch (DataIntegrityViolationException e) {
                // 같은 요청이 동시에 두 번 온 경우. 한쪽이 저장했으니 꺼진 상태다.
            }
        }
        return getSettings(userId);
    }
}
