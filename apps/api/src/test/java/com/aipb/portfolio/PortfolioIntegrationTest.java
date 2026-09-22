package com.aipb.portfolio;

import com.aipb.auth.*;
import com.fasterxml.jackson.databind.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:portfolio;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortfolioIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired SessionRepository sessions;
    @Autowired AssetRepository assets;
    @Autowired ProfileRepository profiles;
    String alice, bob;
    @BeforeEach void setup() {
        assets.deleteAll(); profiles.deleteAll(); sessions.deleteAll(); users.deleteAll();
        auth.register("alice@aipb.demo", "Demo!2026pw"); auth.register("bob@aipb.demo", "Demo!2026pw");
        alice = "Bearer " + auth.login("alice@aipb.demo", "Demo!2026pw").accessToken();
        bob = "Bearer " + auth.login("bob@aipb.demo", "Demo!2026pw").accessToken();
    }
    JsonNode create(String type, long amount) throws Exception {
        return json.readTree(mvc.perform(post("/api/assets").header("Authorization", alice).contentType("application/json")
                .content(json.writeValueAsString(Map.of("name", "가상 자산", "type", type, "amount", amount))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }
    @Test void assetCrudAndDebtTotals() throws Exception {
        var asset = create("DEPOSIT", 10000000); create("DEBT", 12000000);
        mvc.perform(get("/api/assets").header("Authorization", alice)).andExpect(status().isOk())
                .andExpect(jsonPath("totalAssets").value(10000000)).andExpect(jsonPath("totalDebt").value(12000000))
                .andExpect(jsonPath("netAssets").value(-2000000));
        String path = "/api/assets/" + asset.get("id").asText();
        mvc.perform(put(path).header("Authorization", alice).contentType("application/json")
                .content("{\"name\":\"수정 자산\",\"type\":\"CASH\",\"amount\":500,\"version\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("version").value(1));
        mvc.perform(put(path).header("Authorization", alice).contentType("application/json")
                .content("{\"name\":\"오래된 수정\",\"type\":\"CASH\",\"amount\":100,\"version\":0}"))
                .andExpect(status().isConflict());
        mvc.perform(delete(path).param("version", "0").header("Authorization", alice)).andExpect(status().isConflict());
        mvc.perform(delete(path).param("version", "1").header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization", alice)).andExpect(status().isNotFound());
    }
    @Test void otherUserCannotReadChangeOrDeleteAssets() throws Exception {
        String path = "/api/assets/" + create("CASH", 100).get("id").asText();
        mvc.perform(get("/api/assets").header("Authorization", bob)).andExpect(jsonPath("items").isEmpty());
        mvc.perform(get(path).header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(put(path).header("Authorization", bob).contentType("application/json")
                .content("{\"name\":\"탈취 시도\",\"type\":\"CASH\",\"amount\":200,\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(path).param("version", "0").header("Authorization", bob)).andExpect(status().isNotFound());
        mvc.perform(get(path).header("Authorization", alice)).andExpect(jsonPath("amount").value(100));
    }
    @Test void rejectsInvalidAssetAmountsAndMissingAuthorization() throws Exception {
        for (String amount : List.of("0", "-1", "1.5", "1000000000000", "null")) {
            mvc.perform(post("/api/assets").header("Authorization", alice).contentType("application/json")
                    .content("{\"name\":\"자산\",\"type\":\"CASH\",\"amount\":" + amount + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/assets").header("Authorization", alice).contentType("application/json")
                .content("{\"name\":\" \",\"type\":\"INVALID\",\"amount\":100}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/assets")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/investment-profile")).andExpect(status().isUnauthorized());
    }
    @Test void assessmentUsesServerScorePreservesHistoryAndIsPrivate() throws Exception {
        mvc.perform(get("/api/investment-profile").header("Authorization", alice)).andExpect(status().isNoContent());
        mvc.perform(get("/api/investment-profile/questionnaire").header("Authorization", alice))
                .andExpect(jsonPath("questions.length()").value(5));
        for (int answer : List.of(1, 3)) {
            mvc.perform(post("/api/investment-profile").header("Authorization", alice).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("questionnaireVersion", "demo-v1", "answers", Collections.nCopies(5, answer), "score", 25))))
                    .andExpect(status().isCreated()).andExpect(jsonPath("score").value(answer * 5));
        }
        mvc.perform(get("/api/investment-profile").header("Authorization", alice))
                .andExpect(jsonPath("riskLevel").value("BALANCED")).andExpect(jsonPath("expired").value(false));
        mvc.perform(get("/api/investment-profile").header("Authorization", bob)).andExpect(status().isNoContent());
        assertThat(profiles.count()).isEqualTo(2);
        var latest = profiles.findAll().stream().max(Comparator.comparing(p -> p.assessedAt)).orElseThrow();
        latest.expiresAt = Instant.now().minusSeconds(1); profiles.save(latest);
        mvc.perform(get("/api/investment-profile").header("Authorization", alice)).andExpect(jsonPath("expired").value(true));
    }
    @Test void rejectsIncompleteInvalidOrStaleQuestionnaire() throws Exception {
        for (String answers : List.of("[]", "[1,2,3,4]", "[1,2,3,4,6]", "[0,1,1,1,1]", "[1,null,1,1,1]", "[1.5,2,3,4,5]", "null")) {
            mvc.perform(post("/api/investment-profile").header("Authorization", alice).contentType("application/json")
                    .content("{\"questionnaireVersion\":\"demo-v1\",\"answers\":" + answers + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/investment-profile").header("Authorization", alice).contentType("application/json")
                .content("{\"questionnaireVersion\":\"old\",\"answers\":[1,1,1,1,1]}"))
                .andExpect(status().isConflict());
    }
    @ParameterizedTest
    @CsvSource({"5,CONSERVATIVE", "8,CONSERVATIVE", "9,CAUTIOUS", "12,CAUTIOUS", "13,BALANCED", "16,BALANCED", "17,GROWTH", "20,GROWTH", "21,AGGRESSIVE", "25,AGGRESSIVE"})
    void scoringBoundaries(int score, String expected) { assertThat(PortfolioService.riskLevel(score)).isEqualTo(expected); }
}
