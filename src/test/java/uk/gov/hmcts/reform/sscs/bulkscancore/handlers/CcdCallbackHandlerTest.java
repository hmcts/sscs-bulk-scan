package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.EPIMMS_ID;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.PROCESSING_VENUE;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.REGION_ID;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.TEST_SERVICE_AUTH_TOKEN;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.TEST_USER_AUTH_TOKEN;
import static uk.gov.hmcts.reform.sscs.common.TestHelper.TEST_USER_ID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.junit4.rules.SpringClassRule;
import org.springframework.test.context.junit4.rules.SpringMethodRule;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.BenefitType;
import uk.gov.hmcts.reform.sscs.ccd.domain.CaseManagementLocation;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.ccd.validation.sscscasedata.AppealPostcodeHelper;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.sscs.exceptions.InvalidExceptionRecordException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.service.AirLookupService;
import uk.gov.hmcts.reform.sscs.service.CaseManagementLocationService;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.validators.ExceptionRecordValidator;

@RunWith(JUnitParamsRunner.class)
public class CcdCallbackHandlerTest {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @ClassRule
    public static final SpringClassRule springClassRule = new SpringClassRule();

    @Rule
    public final SpringMethodRule springMethodRule = new SpringMethodRule();

    @Mock
    private CaseTransformer caseTransformer;

    @Mock
    private ExceptionRecordValidator caseValidator;

    @Mock
    private DwpAddressLookupService dwpAddressLookupService;

    @Mock
    private AirLookupService airLookupService;

    @Mock
    private AppealPostcodeHelper appealPostcodeHelper;

    @Mock
    private CaseManagementLocationService caseManagementLocationService;

    private CcdCallbackHandler ccdCallbackHandler;

    @Captor
    private ArgumentCaptor<CaseResponse> warningCaptor;

    private Map<String, Object> transformedCase;

    private IdamTokens idamTokens;

    private ListAppender<ILoggingEvent> listAppender;

    @Before
    public void setUp() {
        Logger fooLogger = (Logger) LoggerFactory.getLogger(CcdCallbackHandler.class);

        listAppender = new ListAppender<>();
        listAppender.start();
        fooLogger.addAppender(listAppender);

        SscsDataHelper sscsDataHelper =
            new SscsDataHelper(
                new CaseEvent(null, "validAppealCreated", null, null),
                airLookupService,
                dwpAddressLookupService,
                true);

        ccdCallbackHandler =
            new CcdCallbackHandler(
                sscsDataHelper,
                caseValidator,
                caseTransformer);

        idamTokens = IdamTokens.builder().idamOauth2Token(TEST_USER_AUTH_TOKEN).serviceAuthorization(TEST_SERVICE_AUTH_TOKEN).userId(TEST_USER_ID).build();

        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("PIP", "3")).willReturn("Springburn");
        given(dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice("ESA", "Balham DRT")).willReturn("Balham");

        given(airLookupService.lookupAirVenueNameByPostCode(anyString(), any(BenefitType.class))).willReturn(PROCESSING_VENUE);
        given(caseManagementLocationService.retrieveCaseManagementLocation(eq(PROCESSING_VENUE), any())).willReturn(
            Optional.of(CaseManagementLocation.builder().baseLocation(EPIMMS_ID).region(REGION_ID).build()));

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
        try {
            invokeCallbackHandler(exceptionRecord);
        } catch (InvalidExceptionRecordException e) {
            assertLogContains("Errors found while validating exception record id null - NI Number is invalid");
            throw e;
        }
    }

    @Test(expected = InvalidExceptionRecordException.class)
    public void should_return_exc_data_and_warning_in_callback_when_is_automated_process_true_and_transformation_success_and_validation_fails_with_warning() {
        ExceptionRecord exceptionRecord = ExceptionRecord.builder().isAutomatedProcess(true).build();
        ImmutableList<String> warningList = ImmutableList.of("office is missing");

        CaseResponse response = CaseResponse.builder().warnings(warningList).transformedCase(transformedCase).build();

        when(caseTransformer.transformExceptionRecord(exceptionRecord, false)).thenReturn(response);

        when(caseValidator.validateExceptionRecord(response, exceptionRecord, transformedCase, false))
            .thenReturn(CaseResponse.builder()
                .warnings(warningList)
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

        assertThat(warningCaptor.getAllValues().get(0).getWarnings()).hasSize(1);
        assertThat(warningCaptor.getAllValues().get(0).getWarnings().get(0)).isEqualTo("First warning");

        assertThat(ccdCallbackResponse.getWarnings()).hasSize(2);
    }

    @Test(expected = InvalidExceptionRecordException.class)
    public void givenAWarningInValidationServiceWhenIsAutomatedProcessIsTrue_thenShowWarnings() {
        ExceptionRecord exceptionRecord = ExceptionRecord.builder().isAutomatedProcess(true).build();

        List<String> warnings = new ArrayList<>();

        when(caseTransformer.transformExceptionRecord(exceptionRecord, false))
            .thenReturn(CaseResponse.builder()
                .transformedCase(transformedCase)
                .warnings(warnings)
                .build());

        List<String> warnings2 = new ArrayList<>();
        warnings2.add("Second warning");

        CaseResponse caseValidationResponse = CaseResponse.builder().warnings(warnings2).transformedCase(transformedCase).build();

        when(caseValidator.validateExceptionRecord(any(), eq(exceptionRecord), eq(transformedCase), eq(false)))
            .thenReturn(caseValidationResponse);

        invokeCallbackHandler(exceptionRecord);
    }

    private void assertLogContains(final String logMessage) {
        assertThat(listAppender.list.stream().map(ILoggingEvent::getFormattedMessage)).contains(logMessage);
    }

    private void assertExceptionDataEntries(SuccessfulTransformationResponse successfulTransformationResponse) {
        assertThat(successfulTransformationResponse.getCaseCreationDetails().getCaseTypeId()).isEqualTo("Benefit");
        assertThat(successfulTransformationResponse.getCaseCreationDetails().getEventId()).isEqualTo("validAppealCreated");
        assertThat(successfulTransformationResponse.getCaseCreationDetails().getCaseData()).isEqualTo(transformedCase);
    }

    private SuccessfulTransformationResponse invokeCallbackHandler(ExceptionRecord exceptionRecord) {
        return ccdCallbackHandler.handle(exceptionRecord);
    }
}
