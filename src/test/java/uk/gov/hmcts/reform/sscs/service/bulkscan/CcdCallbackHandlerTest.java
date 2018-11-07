package uk.gov.hmcts.reform.sscs.service.bulkscan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.*;

import com.google.common.collect.ImmutableList;
import java.util.AbstractMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;

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

    @Before
    public void setUp() {
        ccdCallbackHandler = new CcdCallbackHandler(caseTransformer, caseValidator, caseDataHandler);
    }


    @Test
    public void should_return_exception_data_with_case_id_and_state_when_transformation_and_validation_are_successful() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDataCreator.exceptionCaseData()))
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
            caseDataCreator.sscsCaseData(),
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID,
            caseDataCreator.exceptionCaseData(),
            null)
        ).thenReturn(AboutToStartOrSubmitCallbackResponse.builder().data(caseDataCreator.exceptionCaseData()).build());

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

        when(caseTransformer.transformExceptionRecordToCase(caseDataCreator.exceptionCaseData()))
            .thenReturn(CaseResponse.builder()
                .errors(ImmutableList.of("Cannot transform Appellant Date of Birth. Please enter valid date"))
                .build()
            );

        // when
        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse =
            (AboutToStartOrSubmitCallbackResponse) invokeCallbackHandler(caseDetails);

        // then
        assertExceptionDataEntries(ccdCallbackResponse);

        assertThat(ccdCallbackResponse.getErrors())
            .containsOnly("Cannot transform Appellant Date of Birth. Please enter valid date");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test
    public void should_return_exc_data_and_errors_in_callback_when_transformation_success_and_validn_fails_with_errors() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDataCreator.exceptionCaseData()))
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
        assertExceptionDataEntries(ccdCallbackResponse);

        assertThat(ccdCallbackResponse.getErrors()).containsOnly("NI Number is invalid");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }


    private void assertExceptionDataEntries(AboutToStartOrSubmitCallbackResponse ccdCallbackResponse) {
        assertThat(ccdCallbackResponse.getData()).contains(
            entry("journeyClassification", "New Application"),
            entry("poBoxJurisdiction", "SSCS"),
            entry("poBox", "SSCSPO"),
            entry("openingDate", "2018-01-11"),
            entry("scanRecords", caseDataCreator.ocrData())
        );
    }

    private CallbackResponse invokeCallbackHandler(CaseDetails caseDetails) {
        return ccdCallbackHandler.handle(
            ExceptionCaseData.builder()
                .caseDetails(caseDetails)
                .eventId("createNewCase")
                .build(),
            TEST_USER_AUTH_TOKEN,
            TEST_SERVICE_AUTH_TOKEN,
            TEST_USER_ID
        );
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }
}
