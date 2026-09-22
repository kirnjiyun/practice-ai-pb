package com.aipb.portfolio;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
public class Asset {
    public enum Type { CASH, DEPOSIT, STOCK, FUND, BOND, PENSION, OTHER, DEBT }
    @Id UUID id;
    @Column(nullable = false) UUID userId;
    @Column(nullable = false, length = 80) String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) Type type;
    @Column(nullable = false, precision = 15, scale = 0) BigDecimal amount;
    @Version long version;
    protected Asset() {}
    Asset(UUID userId, String name, Type type, BigDecimal amount) {
        this.id = UUID.randomUUID(); this.userId = userId;
        this.name = name; this.type = type; this.amount = amount;
    }
}
