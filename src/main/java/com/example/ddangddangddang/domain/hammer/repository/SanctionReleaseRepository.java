package com.example.ddangddangddang.domain.hammer.repository;

import com.example.ddangddangddang.domain.hammer.entity.SanctionRelease;
import com.example.ddangddangddang.domain.hammer.entity.SanctionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SanctionReleaseRepository extends JpaRepository<SanctionRelease, Long> {

    Optional<SanctionRelease> findTopByUserIdAndTypeOrderByReleasedAtDesc(Long userId, SanctionType type);
}
