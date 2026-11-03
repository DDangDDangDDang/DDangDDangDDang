package com.example.ddangddangddang.domain.hammer.entity;

/**
 * 망치 등급. 거래 신뢰도를 단계로만 보여 준다. (점수 숫자는 밖에 내보내지 않는다)
 * NEWBIE는 구매 확정 거래가 적어 아직 판단할 수 없는 회원이다.
 */
public enum HammerGrade {
    NEWBIE,   // 새 망치
    WOOD,     // 나무 망치
    STONE,    // 돌 망치
    SILVER,   // 은 망치 (시작 점수가 여기)
    GOLD,     // 금 망치
    DIAMOND   // 다이아 망치
}
