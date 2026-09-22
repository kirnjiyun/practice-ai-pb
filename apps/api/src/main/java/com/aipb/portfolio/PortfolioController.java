package com.aipb.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PortfolioController {
    private final PortfolioService service;
    PortfolioController(PortfolioService service) { this.service = service; }
    public record Assessment(@NotBlank String questionnaireVersion,
                             @NotNull @Size(min = 5, max = 5) List<@NotNull @Min(1) @Max(5) Integer> answers) {}
    public record AssetInput(@NotBlank @Size(max = 80) String name, @NotNull Asset.Type type,
                             @NotNull @DecimalMin("1") @DecimalMax("999999999999") @Digits(integer = 12, fraction = 0) BigDecimal amount) {}
    public record AssetUpdate(@NotBlank @Size(max = 80) String name, @NotNull Asset.Type type,
                              @NotNull @DecimalMin("1") @DecimalMax("999999999999") @Digits(integer = 12, fraction = 0) BigDecimal amount,
                              @NotNull @PositiveOrZero Long version) {}
    private UUID owner(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @GetMapping("/investment-profile/questionnaire")
    public PortfolioService.Questionnaire questionnaire() { return service.questionnaire(); }
    @GetMapping("/investment-profile")
    public ResponseEntity<PortfolioService.ProfileView> profile(@AuthenticationPrincipal Jwt jwt) {
        return service.profile(owner(jwt)).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @PostMapping("/investment-profile") @ResponseStatus(HttpStatus.CREATED)
    public PortfolioService.ProfileView assess(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Assessment input) {
        return service.assess(owner(jwt), input.questionnaireVersion(), input.answers());
    }
    @GetMapping("/assets")
    public PortfolioService.AssetList list(@AuthenticationPrincipal Jwt jwt) { return service.list(owner(jwt)); }
    @GetMapping("/assets/{id}")
    public PortfolioService.AssetView asset(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return service.asset(owner(jwt), id); }
    @PostMapping("/assets") @ResponseStatus(HttpStatus.CREATED)
    public PortfolioService.AssetView create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssetInput input) { return service.create(owner(jwt), input); }
    @PutMapping("/assets/{id}")
    public PortfolioService.AssetView update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AssetUpdate input) { return service.update(owner(jwt), id, input); }
    @DeleteMapping("/assets/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam long version) { service.delete(owner(jwt), id, version); }
}
