package uk.gov.hmcts.reform.sscs.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Strings.concat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import org.apache.commons.codec.Charsets;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock
public class CcdCallbackControllerTest {

    private static final String SERVICE_AUTHORIZATION_HEADER_KEY = "ServiceAuthorization";

    private static final String BEARER = "Bearer ";

    private static final String USER_AUTH_TOKEN = BEARER + "TEST_USER_AUTH_TOKEN";

    private static final String SERVICE_AUTH_TOKEN = BEARER + "TEST_SERVICE_AUTH";

    private static final String USER_TOKEN_WITHOUT_CASE_ACCESS = "USER_TOKEN_WITHOUT_CASE_ACCESS";

    private static final String USER_ID = "1234";

    private static final String START_EVENT_APPEAL_CREATED_URL =
        "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/event-triggers/appealCreated/token";

    private static final String START_EVENT_INCOMPLETE_CASE_URL =
        "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/event-triggers/incompleteApplicationReceived/token";

    private static final String SUBMIT_EVENT_URL =
        "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/cases?ignore-warning=true";

    private static final String USER_ID_HEADER = "user-id";

    private static final String KEY = "key";

    private static final String VALUE = "value";

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int randomServerPort;

    @MockBean
    private AuthTokenValidator authTokenValidator;

    @Rule
    public WireMockRule ccdServer;

    private static int wiremockPort = 0;

    private String baseUrl;

    static {
        wiremockPort = SocketUtils.findAvailableTcpPort();
        System.setProperty("core_case_data.api.url", "http://localhost:" + wiremockPort);
    }

    @Before
    public void setUp() {
        baseUrl = "http://localhost:" + randomServerPort + "/exception-record/";
        ccdServer = new WireMockRule(wiremockPort);
        ccdServer.start();
    }

    @After
    public void tearDown() {
        ccdServer.stop();
    }

    @Test
    public void should_handle_callback_and_return_caseid_and_state_case_created_in_exception_record_data()
        throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        startForCaseworkerStub(START_EVENT_APPEAL_CREATED_URL);

        submitForCaseworkerStub(false);

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseData(caseData()), httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        AboutToStartOrSubmitCallbackResponse callbackResponse = result.getBody();

