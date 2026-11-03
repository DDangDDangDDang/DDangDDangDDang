-- 라이브 봇: 봇 방송의 녹화 영상 키, 봇 표 (feat/live-bot)
--
-- ddl-auto가 update면 새 버전이 뜰 때 자동으로 생기므로 앞의 두 문장은 건너뛴다. validate·none이면 배포 전에 실행한다.
-- 기존 방송은 video_key가 NULL(사람 판매자 방송)로 채워진다.

ALTER TABLE live_streams
    ADD COLUMN video_key varchar(200) NULL;

CREATE TABLE live_bots (
    id         bigint       NOT NULL AUTO_INCREMENT,
    created_at datetime(6)  NOT NULL,
    updated_at datetime(6)  NULL,
    user_id    bigint       NOT NULL,
    video_key  varchar(200) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_bots_user (user_id)
);

-- 봇 계정은 비밀번호 없이 만든다. (아무도 로그인할 수 없게)
-- 소셜 로그인 도입 전에 만든 DB는 users.password가 NOT NULL로 남아 있을 수 있어, 그러면 봇을 만들 때 저장에 실패한다.
-- ddl-auto update는 이 제약을 풀지 않으므로 아래 결과가 NO면 실행한다. (소셜 가입도 같은 이유로 실패한다)
-- SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'password';
-- ALTER TABLE users MODIFY password varchar(255) NULL;
