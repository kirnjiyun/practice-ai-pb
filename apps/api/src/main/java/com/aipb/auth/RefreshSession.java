package com.aipb.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_session")
public class RefreshSession {
    @Id UUID id;
    @Column(nullable = false) UUID userId;
    @Column(nullable = false, unique = true, length = 64) String tokenHash;
    @Column(nullable = false) Instant expiresAt;
    @Column(nullable = false) boolean revoked;
    protected RefreshSession() {}
    RefreshSession(UUID userId, String hash, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.userId = userId; this.tokenHash = hash; this.expiresAt = expiresAt;
    }
}
