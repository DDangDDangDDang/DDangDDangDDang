package com.example.ddangddangddang.domain.directmessage.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DmReportReason {

    EXTERNAL_TRADE("외부 거래 유도"),
    SCAM("사기·사칭"),
    HARASSMENT("욕설·괴롭힘"),
    SEXUAL("성적인 내용"),
    SPAM("광고·스팸"),
    OTHER("기타");

    private final String label;
}
