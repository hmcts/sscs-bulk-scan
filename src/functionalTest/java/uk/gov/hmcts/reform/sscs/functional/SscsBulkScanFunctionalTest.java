package uk.gov.hmcts.reform.sscs.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.microsoft.applicationinsights.core.dependencies.apachecommons.io.FileUtils;
import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils;
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

    private String ccdCaseId;

    @Before
    public void setup() {
        RestAssured.baseURI = appUrl;
        idamTokens = idamService.getIdamTokens();
    }

    @Test
    public void create_appeal_created_case_when_all_fields_entered() throws IOException {
        Response response = exceptionRecordEndpointRequest(getJson("all_fields_entered.json"));

        //Due to the async service bus hitting the evidence share service, we can't be sure what the state will be
        List<String> possibleStates = new ArrayList<>(Arrays.asList("appealCreated", "sendingToDwp", "withDwp"));
        assertTrue(possibleStates.contains(findStateOfCaseInCcd(response)));
    }

    @Test
    public void create_incomplete_case_when_missing_mandatory_fields() throws IOException {
        Response response = exceptionRecordEndpointRequest(getJson("some_mandatory_fields_missing.json"));

        assertEquals("incompleteApplication", findStateOfCaseInCcd(response));
    }

    @Test
    public void create_interlocutory_review_case_when_mrn_date_greater_than_13_months() throws IOException {
        Response response = exceptionRecordEndpointRequest(getJson("mrn_date_greater_than_13_months.json"));

        assertEquals("interlocutoryReviewState", findStateOfCaseInCcd(response));
    }

    @Test
    public void validate_and_update_incomplete_case_to_appeal_created_case() throws IOException {
        createCase();
        String json = getJson("validate_appeal_created_case_request.json");
        json = json.replace("CASE_ID_TO_BE_REPLACED", ccdCaseId);

        validationEndpointRequest(json);

        SscsCaseDetails caseDetails = ccdService.getByCaseId(Long.valueOf(ccdCaseId), idamTokens);

        assertEquals("appealCreated", caseDetails.getState());
    }

    private Response exceptionRecordEndpointRequest(String json) throws IOException {
        return simulateCcdCallback(json, "/exception-record");
    }

    private Response validationEndpointRequest(String json) throws IOException {
        return simulateCcdCallback(json, "/validate-record");
    }

    private String getJson(String resource) throws IOException {
        String file = getClass().getClassLoader().getResource("import/" + resource).getFile();
        return FileUtils.readFileToString(new File(file), StandardCharsets.UTF_8.name());
    }

    private Response simulateCcdCallback(String json, String urlPath) {
        final String callbackUrl = appUrl + urlPath;

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

    private void createCase() {
        SscsCaseData caseData = CaseDataUtils.buildMinimalCaseData();
        SscsCaseDetails caseDetails = ccdService.createCase(caseData, "appealCreated",
            "Bulk Scan appeal created", "Bulk Scan appeal created in test", idamTokens);
        ccdCaseId = String.valueOf(caseDetails.getId());
    }

    private String findStateOfCaseInCcd(Response response) {

        JsonPath jsonPathEvaluator = response.jsonPath();
        Long caseRef = Long.parseLong(((HashMap) jsonPathEvaluator.get("data")).get("caseReference").toString());

        SscsCaseDetails caseDetails = ccdService.getByCaseId(caseRef, idamTokens);

        return caseDetails.getState();
    }

}
