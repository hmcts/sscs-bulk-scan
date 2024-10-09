package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertFalse;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.CHILD_MAINTENANCE_NUMBER;

import java.util.*;
import junitparams.JUnitParamsRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.OcrDataField;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.validation.sscscasedata.AddressValidator;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@RunWith(JUnitParamsRunner.class)
public class ExceptionRecordValidatorTest {

    private static final String VALID_POSTCODE = "CM13 0GD";
    private final List<String> titles = new ArrayList<>();
    private final Map<String, Object> ocrCaseData = new HashMap<>();
    private final List<OcrDataField> ocrList = new ArrayList<>();
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    RegionalProcessingCenterService regionalProcessingCenterService;
    @Mock
    SscsJsonExtractor sscsJsonExtractor;
    DwpAddressLookupService dwpAddressLookupService;
    @Mock
    private AddressValidator addressValidator;
    private ExceptionRecordValidator validator;
    private MrnDetails defaultMrnDetails;
    private CaseResponse transformResponse;
    private CaseDetails caseDetails;
    private ScannedData scannedData;
    private ExceptionRecord exceptionRecord;

    private ExceptionRecord exceptionRecordSscs1U;
    private ExceptionRecord exceptionRecordSscs2;
    private ExceptionRecord exceptionRecordSscs5;

    @Before
    public void setup() {
        dwpAddressLookupService = new DwpAddressLookupService();
        scannedData = mock(ScannedData.class);
        caseDetails = mock(CaseDetails.class);
        validator = new ExceptionRecordValidator(sscsJsonExtractor, dwpAddressLookupService, addressValidator, titles);
        transformResponse = CaseResponse.builder().build();

        defaultMrnDetails = MrnDetails.builder().dwpIssuingOffice("2").mrnDate("2018-12-09").build();

        titles.add("Mr");
        titles.add("Mrs");
        ReflectionTestUtils.setField(validator, "titles", titles);
        ocrCaseData.put("person1_address_line4", "county");
        ocrCaseData.put("person2_address_line4", "county");
        ocrCaseData.put("representative_address_line4", "county");
        ocrCaseData.put("office", "2");

        given(regionalProcessingCenterService.getByPostcode(VALID_POSTCODE))
            .willReturn(RegionalProcessingCenter.builder().address1("Address 1").name("Liverpool").build());

        exceptionRecord = ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS1PE.getId()).build();
        given(sscsJsonExtractor.extractJson(exceptionRecord)).willReturn(scannedData);
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseData);

        exceptionRecordSscs1U =
            ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS1U.getId()).build();
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs1U)).willReturn(scannedData);

        exceptionRecordSscs2 =
            ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS2.getId()).build();
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs2)).willReturn(scannedData);
        ocrCaseData.put("person1_child_maintenance_number", CHILD_MAINTENANCE_NUMBER);

        exceptionRecordSscs5 =
            ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS5.getId()).build();
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs5)).willReturn(scannedData);
    }

    @Test
    public void shouldReturnValidCaseResponseWhenNoWarningsAndErrorsExist() {
        given(sscsJsonExtractor.extractJson(exceptionRecord)).willReturn(scannedData);
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseData);

        CaseResponse caseResponse = validator.validateExceptionRecord(transformResponse, exceptionRecord, new HashMap<>(), false);

        assertNotNull(caseResponse);
        assertTrue(caseResponse.getErrors().isEmpty());
        assertTrue(caseResponse.getWarnings().isEmpty());
        assertEquals("VALID", caseResponse.getStatus());
    }

    @Test
    public void shouldCombineWarningsWhenWarningsExistInTransformResponse() {
        List<String> existingWarnings = Collections.singletonList("Warning 1");
        transformResponse = CaseResponse.builder().warnings(existingWarnings).build();

        CaseResponse caseResponse = validator.validateExceptionRecord(transformResponse, exceptionRecord, new HashMap<>(), true);

        assertNotNull(caseResponse);
        assertFalse(caseResponse.getWarnings().isEmpty());
        assertEquals(1, caseResponse.getWarnings().size());
        assertEquals("Warning 1", caseResponse.getWarnings().get(0));
    }

    @Test
    public void shouldCombineWarningsAndErrorsWhenCombineWarningsIsTrue() {
        transformResponse = CaseResponse.builder().build();
        List<String> errors = Collections.singletonList("Error 1");

        given(sscsJsonExtractor.extractJson(exceptionRecord)).willReturn(scannedData);
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseData);
        ReflectionTestUtils.invokeMethod(validator, "combineWarnings", errors, new ArrayList<>());

        CaseResponse caseResponse = validator.validateExceptionRecord(transformResponse, exceptionRecord, new HashMap<>(), true);

        assertNotNull(caseResponse);
        assertTrue(caseResponse.getErrors().isEmpty()); // Errors should be cleared
        assertFalse(caseResponse.getWarnings().isEmpty());
        assertEquals(1, caseResponse.getWarnings().size());
        assertEquals("Error 1", caseResponse.getWarnings().get(0));  // Errors should be moved to warnings
    }

    @Test
    public void shouldNotCombineWarningsWhenCombineWarningsIsFalse() {
        List<String> existingWarnings = Collections.singletonList("Warning 1");
        transformResponse = CaseResponse.builder().warnings(existingWarnings).build();

        CaseResponse caseResponse = validator.validateExceptionRecord(transformResponse, exceptionRecord, new HashMap<>(), false);

        assertNotNull(caseResponse);
        assertFalse(caseResponse.getWarnings().isEmpty());
        assertEquals(1, caseResponse.getWarnings().size());
        assertEquals("Warning 1", caseResponse.getWarnings().get(0));
    }

    @Test
    public void shouldReturnErrorsWhenExceptionRecordContainsErrors() {
        List<String> errors = Collections.singletonList("Error 1");
        given(sscsJsonExtractor.extractJson(exceptionRecord)).willReturn(scannedData);
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseData);

        ReflectionTestUtils.invokeMethod(validator, "validateAppeal", ocrCaseData, new HashMap<>(), false, false, true);

        CaseResponse caseResponse = validator.validateExceptionRecord(transformResponse, exceptionRecord, new HashMap<>(), false);

        assertNotNull(caseResponse);
        assertFalse(caseResponse.getErrors().isEmpty());
        assertEquals(1, caseResponse.getErrors().size());
        assertEquals("Error 1", caseResponse.getErrors().get(0));
        assertEquals("INVALID", caseResponse.getStatus()); // Errors should result in "INVALID" status
    }
}
