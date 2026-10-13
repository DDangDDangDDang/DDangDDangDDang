package com.example.ddangddangddang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class DDangDDangDDangApplication {

    public static void main(String[] args) {
        SpringApplication.run(DDangDDangDDangApplication.class, args);
    }

}
