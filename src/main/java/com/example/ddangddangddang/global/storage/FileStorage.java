package com.example.ddangddangddang.global.storage;

import java.time.Duration;
import java.util.Optional;

/**
 * 파일 저장소. 브라우저가 서버를 거치지 않고 저장소에 바로 올리도록 "미리 서명한 업로드 URL"을 만든다.
 * <p>
 * 1. presignUpload: 업로드 URL을 만든다. 정해진 크기·형식의 파일 하나만, ttl 동안만 올릴 수 있다.
 * 2. 브라우저가 그 URL로 PUT 한다. (Content-Type은 presignUpload에 넘긴 값과 같아야 한다)
 * 3. find: 올라갔는지 확인하고 키를 저장한다.
 * <p>
 * 운영은 S3(S3FileStorage), 로컬 개발은 서버 디스크(LocalFileStorage)를 쓴다. (app.storage.type)
 */
public interface FileStorage {

    /** @return 브라우저가 PUT 할 URL */
    String presignUpload(String key, String contentType, long contentLength, Duration ttl);

    /** 올라간 파일의 크기·형식. 없으면 empty */
    Optional<StoredFile> find(String key);

    /** 없는 파일이어도 오류 없이 넘어간다 */
    void delete(String key);

    /** 화면에서 이미지를 보여 줄 주소 */
    String publicUrl(String key);

    record StoredFile(long size, String contentType) {
    }
}
