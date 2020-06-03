package uk.gov.hmcts.reform.sscs.handler;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.SEND_TO_DWP;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@RunWith(JUnitParamsRunner.class)
public class SscsCaseDataHandlerTest {
    private final IdamTokens token = IdamTokens.builder()
        .idamOauth2Token(TEST_USER_AUTH_TOKEN)
        .serviceAuthorization(TEST_SERVICE_AUTH_TOKEN)
        .userId(TEST_USER_ID)
        .build();
    private SscsCaseDataHandler sscsCaseDataHandler;
    @Mock
    private SscsDataHelper sscsDataHelper;
    @Mock
    CaseDetails caseDetails;
    @Mock
    private CaseDataHelper caseDataHelper;
    private LocalDate localDate;
    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    @Captor
    private ArgumentCaptor<Map<String, Object>> transformedCaseCaptor;
    @Mock
    ExceptionCaseData exceptionCaseData;

    @Before
    public void setup() {
        initMocks(this);
        sscsCaseDataHandler = new SscsCaseDataHandler(sscsDataHelper, caseDataHelper,
            new CaseEvent("appealCreated", "validAppealCreated",
                "incompleteApplicationReceived", "nonCompliant"));
        when(exceptionCaseData.getCaseDetails()).thenReturn(caseDetails);
        when(caseDetails.getCaseData()).thenReturn(new HashMap<>());
        localDate = LocalDate.now();
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsTrue_thenCreateCaseWithIncompleteApplicationEvent() {
        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .benefitType(BenefitType.builder().build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings).transformedCase(transformedCase).build();

        given(caseDataHelper.findCaseBy(getSearchCriteria(), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID)).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("incompleteApplicationReceived");

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData,
            caseValidationResponse, true, token, null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCaseCaptor.capture(), eq(TEST_USER_AUTH_TOKEN), eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_USER_ID), eq("incompleteApplicationReceived"));

        boolean interlocReferralReasonFieldAndValueCheck = transformedCaseCaptor.getAllValues().stream()
            .filter(m -> m.containsKey("interlocReferralReason"))
            .anyMatch(m -> m.containsValue("over13months"));
        assertFalse(interlocReferralReasonFieldAndValueCheck);

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

