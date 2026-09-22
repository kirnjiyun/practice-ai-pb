package com.aipb.portfolio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PortfolioService {
    public static final String QUESTIONNAIRE_VERSION = "demo-v1";
    private final AssetRepository assets;
    private final ProfileRepository profiles;
    PortfolioService(AssetRepository assets, ProfileRepository profiles) { this.assets = assets; this.profiles = profiles; }
    public record AssetView(UUID id, String name, Asset.Type type, BigDecimal amount, long version) {}
    public record AssetList(List<AssetView> items, BigDecimal totalAssets, BigDecimal totalDebt, BigDecimal netAssets, String currency) {}
    public record ProfileView(UUID id, String questionnaireVersion, List<Integer> answers, int score,
                              String riskLevel, Instant assessedAt, Instant expiresAt, boolean expired) {}
    public record Question(String id, String title, List<String> options) {}
    public record Questionnaire(String version, List<Question> questions) {}
    public Questionnaire questionnaire() {
        return new Questionnaire(QUESTIONNAIRE_VERSION, List.of(
            new Question("horizon", "투자금을 사용하기까지 예상하는 기간은?", List.of("6개월 미만", "6개월~1년", "1~3년", "3~5년", "5년 이상")),
            new Question("experience", "투자 상품에 대한 경험은?", List.of("예금만 이용", "채권 경험", "펀드 경험", "주식 경험", "다양한 고위험 상품 경험")),
            new Question("loss", "가상 투자금이 10% 하락한다면?", List.of("전액 회수", "대부분 회수", "일부 회수", "유지하며 관찰", "추가 투자 검토")),
            new Question("priority", "투자에서 우선하는 것은?", List.of("원금 보존", "손실 최소화", "안정과 수익 균형", "가격 변동을 감수한 성장", "높은 변동을 감수한 수익")),
            new Question("capacity", "생활비와 비상금을 제외한 여유자금 비중은?", List.of("거의 없음", "10% 미만", "10~30%", "30~50%", "50% 이상"))
        ));
    }
    static String riskLevel(int score) {
        if (score <= 8) return "CONSERVATIVE";
        if (score <= 12) return "CAUTIOUS";
        if (score <= 16) return "BALANCED";
        if (score <= 20) return "GROWTH";
        return "AGGRESSIVE";
    }
    static ProfileView view(InvestmentProfile p) {
        return new ProfileView(p.id, p.questionnaireVersion,
                Arrays.stream(p.answers.split(",")).map(Integer::valueOf).toList(), p.score, p.riskLevel,
                p.assessedAt, p.expiresAt, !p.expiresAt.isAfter(Instant.now()));
    }
    public Optional<ProfileView> profile(UUID userId) { return profiles.findFirstByUserIdOrderByAssessedAtDescIdDesc(userId).map(PortfolioService::view); }
    @Transactional
    public ProfileView assess(UUID userId, String version, List<Integer> answers) {
        if (!QUESTIONNAIRE_VERSION.equals(version)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Questionnaire changed; reload before submitting");
        var p = new InvestmentProfile();
        p.id = UUID.randomUUID(); p.userId = userId; p.questionnaireVersion = version;
        p.answers = answers.stream().map(String::valueOf).collect(Collectors.joining(","));
        p.score = answers.stream().mapToInt(Integer::intValue).sum(); p.riskLevel = riskLevel(p.score);
        p.assessedAt = Instant.now(); p.expiresAt = p.assessedAt.plus(365, ChronoUnit.DAYS);
        return view(profiles.save(p));
    }
    static AssetView view(Asset a) { return new AssetView(a.id, a.name, a.type, a.amount, a.version); }
    private Asset owned(UUID userId, UUID id) {
        return assets.findByIdAndUserId(id, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }
    public AssetView asset(UUID userId, UUID id) { return view(owned(userId, id)); }
    public AssetList list(UUID userId) {
        var items = assets.findByUserIdOrderByNameAscIdAsc(userId);
        BigDecimal total = BigDecimal.ZERO, debt = BigDecimal.ZERO;
        for (var a : items) { if (a.type == Asset.Type.DEBT) debt = debt.add(a.amount); else total = total.add(a.amount); }
        return new AssetList(items.stream().map(PortfolioService::view).toList(), total, debt, total.subtract(debt), "KRW");
    }
    @Transactional
    public AssetView create(UUID userId, PortfolioController.AssetInput input) {
        return view(assets.saveAndFlush(new Asset(userId, input.name().strip(), input.type(), input.amount())));
    }
    @Transactional
    public AssetView update(UUID userId, UUID id, PortfolioController.AssetUpdate input) {
        var a = owned(userId, id); checkVersion(a, input.version());
        a.name = input.name().strip(); a.type = input.type(); a.amount = input.amount();
        return view(assets.saveAndFlush(a));
    }
    @Transactional
    public void delete(UUID userId, UUID id, long version) {
        var a = owned(userId, id); checkVersion(a, version); assets.delete(a); assets.flush();
    }
    private void checkVersion(Asset a, long version) {
        if (a.version != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "Asset changed; reload before editing");
    }
}
