package com.aipb.auth;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser {
    public enum Role { USER, PB, ADMIN }
    @Id UUID id;
    @Column(nullable = false, unique = true, length = 254) String email;
    @Column(nullable = false, length = 100) String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) Role role;
    protected AppUser() {}
    AppUser(String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID(); this.email = email; this.passwordHash = passwordHash; this.role = role;
    }
}
