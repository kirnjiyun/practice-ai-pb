package com.aipb.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;
    AuthController(AuthService auth) { this.auth = auth; }
    public record Registration(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(min = 10, max = 72) String password) {}
    public record Login(@NotBlank @Email @Size(max = 254) String email,
                        @NotBlank @Size(max = 72) String password) {}
    public record Refresh(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String refreshToken) {}

    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthService.UserView register(@Valid @RequestBody Registration request) {
        return auth.register(request.email(), request.password());
    }
    @PostMapping("/auth/login")
    public ResponseEntity<AuthService.Tokens> login(@Valid @RequestBody Login request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.login(request.email(), request.password()));
    }
    @PostMapping("/auth/refresh")
    public ResponseEntity<AuthService.Tokens> refresh(@Valid @RequestBody Refresh request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.refresh(request.refreshToken()));
    }
    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody Refresh request) { auth.logout(request.refreshToken()); }
    @GetMapping({"/users/me", "/pb/me", "/admin/me"})
    public AuthService.UserView me(@AuthenticationPrincipal Jwt jwt) { return auth.me(UUID.fromString(jwt.getSubject())); }
}
