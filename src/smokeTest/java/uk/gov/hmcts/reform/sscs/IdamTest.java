package uk.gov.hmcts.reform.sscs;

import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;

import com.google.common.collect.ImmutableMap;
import io.restassured.RestAssured;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Service
public class IdamTest {

    private final AuthTokenGenerator authTokenGenerator;

    @Value("${idam.url}")
    private String idamUrl;

    @Value("${idam.oauth2.user.email}")
    private String idamOauth2UserEmail;

    @Value("${idam.oauth2.user.password}")
    private String idamOauth2UserPassword;

    @Value("${idam.oauth2.client.id}")
    private String idamOauth2ClientId;

    @Value("${idam.oauth2.client.secret}")
    private String idamOauth2ClientSecret;

    @Value("${idam.oauth2.redirectUrl}")
    private String idamOauth2RedirectUrl;

    @Autowired
    IdamTest(AuthTokenGenerator authTokenGenerator) {
        this.authTokenGenerator = authTokenGenerator;
    }

    public IdamTokens getIdamTokens() {
        String code = RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(idamUrl)
            .header("Authorization", "Basic " + buildIdamSignInToken())
            .queryParams(
                ImmutableMap.of(
                    "response_type", "code",
                    "client_id", idamOauth2ClientId,
                    "client_secret", idamOauth2ClientSecret,
                    "redirect_uri", idamOauth2RedirectUrl
                )
            )
            .post("/oauth2/authorize")
            .then()
            .extract()
            .body()
            .jsonPath()
            .get("code");

        String idamToken = "Bearer " + getIdamToken(code);
        String userId = getUserId(idamToken);

        return IdamTokens.builder().idamOauth2Token(idamToken).serviceAuthorization(authTokenGenerator.generate()).userId(userId).build();
    }

    private String buildIdamSignInToken() {
        String unencodedToken = String.format("%s:%s", idamOauth2UserEmail, idamOauth2UserPassword);
        return Base64.getEncoder().encodeToString(unencodedToken.getBytes());
    }

    public String getIdamToken(String code) {
        System.out.println("client_id: " + idamOauth2ClientId);
        System.out.println("client_secret: " + idamOauth2ClientSecret.substring(0, 4));
        System.out.println("redirect_uri: " + idamOauth2RedirectUrl);
        System.out.println("code: " + code.substring(0, 4));
        System.out.println("idamOauth2UserEmail: " + idamOauth2UserEmail.substring(0, 26));
        System.out.println("idamOauth2UserPassword: " + idamOauth2UserPassword.substring(0, 4));

        return RestAssured
            .given()
            .relaxedHTTPSValidation()
            .baseUri(idamUrl)
            .header(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_VALUE)
            .queryParams(
                ImmutableMap.of(
                    "grant_type", "authorization_code",
                    "client_id", idamOauth2ClientId,
                    "client_secret", idamOauth2ClientSecret,
                    "redirect_uri", idamOauth2RedirectUrl,
                    "code", code
                )
            )
            .post("/oauth2/token")
            .then()
            .statusCode(200)
            .extract()
            .body()
            .jsonPath()
            .get("access_token");
    }

    public String getUserId(String userToken) {
        return RestAssured
            .given()
            .contentType("application/json")
            .header("Authorization", userToken)
            .get(idamUrl + "/details")
            .then()
            .statusCode(HttpStatus.OK.value())
            .and()
            .extract().path("id").toString();
    }
}
