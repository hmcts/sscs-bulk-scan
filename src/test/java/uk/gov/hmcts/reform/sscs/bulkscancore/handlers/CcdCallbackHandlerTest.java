package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.PreSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.sscs.exceptions.InvalidExceptionRecordException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@RunWith(SpringRunner.class)
public class CcdCallbackHandlerTest {

    private CcdCallbackHandler ccdCallbackHandler;

    private SampleCaseDataCreator caseDataCreator = new SampleCaseDataCreator();

    @Mock
    private CaseTransformer caseTransformer;

    @Mock
    private CaseValidator caseValidator;

    @Mock
    private CaseDataHandler caseDataHandler;

    @Mock
    private DwpAddressLookupService dwpAddressLookupService;

    private SscsDataHelper sscsDataHelper;

    @Captor
    private ArgumentCaptor<CaseResponse> warningCaptor;

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private Map<String, Object> transformedCase;

    private IdamTokens idamTokens;

    @Before
    public void setUp() {
        sscsDataHelper = new SscsDataHelper(new CaseEvent(null, "validAppealCreated", null, null), new ArrayList<>(), dwpAddressLookupService);
        ccdCallbackHandler = new CcdCallbackHandler(caseTransformer, caseValidator, caseDataHandler, sscsDataHelper,
            dwpAddressLookupService);
        idamTokens = IdamTokens.builder().idamOauth2Token(TEST_USER_AUTH_TOKEN).serviceAuthorization(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build();

        ReflectionTestUtils.setField(ccdCallbackHandler, "debugJson", false);
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("PIP", "3"))
            .willReturn("Springburn");
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("ESA", "Balham DRT"))
            .willReturn("Balham");

        LocalDate localDate = LocalDate.now();

