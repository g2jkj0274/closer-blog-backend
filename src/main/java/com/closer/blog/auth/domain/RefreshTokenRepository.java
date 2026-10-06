package com.closer.blog.auth.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    // 새 액세스 토큰에 아이디가 필요하므로 사용자도 함께 읽는다
    @Query("select t from RefreshToken t join fetch t.user where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findWithUserByTokenHash(@Param("tokenHash") String tokenHash);

    /**
     * 아직 회전되지 않았을 때만 회전 표시를 한다. 바뀐 행 수가 0이면 다른 요청이 먼저 회전한 것이다.
     * 두 요청이 동시에 와도 DB가 행을 잠그므로 둘 중 하나만 1을 받는다 (docs/erd.md 4.2절).
     */
    @Modifying(flushAutomatically = true)
    @Query("update RefreshToken t set t.rotatedAt = :now where t.id = :id and t.rotatedAt is null")
    int markRotated(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") UUID familyId);

    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);

}