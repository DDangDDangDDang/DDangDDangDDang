package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.dto.DmMessageResponse;
import com.example.ddangddangddang.domain.directmessage.dto.DmReportRequest;
import com.example.ddangddangddang.domain.directmessage.dto.DmReportResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmMessage;
import com.example.ddangddangddang.domain.directmessage.entity.DmReport;
import com.example.ddangddangddang.domain.directmessage.entity.DmRoom;
import com.example.ddangddangddang.domain.directmessage.exception.DirectMessageErrorCode;
import com.example.ddangddangddang.domain.directmessage.repository.DmMessageRepository;
import com.example.ddangddangddang.domain.directmessage.repository.DmReportRepository;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 1:1 채팅 신고와 관리자 신고 처리 화면용 조회.
 * 관리자는 신고가 들어온 대화방의 메시지만 볼 수 있다. (신고 없이 남의 대화를 보는 API는 두지 않는다)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmReportService {

    private final DmReportRepository dmReportRepository;
    private final DmMessageRepository dmMessageRepository;
    private final DirectMessageService directMessageService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 대화 상대를 신고한다. messageId를 보내면 그 메시지를 신고하고, 상대가 이 방에서 보낸 메시지여야 한다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DmReportResponse report(Long userId, Long roomId, DmReportRequest request) {
        DmRoom room = directMessageService.findRoomAsMember(userId, roomId);
        Long reportedUserId = room.otherUserId(userId);

        DmMessage message = null;
        if (request.messageId() != null) {
            message = dmMessageRepository.findById(request.messageId())
                    .filter(found -> found.getRoomId().equals(roomId) && found.getSenderId().equals(reportedUserId))
                    .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.INVALID_REPORT_TARGET));
            if (dmReportRepository.existsByReporterIdAndMessageId(userId, message.getId())) {
                throw new BusinessException(DirectMessageErrorCode.ALREADY_REPORTED);
            }
        }

        String detail = request.detail() == null || request.detail().isBlank() ? null : request.detail().strip();
        DmReport report;
        try {
            report = transactionTemplate.execute(status -> dmReportRepository.saveAndFlush(
                    DmReport.create(userId, reportedUserId, roomId, request.messageId(), request.reason(), detail)));
        } catch (DataIntegrityViolationException e) {
            // 같은 메시지를 동시에 두 번 신고한 경우
            throw new BusinessException(DirectMessageErrorCode.ALREADY_REPORTED, e);
        }
        return DmReportResponse.of(report, message == null ? null : message.getContent());
    }

    /**
     * 관리자 신고 목록. 최근 신고부터, 신고한 메시지 내용과 함께 보여 준다.
     */
    public CursorResponse<DmReportResponse> getReports(Long cursor, int size) {
        int pageSize = DirectMessageService.pageSize(size);
        List<DmReport> reports = dmReportRepository.findByCursor(cursor, PageRequest.of(0, pageSize + 1));
        List<Long> messageIds = reports.stream().map(DmReport::getMessageId).filter(Objects::nonNull).toList();
        Map<Long, String> contents = dmMessageRepository.findAllById(messageIds).stream()
                .collect(Collectors.toMap(DmMessage::getId, DmMessage::getContent));
        return CursorResponse.of(reports, pageSize,
                report -> DmReportResponse.of(report, report.getMessageId() == null ? null : contents.get(report.getMessageId())),
                DmReport::getId);
    }

    /**
     * 관리자가 신고된 대화방의 메시지를 본다. 앞뒤 맥락을 확인할 수 있도록 방 전체를 최신순 커서로 보여 준다.
     */
    public CursorResponse<DmMessageResponse> getReportedRoomMessages(Long reportId, Long cursor, int size) {
        DmReport report = dmReportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.REPORT_NOT_FOUND));
        int pageSize = DirectMessageService.pageSize(size);
        List<DmMessage> messages = dmMessageRepository.findByCursor(report.getRoomId(), cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(messages, pageSize, DmMessageResponse::from, DmMessage::getId);
    }
}
