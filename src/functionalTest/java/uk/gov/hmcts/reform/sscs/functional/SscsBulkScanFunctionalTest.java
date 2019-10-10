package uk.gov.hmcts.reform.sscs.functional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import com.microsoft.applicationinsights.boot.dependencies.apachecommons.lang3.RandomStringUtils;
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
import java.util.Objects;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@SpringBootTest
@TestPropertySource(locations = "classpath:application_e2e.yaml")
@RunWith(JUnitParamsRunner.class)
@Slf4j
public class SscsBulkScanFunctionalTest {

    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Value("${test-url}")
    private String testUrl;

    @Autowired
    private IdamService idamService;

    private IdamTokens idamTokens;

    @Autowired
    private CcdService ccdService;

    private String ccdCaseId;

    @Before
    public void setup() {
        RestAssured.baseURI = testUrl;
        idamTokens = idamService.getIdamTokens();
        log.info("idamTokens.getUserId()" + idamTokens.getUserId());
        log.info("idamTokens.getServiceAuthorization()" + idamTokens.getServiceAuthorization());
    }

    @Test
    public void create_appeal_created_case_when_all_fields_entered() throws IOException {
        String json = getJson("all_fields_entered.json");
        json = replaceNino(json);
        Response response = exceptionRecordEndpointRequest(json);

        //Due to the async service bus hitting the evidence share service, we can't be sure what the state will be...
        List<String> possibleStates = new ArrayList<>(Arrays.asList("validAppeal", "withDwp"));
        assertTrue(possibleStates.contains(findCaseInCcd(response).getState()));
    }

    @Test
    public void create_incomplete_case_when_missing_mandatory_fields() throws IOException {
        String json = getJson("some_mandatory_fields_missing.json");
        json = replaceNino(json);
        Response response = exceptionRecordEndpointRequest(json);

        assertEquals("incompleteApplication", findCaseInCcd(response).getState());
    }

    @Test
    @Parameters({
        "see scanned SSCS1 form,over13months", ",over13MonthsAndGroundsMissing"
    })
    public void create_interlocutory_review_case_when_mrn_date_greater_than_13_months(String appealGrounds,
                                                                                      String expected)
        throws IOException {
        String json = getJson("mrn_date_greater_than_13_months.json");
        json = json.replace("APPEAL_GROUNDS", appealGrounds);
        json = replaceNino(json);
        Response response = exceptionRecordEndpointRequest(json);

        SscsCaseDetails caseInCcd = findCaseInCcd(response);
        assertEquals("interlocutoryReviewState", caseInCcd.getState());
        assertEquals(expected, caseInCcd.getData().getInterlocReferralReason());
    }

    @Test
    public void validate_nino_normalised() throws IOException {
        createCase();
        String json = getJson("validate_appeal_created_case_request.json");
        json = json.replace("CASE_ID_TO_BE_REPLACED", ccdCaseId);

        validationEndpointRequest(json);

        SscsCaseDetails caseDetails = ccdService.getByCaseId(Long.valueOf(ccdCaseId), idamTokens);

        assertEquals("AB225566B", caseDetails.getData().getAppeal().getAppellant().getIdentity().getNino());
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

    private String replaceNino(String json) {
        json = json.replace("{PERSON1_NINO}", "BB" + RandomStringUtils.random(6, false, true) + "A");
        json = json.replace("{PERSON2_NINO}", "BB" + RandomStringUtils.random(6, false, true) + "B");
        return json;
    }

    private Response exceptionRecordEndpointRequest(String json) throws IOException {
        return simulateCcdCallback(json, "/exception-record");
    }

    private Response validationEndpointRequest(String json) throws IOException {
        return simulateCcdCallback(json, "/validate-record");
    }

    private String getJson(String resource) throws IOException {
        String file = Objects.requireNonNull(getClass().getClassLoader()
            .getResource("import/" + resource)).getFile();
        return FileUtils.readFileToString(new File(file), StandardCharsets.UTF_8.name());
    }

    private Response simulateCcdCallback(String json, String urlPath) {
        final String callbackUrl = testUrl + urlPath;

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

    private SscsCaseDetails findCaseInCcd(Response response) {
        JsonPath jsonPathEvaluator = response.jsonPath();
        Long caseRef = Long.parseLong(((HashMap) jsonPathEvaluator.get("data")).get("caseReference").toString());
        return ccdService.getByCaseId(caseRef, idamTokens);
    }

}
