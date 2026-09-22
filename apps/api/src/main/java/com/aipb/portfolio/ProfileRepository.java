package com.aipb.portfolio;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<InvestmentProfile, UUID> {
    Optional<InvestmentProfile> findFirstByUserIdOrderByAssessedAtDescIdDesc(UUID userId);
}
