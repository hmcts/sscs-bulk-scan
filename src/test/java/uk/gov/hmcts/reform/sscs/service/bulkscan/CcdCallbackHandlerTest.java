package uk.gov.hmcts.reform.sscs.service.bulkscan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import java.util.AbstractMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.springframework.test.context.junit4.SpringRunner;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.*;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.common.SampleCaseDataCreator;
import uk.gov.hmcts.reform.sscs.common.TestHelper;

@RunWith(SpringRunner.class)
public class CcdCallbackHandlerTest {

    private CcdCallbackHandler ccdCallbackHandler;

    private SampleCaseDataCreator caseDataCreator = new SampleCaseDataCreator();

    @Mock
    private CaseTransformer caseTransformer;

    @Mock
    private CaseValidator caseValidator;

    @Before
    public void setUp() {
        ccdCallbackHandler = new CcdCallbackHandler(caseTransformer, caseValidator);
    }

    @Test
    public void should_and_return_case_data_when_transformation_and_validation_is_successful() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDataCreator.exceptionCaseData()))
            .thenReturn(CaseTransformationResponse.builder()
                .transformedCase(caseDataCreator.sscsCaseData())
                .build()
            );

        // No errors and warnings are populated hence validation would be successful
        when(caseValidator.validate(caseDataCreator.sscsCaseData()))
            .thenReturn(CaseValidationResponse.builder().build());

        // when
        CcdCallbackResponse ccdCallbackResponse = invokeCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getData())
            .containsExactly(
                entry("generatedEmail", "sscstest@test.com"),
                entry("caseReference", "123456789"),
                entry("caseCreated", "2018-01-11"),
                entry("generatedNino", "SR11111"),
                entry("generatedSurname", "Smith")
            );

        assertThat(ccdCallbackResponse.getErrors()).isNull();
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test
    public void should_handle_and_return_exception_data_and_errors_when_transformation_fails() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDataCreator.exceptionCaseData()))
            .thenReturn(CaseTransformationResponse.builder()
                .errors(ImmutableList.of("Cannot transform Appellant Date of Birth. Please enter valid date"))
                .build()
            );

        // when
        CcdCallbackResponse ccdCallbackResponse = invokeCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getData()).containsAllEntriesOf(caseDataCreator.exceptionCaseData());

        assertThat(ccdCallbackResponse.getErrors())
            .containsOnly("Cannot transform Appellant Date of Birth. Please enter valid date");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    @Test
    public void should_handle_and_return_exception_data_and_errors_when_transformation_success_and_validn_fails_with_errors() {
        // given
        CaseDetails caseDetails = CaseDetails
            .builder()
            .caseData(caseDataCreator.exceptionCaseData())
            .state("ScannedRecordReceived")
            .build();

        when(caseTransformer.transformExceptionRecordToCase(caseDataCreator.exceptionCaseData()))
            .thenReturn(CaseTransformationResponse.builder()
                .transformedCase(caseDataCreator.sscsCaseData())
                .build()
            );


        when(caseValidator.validate(caseDataCreator.sscsCaseData()))
            .thenReturn(CaseValidationResponse.builder()
                .errors(ImmutableList.of("NI Number is invalid"))
                .build());

        // when
        CcdCallbackResponse ccdCallbackResponse = invokeCallbackHandler(caseDetails);

        // then
        assertThat(ccdCallbackResponse.getData()).containsAllEntriesOf(caseDataCreator.exceptionCaseData());

        assertThat(ccdCallbackResponse.getErrors()).containsOnly("NI Number is invalid");
        assertThat(ccdCallbackResponse.getWarnings()).isNull();
    }

    private CcdCallbackResponse invokeCallbackHandler(CaseDetails caseDetails) {
        return ccdCallbackHandler.handle(
            ExceptionCaseData.builder()
                .caseDetails(caseDetails)
                .eventId("createNewCase")
                .build(),
            TestHelper.TEST_USER_AUTH_TOKEN,
            TestHelper.TEST_SERVICE_AUTH_TOKEN,
            TestHelper.TEST_USER_ID
        );
    }

    private static <K, V> Map.Entry<K, V> entry(K key, V value) {
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }
}
