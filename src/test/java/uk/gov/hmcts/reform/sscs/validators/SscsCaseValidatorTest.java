package uk.gov.hmcts.reform.sscs.validators;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.CHILD_MAINTENANCE_NUMBER;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.OTHER_PARTY_ADDRESS_LINE1;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.OTHER_PARTY_ADDRESS_LINE2;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.OTHER_PARTY_ADDRESS_LINE3;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.OTHER_PARTY_POSTCODE;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.*;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;

import java.util.*;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import junitparams.converters.Nullable;
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
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@RunWith(JUnitParamsRunner.class)
public class SscsCaseValidatorTest {

    private static final String VALID_MOBILE = "07832882849";
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
    private PostcodeValidator postcodeValidator;
    private SscsCaseValidator validator;
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
        validator = new SscsCaseValidator(regionalProcessingCenterService, dwpAddressLookupService, postcodeValidator,
            sscsJsonExtractor, null, false);
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
        given(postcodeValidator.isValid(anyString())).willReturn(true);
        given(postcodeValidator.isValidPostcodeFormat(anyString())).willReturn(true);

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

}
