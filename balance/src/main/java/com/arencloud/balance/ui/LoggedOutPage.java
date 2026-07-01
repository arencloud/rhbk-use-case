package com.arencloud.balance.ui;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/logged-out")
public class LoggedOutPage {
    private final Template loggedOut;

    public LoggedOutPage(Template loggedOut) {
        this.loggedOut = loggedOut;
    }

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance page() {
        return loggedOut.instance();
    }
}
