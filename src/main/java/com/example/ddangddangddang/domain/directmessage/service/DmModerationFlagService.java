package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmModerationFlagResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;
import com.example.ddangddangddang.domain.directmessage.entity.DmModerationFlag;
import com.example.ddangddangddang.domain.directmessage.exception.DirectMessageErrorCode;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmModerationFlagRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 AI 검토 확인 화면용 조회. 신고(DmReportService)와 같이, AI가 표시한 대화방의 메시지만 볼 수 있다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmModerationFlagService {

    private final DmModerationFlagRepository flagRepository;
    private final DmMessageRepository dmMessageRepository;

    /**
     * AI가 표시한 메시지 목록. 최근 표시부터, 메시지 내용과 함께 보여 준다.
     */
    public CursorResponse<DmModerationFlagResponse> getFlags(Long cursor, int size) {
        int pageSize = DirectMessageService.pageSize(size);
        List<DmModerationFlag> flags = flagRepository.findByCursor(cursor, PageRequest.of(0, pageSize + 1));
        List<Long> messageIds = flags.stream().map(DmModerationFlag::getMessageId).toList();
        Map<Long, String> contents = dmMessageRepository.findAllById(messageIds).stream()
                .collect(Collectors.toMap(DmMessage::getId, DmMessage::getContent));
        return CursorResponse.of(flags, pageSize,
                flag -> DmModerationFlagResponse.of(flag, contents.get(flag.getMessageId())),
                DmModerationFlag::getId);
    }

    /**
     * AI가 표시한 대화방의 메시지. 앞뒤 맥락을 확인할 수 있도록 방 전체를 최신순 커서로 보여 준다.
     */
    public CursorResponse<DmMessageResponse> getFlaggedRoomMessages(Long flagId, Long cursor, int size) {
        DmModerationFlag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.MODERATION_FLAG_NOT_FOUND));
        int pageSize = DirectMessageService.pageSize(size);
        List<DmMessage> messages = dmMessageRepository.findByCursor(flag.getRoomId(), cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(messages, pageSize, DmMessageResponse::from, DmMessage::getId);
    }
}
