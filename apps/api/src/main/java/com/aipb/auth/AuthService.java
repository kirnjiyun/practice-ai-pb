package com.aipb.auth;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final long accessMinutes;
    private final long refreshDays;
    private final String dummyHash;
    private final SecureRandom random = new SecureRandom();

    AuthService(UserRepository users, SessionRepository sessions, PasswordEncoder passwords, JwtEncoder jwt,
                @Value("${auth.access-ttl-minutes}") long accessMinutes,
                @Value("${auth.refresh-ttl-days}") long refreshDays) {
        this.users = users; this.sessions = sessions; this.passwords = passwords; this.jwt = jwt;
        if (accessMinutes <= 0 || refreshDays <= 0) throw new IllegalArgumentException("Token TTL must be positive");
        this.accessMinutes = accessMinutes; this.refreshDays = refreshDays;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public record UserView(UUID id, String email, AppUser.Role role) {}
    public record Tokens(String accessToken, String refreshToken, String tokenType, long expiresIn, UserView user) {}
    static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    static UserView view(AppUser user) { return new UserView(user.id, user.email, user.role); }
    static ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials or token"); }

    @Transactional
    public UserView register(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds 72 UTF-8 bytes");
        }
        return view(users.saveAndFlush(new AppUser(normalize(email), passwords.encode(password), AppUser.Role.USER)));
    }
    @Transactional
    public Tokens login(String email, String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) throw unauthorized();
        var user = users.findByEmail(normalize(email)).orElse(null);
        boolean matches = passwords.matches(password, user == null ? dummyHash : user.passwordHash);
        if (user == null || !matches) throw unauthorized();
        return issue(user);
    }
    @Transactional
    public Tokens refresh(String token) {
        var session = sessions.lockByHash(hash(token)).orElseThrow(AuthService::unauthorized);
        if (session.revoked || !session.expiresAt.isAfter(Instant.now())) throw unauthorized();
        session.revoked = true;
        return issue(users.findById(session.userId).orElseThrow(AuthService::unauthorized));
    }
    @Transactional
    public void logout(String token) {
        sessions.lockByHash(hash(token)).ifPresent(session -> session.revoked = true);
    }
    @Transactional(readOnly = true)
    public UserView me(UUID id) { return view(users.findById(id).orElseThrow(AuthService::unauthorized)); }

    private Tokens issue(AppUser user) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        var session = sessions.save(new RefreshSession(user.id, hash(refresh), now.plus(refreshDays, ChronoUnit.DAYS)));
        var claims = JwtClaimsSet.builder().issuer("aipb").subject(user.id.toString())
                .issuedAt(now).expiresAt(now.plus(accessMinutes, ChronoUnit.MINUTES))
                .claim("sid", session.id.toString()).claim("roles", List.of(user.role.name())).build();
        String access = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Tokens(access, refresh, "Bearer", accessMinutes * 60, view(user));
    }
    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
