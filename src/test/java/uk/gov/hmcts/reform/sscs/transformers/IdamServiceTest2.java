package uk.gov.hmcts.reform.sscs.transformers;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import java.util.ArrayList;
import java.util.Base64;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.sscs.idam.Authorize;
import uk.gov.hmcts.reform.sscs.idam.IdamApiClient;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.idam.UserDetails;


@RunWith(MockitoJUnitRunner.class)
public class IdamServiceTest2 {

    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private IdamApiClient idamApiClient;

    @Mock
    private Appender<ILoggingEvent> mockAppender;

    @Captor
    private ArgumentCaptor<LoggingEvent> captorLoggingEvent;

    private Authorize authToken;
    private IdamService2 idamService;

    @Before
    public void setUp() {
        authToken = new Authorize("redirect/", "authCode", "access");
        idamService = new IdamService2(authTokenGenerator, idamApiClient
        );

        ReflectionTestUtils.setField(idamService, "idamOauth2UserEmail", "email");
        ReflectionTestUtils.setField(idamService, "idamOauth2UserPassword", "pass");
        ReflectionTestUtils.setField(idamService, "idamOauth2ClientId", "id");
        ReflectionTestUtils.setField(idamService, "idamOauth2ClientSecret", "secret");
        ReflectionTestUtils.setField(idamService, "idamOauth2RedirectUrl", "redirect/");

        @SuppressWarnings("unchecked")
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.addAppender(mockAppender);
    }

    @After
    public void teardown() {
        final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.detachAppender(mockAppender);
    }

    @Test
    public void shouldReturnAuthTokenGivenNewRequestWithAppropriateLogMessages() {
        String auth = "auth";
        when(authTokenGenerator.generate()).thenReturn(auth);

        String base64Authorisation = Base64.getEncoder().encodeToString("email:pass".getBytes());
        when(idamApiClient.authorizeCodeType("Basic " + base64Authorisation,
                "code",
                "id",
                "redirect/",
                " ")
        ).thenReturn(authToken);
        when(idamApiClient.authorizeToken(authToken.getCode(),
                "authorization_code",
                "redirect/",
                "id",
                "secret",
                " ")
        ).thenReturn(authToken);

        UserDetails expectedUserDetails = new UserDetails("16", "dummy@email.com", new ArrayList<>());
        given(idamApiClient.getUserDetails(eq("Bearer " + authToken.getAccessToken()))).willReturn(expectedUserDetails);

        IdamTokens idamTokens = idamService.getIdamTokens();
        assertThat(idamTokens.getServiceAuthorization(), is(auth));
        assertThat(idamTokens.getUserId(), is(expectedUserDetails.getId()));
        assertThat(idamTokens.getEmail(), is(expectedUserDetails.getEmail()));
        assertThat(idamTokens.getIdamOauth2Token(), containsString("Bearer access"));
    }

    @Test
    public void shouldExceptionGivenErrorWithAppropriateLogMessages() {
        String base64Authorisation = Base64.getEncoder().encodeToString("email:pass".getBytes());
        when(idamApiClient.authorizeCodeType("Basic " + base64Authorisation,
            "code",
            "id",
            "redirect/",
            " ")
        ).thenThrow(new RuntimeException());

        try {
            idamService.getIdamTokens();
        } catch (RuntimeException rte) {
            // Ignore for the purposes of this test
        }
    }
}
