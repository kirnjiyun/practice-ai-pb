package com.aipb.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
@ConditionalOnProperty(name = "auth.demo-seed", havingValue = "true")
public class DemoUsers implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    DemoUsers(UserRepository users, PasswordEncoder passwords) { this.users = users; this.passwords = passwords; }
    @Override @Transactional
    public void run(String... args) {
        for (String name : new String[]{"user1", "user2", "user3", "pb1", "admin"}) {
            String email = name + "@aipb.demo";
            if (users.findByEmail(email).isEmpty()) {
                var role = name.equals("admin") ? AppUser.Role.ADMIN : name.equals("pb1") ? AppUser.Role.PB : AppUser.Role.USER;
                users.save(new AppUser(email, passwords.encode("Demo!2026pw"), role));
            }
        }
    }
}
