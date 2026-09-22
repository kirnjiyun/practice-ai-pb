package com.aipb.auth;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.aipb.portfolio.*;
import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("local")
@ConditionalOnProperty(name = "auth.demo-seed", havingValue = "true")
public class DemoUsers implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final PortfolioService portfolio;
    DemoUsers(UserRepository users, PasswordEncoder passwords, PortfolioService portfolio) {
        this.users = users; this.passwords = passwords; this.portfolio = portfolio;
    }
    @Override @Transactional
    public void run(String... args) {
        for (String name : new String[]{"user1", "user2", "user3", "pb1", "admin"}) {
            String email = name + "@aipb.demo";
            if (users.findByEmail(email).isEmpty()) {
                var role = name.equals("admin") ? AppUser.Role.ADMIN : name.equals("pb1") ? AppUser.Role.PB : AppUser.Role.USER;
                var user = users.saveAndFlush(new AppUser(email, passwords.encode("Demo!2026pw"), role));
                if (name.equals("user1") || name.equals("user2")) {
                    portfolio.assess(user.id, PortfolioService.QUESTIONNAIRE_VERSION,
                            name.equals("user1") ? List.of(3, 3, 3, 3, 3) : List.of(2, 2, 2, 2, 2));
                    portfolio.create(user.id, new PortfolioController.AssetInput("가상 생활비 통장", Asset.Type.CASH, new BigDecimal("3000000")));
                    portfolio.create(user.id, new PortfolioController.AssetInput("가상 정기예금", Asset.Type.DEPOSIT, new BigDecimal("20000000")));
                    portfolio.create(user.id, new PortfolioController.AssetInput("가상 투자자산", Asset.Type.FUND, new BigDecimal("5000000")));
                    portfolio.create(user.id, new PortfolioController.AssetInput("가상 연금", Asset.Type.PENSION, new BigDecimal("7000000")));
                    portfolio.create(user.id, new PortfolioController.AssetInput("가상 대출", Asset.Type.DEBT, new BigDecimal(name.equals("user1") ? "10000000" : "30000000")));
                }
            }
        }
    }
}
