-- 포인트 내역 유형을 MySQL ENUM에서 varchar로 바꾼다 (feat/win-cancel-history)
--
-- ddl-auto로 만든 point_histories.type은 ENUM이다. 새 유형(WIN_CANCEL, WIN_CANCEL_PENALTY)을 저장하려면
-- ENUM 정의에 그 값이 있어야 하는데, 환경에 따라 ddl-auto가 고쳐 주지 않을 수 있다.
-- 다른 엔티티처럼 문자열로 바꿔 두면 값을 더해도 스키마를 고칠 필요가 없다. 기존 값은 그대로 유지된다.
--
-- 이 버전을 배포하기 전에 실행한다. 이미 varchar여도 다시 실행해도 된다.
-- 확인: SHOW CREATE TABLE point_histories;

ALTER TABLE point_histories MODIFY type varchar(20) NOT NULL;
