package com.aipb.portfolio;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {
    List<Asset> findByUserIdOrderByNameAscIdAsc(UUID userId);
    Optional<Asset> findByIdAndUserId(UUID id, UUID userId);
}
