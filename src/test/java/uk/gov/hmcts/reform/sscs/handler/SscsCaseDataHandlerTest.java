package uk.gov.hmcts.reform.sscs.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.SEND_TO_DWP;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamService;

public class SscsCaseDataHandlerTest {

    SscsCaseDataHandler sscsCaseDataHandler;

    @Mock
    SscsDataHelper sscsDataHelper;

    @Mock
    CaseDataHelper caseDataHelper;

    @Mock
    private CcdService ccdService;

    @Mock
    private IdamService idamService;

    LocalDate localDate;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Before
    public void setup() {
        initMocks(this);

        sscsCaseDataHandler = new SscsCaseDataHandler(sscsDataHelper, caseDataHelper, new CaseEvent("appealCreated", "validAppealCreated", "incompleteApplicationReceived", "nonCompliant"), ccdService, idamService);

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

        given(ccdService.findCaseBy(any(), any())).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("incompleteApplicationReceived");

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived");
        verifyZeroInteractions(caseDataHelper);
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsFalse_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).transformedCase(transformedCase).build();

        given(ccdService.findCaseBy(any(), any())).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verifyZeroInteractions(caseDataHelper);

        assertNull(response);
    }

    @Test
    public void givenACaseWithCaseReference_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);
        transformedCase.put("caseReference",1L);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).transformedCase(transformedCase).build();

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
                Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verifyZeroInteractions(caseDataHelper);
    }

    @Test
    public void givenACaseWithExistingNinoMrnDateAndBenefitCode_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        SscsCaseDetails sscsCaseDetails = mock(SscsCaseDetails.class);
        List<SscsCaseDetails> caseDetails = new ArrayList<>();
        caseDetails.add(sscsCaseDetails);
        String nino = "testnino";
        String benifitCode = "002";
        LocalDate mrnDate = LocalDate.of(2019,8, 2);

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(mrnDate.format(formatter)).build())
                .appellant(Appellant.builder().identity(Identity.builder().nino(nino).build()).build())
                .benefitType(BenefitType.builder().code(benifitCode).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);
        transformedCase.put("generatedNino",nino);
        transformedCase.put("benefitCode", benifitCode);

        given(ccdService.findCaseBy(anyMap(), any())).willReturn(caseDetails);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(new ArrayList<>()).transformedCase(transformedCase).build();

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
                Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        verifyZeroInteractions(caseDataHelper);
    }

    @Test
    public void givenACaseWithNoWarnings_thenCreateCaseWithAppealCreatedEventAndSendToDwp() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .appellant(Appellant.builder().address(
                Address.builder().postcode("CM120HN").build())
            .build()).build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(ccdService.findCaseBy(any(), any())).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "validAppealCreated")).willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("validAppealCreated");

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "validAppealCreated");
        verify(caseDataHelper).updateCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, SEND_TO_DWP.getCcdType(), 1L, "Send to DWP", "Send to DWP event has been triggered from Bulk Scan service");
    }

    @Test
    public void givenACaseWithNoWarningsAndMrnDateIsGreaterThan13Months_thenCreateCaseWithNonCompliantApplicationEvent() {

        localDate = LocalDate.now().minusMonths(14);

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(ccdService.findCaseBy(any(), any())).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "nonCompliant")).willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("nonCompliant");

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "nonCompliant");
        verifyZeroInteractions(caseDataHelper);
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

        given(ccdService.findCaseBy(any(), any())).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("incompleteApplicationReceived");

        CallbackResponse response =  sscsCaseDataHandler.handle(caseValidationResponse, true,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived");
        verifyZeroInteractions(caseDataHelper);
    }

    @Test(expected = CaseDataHelperException.class)
    public void shouldThrowCaseDataHelperExceptionForAnyException() throws Exception {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated")).willThrow(new RuntimeException());

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("appealCreated");

        sscsCaseDataHandler.handle(caseValidationResponse, false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(), null);
    }
}
