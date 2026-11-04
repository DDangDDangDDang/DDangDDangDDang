-- 환전 계좌번호 암호화 저장 (fix/point-exchange-and-charge-safety)
--
-- 계좌번호를 AES-256-GCM으로 암호화해 저장하면서(AccountNumberConverter) 값이 길어졌다. (평문 최대 20자 → 암호문 약 70자)
-- ddl-auto(update)는 이미 있는 컬럼의 길이를 늘려 주지 않으므로, 그대로 배포하면 환전 신청 저장이 "Data too long"으로 실패한다.
--
-- 이 버전을 배포하기 "전에" 실행한다. 이미 255여도 다시 실행해도 된다.
-- 기존 평문 계좌번호는 그대로 읽힌다. (암호화 접두사 enc:v1:이 없으면 평문으로 본다)
-- 확인: SHOW CREATE TABLE point_exchanges;
--
-- 같이 준비할 것: 앱 비밀값(Secrets Manager ddang/app, dev도 따로)에 POINT_ACCOUNT_ENCRYPTION_KEY를 넣는다.
--   값 만들기: openssl rand -base64 32
--   한 번 정하면 바꾸지 않는다. (바꾸면 저장된 계좌번호를 읽을 수 없다) 키가 없으면 운영 서버가 뜨지 않는다.

ALTER TABLE point_exchanges MODIFY account_number varchar(255) NOT NULL;
