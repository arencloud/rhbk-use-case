package com.arencloud.balancesaml.service;

import com.arencloud.balancesaml.model.UserView;
import com.arencloud.balancesaml.saml.SamlApplicationConfig;
import com.arencloud.balancesaml.saml.SamlPrincipal;
import com.arencloud.balancesaml.saml.SamlSessionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Optional;

@ApplicationScoped
public class AccessService {
    private final SamlApplicationConfig config;
    private final SamlSessionStore sessions;

    public AccessService(SamlApplicationConfig config, SamlSessionStore sessions) {
        this.config = config;
        this.sessions = sessions;
    }

    public Optional<SamlPrincipal> current(HttpHeaders headers) {
        Cookie cookie = headers.getCookies().get(config.sessionCookie());
        return cookie == null ? Optional.empty() : sessions.find(cookie.getValue());
    }

    public SamlPrincipal require(HttpHeaders headers, String... roles) {
        SamlPrincipal principal = current(headers)
                .orElseThrow(() -> new NotAuthorizedException("SAML login required"));
        if (roles.length > 0 && !principal.hasAny(roles)) {
            throw new NotAuthorizedException("Required role missing");
        }
        return principal;
    }

    public UserView user(SamlPrincipal principal) {
        return new UserView(
                principal.nameId(),
                principal.displayName(),
                principal.email(),
                principal.roles(),
                principal.hasAny("balance_user", "balance_approver", "balance_auditor", "balance_admin"),
                principal.hasAny("balance_user", "balance_admin"),
                principal.hasAny("balance_approver", "balance_admin"),
                principal.hasAny("balance_auditor", "balance_admin"),
                principal.hasAny("balance_admin"));
    }

    public Response redirectToLogin(String target) {
        return Response.seeOther(URI.create("/login?target=" + target)).build();
    }

    public NewCookie sessionCookie(String sessionId) {
        return new NewCookie.Builder(config.sessionCookie())
                .value(sessionId)
                .path("/")
                .httpOnly(true)
                .secure(config.publicUrl().startsWith("https://"))
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(8 * 60 * 60)
                .build();
    }

    public NewCookie loginStateCookie(String requestId) {
        return new NewCookie.Builder(config.loginStateCookie())
                .value(requestId)
                .path("/saml")
                .httpOnly(true)
                .secure(config.publicUrl().startsWith("https://"))
                .sameSite(NewCookie.SameSite.LAX)
                .maxAge(5 * 60)
                .build();
    }

    public NewCookie clearSessionCookie() {
        return new NewCookie.Builder(config.sessionCookie())
                .value("")
                .path("/")
                .httpOnly(true)
                .maxAge(0)
                .build();
    }

    public NewCookie clearLoginStateCookie() {
        return new NewCookie.Builder(config.loginStateCookie())
                .value("")
                .path("/saml")
                .httpOnly(true)
                .maxAge(0)
                .build();
    }
}
