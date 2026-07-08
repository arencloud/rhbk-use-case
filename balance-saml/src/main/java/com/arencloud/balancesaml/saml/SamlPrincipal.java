package com.arencloud.balancesaml.saml;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record SamlPrincipal(
        String sessionId,
        String nameId,
        String displayName,
        String email,
        Set<String> roles,
        Map<String, List<String>> attributes,
        Instant authenticatedAt) {

    public boolean hasAny(String... requiredRoles) {
        for (String requiredRole : requiredRoles) {
            if (roles.contains(requiredRole)) {
                return true;
            }
        }
        return false;
    }
}
