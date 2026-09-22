package com.aipb.auth;

import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired PasswordEncoder passwords;
    @Autowired AuthService auth;
    @Autowired JwtEncoder encoder;

    @BeforeEach void clean() { sessions.deleteAll(); users.deleteAll(); }
    private void user(String email, AppUser.Role role) { users.save(new AppUser(email, passwords.encode("Demo!2026pw"), role)); }
    private JsonNode login(String email) throws Exception {
        return json.readTree(mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", email, "password", "Demo!2026pw"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
    }
    private String refreshBody(String token) throws Exception { return json.writeValueAsString(Map.of("refreshToken", token)); }

    @Test void registrationCannotChoosePrivilegedRoleAndHashesPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"New@aipb.demo\",\"password\":\"Demo!2026pw\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("role").value("USER"))
                .andExpect(jsonPath("passwordHash").doesNotExist());
        var stored = users.findByEmail("new@aipb.demo").orElseThrow();
        assertThat(stored.passwordHash).isNotEqualTo("Demo!2026pw");
        assertThat(passwords.matches("Demo!2026pw", stored.passwordHash)).isTrue();
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"new@aipb.demo\",\"password\":\"Demo!2026pw\"}"))
                .andExpect(status().isConflict());
    }
    @Test void rejectsInvalidInputAndBadCredentials() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"email\":\"bad\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", "new@aipb.demo", "password", "가".repeat(30)))))
                .andExpect(status().isBadRequest());
        user("user@aipb.demo", AppUser.Role.USER);
        for (String email : List.of("user@aipb.demo", "unknown@aipb.demo")) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                    .content(json.writeValueAsString(Map.of("email", email, "password", "wrong"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void rotationRejectsReplayAndLogoutRevokesAccess() throws Exception {
        user("user@aipb.demo", AppUser.Role.USER);
        var first = login("USER@aipb.demo");
        String refresh = first.get("refreshToken").asText();
        assertThat(sessions.findAll().getFirst().tokenHash).isNotEqualTo(refresh).hasSize(64);
        var result = mvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody(refresh)))
                .andExpect(status().isOk()).andReturn();
        var second = json.readTree(result.getResponse().getContentAsString());
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(refreshBody(refresh)))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + first.get("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        String access = second.get("accessToken").asText();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("email").value("user@aipb.demo"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/logout").contentType("application/json")
                    .content(refreshBody(second.get("refreshToken").asText()))).andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(refreshBody(second.get("refreshToken").asText()))).andExpect(status().isUnauthorized());
    }
    @Test void roleMatrix() throws Exception {
        for (var role : AppUser.Role.values()) {
            String email = role.name().toLowerCase() + "@aipb.demo";
            user(email, role);
            String bearer = "Bearer " + login(email).get("accessToken").asText();
            mvc.perform(get("/api/users/me").header("Authorization", bearer)).andExpect(status().isOk());
            mvc.perform(get("/api/pb/me").header("Authorization", bearer))
                    .andExpect(status().is(role == AppUser.Role.USER ? 403 : 200));
            mvc.perform(get("/api/admin/me").header("Authorization", bearer))
                    .andExpect(status().is(role == AppUser.Role.ADMIN ? 200 : 403));
        }
    }
    @Test void expiredRefreshAndExpiredOrForgedJwtAreRejected() throws Exception {
        user("user@aipb.demo", AppUser.Role.USER);
        var tokens = login("user@aipb.demo");
        var session = sessions.findAll().getFirst();
        var expired = JwtClaimsSet.builder().issuer("aipb").subject(session.userId.toString())
                .issuedAt(Instant.now().minusSeconds(300)).expiresAt(Instant.now().minusSeconds(120))
                .claim("sid", session.id.toString()).claim("roles", List.of("USER")).build();
        String expiredToken = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), expired)).getTokenValue();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expiredToken)).andExpect(status().isUnauthorized());
        String[] parts = tokens.get("accessToken").asText().split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        session.expiresAt = Instant.now().minusSeconds(1); sessions.save(session);
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(refreshBody(tokens.get("refreshToken").asText()))).andExpect(status().isUnauthorized());
    }
    @Test void concurrentRefreshOnlySucceedsOnce() throws Exception {
        user("user@aipb.demo", AppUser.Role.USER);
        String token = login("user@aipb.demo").get("refreshToken").asText();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> task = () -> {
                start.await();
                try { auth.refresh(token); return true; }
                catch (org.springframework.web.server.ResponseStatusException e) {
                    assertThat(e.getStatusCode().value()).isEqualTo(401); return false;
                }
            };
            var a = executor.submit(task); var b = executor.submit(task); start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }
    @Test void healthIsPublicAndCorsRestrictsOrigins() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isOk());
        mvc.perform(options("/api/auth/login").header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
}
