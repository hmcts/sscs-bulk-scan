package uk.gov.hmcts.reform.sscs.functional;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.microsoft.applicationinsights.core.dependencies.apachecommons.io.FileUtils;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@SpringBootTest
@RunWith(SpringRunner.class)
public class SscsBulkScanFunctionalTest {

    private final String appUrl = System.getenv("TEST_URL");

    @Autowired
    private IdamService idamService;

    private IdamTokens idamTokens;

    @Autowired
    private CcdService ccdService;

    @Before
    public void setup() {
        idamTokens = idamService.getIdamTokens();
    }

    @Test
    public void create_incomplete_case_when_missing_mandatory_fields() throws IOException {
        Response response = simulateCcdCallback("some_mandatory_fields_missing.json");

        assertEquals("incompleteApplication", findStateOfCaseInCcd(response));
    }

    @Test
    public void create_interlocutory_review_case_when_mrn_date_greater_than_13_months() throws IOException {
        Response response = simulateCcdCallback("mrn_date_greater_than_13_months.json");

        assertEquals("interlocutoryReviewState", findStateOfCaseInCcd(response));
    }

    private Response simulateCcdCallback(String resource) throws IOException {
        final String callbackUrl = appUrl + "/exception-record/";

        System.out.println("XXXXXXX" + System.getenv("IDAM_OAUTH2_REDIRECT_URL"));
        String path = getClass().getClassLoader().getResource("import/" + resource).getFile();
        String json = FileUtils.readFileToString(new File(path), StandardCharsets.UTF_8.name());

        RestAssured.useRelaxedHTTPSValidation();
        Response response = RestAssured
            .given()
            .header("ServiceAuthorization", "" + idamTokens.getServiceAuthorization())
            .header(AUTHORIZATION, idamTokens.getIdamOauth2Token())
            .header("user-id", idamTokens.getUserId())
            .contentType("application/json")
            .body(json)
            .when()
            .post(callbackUrl);

        assertEquals(200, response.getStatusCode());

        return response;
    }

    private String findStateOfCaseInCcd(Response response) {
        JsonPath jsonPathEvaluator = response.jsonPath();
        Long caseRef = Long.parseLong(((HashMap) jsonPathEvaluator.get("data")).get("caseReference").toString());

        SscsCaseDetails caseDetails = ccdService.getByCaseId(caseRef, idamTokens);

        return caseDetails.getState();
    }

}
