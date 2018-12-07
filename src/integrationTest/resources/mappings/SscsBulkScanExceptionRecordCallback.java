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
import static uk.gov.hmcts.reform.sscs.helper.TestConstants.*;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import org.apache.commons.codec.Charsets;
import org.joda.time.DateTimeUtils;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;
import uk.gov.hmcts.reform.sscs.service.EvidenceManagementService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock
@TestPropertySource(locations = "classpath:application_it.yaml")
public class SscsBulkScanExceptionRecordCallback {

    @Autowired
    private TestRestTemplate restTemplate;

    @LocalServerPort
    private int randomServerPort;

    @MockBean
    private AuthTokenValidator authTokenValidator;

    @MockBean
    private EvidenceManagementService evidenceManagementService;

    @MockBean
    private JavaMailSender mailSender;

    @Rule
    public WireMockRule ccdServer;

    private static int wiremockPort = 0;

    private Session session = Session.getInstance(new Properties());

    private String baseUrl;

    private MimeMessage message;

    static {
        wiremockPort = SocketUtils.findAvailableTcpPort();
        System.setProperty("core_case_data.api.url", "http://localhost:" + wiremockPort);
    }

    @Before
    public void setUp() {
        baseUrl = "http://localhost:" + randomServerPort + "/exception-record/";
        ccdServer = new WireMockRule(wiremockPort);
        ccdServer.start();
        DateTimeUtils.setCurrentMillisFixed(1542820369000L);

        message = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(message);

        byte[] expectedBytes = {1, 2, 3};
        when(evidenceManagementService.download(URI.create("http://www.bbc.com"), null)).thenReturn(expectedBytes);
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

        submitForCaseworkerStub("appealCreated");

        readForCaseworkerStub(READ_EVENT_URL, true);

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

    @Test
    public void should_return_error_list_populated_when_key_value_pair_validation_fails() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithInvalidKey()),
            httpHeaders()
        );

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("#: extraneous key [invalid_key] is not permitted");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_create_incomplete_case_when_warnings_are_ignored() throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        startForCaseworkerStub(START_EVENT_INCOMPLETE_CASE_URL);

        submitForCaseworkerStub("incompleteApplication");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(
            exceptionCaseDataWithIgnoreWarnings(caseDataWithMissingAppellantDetails()),
            httpHeaders()
        );

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        AboutToStartOrSubmitCallbackResponse callbackResponse = result.getBody();

        assertThat(callbackResponse.getErrors()).isNull();
        assertThat(callbackResponse.getData()).contains(
            entry("caseReference", "1539878003972756"),
            entry("state", "ScannedRecordCaseCreated")
        );

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_create_non_compliant_case_when_mrn_date_greater_than_13_months() throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        startForCaseworkerStub(START_EVENT_NON_COMPLIANT_CASE_URL);

        submitForCaseworkerStub("nonCompliant");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithMrnDate("01/01/2017")),
            httpHeaders()
        );

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        AboutToStartOrSubmitCallbackResponse callbackResponse = result.getBody();

        assertThat(callbackResponse.getErrors()).isNull();
        assertThat(callbackResponse.getData()).contains(
            entry("caseReference", "1539878003972756"),
            entry("state", "ScannedRecordCaseCreated")
        );

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_warnings_when_appellant_details_are_not_available() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

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
    public void should_return_503_status_when_ccd_service_is_not_available_when_creating_appeal() throws Exception {
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

    @Test
    public void should_return_503_status_when_ccd_service_is_not_available_when_creating_incomplete_appeal() throws Exception {
        // Given
        startForCaseworkerStubWithCcdUnavailable(START_EVENT_INCOMPLETE_CASE_URL);

        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionCaseData> request = new HttpEntity<>(exceptionCaseDataWithIgnoreWarnings(caseDataWithMissingAppellantDetails()), httpHeaders());

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

    private Map<String, Object> caseDataWithInvalidKey() {
        List<Object> ocrList = new ArrayList<>();

        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "invalid_key", VALUE, true))
        );

        return exceptionRecord(ocrList, null);
    }

    private Map<String, Object> caseDataWithMissingAppellantDetails() {
        List<Object> ocrList = new ArrayList<>();

        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "mrn_date", VALUE, "01/11/2048"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "contains_mrn", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "benefit_type_description", VALUE, "ESA"))
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

    private ExceptionCaseData exceptionCaseDataWithIgnoreWarnings(Map<String, Object> caseData) {

        ExceptionCaseData exceptionCaseData = exceptionCaseData(caseData);
        exceptionCaseData.setIgnoreWarnings(true);

        return exceptionCaseData;
    }

    private ExceptionCaseData exceptionCaseData(Map<String, Object> caseData) {
        return ExceptionCaseData.builder()
            .caseDetails(CaseDetails.builder()
                .caseData(caseData)
                .caseId("1234567890")
                .build())
            .build();
    }

    private Map<String, Object> caseData() {
        return caseDataWithMrnDate("01/11/2048");
    }

    private Map<String, Object> caseDataWithMrnDate(String mrnDate) {
        List<Object> ocrList = new ArrayList<>();
        List<Object> docList = new ArrayList<>();

        docList.add(ocrEntry(
            VALUE,
            ScannedRecord.builder().docScanDate("2018-10-10")
                .documentControlNumber("11111")
                .documentLink(DocumentLink.builder()
                    .documentUrl("http://www.bbc.com")
                    .documentBinaryUrl("http://www.bbc.com/binary")
                    .documentFilename("myfile.jpg").build())
                .documentType("other")
                .filename("11111.pdf")
                .build())
        );

        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "mrn_date", VALUE, mrnDate))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "contains_mrn", VALUE, true))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "benefit_type_description", VALUE, "ESA"))
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
            ImmutableMap.of(KEY, "person1_postcode", VALUE, "CM13 1AQ"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_phone", VALUE, "01234567899"))
        );
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "person1_mobile", VALUE, "01234567899"))
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
        ocrList.add(ocrEntry(
            VALUE,
            ImmutableMap.of(KEY, "hearing_options_exclude_dates", VALUE, "01/12/2018"))
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

    private void submitForCaseworkerStub(String eventId) throws Exception {
        String createCaseRequest = "";
        switch (eventId) {
            case "appealCreated": createCaseRequest = loadJson("mappings/exception/case-creation-request.json");
                break;

            case "incompleteApplication": createCaseRequest = loadJson("mappings/exception/case-incomplete-creation-request.json");
                break;

            case "nonCompliant": createCaseRequest = loadJson("mappings/exception/case-non-compliant-creation-request.json");
                break;

            default: break;
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

    private void readForCaseworkerStub(String eventUrl, Boolean validData) throws Exception {
        String createCaseResponse = validData ? "mappings/create-case-200-response.json" : "mappings/create-case-invalid-robotics-200-response.json";

        ccdServer.stubFor(get(concat(eventUrl))
            .withHeader(AUTHORIZATION, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(200)
                .withBody(loadJson(createCaseResponse))));
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
