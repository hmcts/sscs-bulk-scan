package uk.gov.hmcts.reform.sscs.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;

public class SscsCaseDataHandlerTest {

    @InjectMocks
    SscsCaseDataHandler sscsCaseDataHandler;

    @Mock
    CaseDataHelper caseDataHelper;

    @Before
    public void setup() {
        initMocks(this);
        ReflectionTestUtils.setField(sscsCaseDataHandler, "caseCreatedEventId", "appealCreated");
        ReflectionTestUtils.setField(sscsCaseDataHandler, "incompleteApplicationEventId", "incompleteApplicationReceived");
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsTrue_thenCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).build();
        Map<String, Object> transformedCase = new HashMap<>();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true, transformedCase,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsFalse_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).build();
        Map<String, Object> transformedCase = new HashMap<>();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false, transformedCase,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verifyZeroInteractions(caseDataHelper);
        assertNull(response);
    }

    @Test
    public void givenACaseWithNoWarnings_thenCreateCaseWithAppealCreatedEvent() {

        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        Map<String, Object> transformedCase = new HashMap<>();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false, transformedCase,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

}
