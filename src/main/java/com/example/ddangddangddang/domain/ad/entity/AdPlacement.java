package com.example.ddangddangddang.domain.ad.entity;

/**
 * 배너 광고 자리. 프론트의 자리 이름(lib/ads.ts의 AdPlacement)과 짝을 이룬다.
 * HOME_TOP: 홈 맨 위 가로 배너 (화면 1024×128, 권장 이미지 2048×256)
 * SIDEBAR: 왼쪽 메뉴 아래 세로 배너 (화면 240×320, 권장 이미지 480×640)
 */
public enum AdPlacement {
    HOME_TOP,
    SIDEBAR
}
