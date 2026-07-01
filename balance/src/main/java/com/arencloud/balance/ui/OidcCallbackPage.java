package com.arencloud.balance.ui;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

import java.net.URI;

@Path("/authorization-code/callback")
@Authenticated
public class OidcCallbackPage {
    @GET
    public Response callback() {
        return Response.seeOther(URI.create("/")).build();
    }
}
