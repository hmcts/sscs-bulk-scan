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
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.ccd.util.CaseDataUtils;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Slf4j
@TestPropertySource(locations = "classpath:application_e2e.yaml")
public class BaseFunctionalTest {

    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    protected IdamTokens idamTokens;

    @Autowired
    private IdamService idamService;

    @Autowired
    protected CcdService ccdService;

    @Value("${test-url}")
    private String testUrl;

    protected String ccdCaseId;

    @Before
    public void setup() {
        RestAssured.baseURI = testUrl;
        idamTokens = idamService.getIdamTokens();
        log.info("idamTokens.getUserId()" + idamTokens.getUserId());
        log.info("idamTokens.getServiceAuthorization()" + idamTokens.getServiceAuthorization());
        log.info("idamTokens.getIdamOauth2Token()" + idamTokens.getIdamOauth2Token());
    }

    protected Response simulateCcdCallback(String json, String urlPath) {
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

    protected void createCase() {
        SscsCaseData caseData = CaseDataUtils.buildMinimalCaseData();
        SscsCaseDetails caseDetails = ccdService.createCase(caseData, "appealCreated",
            "Bulk Scan appeal created", "Bulk Scan appeal created in test", idamTokens);
        ccdCaseId = String.valueOf(caseDetails.getId());
    }

    protected SscsCaseDetails findCaseInCcd(Response response) {
        JsonPath jsonPathEvaluator = response.jsonPath();
        @SuppressWarnings("rawtypes")
        Long caseRef = Long.parseLong(((HashMap) jsonPathEvaluator.get("data")).get("caseReference").toString());
        return ccdService.getByCaseId(caseRef, idamTokens);
    }

    protected String getJson(String resource) throws IOException {
        String file = Objects.requireNonNull(getClass().getClassLoader()
            .getResource(resource)).getFile();
        return FileUtils.readFileToString(new File(file), StandardCharsets.UTF_8.name());
    }

    protected Response exceptionRecordEndpointRequest(String json) {
        return simulateCcdCallback(json, "/exception-record");
    }

    protected Response validateRecordEndpointRequest(String json) {
        return simulateCcdCallback(json, "/validate-record");
    }

    protected Response validateOcrEndpointRequest(String json, String formType) {
        return simulateCcdCallback(json, "/forms/" + formType + "/validate-ocr");
    }
}
