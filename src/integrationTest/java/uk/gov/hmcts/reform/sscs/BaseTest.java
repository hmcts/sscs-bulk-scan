package uk.gov.hmcts.reform.sscs;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
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

    @Rule
    public WireMockRule ccdServer;

    protected static int wiremockPort = 0;

    protected String baseUrl;

    static {
        wiremockPort = SocketUtils.findAvailableTcpPort();
        System.setProperty("core_case_data.api.url", "http://localhost:" + wiremockPort);
    }

    @Before
    public void setUp() {
        ccdServer = new WireMockRule(wiremockPort);
        ccdServer.start();
        DateTimeUtils.setCurrentMillisFixed(1542820369000L);
    }

    @After
    public void tearDown() {
        ccdServer.stop();
    }

}
