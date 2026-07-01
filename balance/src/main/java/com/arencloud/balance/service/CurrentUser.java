package com.arencloud.balance.service;

import com.arencloud.balance.dto.UserProfileDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

@ApplicationScoped
public class CurrentUser {
    @Inject
    SecurityIdentity identity;

    public String username() {
        return identity.isAnonymous() ? "anonymous" : identity.getPrincipal().getName();
    }

    public Set<String> roles() {
        return new TreeSet<>(identity.getRoles());
    }

    public UserProfileDto profile() {
        return new UserProfileDto(
                username(),
                claim("name").orElse(username()),
                claim("email").orElse(""),
                roles(),
                hasAny("balance_user", "balance_approver", "balance_auditor", "balance_admin"),
                hasAny("balance_approver", "balance_admin"),
                hasAny("balance_auditor", "balance_admin"),
                hasAny("balance_admin"));
    }

    public boolean hasAny(String... roles) {
        for (String role : roles) {
            if (identity.hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    private Optional<String> claim(String name) {
        Principal principal = identity.getPrincipal();
        if (!(principal instanceof JsonWebToken jwt)) {
            return Optional.empty();
        }
        Object value = jwt.getClaim(name);
        return value == null ? Optional.empty() : Optional.of(value.toString());
    }
}
