package uk.gov.hmcts.reform.sscs;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.util.Strings.concat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static uk.gov.hmcts.reform.sscs.helper.TestConstants.*;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.SocketUtils;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWireMock
@TestPropertySource(locations = "classpath:application_it.yaml")
public abstract class BaseTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    @LocalServerPort
    protected int randomServerPort;

    @MockBean
    protected AuthTokenValidator authTokenValidator;

    @MockBean
    protected IdamService idamService;

    @Rule
    public WireMockRule ccdServer;

    protected static int wiremockPort = 0;

    protected String baseUrl;

    protected IdamTokens idamTokens;

    static {
        wiremockPort = SocketUtils.findAvailableTcpPort();
        System.setProperty("core_case_data.api.url", "http://localhost:" + wiremockPort);
    }

    @Before
    public void setUp() {
        ccdServer = new WireMockRule(wiremockPort);
        ccdServer.start();
        DateTimeUtils.setCurrentMillisFixed(1542820369000L);
        idamTokens = IdamTokens.builder().idamOauth2Token(USER_AUTH_TOKEN).serviceAuthorization(SERVICE_AUTH_TOKEN).userId(USER_ID).build();
        when(idamService.getIdamTokens()).thenReturn(idamTokens);
    }

    @After
    public void tearDown() {
        ccdServer.stop();
    }

    protected void checkForLinkedCases(String eventUrl) {
        String queryUrl = getParamsMatchCaseUrl();

        ccdServer.stubFor(get(concat(eventUrl + queryUrl)).atPriority(1)
            .withHeader(AUTHORIZATION, equalTo(idamTokens.getIdamOauth2Token()))
            .withHeader(SERVICE_AUTHORIZATION_HEADER_KEY, equalTo(idamTokens.getServiceAuthorization()))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(aResponse()
                .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .withStatus(200)
                .withBody("[]")));
    }

    protected String getParamsUrl(String mrnDate) {
        Map<String, String> searchCriteria = new HashMap<>();
        searchCriteria.put("case.appeal.appellant.identity.nino", "BB000000B");
        searchCriteria.put("case.appeal.benefitType.code", "ESA");
        searchCriteria.put("case.appeal.mrnDetails.mrnDate", mrnDate);

        return searchCriteria.entrySet().stream()
            .map(p -> p.getKey() + "=" + p.getValue())
            .reduce((p1, p2) -> p1 + "&" + p2)
            .map(s -> "?" + s)
            .orElse("");
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
