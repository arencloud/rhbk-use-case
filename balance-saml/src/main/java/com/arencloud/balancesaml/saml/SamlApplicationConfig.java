package com.arencloud.balancesaml.saml;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

@ConfigMapping(prefix = "balance.saml")
public interface SamlApplicationConfig {
    String entityId();

    String publicUrl();

    String idpMetadataUrl();

    Optional<String> idpSsoUrl();

    String sessionCookie();

    String loginStateCookie();

    String trustedCaPem();
}
