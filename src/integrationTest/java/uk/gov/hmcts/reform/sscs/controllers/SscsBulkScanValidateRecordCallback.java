package uk.gov.hmcts.reform.sscs.controllers;

import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static uk.gov.hmcts.reform.sscs.helper.TestConstants.*;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Properties;
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
import org.springframework.http.ResponseEntity;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.service.EvidenceManagementService;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock
@TestPropertySource(locations = "classpath:application_it.yaml")
public class SscsBulkScanValidateRecordCallback {

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
        baseUrl = "http://localhost:" + randomServerPort + "/validate-record/";
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
    public void should_handle_callback_and_return_caseid_and_state_case_created_in_validate_record_data()
        throws Exception {
        // Given
        when(authTokenValidator.getServiceName(SERVICE_AUTH_TOKEN)).thenReturn("test_service");

        String validationJson = loadJson("mappings/validation/validate-appeal-created-case-request.json");

        HttpEntity<String> request = new HttpEntity<>(validationJson, httpHeaders());

        // When
        ResponseEntity<AboutToStartOrSubmitCallbackResponse> result =
            this.restTemplate.postForEntity(baseUrl, request, AboutToStartOrSubmitCallbackResponse.class);

        // Then
        assertThat(result.getStatusCodeValue()).isEqualTo(200);

        AboutToStartOrSubmitCallbackResponse callbackResponse = result.getBody();

        assertThat(callbackResponse.getErrors()).isNull();
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
            .containsOnly("person1_last_name is empty",
                "person1_address_line1 is empty",
                "person1_address_line3 is empty",
                "person1_address_line4 is empty",
                "person1_postcode is empty",
                "person1_first_name is empty");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appointee_details_are_only_partially_entered() throws IOException {
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
            .containsOnly("person1_first_name is empty",
                "person1_last_name is empty");

        verify(authTokenValidator).getServiceName(SERVICE_AUTH_TOKEN);
    }

    @Test
    public void should_return_error_when_appellant_and_appointee_details_are_only_partially_entered() throws IOException {
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
            .containsOnly("person1_first_name is empty",
                "person1_last_name is empty",
                "person2_first_name is empty",
                "person2_last_name is empty");

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
            .containsOnly("representative_address_line1 is empty",
                "representative_address_line3 is empty",
                "representative_address_line4 is empty",
                "representative_postcode is empty");

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
