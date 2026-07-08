package com.arencloud.balancesaml.saml;

import com.onelogin.saml2.authn.AuthnRequest;
import com.onelogin.saml2.authn.SamlResponse;
import com.onelogin.saml2.http.HttpRequest;
import com.onelogin.saml2.settings.IdPMetadataParser;
import com.onelogin.saml2.settings.Metadata;
import com.onelogin.saml2.settings.Saml2Settings;
import com.onelogin.saml2.settings.SettingsBuilder;
import com.onelogin.saml2.util.Constants;
import com.onelogin.saml2.util.Util;
import jakarta.enterprise.context.ApplicationScoped;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URL;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@ApplicationScoped
public class SamlService {
    private final SamlApplicationConfig config;
    private volatile Saml2Settings settings;

    public SamlService(SamlApplicationConfig config) {
        this.config = config;
    }

    public LoginRequest loginRequest(String relayState) {
        try {
            AuthnRequest request = new AuthnRequest(settings());
            String redirect = settings().getIdpSingleSignOnServiceUrl()
                    + "?SAMLRequest=" + Util.urlEncoder(request.getEncodedAuthnRequest())
                    + "&RelayState=" + Util.urlEncoder(relayState == null || relayState.isBlank() ? "/" : relayState);
            return new LoginRequest(request.getId(), URI.create(redirect));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to build SAML login request", e);
        }
    }

    public SamlPrincipal validateResponse(String samlResponse, String requestId) {
        try {
            HttpRequest request = new HttpRequest(config.publicUrl() + "/saml/acs")
                    .addParameter("SAMLResponse", samlResponse);
            SamlResponse response = new SamlResponse(settings(), request);
            if (!response.isValid(requestId)) {
                throw new IllegalArgumentException("Invalid SAML response: " + response.getError());
            }

            Map<String, List<String>> attributes = response.getAttributes();
            Set<String> roles = new TreeSet<>(attributes.getOrDefault("Role", List.of()));
            roles.addAll(attributes.getOrDefault("roles", List.of()));

            String nameId = response.getNameId();
            String email = first(attributes, "email");
            String firstName = first(attributes, "firstName");
            String lastName = first(attributes, "lastName");
            String displayName = ((firstName + " " + lastName).trim());
            if (displayName.isBlank()) {
                displayName = email == null || email.isBlank() ? nameId : email;
            }

            return new SamlPrincipal(
                    "",
                    nameId,
                    displayName,
                    email == null ? "" : email,
                    roles,
                    Map.copyOf(attributes),
                    Instant.now());
        } catch (Exception e) {
            throw new IllegalArgumentException("SAML response validation failed: " + rootMessage(e), e);
        }
    }

    public String spMetadata() {
        try {
            return new Metadata(settings()).getMetadataString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to generate SAML SP metadata", e);
        }
    }

    private Saml2Settings settings() {
        Saml2Settings local = settings;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (settings == null) {
                settings = buildSettings();
            }
            return settings;
        }
    }

    private Saml2Settings buildSettings() {
        try {
            configureTrustIfPresent();
            Map<String, Object> values = new LinkedHashMap<>(
                    IdPMetadataParser.parseRemoteXML(new URL(config.idpMetadataUrl())));
            values.put(SettingsBuilder.STRICT_PROPERTY_KEY, "true");
            values.put(SettingsBuilder.DEBUG_PROPERTY_KEY, "false");
            values.put(SettingsBuilder.SP_ENTITYID_PROPERTY_KEY, config.entityId());
            values.put(SettingsBuilder.SP_ASSERTION_CONSUMER_SERVICE_URL_PROPERTY_KEY, config.publicUrl() + "/saml/acs");
            values.put(SettingsBuilder.SP_ASSERTION_CONSUMER_SERVICE_BINDING_PROPERTY_KEY, Constants.BINDING_HTTP_POST);
            values.put(SettingsBuilder.SP_SINGLE_LOGOUT_SERVICE_URL_PROPERTY_KEY, config.publicUrl() + "/saml/logout");
            values.put(SettingsBuilder.SP_SINGLE_LOGOUT_SERVICE_BINDING_PROPERTY_KEY, Constants.BINDING_HTTP_POST);
            values.put(SettingsBuilder.SP_NAMEIDFORMAT_PROPERTY_KEY, Constants.NAMEID_UNSPECIFIED);
            config.idpEntityId()
                    .filter(value -> !value.isBlank())
                    .ifPresent(value -> values.put(SettingsBuilder.IDP_ENTITYID_PROPERTY_KEY, value));
            config.idpSsoUrl()
                    .filter(value -> !value.isBlank())
                    .ifPresent(value -> values.put(SettingsBuilder.IDP_SINGLE_SIGN_ON_SERVICE_URL_PROPERTY_KEY, value));
            values.put(SettingsBuilder.SECURITY_AUTHREQUEST_SIGNED, "false");
            values.put(SettingsBuilder.SECURITY_WANT_ASSERTIONS_SIGNED, "true");
            values.put(SettingsBuilder.SECURITY_WANT_MESSAGES_SIGNED, "false");
            values.put(SettingsBuilder.SECURITY_WANT_ASSERTIONS_ENCRYPTED, "false");
            values.put(SettingsBuilder.SECURITY_WANT_NAMEID, "true");
            values.put(SettingsBuilder.SECURITY_ALLOW_REPEAT_ATTRIBUTE_NAME_PROPERTY_KEY, "true");
            values.put(SettingsBuilder.SECURITY_REJECT_DEPRECATED_ALGORITHM, "true");
            values.put(SettingsBuilder.COMPRESS_REQUEST, "true");
            values.put(SettingsBuilder.COMPRESS_RESPONSE, "true");

            Saml2Settings built = new SettingsBuilder().fromValues(values).build();
            List<String> errors = built.checkSettings();
            if (!errors.isEmpty()) {
                throw new IllegalStateException("Invalid SAML settings: " + errors);
            }
            return built;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to initialize SAML settings from IdP metadata", e);
        }
    }

    private void configureTrustIfPresent() throws Exception {
        if (config.trustedCaPem() == null || config.trustedCaPem().isBlank()) {
            return;
        }

        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates;
        try (FileInputStream input = new FileInputStream(config.trustedCaPem())) {
            certificates = factory.generateCertificates(input);
        }
        if (certificates.isEmpty()) {
            return;
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        int index = 0;
        for (Certificate certificate : certificates) {
            keyStore.setCertificateEntry("ca-" + index++, certificate);
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        SSLContext.setDefault(sslContext);
    }

    private static String first(Map<String, List<String>> attributes, String name) {
        List<String> values = attributes.get(name);
        return values == null || values.isEmpty() ? "" : values.get(0);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    public record LoginRequest(String requestId, URI redirectUri) {
    }
}
