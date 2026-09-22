package com.aipb.portfolio;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
public class InvestmentProfile {
    @Id UUID id;
    @Column(nullable = false) UUID userId;
    @Column(nullable = false, length = 40) String questionnaireVersion;
    @Column(nullable = false, length = 30) String answers;
    @Column(nullable = false) int score;
    @Column(nullable = false, length = 30) String riskLevel;
    @Column(nullable = false) Instant assessedAt;
    @Column(nullable = false) Instant expiresAt;
    protected InvestmentProfile() {}
}
