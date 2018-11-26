package uk.gov.hmcts.reform.sscs.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;

public class SscsCaseDataHandlerTest {

    @InjectMocks
    SscsCaseDataHandler sscsCaseDataHandler;

    @Mock
    CaseDataHelper caseDataHelper;

    LocalDate localDate;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Before
    public void setup() {
        initMocks(this);
        ReflectionTestUtils.setField(sscsCaseDataHandler, "caseCreatedEventId", "appealCreated");
        ReflectionTestUtils.setField(sscsCaseDataHandler, "incompleteApplicationEventId", "incompleteApplicationReceived");
        ReflectionTestUtils.setField(sscsCaseDataHandler, "nonCompliantEventId", "nonCompliant");

        localDate = LocalDate.now();
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsTrue_thenCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsFalse_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verifyZeroInteractions(caseDataHelper);
        assertNull(response);
    }

    @Test
    public void givenACaseWithNoWarnings_thenCreateCaseWithAppealCreatedEvent() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

    @Test
    public void givenACaseWithNoWarningsAndMrnDateIsGreaterThan13Months_thenCreateCaseWithNonCompliantApplicationEvent() {

        localDate = LocalDate.now().minusMonths(14);

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "nonCompliant")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "nonCompliant");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsTrueAndMrnDateIsGreaterThan13Months_thenCreateCaseWithNonCompliantApplicationEvent() {

        localDate = LocalDate.now().minusMonths(14);

        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

    @Test
    public void givenACaseWithNoWarningsAndNoMrnDate_thenCreateCaseWithAppealCreatedEvent() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated");
        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());
    }

    @Test(expected = CaseDataHelperException.class)
    public void shouldThrowCaseDataHelperExceptionForAnyException() throws Exception {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated")).willThrow(new RuntimeException());

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);
    }
}
