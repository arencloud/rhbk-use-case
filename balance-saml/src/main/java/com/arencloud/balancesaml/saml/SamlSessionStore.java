package com.arencloud.balancesaml.saml;

import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class SamlSessionStore {
    private static final Duration SESSION_TTL = Duration.ofHours(8);
    private static final Duration LOGIN_STATE_TTL = Duration.ofMinutes(5);
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, SamlPrincipal> sessions = new ConcurrentHashMap<>();
    private final Map<String, Instant> loginStates = new ConcurrentHashMap<>();

    public String newLoginState(String requestId) {
        cleanup();
        loginStates.put(requestId, Instant.now().plus(LOGIN_STATE_TTL));
        return requestId;
    }

    public boolean consumeLoginState(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        Instant expiresAt = loginStates.remove(requestId);
        return expiresAt != null && expiresAt.isAfter(Instant.now());
    }

    public SamlPrincipal create(SamlPrincipal principal) {
        cleanup();
        String sessionId = token();
        SamlPrincipal stored = new SamlPrincipal(
                sessionId,
                principal.nameId(),
                principal.displayName(),
                principal.email(),
                principal.roles(),
                principal.attributes(),
                Instant.now());
        sessions.put(sessionId, stored);
        return stored;
    }

    public Optional<SamlPrincipal> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        SamlPrincipal principal = sessions.get(sessionId);
        if (principal == null) {
            return Optional.empty();
        }
        if (principal.authenticatedAt().plus(SESSION_TTL).isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(principal);
    }

    public void remove(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private String token() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void cleanup() {
        Instant now = Instant.now();
        loginStates.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        sessions.entrySet().removeIf(entry -> entry.getValue().authenticatedAt().plus(SESSION_TTL).isBefore(now));
    }
}
