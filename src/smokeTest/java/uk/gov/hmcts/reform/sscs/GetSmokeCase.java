package uk.gov.hmcts.reform.sscs;

import static org.junit.Assert.assertEquals;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.exceptionRecord;

import io.restassured.RestAssured;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.IdamClient;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GetSmokeCase {

    private final String appUrl = System.getenv("TEST_URL");

    @Value("${idam.oauth2.user.email}")
    private String idamOauth2UserEmail;

    @Value("${idam.oauth2.user.password}")
    private String idamOauth2UserPassword;

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    @Autowired
    private IdamClient idamClient;

    @Test
    public void givenASmokeCase_retrieveFromCcd() throws IOException {
        RestAssured.baseURI = appUrl;
        RestAssured.useRelaxedHTTPSValidation();

        String userToken = idamClient.authenticateUser(idamOauth2UserEmail, idamOauth2UserPassword);
        String userId = idamClient.getUserDetails(userToken).getId();

        IdamTokens idamTokens = IdamTokens.builder()
            .idamOauth2Token(userToken)
            .serviceAuthorization(authTokenGenerator.generate())
            .userId(userId)
            .build();

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