        Appeal appeal = Appeal.builder().mrnDetails(MrnDetails.builder().mrnDate(localDate.format(formatter)).build()).build();
        transformedCase = new HashMap<>();
        transformedCase.put("appeal", appeal);
    }

    @Test
    public void should_return_exception_data_with_case_id_and_state_when_transformation_and_validation_are_successful() {
        ExceptionRecord exceptionRecord = ExceptionRecord.builder().build();

        CaseResponse response = CaseResponse.builder().transformedCase(transformedCase).build();

        when(caseTransformer.transformExceptionRecord(exceptionRecord, false)).thenReturn(response);

        // No errors and warnings are populated hence validation would be successful
        CaseResponse caseValidationResponse = CaseResponse.builder().transformedCase(transformedCase).build();
        when(caseValidator.validateExceptionRecord(response, exceptionRecord, transformedCase, false))
            .thenReturn(caseValidationResponse);

        SuccessfulTransformationResponse ccdCallbackResponse = invokeCallbackHandler(exceptionRecord);

        assertExceptionDataEntries(ccdCallbackResponse);
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test(expected = InvalidExceptionRecordException.class)
    public void should_return_exception_record_and_errors_in_callback_response_when_transformation_fails() {
        ExceptionRecord exceptionRecord = ExceptionRecord.builder().build();

        when(caseTransformer.transformExceptionRecord(exceptionRecord, false))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("Cannot transform Appellant Date of Birth. Please enter valid date"))
                .build());

        invokeCallbackHandler(exceptionRecord);
    }

    @Test(expected = InvalidExceptionRecordException.class)
    public void should_return_exc_data_and_errors_in_callback_when_transformation_success_and_validation_fails_with_errors() {
        ExceptionRecord exceptionRecord = ExceptionRecord.builder().build();

        CaseResponse response = CaseResponse.builder().transformedCase(transformedCase).build();

        when(caseTransformer.transformExceptionRecord(exceptionRecord, false)).thenReturn(response);

        when(caseValidator.validateExceptionRecord(response, exceptionRecord, transformedCase, false))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("NI Number is invalid"))
                .build());

        invokeCallbackHandler(exceptionRecord);
    }

    @Test
    public void givenAWarningInTransformationServiceAndAnotherWarningInValidationService_thenShowBothWarnings() {
        ExceptionRecord exceptionRecord = ExceptionRecord.builder().build();

        List<String> warnings = new ArrayList<>();
        warnings.add("First warning");

        when(caseTransformer.transformExceptionRecord(exceptionRecord, false))
            .thenReturn(CaseResponse.builder()
                .transformedCase(transformedCase)
                .warnings(warnings)
                .build());

        List<String> warnings2 = new ArrayList<>();
        warnings2.add("First warning");
        warnings2.add("Second warning");

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings2).transformedCase(transformedCase).build();

        when(caseValidator.validateExceptionRecord(any(), eq(exceptionRecord), eq(transformedCase), eq(false)))
            .thenReturn(caseValidationResponse);

        SuccessfulTransformationResponse ccdCallbackResponse = invokeCallbackHandler(exceptionRecord);

        verify(caseValidator).validateExceptionRecord(warningCaptor.capture(), eq(exceptionRecord), eq(transformedCase), eq(false));

        assertThat(warningCaptor.getAllValues().get(0).getWarnings().size()).isEqualTo(1);
        assertThat(warningCaptor.getAllValues().get(0).getWarnings().get(0)).isEqualTo("First warning");

        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(2);
    }

    @Test
    public void should_return_no_warnings_or_errors_or_data_when_validation_endpoint_is_successful_for_pip_case() {

        Appeal appeal = Appeal.builder()
            .appellant(Appellant.builder()
                .name(Name.builder().firstName("Fred").lastName("Ward").build())
                .identity(Identity.builder().nino("JT123456N").dob("12/08/1990").build())
                .build())
            .mrnDetails(MrnDetails.builder().dwpIssuingOffice("3").build())
            .benefitType(BenefitType.builder().code("PIP").build())
            .build();

        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().appeal(appeal).interlocReviewState("something").build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        when(caseValidator.validateValidationRecord(any())).thenReturn(caseValidationResponse);

        PreSubmitCallbackResponse<SscsCaseData> ccdCallbackResponse = invokeValidationCallbackHandler(caseDetails.getCaseData());

        assertThat(ccdCallbackResponse.getData()).isNotNull();
        assertThat(ccdCallbackResponse.getErrors().size()).isEqualTo(0);
        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(0);
        assertThat(ccdCallbackResponse.getData().getInterlocReviewState()).isEqualTo("none");
        assertThat(ccdCallbackResponse.getData().getCreatedInGapsFrom()).isEqualTo("validAppeal");
        assertThat(ccdCallbackResponse.getData().getEvidencePresent()).isEqualTo("No");
        assertThat(ccdCallbackResponse.getData().getGeneratedSurname()).isEqualTo("Ward");
        assertThat(ccdCallbackResponse.getData().getGeneratedNino()).isEqualTo("JT123456N");
        assertThat(ccdCallbackResponse.getData().getGeneratedDob()).isEqualTo("12/08/1990");
        assertThat(ccdCallbackResponse.getData().getBenefitCode()).isEqualTo("002");
        assertThat(ccdCallbackResponse.getData().getIssueCode()).isEqualTo("DD");
        assertThat(ccdCallbackResponse.getData().getCaseCode()).isEqualTo("002DD");
        assertThat(ccdCallbackResponse.getData().getDwpRegionalCentre()).isEqualTo("Springburn");
    }

    @Test
    public void should_return_no_warnings_or_errors_or_data_when_validation_endpoint_is_successful_for_esa_case() {

        Appeal appeal = Appeal.builder()
            .appellant(Appellant.builder()
                .name(Name.builder().firstName("Fred").lastName("Ward").build())
                .identity(Identity.builder().nino("JT123456N").dob("12/08/1990").build())
                .build())
            .mrnDetails(MrnDetails.builder().dwpIssuingOffice("Balham DRT").build())
            .benefitType(BenefitType.builder().code("ESA").build())
            .build();

        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().appeal(appeal).interlocReviewState("something").build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        when(caseValidator.validateValidationRecord(any())).thenReturn(caseValidationResponse);

        PreSubmitCallbackResponse<SscsCaseData> ccdCallbackResponse = invokeValidationCallbackHandler(caseDetails.getCaseData());

        assertThat(ccdCallbackResponse.getData()).isNotNull();
        assertThat(ccdCallbackResponse.getErrors().size()).isEqualTo(0);
        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(0);
        assertThat(ccdCallbackResponse.getData().getInterlocReviewState()).isEqualTo("none");
        assertThat(ccdCallbackResponse.getData().getCreatedInGapsFrom()).isEqualTo("validAppeal");
        assertThat(ccdCallbackResponse.getData().getEvidencePresent()).isEqualTo("No");
        assertThat(ccdCallbackResponse.getData().getGeneratedSurname()).isEqualTo("Ward");
        assertThat(ccdCallbackResponse.getData().getGeneratedNino()).isEqualTo("JT123456N");
        assertThat(ccdCallbackResponse.getData().getGeneratedDob()).isEqualTo("12/08/1990");
        assertThat(ccdCallbackResponse.getData().getBenefitCode()).isEqualTo("051");
        assertThat(ccdCallbackResponse.getData().getIssueCode()).isEqualTo("DD");
        assertThat(ccdCallbackResponse.getData().getCaseCode()).isEqualTo("051DD");
        assertThat(ccdCallbackResponse.getData().getDwpRegionalCentre()).isEqualTo("Balham");
    }

    @Test
    public void should_return_exc_data_and_errors_in_callback_when_validation_endpoint_fails_with_errors() {
        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        when(caseValidator.validateValidationRecord(any()))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("NI Number is invalid"))
                .build());

        // when
        PreSubmitCallbackResponse<SscsCaseData> ccdCallbackResponse = invokeValidationCallbackHandler(caseDetails.getCaseData());

        // then
        assertThat(ccdCallbackResponse.getErrors()).containsOnly("NI Number is invalid");
        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(0);
    }

    @Test
    public void should_return_exc_data_and_errors_in_callback_when_validation_endpoint_fails_with_warnings() {
        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        when(caseValidator.validateValidationRecord(any()))
            .thenReturn(CaseResponse.builder()
                .warnings(ImmutableList.of("Postcode is invalid"))
                .build());

        // when
        PreSubmitCallbackResponse<SscsCaseData> ccdCallbackResponse = invokeValidationCallbackHandler(caseDetails.getCaseData());

        // then
        assertThat(ccdCallbackResponse.getErrors()).containsOnly("Postcode is invalid");
        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(0);
    }

    private void assertExceptionDataEntries(SuccessfulTransformationResponse successfulTransformationResponse) {
        assertThat(successfulTransformationResponse.getCaseCreationDetails().getCaseTypeId().equals("Benefit"));
        assertThat(successfulTransformationResponse.getCaseCreationDetails().getEventId().equals("validAppealCreated"));
        assertThat(successfulTransformationResponse.getCaseCreationDetails().getCaseData()).isEqualTo(transformedCase);
    }

    private SuccessfulTransformationResponse invokeCallbackHandler(ExceptionRecord exceptionRecord) {
        return ccdCallbackHandler.handle(exceptionRecord);
    }

    private PreSubmitCallbackResponse<SscsCaseData> invokeValidationCallbackHandler(SscsCaseData caseDetails) {
        uk.gov.hmcts.reform.sscs.ccd.domain.CaseDetails<SscsCaseData> c = new uk.gov.hmcts.reform.sscs.ccd.domain.CaseDetails<>(123L, "sscs",
            State.INTERLOCUTORY_REVIEW_STATE, caseDetails, LocalDateTime.now());

        return ccdCallbackHandler.handleValidationAndUpdate(
            new Callback<>(c, Optional.empty(), EventType.VALID_APPEAL, false), idamTokens);
    }
}