        given(caseDataHelper.findCaseBy(getSearchCriteria(), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID)).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "incompleteApplicationReceived")).willReturn(1L);

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData,
            caseValidationResponse, false, token, null);

        verifyZeroInteractions(caseDataHelper);
        assertNull(response);
    }

    @Test
    public void givenACaseWithCaseReference_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .benefitType(BenefitType.builder().build()).build();
        Map<String, Object> caseData = new HashMap<>();
        caseData.put("caseReference", 1L);

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        when(caseDetails.getCaseData()).thenReturn(caseData);
        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(new ArrayList<>()).transformedCase(transformedCase).build();

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData, caseValidationResponse, false, token, null);

        verifyZeroInteractions(caseDataHelper);
    }

    @Test
    public void givenACaseWithExistingNinoMrnDateAndBenefitCode_thenDoNotCreateCaseWithIncompleteApplicationEvent() {
        uk.gov.hmcts.reform.ccd.client.model.CaseDetails sscsCaseDetails = mock(uk.gov.hmcts.reform.ccd.client.model.CaseDetails.class);
        List<uk.gov.hmcts.reform.ccd.client.model.CaseDetails> caseDetails = new ArrayList<>();
        caseDetails.add(sscsCaseDetails);
        String nino = "testnino";
        String benifitCode = "002";
        LocalDate mrnDate = LocalDate.of(2019, 8, 2);

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(mrnDate.format(formatter)).build())
            .appellant(Appellant.builder().identity(Identity.builder().nino(nino).build()).build())
            .benefitType(BenefitType.builder().code(benifitCode).build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);
        transformedCase.put("benefitCode", benifitCode);

        given(caseDataHelper.findCaseBy(getSearchCriteria(nino, benifitCode, mrnDate.toString()), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID)).willReturn(caseDetails);

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(new ArrayList<>()).transformedCase(transformedCase).build();

        sscsCaseDataHandler.handle(exceptionCaseData, caseValidationResponse, false, token, null);

        verify(caseDataHelper).findCaseBy(getSearchCriteria(nino, benifitCode, mrnDate.toString()), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID);
    }

    @Test
    public void givenACaseWithNoWarnings_thenCreateCaseWithAppealCreatedEventAndSendToDwpCheckMatches() {

        String nino = "testnino";
        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .benefitType(BenefitType.builder().build())
            .appellant(Appellant.builder().identity(Identity.builder().nino(nino).build())
                .address(Address.builder().postcode("CM120HN").build())
                .build()).build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        uk.gov.hmcts.reform.ccd.client.model.CaseDetails matchingCase = uk.gov.hmcts.reform.ccd.client.model.CaseDetails.builder().id(12345678L).build();

        List<uk.gov.hmcts.reform.ccd.client.model.CaseDetails> matchedByNinoCases = new ArrayList<>();
        matchedByNinoCases.add(matchingCase);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.findCaseBy(getMatchSearchCriteria(nino), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID))
            .willReturn(matchedByNinoCases);

        given(caseDataHelper.createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID,
            "validAppealCreated")).willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("validAppealCreated");

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData,
            caseValidationResponse, false, token, null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCaseCaptor.capture(), eq(TEST_USER_AUTH_TOKEN), eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_USER_ID), eq("validAppealCreated"));

        verify(caseDataHelper).updateCase(transformedCase,
            TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, SEND_TO_DWP.getCcdType(),
            1L, "Send to DWP", "Send to DWP event has been triggered from Bulk Scan service");
    }

    @Test
    public void givenACaseWithNoWarnings_thenCreateCaseWithAppealCreatedEventAndSendToDwp() {

        String nino = "testnino";

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build())
            .benefitType(BenefitType.builder().build())
            .appellant(Appellant.builder().address(
                Address.builder().postcode("CM120HN").build())
                .identity(Identity.builder().nino(nino).build())
                .build()).build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        final CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.findCaseBy(getSearchCriteria(), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID))
            .willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID,
            "validAppealCreated")).willReturn(1L);

        List<uk.gov.hmcts.reform.ccd.client.model.CaseDetails> matchedByNinoCases = new ArrayList<>();

        given(caseDataHelper.findCaseBy(getMatchSearchCriteria(nino), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID)).willReturn(matchedByNinoCases);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("validAppealCreated");

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData,
            caseValidationResponse, false, token, null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCaseCaptor.capture(), eq(TEST_USER_AUTH_TOKEN), eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_USER_ID), eq("validAppealCreated"));

        boolean interlocReferralReasonFieldAndValueCheck = transformedCaseCaptor.getAllValues().stream()
            .filter(m -> m.containsKey("interlocReferralReason"))
            .anyMatch(m -> m.containsValue("over13months"));
        assertFalse(interlocReferralReasonFieldAndValueCheck);

        verify(caseDataHelper).updateCase(transformedCase,
            TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, SEND_TO_DWP.getCcdType(),
            1L, "Send to DWP", "Send to DWP event has been triggered from Bulk Scan service");
    }

    @Test
    @Parameters(method = "generatePossibleAppealReasonsScenarios")
    public void givenACaseWithNoWarningsAndMrnDateIsGreaterThan13Months_thenCreateCaseWithNonCompliantApplicationEvent(
        Appeal appeal, String expectedInterlocReferralReason) {

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.findCaseBy(getSearchCriteria(), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID)).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID,
            "nonCompliant"))
            .willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("nonCompliant");

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData,
            caseValidationResponse, true, token, null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCaseCaptor.capture(), eq(TEST_USER_AUTH_TOKEN), eq(TEST_SERVICE_AUTH_TOKEN),
            eq(TEST_USER_ID), eq("nonCompliant"));

        boolean interlocReferralReasonFieldAndValueCheck = transformedCaseCaptor.getAllValues().stream()
            .filter(m -> m.containsKey("interlocReferralReason"))
            .anyMatch(m -> m.containsValue(expectedInterlocReferralReason));
        assertTrue(interlocReferralReasonFieldAndValueCheck);

        verifyZeroInteractions(caseDataHelper);
    }

    private Object[] generatePossibleAppealReasonsScenarios() {
        localDate = LocalDate.now().minusMonths(14);
        Appeal appealWithAppealReason = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(Collections.singletonList(AppealReason.builder()
                    .value(AppealReasonDetails.builder()
                        .reason("reason")
                        .description("description")
                        .build())
                    .build()))
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithNoAppealReason = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithNullAppealReason = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(null)
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithEmptyAppealReason = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder().build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithNullReasonsAndNoOtherReasons = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(null)
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithNullListAndNoOtherReasons = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(Collections.singletonList(null))
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealNoValueNoOtherReasons = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(Collections.singletonList(AppealReason.builder().build()))
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealEmptyValueNoOtherReasons = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(Collections.singletonList(AppealReason.builder()
                    .value(AppealReasonDetails.builder().build())
                    .build()))
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithDescAndNoReasonNoOtherReasons = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(Collections.singletonList(AppealReason.builder()
                    .value(AppealReasonDetails.builder()
                        .description("some")
                        .build())
                    .build()))
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Appeal appealWithOtherReasonsAndNoReasons = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .otherReasons("others")
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        return new Object[]{
            new Object[]{appealWithAppealReason, "over13months"},
            new Object[]{appealWithNoAppealReason, "over13MonthsAndGroundsMissing"},
            new Object[]{appealWithNullAppealReason, "over13MonthsAndGroundsMissing"},
            new Object[]{appealWithEmptyAppealReason, "over13MonthsAndGroundsMissing"},
            new Object[]{appealWithNullReasonsAndNoOtherReasons, "over13MonthsAndGroundsMissing"},
            new Object[]{appealWithNullListAndNoOtherReasons, "over13MonthsAndGroundsMissing"},
            new Object[]{appealNoValueNoOtherReasons, "over13MonthsAndGroundsMissing"},
            new Object[]{appealEmptyValueNoOtherReasons, "over13MonthsAndGroundsMissing"},
            new Object[]{appealWithDescAndNoReasonNoOtherReasons, "over13months"},
            new Object[]{appealWithOtherReasonsAndNoReasons, "over13months"}
        };
    }

    @Test
    public void givenACaseWithWarningsAndIgnoreWarningsTrueAndMrnDateIsGreaterThan13Months_thenCreateCaseWithNonCompliantApplicationEvent() {

        localDate = LocalDate.now().minusMonths(14);

        List<String> warnings = new ArrayList<>();
        warnings.add("I am a warning");

        Appeal appeal = Appeal.builder()
            .benefitType(BenefitType.builder().build())
            .appealReasons(AppealReasons.builder()
                .reasons(Collections.singletonList(AppealReason.builder()
                    .value(AppealReasonDetails.builder()
                        .reason("reason")
                        .description("description")
                        .build())
                    .build()))
                .build())
            .mrnDetails(MrnDetails.builder()
                .mrnDate(localDate.format(formatter))
                .build())
            .build();

        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder()
            .warnings(warnings).transformedCase(transformedCase).build();

        given(caseDataHelper.findCaseBy(getSearchCriteria(), TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID)).willReturn(Lists.emptyList());

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "nonCompliant"))
            .willReturn(1L);

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("nonCompliant");

        CallbackResponse response = sscsCaseDataHandler.handle(exceptionCaseData,
            caseValidationResponse, true, token, null);

        assertEquals("ScannedRecordCaseCreated", ((HandlerResponse) response).getState());
        assertEquals("1", ((HandlerResponse) response).getCaseId());

        verify(caseDataHelper).createCase(transformedCaseCaptor.capture(), eq(TEST_USER_AUTH_TOKEN),
            eq(TEST_SERVICE_AUTH_TOKEN), eq(TEST_USER_ID), eq("nonCompliant"));

        boolean interlocReferralReasonFieldAndValueCheck = transformedCaseCaptor.getAllValues().stream()
            .filter(m -> m.containsKey("interlocReferralReason"))
            .anyMatch(m -> m.containsValue("over13months"));
        assertTrue(interlocReferralReasonFieldAndValueCheck);
        verifyZeroInteractions(caseDataHelper);
    }

    @Test(expected = CaseDataHelperException.class)
    public void shouldThrowCaseDataHelperExceptionForAnyException() {

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().build())
            .benefitType(BenefitType.builder().build()).build();
        Map<String, Object> transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);

        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();

        given(caseDataHelper.createCase(
            transformedCase, TEST_USER_AUTH_TOKEN, TEST_SERVICE_AUTH_TOKEN, TEST_USER_ID, "appealCreated"))
            .willThrow(new RuntimeException());

        given(sscsDataHelper.findEventToCreateCase(caseValidationResponse)).willReturn("appealCreated");

        sscsCaseDataHandler.handle(exceptionCaseData, caseValidationResponse, false,
            token, null);
    }

    private Map<String, String> getSearchCriteria() {
        Map<String, String> searchCriteria = new HashMap<>();
        searchCriteria.put("case.appeal.appellant.identity.nino", "");
        searchCriteria.put("case.appeal.benefitType.code", "");
        searchCriteria.put("case.appeal.mrnDetails.mrnDate", "");
        return searchCriteria;
    }

    private Map<String, String> getSearchCriteria(String nino, String benefitCode, String mrnDate) {
        Map<String, String> searchCriteria = new HashMap<>();
        searchCriteria.put("case.appeal.appellant.identity.nino", nino);
        searchCriteria.put("case.appeal.benefitType.code", benefitCode);
        searchCriteria.put("case.appeal.mrnDetails.mrnDate", mrnDate);
        return searchCriteria;
    }

    private Map<String,String> getMatchSearchCriteria(String nino) {
        Map<String, String> searchCriteria = new HashMap<>();
        searchCriteria.put("case.appeal.appellant.identity.nino", nino);
        return searchCriteria;
    }
}
