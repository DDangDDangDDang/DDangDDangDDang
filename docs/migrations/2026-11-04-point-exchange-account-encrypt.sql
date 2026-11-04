-- 환전 계좌번호 암호화 저장 (fix/point-exchange-and-charge-safety)
--
-- 계좌번호를 AES-256-GCM으로 암호화해 저장하면서(AccountNumberConverter) 값이 길어졌다. (평문 최대 20자 → 암호문 약 70자)
-- ddl-auto(update)는 이미 있는 컬럼의 길이를 늘려 주지 않으므로, 그대로 배포하면 환전 신청 저장이 "Data too long"으로 실패한다.
--
-- 이 버전을 배포하기 "전에" 실행한다. 이미 255여도 다시 실행해도 된다.
-- 기존 평문 계좌번호는 그대로 읽힌다. 배포가 안정된 뒤(롤백할 일이 없을 때) POINT_EXCHANGE_REENCRYPTONSTARTUP=true로
-- 서버를 한 번 다시 시작하면 AccountNumberReencryptRunner가 평문 행을 암호화해 다시 저장한다. (기본은 꺼져 있음)
--   확인: SELECT COUNT(*) FROM point_exchanges WHERE account_number NOT LIKE 'enc:v1:%';  -- 켠 뒤 0이어야 한다
-- 주의: 이 버전 배포 뒤 새로 저장한 계좌번호는 암호화돼 있어, 이전 이미지로 롤백하면 그 행은 암호문으로 보인다.
-- 확인: SHOW CREATE TABLE point_exchanges;
--
-- 같이 준비할 것: 앱 비밀값(Secrets Manager ddang/app, dev도 따로)에 POINT_ACCOUNT_ENCRYPTION_KEY를 넣는다.
--   값 만들기: openssl rand -base64 32
--   한 번 정하면 바꾸지 않는다. (바꾸면 저장된 계좌번호를 읽을 수 없다) 키가 없으면 운영 서버가 뜨지 않는다.

ALTER TABLE point_exchanges MODIFY account_number varchar(255) NOT NULL;

-- 결제 취소 회수 내역(CHARGE_CANCEL)을 저장하려면 point_histories.type이 varchar여야 한다.
-- 2026-11-03-point-history-type-varchar.sql과 같은 SQL이다. 이미 실행했어도 다시 실행해도 된다.
ALTER TABLE point_histories MODIFY type varchar(20) NOT NULL;
