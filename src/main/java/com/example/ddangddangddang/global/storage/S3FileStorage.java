package com.example.ddangddangddang.global.storage;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Optional;

/**
 * S3 저장소. 업로드 URL에 Content-Type과 Content-Length를 서명에 넣어, 다른 형식·크기의 파일은 S3가 거절하게 한다.
 * 화면에는 publicBaseUrl(CloudFront 등) + 키로 보여 주므로, 버킷(또는 CloudFront)이 이 키들을 공개로 읽을 수 있어야 한다.
 */
public class S3FileStorage implements FileStorage {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3FileStorage(S3Client s3Client, S3Presigner presigner, String bucket, String publicBaseUrl) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
    }

    @Override
    public String presignUpload(String key, String contentType, long contentLength, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        return presigner.presignPutObject(request -> request.signatureDuration(ttl).putObjectRequest(put))
                .url().toString();
    }

    @Override
    public Optional<StoredFile> find(String key) {
        try {
            HeadObjectResponse head = s3Client.headObject(request -> request.bucket(bucket).key(key));
            return Optional.of(new StoredFile(head.contentLength(), head.contentType()));
        } catch (S3Exception e) {
            // HEAD는 본문이 없어 NoSuchKeyException이 아니라 상태 코드 404로만 알 수 있다.
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void delete(String key) {
        s3Client.deleteObject(request -> request.bucket(bucket).key(key));
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }
}
