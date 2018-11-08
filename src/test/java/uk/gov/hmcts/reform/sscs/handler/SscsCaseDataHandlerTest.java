package uk.gov.hmcts.reform.sscs.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
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
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

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
        Map<String, Object> exceptionRecordData = new HashMap<>();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true, transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, exceptionRecordData, null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived");
        assertEquals(warnings.get(0), ((AboutToStartOrSubmitCallbackResponse) response).getWarnings().get(0));
        assertEquals("1", ((AboutToStartOrSubmitCallbackResponse) response).getData().get("caseReference"));
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsFalse_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).build();
        Map<String, Object> transformedCase = new HashMap<>();
        Map<String, Object> exceptionRecordData = new HashMap<>();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false, transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, exceptionRecordData, null);

        verifyZeroInteractions(caseDataHelper);
        assertEquals(warnings.get(0), ((AboutToStartOrSubmitCallbackResponse) response).getWarnings().get(0));
    }

    @Test
    public void givenACaseWithNoWarnings_thenCreateCaseWithAppealCreatedEvent() {

        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        Map<String, Object> transformedCase = new HashMap<>();
        Map<String, Object> exceptionRecordData = new HashMap<>();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false, transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, exceptionRecordData, null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated");
        assertNull(((AboutToStartOrSubmitCallbackResponse) response).getWarnings());
        assertEquals("1", ((AboutToStartOrSubmitCallbackResponse) response).getData().get("caseReference"));
    }

}
