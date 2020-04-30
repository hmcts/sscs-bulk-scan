package uk.gov.hmcts.reform.sscs.controllers;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.util.Strings.concat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.skyscreamer.jsonassert.JSONCompareMode.NON_EXTENSIBLE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.hmcts.reform.sscs.bulkscancore.domain.JourneyClassification.NEW_APPLICATION;
import static uk.gov.hmcts.reform.sscs.helper.OcrDataBuilderTest.buildScannedValidationOcrData;
import static uk.gov.hmcts.reform.sscs.helper.TestConstants.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.codec.Charsets;
import org.junit.Before;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.reform.sscs.BaseTest;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ErrorResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.InputScannedDoc;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;
import uk.gov.hmcts.reform.sscs.ccd.domain.DocumentLink;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;

public class SscsBulkScanExceptionRecordCallback extends BaseTest {

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Before
    public void setup() {
        baseUrl = "http://localhost:" + randomServerPort + "/transform-exception-record/";
    }

    @Test
    public void should_handle_callback_and_return_caseid_and_state_case_created_in_exception_record_data()
        throws Exception {
        checkForLinkedCases(FIND_CASE_EVENT_URL);
        findCaseByForCaseworker(FIND_CASE_EVENT_URL, "2020-04-09");

        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(exceptionCaseData(caseDataWithMrnDate("09/04/2020")),
            httpHeaders());

        ResponseEntity<SuccessfulTransformationResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, SuccessfulTransformationResponse.class);

        SuccessfulTransformationResponse callbackResponse = result.getBody();

