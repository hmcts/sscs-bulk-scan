package uk.gov.hmcts.reform.sscs.controllers;

import static com.google.common.io.Resources.getResource;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static uk.gov.hmcts.reform.sscs.helper.TestConstants.*;

import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.commons.codec.Charsets;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.BaseTest;

@RunWith(JUnitParamsRunner.class)
public class SscsBulkScanValidateRecordCallback extends BaseTest {

    @ClassRule
    public static final SpringClassRule SPRING_CLASS_RULE = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Before
    public void setup() {
        baseUrl = "http://localhost:" + randomServerPort + "/validate-record/";
    }

    @Test
    public void should_handle_callback_and_return_caseid_and_state_case_created_in_validate_record_data()
        throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");
        checkForLinkedCases(FIND_CASE_EVENT_URL);

        String validationJson = loadJson("mappings/validation/validate-appeal-created-case-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson, httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        AboutToStartOrSubmitCallbackResponse callbackResponse = result.getBody();

        assertThat(callbackResponse.getErrors()).isEmpty();
        assertThat(callbackResponse.getWarnings()).isEmpty();

        assertEquals("readyToList", callbackResponse.getData().get("createdInGapsFrom"));
        assertEquals("Cardiff", callbackResponse.getData().get("processingVenue"));

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_handle_callback_and_return_caseid_and_state_case_created_in_validate_interloc_record_data()
        throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");
        checkForLinkedCases(FIND_CASE_EVENT_URL);

        String validationJson = loadJson("mappings/validation/validate-interloc-appeal-created-case-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson, httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        AboutToStartOrSubmitCallbackResponse callbackResponse = result.getBody();

        assertThat(callbackResponse.getErrors()).isEmpty();
        assertThat(callbackResponse.getWarnings()).isEmpty();

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appellant_details_are_partially_entered() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-missing-appellant-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly(
                "Appellant title is empty",
                "Appellant last name is empty",
                "Appellant address line 1 is empty",
                "Appellant address town is empty",
                "Appellant address county is empty",
                "Appellant postcode is empty",
                "Appellant first name is empty",
                "Appellant date of birth is in future",
                "Hearing options exclude dates is in past",
                "Mrn date is empty",
                "DWP issuing office is empty",
                "Benefit type description is empty",
                "Hearing type is invalid");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appointee_details_are_only_partially_entered_and_missing_hearing_sub_type_for_form_type_null() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-missing-appointee-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly(
                "Appointee title is empty",
                "Appointee first name is empty",
                "Appointee last name is empty");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appointee_details_are_only_partially_entered_and_missing_hearing_sub_type_for_auto_scan_form() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/auto-validate-appeal-created-missing-appointee-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly(
                "Appointee title is empty",
                "Appointee first name is empty",
                "Appointee last name is empty",
                "Hearing option telephone, video and face to face are empty. At least one must be populated");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appellant_and_appointee_details_are_only_partially_entered_for_auto_scan_form_type() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/auto-validate-appeal-created-missing-appellant-and-appointee-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("Appointee title is empty",
                "Appointee first name is empty",
                "Appointee last name is empty",
                "Appellant title is empty",
                "Appellant first name is empty",
                "Appellant last name is empty",
                "Hearing option telephone, video and face to face are empty. At least one must be populated");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appellant_and_appointee_details_are_only_partially_entered_for_form_type_null() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-missing-appellant-and-appointee-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("Appointee title is empty",
                "Appointee first name is empty",
                "Appointee last name is empty",
                "Appellant title is empty",
                "Appellant first name is empty",
                "Appellant last name is empty");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_representative_details_are_only_partially_entered() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-missing-representative-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("Representative organisation, Representative first name and Representative last name are empty. At least one must be populated",
                "Representative address town is empty",
                "Representative address county is empty",
                "Representative postcode is empty");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    @Parameters({"rep", "hasRepresentative"})
    public void should_return_error_when_representative_details_are_not_entered(String fieldToRename) throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-missing-representative-request.json")
            .replaceAll(fieldToRename, "fieldMoved");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("The \"Has representative\" field is not selected, please select an option to proceed");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_status_code_401_when_service_auth_token_is_missing() throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);
        headers.set(CONTENT_TYPE, JSON_TYPE);

        String validationJson = loadJson("mappings/validation/validate-appeal-created-case-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson, headers);

        // When
        ResponseEntity<Void> result =
            this.restTemplate.postForEntity(baseUrl, request, Void.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(401);
    }

    @Test
    public void should_return_status_code_403_when_service_auth_token_is_missing() throws IOException {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("forbidden_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-case-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson,  httpHeaders());

        // When
        ResponseEntity<Void> result =
            this.restTemplate.postForEntity(baseUrl, request, Void.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(403);

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_postcode_is_invalid()
        throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");
        checkForLinkedCases(FIND_CASE_EVENT_URL);

        final String invalidPostcode = "CM13 9HY";
        String validationJson = loadJson("mappings/validation/validate-appeal-created-case-request.json")
            .replaceAll("CF48 2HY", invalidPostcode);

        HttpEntity<String> request = new HttpEntity<>(validationJson, httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);
        assertThat(result.getBody().getErrors())
            .containsOnly("Appellant postcode is not a valid postcode");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    private static String loadJson(String fileName) throws IOException {
        URL url = getResource(fileName);
        return Resources.toString(url, Charsets.toCharset("UTF-8"));
    }

    private HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(AUTHORIZATION, USER_AUTH_TOKEN);
        headers.set(SERVICE_AUTHORIZATION_HEADER_KEY, SERVICE_AUTH_TOKEN);
        headers.set(USER_ID_HEADER, USER_ID);
        headers.set(CONTENT_TYPE, JSON_TYPE);
        return headers;
    }
}
