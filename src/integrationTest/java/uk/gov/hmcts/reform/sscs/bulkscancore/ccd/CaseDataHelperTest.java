package uk.gov.hmcts.reform.sscs.bulkscancore.ccd;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.google.common.io.Resources.getResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import feign.FeignException;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.apache.commons.codec.Charsets;
import org.assertj.core.util.Strings;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.WireMockSpring;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "core_case_data.api.url=http://localhost:4000"
    })
@ContextConfiguration
@DirtiesContext
public class CaseDataHelperTest {

    private static final String AUTHORIZATION_HEADER_KEY = "Authorization";

    private static final String SERVICE_AUTHORIZATION_HEADER_KEY = "ServiceAuthorization";

    private static final String USER_AUTH_TOKEN = "Bearer eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJhOGdyMjR2NmtiYXRibXFlcWthM3VuamVicSIsInN1YiI6I"
        + "jYwIiwiaWF0IjoxNTA2NDE0OTI0LCJleHAiOjE1MDY0NDM3MjQsImRhdGEiOiJjaXRpemVuLGRpdm9yY2UtcHJpdmF0ZS1iZXRhLGNpdGl"
        + "6ZW4tbG9hMSxkaXZvcmNlLXByaXZhdGUtYmV0YS1sb2ExIiwidHlwZSI6IkFDQ0VTUyIsImlkIjoiNjAiLCJmb3JlbmFtZSI6ImpvaG4iL"
        + "CJzdXJuYW1lIjoic21pdGgiLCJkZWZhdWx0LXNlcnZpY2UiOiJEaXZvcmNlIiwibG9hIjoxLCJkZWZhdWx0LXVybCI6Imh0dHBzOi8vd3d"
        + "3LWxvY2FsLnJlZ2lzdHJhdGlvbi5yZWZvcm0uaG1jdHMubmV0OjkwMDAvcG9jL2Rpdm9yY2UiLCJncm91cCI6ImRpdm9yY2UtcHJpdmF0Z"
        + "S1iZXRhIn0.mkKaw1_CGwC7KuntMlp8SWsLLgrCFwKtr0oFmFq42AA";

    private static final String SERVICE_AUTH_TOKEN = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJkaXZvcmNlX2NjZF9zdWJtaXNzaW9uIiwiZXh"
        + "wIjoxNTA2NDUwNTUyfQ.IvB5-Rtywc9_pDlLkk3wMnWFT5ACu9FU2av4Z4xjCi7NRuDlvLy78TIDC2KzIVSqyJL4IklHOUPG7FCBT3SoIQ";

    private static final String USER_ID = "1234";

    private static final String INVALID_USER_TOKEN = "INVALID_USER_TOKEN";

    private static final String INVALID_SERVICE_TOKEN = "INVALID_SERVICE_TOKEN";

    private static final String START_EVENT_URL =
        "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/event-triggers/appealCreated/token";

    @ClassRule
    public static WireMockClassRule ccdServer = new WireMockClassRule(WireMockSpring.options().port(4000)
        .bindAddress("localhost"));

    @Autowired
    private CaseDataHelper caseDataHelper;


    @Test
    public void should_successfully_create_case_and_return_case_data() throws Exception {
        startForCaseworkerStub();

        submitForCaseworkerStub();

        Long caseId = caseDataHelper.createCase(
            sscsCaseData(),
            USER_AUTH_TOKEN,
            SERVICE_AUTH_TOKEN,
            USER_ID
        );

        assertThat(caseId).isEqualTo(Long.valueOf("1539878003972756"));
    }

    @Test
    public void should_return_403_forbidden_response_when_user_auth_token_is_invalid() throws Exception {
        startForCaseworkerStubWithInvalidUserToken();

        Throwable exception = catchThrowable(() -> caseDataHelper.createCase(
            sscsCaseData(),
            INVALID_USER_TOKEN,
            SERVICE_AUTH_TOKEN,
            USER_ID
        ));

        assertThat(exception).isInstanceOf(FeignException.class);

        assertThat(exception).hasStackTraceContaining(" \"status\": 403");
        assertThat(exception).hasStackTraceContaining("\"message\": \"Access Denied\"");

        assertThat(exception)
            .hasStackTraceContaining("\"path\": \"/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/event-triggers/appealCreated/token\"");
    }

    @Test
    public void should_return_403_forbidden_response_when_service_auth_token_is_invalid() throws Exception {
        startForCaseworkerStubWithInvalidServiceToken();

        Throwable exception = catchThrowable(() -> caseDataHelper.createCase(
            sscsCaseData(),
            USER_AUTH_TOKEN,
            INVALID_SERVICE_TOKEN,
            USER_ID
        ));

        assertThat(exception).isInstanceOf(FeignException.class);

        assertThat(exception).hasStackTraceContaining(" \"status\": 403");
        assertThat(exception).hasStackTraceContaining("\"message\": \"Access Denied\"");

        assertThat(exception)
            .hasStackTraceContaining("\"path\": \"/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/event-triggers/appealCreated/token\"");
    }