        verifyResultData(result, "mappings/exception/valid-appeal-response.json");
    }

    @Test
    public void should_transform_incomplete_case_when_data_missing() throws Exception {
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithMissingAppellantDetails()),
            httpHeaders()
        );

        ResponseEntity<SuccessfulTransformationResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, SuccessfulTransformationResponse.class);

        verifyResultData(result, "mappings/exception/case-incomplete-response.json");

    }

    @Test
    public void should_create_non_compliant_case_when_mrn_date_greater_than_13_months() throws Exception {
        checkForLinkedCases(FIND_CASE_EVENT_URL);
        findCaseByForCaseworker(FIND_CASE_EVENT_URL, "2017-01-01");

        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithMrnDate("01/01/2017")),
            httpHeaders()
        );

        // When
        ResponseEntity<SuccessfulTransformationResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, SuccessfulTransformationResponse.class);

        verifyResultData(result, "mappings/exception/case-non-compliant-response.json");
    }

    @Test
    public void should_return_error_list_populated_when_exception_record_transformation_fails() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithContradictingValues()),
            httpHeaders()
        );

        // When
        ResponseEntity<ErrorResponse> result = this.restTemplate.postForEntity(baseUrl, request, ErrorResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(422);
        assertThat(result.getBody().errors)
            .containsOnly("is_hearing_type_oral and is_hearing_type_paper have contradicting values");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_list_populated_when_key_value_pair_validation_fails() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithInvalidKey()),
            httpHeaders()
        );

        // When
        ResponseEntity<ErrorResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, ErrorResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(422);
        assertThat(result.getBody().errors)
            .containsOnly("#: extraneous key [invalid_key] is not permitted");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }


    @Test
    public void should_not_create_duplicate_non_compliant_case_when_mrndate_nino_benefit_code_case_exists() throws Exception {
        // Given
        checkForLinkedCases(FIND_CASE_EVENT_URL);
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(
                exceptionCaseData(caseDataWithMrnDate("01/01/2017")),
                httpHeaders()
        );

        findCaseByForCaseworkerReturnCaseDetails(FIND_CASE_EVENT_URL, "2017-01-01");

        // When
        ResponseEntity<ErrorResponse> result =
                this.restTemplate.postForEntity(baseUrl, request, ErrorResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(422);
        assertThat(result.getBody().errors).containsOnly("Duplicate case already exists - please reject this exception record");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_warnings_when_tell_tribunal_about_dates_is_true_and_no_excluded_dates_provided() {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(
            exceptionCaseData(caseDataWithNoExcludedHearingDates()),
            httpHeaders()
        );

        // When
        ResponseEntity<SuccessfulTransformationResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, SuccessfulTransformationResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getWarnings())
            .contains("No excluded dates provided but data indicates that there are dates customer cannot attend hearing as tell_tribunal_about_dates is true. Is this correct?");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_status_code_401_when_service_auth_token_is_missing() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(exceptionCaseData(caseData()), headers);

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

        HttpEntity<ExceptionRecord> request = new HttpEntity<>(exceptionCaseData(caseData()), httpHeaders());

        // When
        ResponseEntity<Void> result =
            this.restTemplate.postForEntity(baseUrl, request, Void.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(403);

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    private Map<String, Object> caseDataWithContradictingValues() {
        Map<String, Object> ocrList = new HashMap<>();

        ocrList.put("is_hearing_type_oral", true);
        ocrList.put("is_hearing_type_paper", true);

        return exceptionRecord(ocrList, null);
    }

    private Map<String, Object> caseDataWithNoExcludedHearingDates() {
        Map<String, Object> ocrList = new HashMap<>();

        ocrList.put("tell_tribunal_about_dates", true);
        ocrList.put("hearing_options_exclude_dates", "");

        return exceptionRecord(ocrList, null);
    }

    private Map<String, Object> caseDataWithInvalidKey() {
        Map<String, Object> ocrList = new HashMap<>();

        ocrList.put("invalid_key", true);

        return exceptionRecord(ocrList, null);
    }

    private Map<String, Object> caseDataWithMissingAppellantDetails() {
        Map<String, Object> ocrList = new HashMap<>();

        ocrList.put("mrn_date", "09/12/2018");
        ocrList.put("office", "Balham DRT");
        ocrList.put("contains_mrn", true);
        ocrList.put("benefit_type_description", "ESA");
        ocrList.put("person1_title", "Mr");
        ocrList.put("person1_first_name", "John");
        ocrList.put("is_hearing_type_oral", true);
        ocrList.put("is_hearing_type_paper", false);

        return exceptionRecord(ocrList, null);
    }

    private Map<String, Object> exceptionRecord(Map<String, Object> ocrList, List<InputScannedDoc> docList) {
        Map<String, Object> exceptionRecord = new HashMap<>();
        exceptionRecord.put("scanOCRData", ocrList);
        exceptionRecord.put("scannedDocuments", docList);
        return exceptionRecord;
    }

    @SuppressWarnings("unchecked")
    private ExceptionRecord exceptionCaseData(Map<String, Object> caseData) {

        Map<String, Object> scannedData = (HashMap<String, Object>) caseData.get("scanOCRData");
        List<OcrDataField> scanOcrData = buildScannedValidationOcrData(scannedData.entrySet().stream().map(f -> {
            HashMap<String, Object> valueMap = new HashMap<>();
            valueMap.put("name", f.getKey());
            valueMap.put("value", f.getValue());
            return valueMap;
        }).toArray(HashMap[]::new));

        return ExceptionRecord.builder()
            .ocrDataFields(scanOcrData)
            .poBox("SSCSPO")
            .jurisdiction("SSCS")
            .formType("SSCS1")
            .journeyClassification(NEW_APPLICATION)
            .scannedDocuments((List<InputScannedDoc>) caseData.get("scannedDocuments"))
            .id("1234567890")
            .openingDate(LocalDateTime.parse("2018-01-11 12:00:00", formatter))
            .deliveryDate(LocalDateTime.parse("2018-01-11 12:00:00", formatter))
            .build();
    }

    private Map<String, Object> caseData() {
        return caseDataWithMrnDate("09/12/2018");
    }

    private Map<String, Object> caseDataWithMrnDate(String mrnDate) {
        Map<String, Object> ocrList = new HashMap<>();

        List<InputScannedDoc> docList = new ArrayList<>();

        LocalDateTime dateTime = LocalDateTime.parse("2018-10-10 12:00:00", formatter);

        docList.add(InputScannedDoc.builder().scannedDate(dateTime)
                .controlNumber("11111")
                .url(DocumentLink.builder()
                    .documentUrl("http://www.bbc.com")
                    .documentBinaryUrl("http://www.bbc.com/binary")
                    .documentFilename("myfile.jpg").build())
                .type("other")
                .subtype("my subtype")
                .fileName("11111.pdf")
                .build());

        ocrList.put("mrn_date", mrnDate);
        ocrList.put("office", "Balham DRT");
        ocrList.put("contains_mrn", true);
        ocrList.put("benefit_type_description", "ESA");
        ocrList.put("person1_title", "Mr");
        ocrList.put("person1_first_name", "John");
        ocrList.put("person1_last_name", "Smith");
        ocrList.put("person1_address_line1", "2 Drake Close");
        ocrList.put("person1_address_line2", "Hutton");
        ocrList.put("person1_address_line3", "Brentwood");
        ocrList.put("person1_address_line4", "Essex");
        ocrList.put("person1_postcode", "CM13 1AQ");
        ocrList.put("person1_phone", "01234567899");
        ocrList.put("person1_mobile", "07411222222");
        ocrList.put("person1_dob", "11/11/1976");
        ocrList.put("person1_nino", "BB000000B");
        ocrList.put("is_hearing_type_oral", true);
        ocrList.put("is_hearing_type_paper", false);
        ocrList.put("hearing_options_exclude_dates", "01/12/2030");

        return exceptionRecord(ocrList, docList);
    }

    private void findCaseByForCaseworkerReturnCaseDetails(String eventUrl, String mrnDate) throws Exception {
        String queryUrl = getParamsUrl(mrnDate);

        ccdServer.stubFor(get(concat(eventUrl + queryUrl)).atPriority(1)
                .withHeader(AUTHORIZATION, equalTo(USER_AUTH_TOKEN))
                .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
                .willReturn(aResponse()
                        .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                        .withStatus(200)
                        .withBody(loadJson("mappings/existing-case-details-200-response.json"))));
    }

    private static String loadJson(String fileName) throws IOException {
        URL url = getResource(fileName);
        return Resources.toString(url, Charsets.toCharset("UTF-8"));
    }

    private void verifyResultData(ResponseEntity<SuccessfulTransformationResponse> result, String expectedDataFileLocation) throws Exception {
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        SuccessfulTransformationResponse callbackResponse = result.getBody();

        String expected = loadJson(expectedDataFileLocation);

        expected = expected.replace("TYA_RANDOM_NUMBER", ((HashMap) ((HashMap) callbackResponse.getCaseCreationDetails().getCaseData().get("subscriptions")).get("appellantSubscription")).get("tya").toString());

        ObjectMapper obj = new ObjectMapper();
        String jsonStr = obj.writeValueAsString(callbackResponse);

        JSONAssert.assertEquals(expected, jsonStr, NON_EXTENSIBLE);

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }

    private HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_AUTH_TOKEN);
        headers.set(SERVICE_AUTHORIZATION_HEADER_KEY, SERVICE_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);
        return headers;
    }

    private String getParamsMatchCaseUrl() {
        Map<String, String> searchCriteria = new HashMap<>();
        searchCriteria.put("case.appeal.appellant.identity.nino", "BB000000B");

        return searchCriteria.entrySet().stream()
            .map(p -> p.getKey() + "=" + p.getValue())
            .reduce((p1, p2) -> p1 + "&" + p2)
            .map(s -> "?" + s)
            .orElse("");
    }
}
