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
    public void givenASmokeCase_thenValidate() throws IOException {
        RestAssured.baseURI = appUrl;
        RestAssured.useRelaxedHTTPSValidation();

        IdamTokens idamTokens = idamService.getIdamTokens();

        List<String> warnings = RestAssured
            .given()
            .contentType("application/json")
            .header("Authorization", idamTokens.getIdamOauth2Token())
            .header("serviceauthorization", idamTokens.getServiceAuthorization())
            .header("user-id", idamTokens.getUserId())
            .body(exceptionRecord("smokeRecord.json"))
            .when()
            .post("/forms/SSCS1/validate-ocr")
            .then()
            .statusCode(HttpStatus.OK.value())
            .and()
            .extract().path("warnings");

        assertEquals("mrn_date is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy", warnings.get(0));
    }
}
