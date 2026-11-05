package com.example.ddangddangddang.domain.user.repository;

import com.example.ddangddangddang.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    // 자기 자신은 빼고 본다. DB가 대소문자를 구분하지 않아(utf8mb4_unicode_ci) "Abc" → "abc"처럼 대소문자만 바꿀 때 자기와 겹치므로
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    /** 회원 한 명의 작업(배송지 추가 등)을 차례로 처리하려고 회원 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}