        assertThat(callbackResponse.getErrors()).isNull();
        assertThat(callbackResponse.getWarnings()).isEmpty();
        assertThat(callbackResponse.getData()).contains(
            entry("caseReference", "1539878003972756"),
            entry("state", "ScannedRecordCaseCreated")
        );

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_status_code_401_when_service_auth_token_is_missing() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseData(caseData()), headers);

        // When
        ResponseEntity<Void> result =
            this.restTemplate.postForEntity(baseUrl, request, Void.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    public void should_return_status_code_403_when_service_auth_token_is_missing() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("forbidden_service");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseData(caseData()), httpHeaders());

        // When
        ResponseEntity<Void> result =
            this.restTemplate.postForEntity(baseUrl, request, Void.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(403);

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_list_populated_when_exception_record_transformation_fails() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithContradictingValues()),
            httpHeaders()
        );

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("is_hearing_type_oral and is_hearing_type_paper have contradicting values");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    //FIXME: Add test for first time warning button is pressed after PR for RDM-3246 is merged by CCD

    @Test
    public void should_return_warnings_when_appellant_details_are_not_available() throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        startForCaseworkerStub(START_EVENT_INCOMPLETE_CASE_URL);

        submitForCaseworkerStub(true);

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithMissingAppellantDetails()),
            httpHeaders()
        );

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getWarnings())
            .containsOnly("person1_last_name is empty",
                "person1_address_line1 is empty",
                "person1_address_line3 is empty",
                "person1_address_line4 is empty",
                "person1_postcode is empty",
                "person1_nino is empty");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }


    @Test
    public void should_return_403_status_when_usertoken_does_not_have_access_to_jurisdiction() throws Exception {
        // Given
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_TOKEN_WITHOUT_CASE_ACCESS);
        headers.set(SERVICE_AUTHORIZATION_HEADER_KEY, SERVICE_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);

        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        startForCaseworkerStubWithUserTokenHavingNoAccess(START_EVENT_APPEAL_CREATED_URL);

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseData(caseData()), headers);

        // When
        ResponseEntity<String> result =
            this.restTemplate.postForEntity(baseUrl, request, String.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(403);
        assertThat(result.getBody())
            .contains(
                " \"status\": 403,\n"
                    + "  \"error\": \"Forbidden\",\n"
                    + "  \"message\": \"Access Denied"
            );

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_503_status_when_ccd_service_is_not_available() throws Exception {
        // Given
        startForCaseworkerStubWithCcdUnavailable(START_EVENT_APPEAL_CREATED_URL);

        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseData(caseData()), httpHeaders());

        // When
        ResponseEntity<Void> result =
            this.restTemplate.postForEntity(baseUrl, request, Void.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(503);

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }


    private Map<String, Object> caseDataWithContradictingValues() {
        List<Object> ocrList = new ArrayList<>();

        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "is_hearing_type_oral", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "is_hearing_type_paper", VALUE, true))
        );

        return exceptionRecord(ocrList, null);
    }

    private Map<String, Object> caseDataWithMissingAppellantDetails() {
        List<Object> ocrList = new ArrayList<>();

        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "have_right_to_appeal_yes", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "have_right_to_appeal_no", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "contains_mrn", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "benefit_type_description", VALUE, "Employment Support Allowance"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_title", VALUE, "Mr"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_first_name", VALUE, "John"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "is_hearing_type_oral", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "is_hearing_type_paper", VALUE, false))
        );

        return exceptionRecord(ocrList, null);
    }


    private Map<String, Object> exceptionRecord(List<Object> ocrList, List<Object> docList) {
        Map<String, Object> exceptionRecord = new HashMap<>();
        exceptionRecord.put("journeyClassification", "New Application");
        exceptionRecord.put("poBoxJurisdiction", "SSCS");
        exceptionRecord.put("poBox", "SSCSPO");
        exceptionRecord.put("openingDate", "2018-01-11");
        exceptionRecord.put("scanOCRData", ocrList);
        exceptionRecord.put("scanRecords", docList);
        return exceptionRecord;
    }

    private ExceptionCaseData exceptionCaseData(Map<String, Object> caseData) {
        return ExceptionCaseData.builder()
            .caseDetails(CaseDetails.builder()
                .caseData(caseData)
                .build())
            .build();
    }

    private Map<String, Object> caseData() {
        List<Object> ocrList = new ArrayList<>();
        List<Object> docList = new ArrayList<>();

        docList.add(ocrEntry(
            VALUE,
            ScannedRecord.builder().docScanDate("2018-10-10")
                .documentControlNumber("11111")
                .documentLink(DocumentLink.builder().documentUrl("http://www.bbc.com").build())
                .documentType("other")
                .filename("11111.pdf")
                .build())
        );


        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "have_right_to_appeal_yes", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "have_right_to_appeal_no", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "contains_mrn", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "benefit_type_description", VALUE, "Employment Support Allowance"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_title", VALUE, "Mr"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_first_name", VALUE, "John"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_last_name", VALUE, "Smith"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_address_line1", VALUE, "2 Drake Close"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_address_line2", VALUE, "Hutton"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_address_line3", VALUE, "Brentwood"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_address_line4", VALUE, "Essex"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_postcode", VALUE, "SE000RS"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_phone", VALUE, "012345678"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_mobile", VALUE, "012345678"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_dob", VALUE, "11/11/1976"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_nino", VALUE, "JT0123456B"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "is_hearing_type_oral", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "is_hearing_type_paper", VALUE, false))
        );

        return exceptionRecord(ocrList, docList);
    }

    private void startForCaseworkerStub(String eventUrl) throws Exception {
        String eventStartResponseBody = loadJson("mappings/event-start-200-response.json");

        ccdServer.stubFor(get(concat(eventUrl))
            .withHeader(AUTHORIZATION, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(200)
                .withBody(eventStartResponseBody)));
    }

    private void submitForCaseworkerStub(Boolean isIncompleteApplication) throws Exception {
        String createCaseRequest;
        if (isIncompleteApplication) {
            createCaseRequest = loadJson("mappings/case-incomplete-creation-request.json");
        }  else {
            createCaseRequest = loadJson("mappings/case-creation-request.json");
        }

        ccdServer.stubFor(post(concat(SUBMIT_EVENT_URL))
            .withHeader(AUTHORIZATION, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .withRequestBody(equalToJson(createCaseRequest))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(200)
                .withBody(loadJson("mappings/create-case-200-response.json"))));
    }

    private void startForCaseworkerStubWithUserTokenHavingNoAccess(String eventUrl) throws Exception {
        String eventStartResponseBody = loadJson("mappings/event-start-403-response.json");

        ccdServer.stubFor(get(concat(eventUrl))
            .withHeader(AUTHORIZATION, equalTo(USER_TOKEN_WITHOUT_CASE_ACCESS))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(403)
                .withBody(eventStartResponseBody)));
    }

    private void startForCaseworkerStubWithCcdUnavailable(String eventUrl) {
        ccdServer.stubFor(get(concat(eventUrl))
            .withHeader(AUTHORIZATION, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(503)));
    }


    private static String loadJson(String fileName) throws IOException {
        URL url = getResource(fileName);
        return Resources.toString(url, Charsets.toCharset("UTF-8"));
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private static Map<String, Object> ocrEntry(String key, Object value) {
        return ImmutableMap.of(key, value);
    }

    private HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_AUTH_TOKEN);
        headers.set(SERVICE_AUTHORIZATION_HEADER_KEY, SERVICE_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);
        return headers;
    }
}
