package uk.gov.hmcts.reform.sscs;

import static org.junit.Assert.assertEquals;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.exceptionRecord;

import io.restassured.RestAssured;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GetSmokeCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetSmokeCase.class);

    private final String appUrl = System.getenv("TEST_URL");

    @Autowired
    private IdamService idamService;

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

    @Test
    public void givenASmokeCase_retrieveFromCcd() throws IOException {
        RestAssured.baseURI = appUrl;
        RestAssured.useRelaxedHTTPSValidation();

        LOG.info("values are idamOauth2UserEmail {}, idamOauth2UserPassword {}, "
                + "idamOauth2ClientId {}, idamOauth2ClientSecret {}, idamOauth2RedirectUrl {}",
            idamOauth2UserEmail, idamOauth2UserPassword, idamOauth2ClientId, idamOauth2ClientSecret, idamOauth2RedirectUrl);
        IdamTokens idamTokens = idamService.getIdamTokens();

        List<String> errors = RestAssured
            .given()
            .contentType("application/json")
            .header("Authorization", idamTokens.getIdamOauth2Token())
            .header("serviceauthorization", idamTokens.getServiceAuthorization())
            .header("user-id", idamTokens.getUserId())
            .body(exceptionRecord("smokeRecord.json"))
            .when()
            .post("/exception-record/")
            .then()
            .statusCode(HttpStatus.OK.value())
            .and()
            .extract().path("errors");

        assertEquals("mrn_date is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy", errors.get(0));
    }
}
