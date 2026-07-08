package com.arencloud.balancesaml.saml;

import io.smallrye.config.ConfigMapping;

@ConfigMapping(prefix = "balance.saml")
public interface SamlApplicationConfig {
    String entityId();

    String publicUrl();

    String idpMetadataUrl();

    String sessionCookie();

    String loginStateCookie();

    String trustedCaPem();
}
