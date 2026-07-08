package com.arencloud.balancesaml.saml;

import com.arencloud.balancesaml.service.AccessService;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.net.URI;

@Path("/")
public class SamlResource {
    private static final Logger LOG = Logger.getLogger(SamlResource.class);

    private final SamlApplicationConfig config;
    private final SamlService saml;
    private final SamlSessionStore sessions;
    private final AccessService access;

    public SamlResource(SamlApplicationConfig config, SamlService saml, SamlSessionStore sessions, AccessService access) {
        this.config = config;
        this.saml = saml;
        this.sessions = sessions;
        this.access = access;
    }

    @GET
    @Path("/login")
    public Response login(@QueryParam("target") String target) {
        SamlService.LoginRequest request = saml.loginRequest(target == null || target.isBlank() ? "/" : target);
        sessions.newLoginState(request.requestId());
        return Response.seeOther(request.redirectUri())
                .cookie(access.loginStateCookie(request.requestId()))
                .build();
    }

    @POST
    @Path("/saml/acs")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response acs(@FormParam("SAMLResponse") String samlResponse,
                        @FormParam("RelayState") String relayState,
                        @Context HttpHeaders headers) {
        Cookie stateCookie = headers.getCookies().get(config.loginStateCookie());
        String requestId = stateCookie == null ? "" : stateCookie.getValue();
        if (!sessions.consumeLoginState(requestId)) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("SAML login state is missing or expired")
                    .type(MediaType.TEXT_PLAIN)
                    .build();
        }

        SamlPrincipal principal;
        try {
            principal = sessions.create(saml.validateResponse(samlResponse, requestId));
        } catch (IllegalArgumentException e) {
            LOG.warn("SAML ACS validation failed", e);
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(e.getMessage())
                    .type(MediaType.TEXT_PLAIN)
                    .cookie(access.clearLoginStateCookie())
                    .build();
        }
        String target = relayState == null || relayState.isBlank() ? "/" : relayState;
        return Response.seeOther(URI.create(target))
                .cookie(access.sessionCookie(principal.sessionId()))
                .cookie(access.clearLoginStateCookie())
                .build();
    }

    @GET
    @Path("/logout")
    public Response logout(@Context HttpHeaders headers) {
        Cookie cookie = headers.getCookies().get(config.sessionCookie());
        if (cookie != null) {
            sessions.remove(cookie.getValue());
        }
        return Response.seeOther(URI.create("/logged-out"))
                .cookie(access.clearSessionCookie())
                .build();
    }

    @POST
    @Path("/saml/logout")
    public Response slo(@Context HttpHeaders headers) {
        return logout(headers);
    }

    @GET
    @Path("/saml/metadata")
    @Produces(MediaType.APPLICATION_XML)
    public String metadata() {
        return saml.spMetadata();
    }
}
