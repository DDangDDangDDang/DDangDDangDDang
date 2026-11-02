package com.example.ddangddangddang.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장소 설정. 값이 없으면 로컬 디스크에 저장한다. (환경변수 APP_STORAGE_TYPE=s3, APP_STORAGE_S3_BUCKET ...)
 *
 * @param type  local(기본, 서버 디스크) 또는 s3
 * @param s3    bucket: 버킷 이름, region: 리전(기본 ap-northeast-2),
 *              publicBaseUrl: 이미지를 보여 줄 주소 앞부분(CloudFront 등). 비우면 버킷 주소(https://{bucket}.s3.{region}.amazonaws.com)
 * @param local dir: 파일을 둘 폴더. 비우면 임시 폴더 아래 ddang-uploads
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(String type, S3 s3, Local local) {

    public StorageProperties {
        if (type == null || type.isBlank()) {
            type = "local";
        }
        if (s3 == null) {
            s3 = new S3(null, null, null);
        }
        if (local == null) {
            local = new Local(null);
        }
    }

    public record S3(String bucket, String region, String publicBaseUrl) {

        public S3 {
            if (region == null || region.isBlank()) {
                region = "ap-northeast-2";
            }
        }
    }

    public record Local(String dir) {

        public Local {
            if (dir == null || dir.isBlank()) {
                dir = System.getProperty("java.io.tmpdir") + "/ddang-uploads";
            }
        }
    }
}
