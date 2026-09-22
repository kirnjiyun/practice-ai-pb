package com.aipb.auth;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends JpaRepository<RefreshSession, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :hash")
    Optional<RefreshSession> lockByHash(@Param("hash") String hash);
}
