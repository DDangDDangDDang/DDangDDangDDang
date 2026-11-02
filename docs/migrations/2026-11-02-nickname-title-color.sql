-- 채팅 뱃지(칭호)와 닉네임 색상을 스킨과 함께 장착하기 (feat/shop-tabs-chat-title)
--
-- users에 장착 컬럼 두 개를 더한다. 둘 다 NULL 허용이라 기존 회원에게 영향이 없다.
-- 가진 꾸미기는 기존 user_badges(badge는 varchar)에 그대로 들어가므로 바꿀 것이 없다.
--
-- ddl-auto가 update면 새 버전이 뜰 때 컬럼이 자동으로 생겨 이 SQL은 필요 없다.
-- ddl-auto가 validate·none이면 새 버전을 띄우기 전에 한 번 실행한다.

ALTER TABLE users
    ADD COLUMN equipped_title varchar(20) NULL,
    ADD COLUMN equipped_color varchar(20) NULL;
