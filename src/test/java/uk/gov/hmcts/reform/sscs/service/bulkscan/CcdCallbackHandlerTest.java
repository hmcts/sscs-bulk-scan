package uk.gov.hmcts.reform.sscs.service.bulkscan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import com.google.common.collect.ImmutableList;
import java.util.AbstractMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.*;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;
import uk.gov.hmcts.reform.sscs.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.domain.ValidateCaseData;
import uk.gov.hmcts.reform.sscs.handler.SscsRoboticsHandler;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;

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
    private CaseDataHelper caseDataHelper;

    @Mock
    private SscsRoboticsHandler roboticsHandler;

    private SscsDataHelper sscsDataHelper;

    @Before
    public void setUp() {
        sscsDataHelper = new SscsDataHelper(null);
        ccdCallbackHandler = new CcdCallbackHandler(caseTransformer, caseValidator, caseDataHandler, roboticsHandler, sscsDataHelper, caseDataHelper);
        ReflectionTestUtils.setField(ccdCallbackHandler, "sendToDwpFeature", true);
        ReflectionTestUtils.setField(ccdCallbackHandler, "debugJson", false);
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
        when(caseValidator.validate(caseDataCreator.sscsCaseData()))
            .thenReturn(caseValidationResponse);

        // Return case id for successful ccd case creation
        when(caseDataHandler.handle(
            caseValidationResponse,
            false,
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build(),
            null)
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

        when(caseValidator.validate(caseDataCreator.sscsCaseData()))
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
    public void should_return_no_warnings_or_errors_or_data_when_validation_endpoint_is_successful() {

        Appeal appeal = Appeal.builder()
            .appellant(Appellant.builder()
                .name(Name.builder().firstName("Fred").lastName("Ward").build())
                .identity(Identity.builder().nino("JT123456N").dob("12/08/1990").build())
                .build())
            .build();

        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().appeal(appeal).build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        when(caseValidator.validate(any())).thenReturn(caseValidationResponse);

        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeValidationCallbackHandler(caseDetails);

        assertThat(ccdCallbackResponse.getData()).isNull();
        assertThat(ccdCallbackResponse.getErrors()).isNull();
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
        verifyZeroInteractions(roboticsHandler);
    }

    @Test
    public void should_return_no_warnings_or_errors_or_data_when_validation_endpoint_is_successful_and_send_to_robotics_when_dwp_feature_false() {
        ReflectionTestUtils.setField(ccdCallbackHandler, "sendToDwpFeature", false);

        Appeal appeal = Appeal.builder()
            .appellant(Appellant.builder()
                .name(Name.builder().firstName("Fred").lastName("Ward").build())
                .identity(Identity.builder().nino("JT123456N").dob("12/08/1990").build())
                .build())
            .build();

        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().appeal(appeal).build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        CaseResponse caseValidationResponse = CaseResponse.builder().build();
        when(caseValidator.validate(any())).thenReturn(caseValidationResponse);

        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeValidationCallbackHandler(caseDetails);

        assertThat(ccdCallbackResponse.getData()).isNull();
        assertThat(ccdCallbackResponse.getErrors()).isNull();
        assertThat(ccdCallbackResponse.getWarnings()).isNull();

        ValidateCaseData v = ValidateCaseData.builder()
            .caseDetails(caseDetails)
            .eventId("validAppeal")
            .build();

        verify(roboticsHandler).handle(any(), eq(1234L));
    }

    @Test
    public void should_return_exc_data_and_errors_in_callback_when_validation_endpoint_fails_with_errors() {
        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        when(caseValidator.validate(any()))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("NI Number is invalid"))
                .build());

        // when
        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeValidationCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getErrors()).containsOnly("NI Number is invalid");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test
    public void should_return_exc_data_and_errors_in_callback_when_validation_endpoint_fails_with_warnings() {
        SscsCaseDetails caseDetails = SscsCaseDetails
            .builder()
            .caseData(SscsCaseData.builder().build())
            .state("ScannedRecordReceived")
            .caseId("1234")
            .build();

        when(caseValidator.validate(any()))
            .thenReturn(CaseResponse.builder()
                .warnings(ImmutableList.of("Postcode is invalid"))
                .build());

        // when
        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeValidationCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getErrors()).containsOnly("Postcode is invalid");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
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

    private CallbackResponse invokeValidationCallbackHandler(SscsCaseDetails caseDetails) {
        return ccdCallbackHandler.handleValidationAndUpdate(
            ValidateCaseData.builder()
                .caseDetails(caseDetails)
                .eventId("validAppeal")
                .build(),
            Token.builder().userAuthToken(TEST_USER_AUTH_TOKEN).serviceAuthToken(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build()
        );
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }
}
