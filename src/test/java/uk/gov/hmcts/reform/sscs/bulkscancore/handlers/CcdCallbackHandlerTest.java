package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import com.google.common.collect.ImmutableList;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.*;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.PreSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;
import uk.gov.hmcts.reform.sscs.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
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

    private AboutToStartOrSubmitCallbackResponse transformErrorResponse;

    @Captor
    private ArgumentCaptor<AboutToStartOrSubmitCallbackResponse> warningCaptor;

    @Before
    public void setUp() {
        sscsDataHelper = new SscsDataHelper(null, new ArrayList<>(), dwpAddressLookupService);
        ccdCallbackHandler = new CcdCallbackHandler(caseTransformer, caseValidator, caseDataHandler, sscsDataHelper,
            dwpAddressLookupService);
        ReflectionTestUtils.setField(ccdCallbackHandler, "debugJson", false);
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("PIP", "3"))
            .willReturn("Springburn");
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("ESA", "Balham DRT"))
            .willReturn("Balham");
    }

    @Test
    public void should_return_exception_data_with_case_id_and_state_when_transformation_and_validation_are_successful() {
        ReflectionTestUtils.setField(ccdCallbackHandler, "debugJson", true);
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDetails))
            .thenReturn(CaseResponse.builder()
                .transformedCase(caseDataCreator.sscsCaseData())
                .build()
            );

        // No errors and warnings are populated hence validation would be successful
        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        when(caseValidator.validate(transformErrorResponse, caseDetails, caseDataCreator.sscsCaseData()))
            .thenReturn(caseValidationResponse);

        // Return case id for successful ccd case creation
        when(caseDataHandler.handle(
            any(ExceptionCaseData.class),
            eq(caseValidationResponse),
            eq(false),
            eq(Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build()),
            eq(null))
        ).thenReturn(HandlerResponse.builder().state("DocUpdated").build());

        // when
        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeCallbackHandler(caseDetails);

        assertExceptionDataEntries(ccdCallbackResponse);
        assertThat(ccdCallbackResponse.getErrors()).isNull();
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test
    public void should_return_exception_record_and_errors_in_callback_response_when_transformation_fails() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDetails))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("Cannot transform Appellant Date of Birth. Please enter valid date"))
                .build()
            );

        // when
        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getErrors())
            .containsOnly("Cannot transform Appellant Date of Birth. Please enter valid date");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test
    public void should_return_exc_data_and_errors_in_callback_when_transformation_success_and_validation_fails_with_errors() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDetails))
            .thenReturn(CaseResponse.builder()
                .transformedCase(caseDataCreator.sscsCaseData())
                .build()
            );

        when(caseValidator.validate(transformErrorResponse, caseDetails, caseDataCreator.sscsCaseData()))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("NI Number is invalid"))
                .build());

        // when
        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getErrors()).containsOnly("NI Number is invalid");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
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
        when(caseValidator.validate(eq(transformErrorResponse), isNull(), any())).thenReturn(caseValidationResponse);

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
        when(caseValidator.validate(eq(transformErrorResponse), isNull(), any())).thenReturn(caseValidationResponse);

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

        when(caseValidator.validate(eq(transformErrorResponse), isNull(), any()))
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

        when(caseValidator.validate(eq(transformErrorResponse), isNull(), any()))
            .thenReturn(CaseResponse.builder()
                .warnings(ImmutableList.of("Postcode is invalid"))
                .build());

        // when
        PreSubmitCallbackResponse<SscsCaseData> ccdCallbackResponse = invokeValidationCallbackHandler(caseDetails.getCaseData());

        // then
        assertThat(ccdCallbackResponse.getErrors()).containsOnly("Postcode is invalid");
        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(0);
    }

    @Test
    public void givenAWarningInTransformationServiceAndAnotherWarningInValidationService_thenShowBothWarnings() {
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        List<String> warnings = new ArrayList<>();
        warnings.add("First warning");

        CaseResponse caseResponse = CaseResponse.builder()
            .transformedCase(caseDataCreator.sscsCaseData())
            .warnings(warnings)
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDetails))
            .thenReturn(caseResponse);

        List<String> warnings2 = new ArrayList<>();
        warnings2.add("First warning");
        warnings2.add("Second warning");

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings2).build();
        when(caseValidator.validate(any(), eq(caseDetails), eq(caseDataCreator.sscsCaseData())))
            .thenReturn(caseValidationResponse);

        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeCallbackHandler(caseDetails);

        verify(caseValidator).validate(warningCaptor.capture(), eq(caseDetails), eq(caseDataCreator.sscsCaseData()));

        assertThat(warningCaptor.getAllValues().get(0).getWarnings().size()).isEqualTo(1);
        assertThat(warningCaptor.getAllValues().get(0).getWarnings().get(0)).isEqualTo("First warning");

        assertThat(ccdCallbackResponse.getWarnings().size()).isEqualTo(2);
    }

    private void assertExceptionDataEntries(AboutToStartOrSubmitCallbackResponse ccdCallbackResponse) {
        assertThat(ccdCallbackResponse.getData()).contains(
            entry("journeyClassification", "New Application"),
            entry("poBoxJurisdiction", "SSCS"),
            entry("poBox", "SSCSPO"),
            entry("openingDate", "2018-01-11"),
            entry("scannedDocuments", caseDataCreator.ocrData())
        );
    }

    private CallbackResponse invokeCallbackHandler(CaseDetails caseDetails) {
        return ccdCallbackHandler.handle(
            ExceptionCaseData.builder()
                .caseDetails(caseDetails)
                .eventId("createNewCase")
                .build(),
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build()
        );
    }

    private PreSubmitCallbackResponse<SscsCaseData> invokeValidationCallbackHandler(SscsCaseData caseDetails) {
        uk.gov.hmcts.reform.sscs.ccd.domain.CaseDetails<SscsCaseData> c = new uk.gov.hmcts.reform.sscs.ccd.domain.CaseDetails<>(123L, "sscs",
            State.INTERLOCUTORY_REVIEW_STATE, caseDetails, LocalDateTime.now());

        return ccdCallbackHandler.handleValidationAndUpdate(
            new Callback<>(c, Optional.empty(), EventType.VALID_APPEAL, false)
        );
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }
}
