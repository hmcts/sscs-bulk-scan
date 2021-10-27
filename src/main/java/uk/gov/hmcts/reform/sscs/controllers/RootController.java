package uk.gov.hmcts.reform.sscs.controllers;

import static org.springframework.http.ResponseEntity.ok;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.idam.client.IdamClient;

/**
 * Default endpoints per application.
 */
@RestController
public class RootController {

    @Value("${idam.oauth2.user.email}")
    private String idamOauth2UserEmail;

    @Value("${idam.oauth2.user.password}")
    private String idamOauth2UserPassword;

    @Value("${idam.client.redirect_uri:}")
    private String redirectUri;
    @Value("${idam.client.id:}")
    private String clientId;
    @Value("${idam.client.secret:}")
    private String clientSecret;
    @Value("${idam.client.scope:openid profile roles}")
    private String clientScope;


    @Autowired
    private IdamClient idamClient;

    /**
     * Root GET endpoint.
     *
     * <p>Azure application service has a hidden feature of making requests to root endpoint when
     * "Always On" is turned on.
     * This is the endpoint to deal with that and therefore silence the unnecessary 404s as a response code.
     *
     * @return Welcome message from the service.
     */
    @GetMapping
    public ResponseEntity<String> welcome() {
        String accessToken = idamClient.getAccessToken(idamOauth2UserEmail, idamOauth2UserPassword);
        return ok("Welcome to sscs-bulk-scan - email: " + idamOauth2UserEmail + " | password: " + idamOauth2UserPassword
            + " | client id: " + clientId + " | secret: " + clientSecret
            + " | redirect url: " + redirectUri + " | scope: " + clientScope + " | token: " + accessToken);
    }
}
