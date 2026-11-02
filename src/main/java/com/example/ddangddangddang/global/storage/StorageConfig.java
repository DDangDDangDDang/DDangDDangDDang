package com.example.ddangddangddang.global.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.nio.file.Path;
import java.time.Clock;

/**
 * app.storage.type=s3면 S3, 아니면(기본) 서버 디스크에 저장한다.
 * S3 접근 권한은 AWS 기본 자격 증명(EC2 인스턴스 역할, 환경변수 등)을 쓴다.
 */
@Configuration
public class StorageConfig {

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Client s3Client(StorageProperties properties) {
        return S3Client.builder().region(Region.of(properties.s3().region())).build();
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder().region(Region.of(properties.s3().region())).build();
    }

    /** 버킷이 비어 있으면 첫 업로드 때가 아니라 서버를 띄울 때 바로 실패시킨다. */
    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
    public FileStorage s3FileStorage(StorageProperties properties, S3Client s3Client, S3Presigner presigner) {
        StorageProperties.S3 s3 = properties.s3();
        Assert.hasText(s3.bucket(), "APP_STORAGE_S3_BUCKET(버킷 이름)이 필요합니다.");
        String publicBaseUrl = s3.publicBaseUrl() == null || s3.publicBaseUrl().isBlank()
                ? "https://" + s3.bucket() + ".s3." + s3.region() + ".amazonaws.com"
                : s3.publicBaseUrl();
        return new S3FileStorage(s3Client, presigner, s3.bucket(), publicBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
    public LocalFileStorage localFileStorage(StorageProperties properties) {
        return new LocalFileStorage(Path.of(properties.local().dir()), Clock.systemDefaultZone());
    }
}
