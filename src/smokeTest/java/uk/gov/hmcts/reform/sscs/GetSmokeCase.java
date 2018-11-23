package uk.gov.hmcts.reform.sscs;

import static org.junit.Assert.assertEquals;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.exceptionRecord;

import io.restassured.RestAssured;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@SpringBootTest
@RunWith(SpringRunner.class)
public class GetSmokeCase {

    private final String appUrl = System.getenv("TEST_URL");

    @Autowired
    private IdamService idamService;

    @Test
    public void givenASmokeCase_retrieveFromCcd() throws IOException {
        RestAssured.baseURI = appUrl;
        RestAssured.useRelaxedHTTPSValidation();
        System.out.println(System.getenv("IDAM_OAUTH2_CLIENT_SECRET").substring(0,3));
        System.exit(0);

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

        assertEquals("mrn_date is an invalid date field. Needs to be in the format dd/mm/yyyy", errors.get(0));
    }
}