    @Test
    public void should_return_404_field_not_found_when_submitting_case_data_with_invalid_fields() throws Exception {
        startForCaseworkerStub();

        submitForCaseworkerStubWithInvalidFields();

        Throwable exception = catchThrowable(() -> caseDataHelper.createCase(
            sscsCaseDataWithInvalidFields(),
            USER_AUTH_TOKEN,
            SERVICE_AUTH_TOKEN,
            USER_ID
        ));

        assertThat(exception).isInstanceOf(FeignException.class);

        assertThat(exception).hasStackTraceContaining(" \"status\": 404");
        assertThat(exception).hasStackTraceContaining("\"message\": \"No field found\"");

        assertThat(exception)
            .hasStackTraceContaining("\"path\": \"/caseworkers/25/jurisdictions/SSCS/case-types/Benefit/cases\"");
    }

    private void startForCaseworkerStub() throws Exception {
        String eventStartResponseBody = loadJson("wiremockstubs/ccd/event-start-200-response.json");

        String startEventUrl = "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/event-triggers/appealCreated/token";

        ccdServer.stubFor(get(Strings.concat(startEventUrl))
            .withHeader(AUTHORIZATION_HEADER_KEY, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(200)
                .withBody(eventStartResponseBody)));
    }

    private void startForCaseworkerStubWithInvalidUserToken() throws Exception {
        String eventStartResponseBody = loadJson("wiremockstubs/ccd/event-start-403-response.json");

        ccdServer.stubFor(get(Strings.concat(START_EVENT_URL))
            .withHeader(AUTHORIZATION_HEADER_KEY, equalTo(INVALID_USER_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(403)
                .withBody(eventStartResponseBody)));
    }

    private void startForCaseworkerStubWithInvalidServiceToken() throws Exception {
        String eventStartResponseBody = loadJson("wiremockstubs/ccd/event-start-403-response.json");

        ccdServer.stubFor(get(Strings.concat(START_EVENT_URL))
            .withHeader(AUTHORIZATION_HEADER_KEY, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(INVALID_SERVICE_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(403)
                .withBody(eventStartResponseBody)));
    }

    private void submitForCaseworkerStub() throws Exception {
        String createCaseRequest = loadJson("wiremockstubs/ccd/case-creation-request.json");

        String startEventUrl = "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/cases?ignore-warning=true";

        ccdServer.stubFor(WireMock.post(Strings.concat(startEventUrl))
            .withHeader(AUTHORIZATION_HEADER_KEY, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .withRequestBody(equalToJson(createCaseRequest))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(200)
                .withBody(loadJson("wiremockstubs/ccd/create-case-200-response.json"))));
    }

    private void submitForCaseworkerStubWithInvalidFields() throws Exception {
        String createCaseRequest = loadJson("wiremockstubs/ccd/case-creation-request-invalid-fields.json");

        String startEventUrl = "/caseworkers/1234/jurisdictions/SSCS/case-types/Benefit/cases?ignore-warning=true";

        ccdServer.stubFor(WireMock.post(Strings.concat(startEventUrl))
            .withHeader(AUTHORIZATION_HEADER_KEY, equalTo(USER_AUTH_TOKEN))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(SERVICE_AUTH_TOKEN))
            .withHeader(CONTENT_TYPE, equalTo(MediaType.APPLICATION_JSON_UTF8_VALUE))
            .withRequestBody(equalToJson(createCaseRequest))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(404)
                .withBody(loadJson("wiremockstubs/ccd/create-case-404-response.json"))));
    }

    public static String loadJson(String fileName) throws IOException {
        URL url = getResource(fileName);
        return Resources.toString(url, Charsets.toCharset("UTF-8"));
    }

    public Map<String, Object> sscsCaseData() {
        return ImmutableMap.of(
            "generatedEmail", "sscstest@test.com",
            "caseReference", "123456789",
            "caseCreated", "2018-01-11",
            "generatedNino", "SR11111",
            "generatedSurname", "Smith"
        );
    }

    public Map<String, Object> sscsCaseDataWithInvalidFields() {
        return ImmutableMap.of(
            "generatedEmail", "sscstest@test.com",
            "caseReference1", "123456789",
            "caseCreated", "2018-01-11",
            "generatedNino", "SR11111",
            "generatedSurname1", "Smith"
        );
    }
}
