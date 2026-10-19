package com.example.ddangddangddang.domain.directmessage.service;

import com.example.ddangddangddang.domain.directmessage.dto.DmBlockResponse;
import com.example.ddangddangddang.domain.directmessage.entity.DmBlock;
import com.example.ddangddangddang.domain.directmessage.exception.DirectMessageErrorCode;
import com.example.ddangddangddang.domain.directmessage.repository.DmBlockRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 1:1 채팅 차단. 차단하면 두 사람 모두 서로에게 메시지를 보낼 수 없다. (DirectMessageService.send에서 확인)
 * 대화방과 이전 메시지는 그대로 남는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DmBlockService {

    private final DmBlockRepository dmBlockRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 차단한다. 이미 차단한 상대면 그대로 성공으로 응답한다. (버튼 연타, 재시도)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public DmBlockResponse block(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new BusinessException(DirectMessageErrorCode.CANNOT_BLOCK_SELF);
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(DirectMessageErrorCode.TARGET_USER_NOT_FOUND));

        DmBlock block = findBlock(userId, targetUserId);
        if (block == null) {
            try {
                block = transactionTemplate.execute(status ->
                        dmBlockRepository.saveAndFlush(DmBlock.create(userId, targetUserId)));
            } catch (DataIntegrityViolationException e) {
                // 동시에 두 번 차단한 경우. 먼저 저장된 차단을 돌려준다.
                block = findBlock(userId, targetUserId);
                if (block == null) {
                    throw e;
                }
            }
        }
        return new DmBlockResponse(target.getId(), target.getNickname(), block.getCreatedAt());
    }

    /**
     * 차단을 푼다. 차단하지 않은 상대여도 성공으로 응답한다.
     */
    @Transactional
    public void unblock(Long userId, Long targetUserId) {
        dmBlockRepository.deleteBlock(userId, targetUserId);
    }

    public List<DmBlockResponse> getBlocks(Long userId) {
        List<DmBlock> blocks = dmBlockRepository.findByBlockerIdOrderByIdDesc(userId);
        Map<Long, String> nicknames = userRepository.findAllById(blocks.stream().map(DmBlock::getBlockedId).toList())
                .stream().collect(Collectors.toMap(User::getId, User::getNickname));
        return blocks.stream()
                .map(block -> new DmBlockResponse(block.getBlockedId(), nicknames.get(block.getBlockedId()), block.getCreatedAt()))
                .toList();
    }

    private DmBlock findBlock(Long blockerId, Long blockedId) {
        return dmBlockRepository.findByBlockerIdAndBlockedId(blockerId, blockedId).orElse(null);
    }
}
