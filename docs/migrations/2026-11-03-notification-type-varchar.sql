-- 알림 종류를 MySQL ENUM에서 varchar로 바꾼다 (feat/seller-welcome-mileage)
--
-- ddl-auto로 만든 notifications.type은 ENUM이다. 새 종류(DONATION_POINT_SELLER_WELCOME: 판매자 승인 축하 마일리지)를
-- 저장하려면 ENUM 정의에 그 값이 있어야 하는데, ddl-auto가 기존 컬럼의 ENUM 정의를 고쳐 주지 않을 수 있다.
-- 다른 엔티티처럼 문자열로 바꿔 두면 값을 더해도 스키마를 고칠 필요가 없다. 기존 값은 그대로 유지된다.
--
-- 이 버전을 배포하기 전에 실행한다. 이미 varchar여도 다시 실행해도 된다.
-- 확인: SHOW CREATE TABLE notifications;

ALTER TABLE notifications MODIFY type varchar(30) NOT NULL;
