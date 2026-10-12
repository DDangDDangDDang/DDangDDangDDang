package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "point_wallets")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointWallet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** 사용 가능 포인트 */
    @Column(nullable = false)
    private long balance;

    /** 입찰로 묶여 있는 포인트 */
    @Column(nullable = false)
    private long heldBalance;

    /** 비관적 락을 쓰지 않는 경로에서 동시 수정이 일어났을 때를 대비한 안전장치 */
    @Version
    private Long version;

    private PointWallet(Long userId) {
        this.userId = userId;
        this.balance = 0L;
        this.heldBalance = 0L;
    }

    public static PointWallet create(Long userId) {
        return new PointWallet(userId);
    }
}