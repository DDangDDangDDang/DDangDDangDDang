package com.example.ddangddangddang.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * 실제 MySQL에서 실행해야 하는 테스트의 공통 부모 클래스.
 *
 * H2는 락 동작(갭 락, 데드락 감지, 유니크 키 대기)이 MySQL InnoDB와 달라서,
 * 동시성 테스트는 운영과 같은 MySQL에서 확인해야 의미가 있다.
 *
 * 컨테이너는 테스트 클래스마다 새로 띄우지 않고 JVM에서 한 번만 띄워 재사용한다. (싱글턴 컨테이너)
 * 클래스마다 띄우면 느리고, 스프링 테스트 컨텍스트 캐시가 이미 내려간 컨테이너 주소를 들고 있게 된다.
 * Docker가 실행 중이어야 한다.
 */
@SpringBootTest
public abstract class MySqlIntegrationTest {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("ddang")
            .withUsername("ddang")
            .withPassword("ddang");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET SESSION innodb_lock_wait_timeout = 3");
    }
}