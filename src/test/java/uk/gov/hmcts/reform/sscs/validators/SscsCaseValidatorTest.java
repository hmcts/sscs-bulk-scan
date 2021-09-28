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

    @Before
    public void setup() {
        dwpAddressLookupService = new DwpAddressLookupService();
        scannedData = mock(ScannedData.class);
        caseDetails = mock(CaseDetails.class);
        validator = new SscsCaseValidator(regionalProcessingCenterService, dwpAddressLookupService, postcodeValidator,
            sscsJsonExtractor, false);
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
        ocrCaseData.put("person1_child_maintenance_number", CHILD_MAINTENANCE_NUMBER);
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs2)).willReturn(scannedData);
    }

    @Test
    @Parameters({"ESA", "JSA", "PIP", "DLA", "attendanceAllowance", "industrialInjuriesDisablement",
        "socialFund", "incomeSupport", "industrialDeathBenefit", "pensionCredit", "retirementPension"})
    public void givenAnAppealContainsAnInvalidOfficeForBenefitTypeOtherNotAutoOffice_thenAddAWarning(
        String benefitShortName) {
        defaultMrnDetails.setDwpIssuingOffice("Invalid Test Office");

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(benefitShortName, buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        assertEquals("office is invalid", response.getWarnings().get(0));
    }

    @Test
    @Parameters({"carersAllowance", "bereavementBenefit", "maternityAllowance", "bereavementSupportPaymentScheme"})
    public void givenAnAppealContainsAnInvalidOfficeForBenefitTypeOtherAutoOffice_thenDoNotAddAWarning(
        String benefitShortName) {
        defaultMrnDetails.setDwpIssuingOffice("Invalid Test Office");

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(benefitShortName, buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAnInvalidOfficeForBenefitTypeUC_ucOfficeFeatureActive_thenAddAWarning() {
        defaultMrnDetails.setDwpIssuingOffice("Invalid Test Office");
        validator.setUcOfficeFeatureActive(true);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(UC.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        assertEquals("office is invalid", response.getWarnings().get(0));
    }

    @Test
    //TODO: Invalid when ucOfficeFeatureActive fully enabled, to be removed then.
    public void givenAnAppealContainsAnInvalidOfficeForBenefitTypeUC_thenDoNotAddAWarning() {
        defaultMrnDetails.setDwpIssuingOffice("Invalid Test Office");
        validator.setUcOfficeFeatureActive(false);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(UC.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    @Parameters({"The Pension Service 11", "Recovery from Estates"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeAttendanceAllowance_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse,
                exceptionRecord,
                buildMinimumAppealDataWithBenefitTypeAndFormType(ATTENDANCE_ALLOWANCE.getShortName(),
                    buildAppellant(false),
                    true, FormType.SSCS1U),
                false);

        String assertionMessage = "Asserting Benefit: Attendance Allowance with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Disability Benefit Centre 4", "The Pension Service 11", "Recovery from Estates"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeDla_thenDoNotAddAWarning(String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(DLA.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: DLA with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Worthing DRT", "Birkenhead DRT", "Recovery from Estates", "Inverness DRT"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeIncomeSupport_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(INCOME_SUPPORT.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Income Support with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Barrow IIDB Centre", "Barnsley Benefit Centre"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeIidb_thenDoNotAddAWarning(String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(IIDB.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: IIDB with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Worthing DRT", "Birkenhead DRT", "Recovery from Estates", "Inverness DRT"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeJsa_thenDoNotAddAWarning(String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(JSA.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: JSA with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"St Helens Sure Start Maternity Grant", "Funeral Payment Dispute Resolution Team",
        "Pensions Dispute Resolution Team"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeSocialFund_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(SOCIAL_FUND.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Social Fund with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Barrow IIDB Centre", "Barnsley Benefit Centre"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeIndustrialDeathBenefit_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(INDUSTRIAL_DEATH_BENEFIT.getShortName(),
                buildAppellant(false), true, FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Industrial Death Benefit with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Pensions Dispute Resolution Team", "Recovery from Estates"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypePensionCredit_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(PENSION_CREDIT.getShortName(), buildAppellant(false), true,
                FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Pension Credit with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Pensions Dispute Resolution Team", "Recovery from Estates"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeRetirementPension_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(RETIREMENT_PENSION.getShortName(), buildAppellant(false),
                true, FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Retirement Pension with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Pensions Dispute Resolution Team"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeBereavementBenefit_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(BEREAVEMENT_BENEFIT.getShortName(), buildAppellant(false),
                true, FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Bereavement Benefit with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Carer’s Allowance Dispute Resolution Team"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeCarersAllowance_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(CARERS_ALLOWANCE.getShortName(), buildAppellant(false),
                true, FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Carers Allowance with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Walsall Benefit Centre"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeMaternityAllowance_thenDoNotAddAWarning(
        String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(MATERNITY_ALLOWANCE.getShortName(), buildAppellant(false),
                true, FormType.SSCS1U),
            false);

        String assertionMessage = "Asserting Benefit: Maternity Allowance with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    @Parameters({"Pensions Dispute Resolution Team"})
    public void givenAnAppealContainsAValidOfficeForBenefitTypeBsps_thenDoNotAddAWarning(String dwpIssuingOffice) {
        defaultMrnDetails.setDwpIssuingOffice(dwpIssuingOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(BEREAVEMENT_SUPPORT_PAYMENT_SCHEME.getShortName(),
                buildAppellant(false),
                true, FormType.SSCS1U),
            false);

        String assertionMessage =
            "Asserting Benefit: Bereavement Support Payment Scheme with Office: " + dwpIssuingOffice;
        assertEquals(assertionMessage, 0, response.getWarnings().size());
        assertEquals(assertionMessage, 0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantIsEmpty_thenAddAWarning() {
        Map<String, Object> ocrCaseDataEmptyOffice = new HashMap<>();
        ocrCaseDataEmptyOffice.put("person1_address_line4", "county");
        ocrCaseDataEmptyOffice.put("person2_address_line4", "county");
        ocrCaseDataEmptyOffice.put("representative_address_line4", "county");
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseDataEmptyOffice);
        Map<String, Object> pairs = new HashMap<>();
        pairs.put("appeal", Appeal.builder().hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);
        pairs.put("formType", FormType.SSCS1);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord, pairs, false);

        assertThat(response.getWarnings())
            .containsOnly(
                "person1_title is empty",
                "person1_first_name is empty",
                "person1_last_name is empty",
                "person1_address_line1 is empty",
                "person1_address_line3 is empty",
                "person1_address_line4 is empty",
                "person1_postcode is empty",
                "person1_nino is empty",
                "mrn_date is empty",
                "office is empty",
                "benefit_type_description is empty");
    }

    @Test
    public void givenAnAppellantWithNoName_thenAddWarnings() {
        Map<String, Object> pairs = new HashMap<>();

        pairs.put("appeal", Appeal.builder().appellant(Appellant.builder().address(
            Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode(VALID_POSTCODE).build())
            .identity(Identity.builder().nino("BB000000B").build()).build())
            .benefitType(BenefitType.builder().code(PIP.name()).build())
            .mrnDetails(defaultMrnDetails)
            .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord, pairs, false);

        assertThat(response.getWarnings())
            .containsOnly("person1_title is empty",
                "person1_first_name is empty",
                "person1_last_name is empty");
    }

    @Test
    public void givenAnAppellantWithHearingTypeOralAndNoHearingSubType_thenAddWarnings() {
        Map<String, Object> pairs = new HashMap<>();

        pairs.put("appeal", Appeal.builder()
            .appellant(Appellant.builder().name(Name.builder().firstName("Harry").lastName("Kane").build())
                .address(Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode(VALID_POSTCODE)
                    .build())
                .identity(Identity.builder().nino("BB000000B").build()).build())
            .benefitType(BenefitType.builder().code(PIP.name()).build())
            .mrnDetails(defaultMrnDetails)
            .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);
        pairs.put("formType", FormType.SSCS1PEU);

        ocrCaseData.put(HEARING_TYPE_TELEPHONE_LITERAL, "");
        ocrCaseData.put(HEARING_TYPE_VIDEO_LITERAL, "");
        ocrCaseData.put(HEARING_TYPE_FACE_TO_FACE_LITERAL, "");

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord, pairs, false);

        assertThat(response.getWarnings())
            .containsOnly("person1_title is empty",
                "hearing_type_telephone, hearing_type_video and hearing_type_face_to_face are empty. At least one must be populated");
    }

    @Test
    public void givenAnAppellantWithNoNameAndEmptyAppointeeDetails_thenAddWarnings() {
        Map<String, Object> pairs = new HashMap<>();

        Appointee appointee = Appointee.builder()
            .address(Address.builder().build())
            .name(Name.builder().build())
            .contact(Contact.builder().build())
            .identity(Identity.builder().build())
            .build();

        pairs.put("appeal", Appeal.builder().appellant(Appellant.builder()
            .appointee(appointee)
            .address(
                Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode(VALID_POSTCODE).build())
            .identity(Identity.builder().nino("BB000000B").build()).build())
            .benefitType(BenefitType.builder().code(PIP.name()).build())
            .mrnDetails(defaultMrnDetails)
            .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord, pairs, false);

        assertThat(response.getWarnings())
            .containsOnly(
                "person1_title is empty",
                "person1_first_name is empty",
                "person1_last_name is empty");
    }

    @Test
    public void givenAnAppellantWithNoAddress_thenAddWarnings() {
        Map<String, Object> pairs = new HashMap<>();

        pairs.put("appeal", Appeal.builder().appellant(Appellant.builder().name(
            Name.builder().firstName("Harry").lastName("Kane").build())
            .identity(Identity.builder().nino("BB000000B").build()).build())
            .benefitType(BenefitType.builder().code(PIP.name()).build())
            .mrnDetails(defaultMrnDetails)
            .hearingType(HEARING_TYPE_ORAL)
            .hearingSubtype(HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build()).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord, pairs, false);

        assertThat(response.getWarnings())
            .containsOnly(
                "person1_title is empty",
                "person1_address_line1 is empty",
                "person1_address_line3 is empty",
                "person1_address_line4 is empty",
                "person1_postcode is empty");
    }

    @Test
    public void givenAnAppellantDoesNotContainATitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_title is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidTitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Bla");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_title is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitleWithFullStop_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Mr.");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitleLowercase_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("mr");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitle_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Mr");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setFirstName(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setLastName(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine1(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainValidAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine1("[my house");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line1 has invalid characters at the beginning", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainATownAndContainALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2("101 Street");
        appellant.getAddress().setTown(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidTownAndContainALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2("101 Street");
        appellant.getAddress().setTown("@invalid");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line3 has invalid characters at the beginning", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainATownAndALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setTown(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line2 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidTownAndALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setTown("@invalid");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line2 has invalid characters at the beginning", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainACountyAndContainALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2("101 Street");
        appellant.getAddress().setCounty(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidCountyAndContainALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2("101 Street");
        appellant.getAddress().setCounty("(Bad County");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line4 has invalid characters at the beginning", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainACountyAndALine2_thenAddAWarning() {
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setCounty(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidCountyAndALine2_thenAddAWarning() {
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setCounty("£bad County");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line3 has invalid characters at the beginning", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantContainsAPlaceholderCountyAndALine2_thenNoAWarning() {
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setCounty(".");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAnAppellantDoesNotContainAPostcode_thenAddAWarningAndDoNotAddRegionalProcessingCenter() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
        verifyNoInteractions(regionalProcessingCenterService);
    }

    @Test
    public void givenAnAppellantContainsPostcodeWithNoRegionalProcessingCenter_thenDoNotAddRegionalProcessingCenter() {
        Appellant appellant = buildAppellant(false);
        given(regionalProcessingCenterService.getByPostcode(VALID_POSTCODE)).willReturn(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertNull(response.getTransformedCase().get("regionalProcessingCenter"));
        assertNull(response.getTransformedCase().get("region"));
        assertEquals("person1_postcode is not a postcode that maps to a regional processing center",
            response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointee_thenRegionalProcessingCenterIsAlwaysFromTheAppellantsPostcode() {
        Appellant appellant = buildAppellant(true);
        RegionalProcessingCenter rpc = RegionalProcessingCenter.builder().name("person2_postcode").build();
        given(regionalProcessingCenterService.getByPostcode(appellant.getAddress().getPostcode())).willReturn(rpc);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals(rpc, response.getTransformedCase().get("regionalProcessingCenter"));
        assertEquals(rpc.getName(), response.getTransformedCase().get("region"));
        assertTrue(response.getWarnings().size() == 0);
    }

    @Test
    public void givenAnAppellantDoesNotContainANino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidNino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino("Bla");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_nino is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesContainANino_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino("BB000000B");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppointeeExistsAndAnAppellantDoesNotContainANino_thenAddAWarningAboutPerson2() {
        Appellant appellant = buildAppellant(true);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person2_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeWithEmptyDetailsAndAnAppellantDoesNotContainANino_thenAddAWarningAboutPerson1() {
        Appellant appellant = buildAppellant(true);
        appellant.getIdentity().setNino(null);
        appellant.getAppointee().setName(null);
        appellant.getAppointee().setAddress(null);
        appellant.getAppointee().setContact(null);
        appellant.getAppointee().setIdentity(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainAnMrnDate_thenAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("2").build(), buildAppellant(false),
                true), false);

        assertEquals("mrn_date is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnMrnDateInFuture_thenAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("2").mrnDate("2148-10-10").build(),
                buildAppellant(false), true), false);

        assertEquals("mrn_date is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnDoesNotContainADwpIssuingOfficeAndOcrDataIsEmpty_thenAddAWarning() {
        Map<String, Object> ocrCaseDataInvalidOffice = new HashMap<>();
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseDataInvalidOffice);
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice(null).build(),
                buildAppellant(false), true), false);

        assertEquals("office is empty", response.getWarnings().get(0));
        assertEquals(1, response.getWarnings().size());
    }

    @Test
    public void givenAnMrnDoesNotContainAValidDwpIssuingOfficeAndOcrDataIsEmpty_thenAddAWarning() {
        Map<String, Object> ocrCaseDataInvalidOffice = new HashMap<>();
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseDataInvalidOffice);
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("Bla").build(),
                buildAppellant(false), true), false);

        assertEquals("office is invalid", response.getWarnings().get(0));
        assertEquals(1, response.getWarnings().size());
    }

    @Test
    public void givenAnMrnDoesNotContainADwpIssuingOffice_thenAddAWarning() {
        given(scannedData.getOcrCaseData()).willReturn(emptyMap());
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice(null).build(),
                buildAppellant(false), true), false);

        assertEquals("office is empty", response.getWarnings().get(0));
        assertEquals(1, response.getWarnings().size());
    }

    @Test
    public void givenAnMrnDoesNotContainAValidDwpIssuingOffice_thenAddAWarning() {
        Map<String, Object> ocrCaseDataInvalidOffice = new HashMap<>();
        ocrCaseDataInvalidOffice.put("office", "Bla");
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseDataInvalidOffice);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice(null).build(),
                buildAppellant(false), true), false);

        assertEquals("office is invalid", response.getWarnings().get(0));
        assertEquals(1, response.getWarnings().size());
    }

    @Test
    public void givenAnMrnDoesContainValidUpperCaseDwpIssuingOffice_thenNoWarning() {
        given(scannedData.getOcrCaseData()).willReturn(emptyMap());
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(
                MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("BALHAM DRT").build(),
                buildAppellant(false), true), false);

        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void givenAnMrnDoesContainValidCapitaliseDwpIssuingOffice_thenNoWarning() {
        given(scannedData.getOcrCaseData()).willReturn(emptyMap());
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithMrn(
                MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("Balham DRT").build(),
                buildAppellant(false), true), false);

        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void givenAnAppealContainsAnAppellantDateOfBirthInFuture_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setDob("2148-10-10");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_dob is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnAppointeeDateOfBirthInFuture_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getIdentity().setDob("2148-10-10");

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_dob is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAHearingExcludedDateInPast_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithExcludedDate("2018-10-10", appellant, true), false);

        assertEquals("hearing_options_exclude_dates is in past", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainABenefitTypeDescription_thenAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType(null, buildAppellant(false), true), false);

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainABenefitTypeOtherForSscs1UForm_thenDoNotAddAWarning() {
        Map<String, Object> caseData = buildMinimumAppealDataWithBenefitType(null, buildAppellant(false), true);
        caseData.put("formType", FormType.SSCS1U);
        CaseResponse response =
            validator.validateExceptionRecord(transformResponse, exceptionRecordSscs1U, caseData, false);

        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void givenAnAppealContainsAnInvalidBenefitTypeDescription_thenAddAnError() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", buildAppellant(false), true), false);

        List<String> benefitNameList = new ArrayList<>();
        for (Benefit be : Benefit.values()) {
            benefitNameList.add(be.getShortName());
        }

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " invalid. Should be one of: " + String.join(", ", benefitNameList),
            response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidLowercaseBenefitTypeDescription_thenDoNotAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType(PIP.name().toLowerCase(), buildAppellant(false), true), false);

        List<String> benefitNameList = new ArrayList<>();
        for (Benefit be : Benefit.values()) {
            benefitNameList.add(be.name());
        }

        assertEquals("PIP", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getCode());
        assertEquals("Personal Independence Payment",
            ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getDescription());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAValidBenefitTypeDescription_thenDoNotAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false), true), false);

        assertEquals("PIP", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getCode());
        assertEquals("Personal Independence Payment",
            ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getDescription());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAPostcode_thenAddRegionalProcessingCenterToCase() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false), true), false);

        assertEquals("Address 1",
            ((RegionalProcessingCenter) response.getTransformedCase().get("regionalProcessingCenter")).getAddress1());
        assertEquals("Liverpool", (response.getTransformedCase().get("region")));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantMobileNumberLessThan10Digits_thenAddAnError() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithMobileNumber("07776156"), true), false);

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidRepresentativeMobileNumberLessThan10Digits_thenAddAnError() {
        Representative representative = buildRepresentative();
        representative.setContact(Contact.builder().build());
        representative.getContact().setMobile("0123456");

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsValidAppellantAnInvalidAppointeeMobileNumberLessThan10Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile(VALID_MOBILE);
        appellant.setAppointee(buildAppointeeWithMobileNumber("07776156"));
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", appellant, true), false);

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInValidAppellantAnInvalidAppointeeMobileNumberLessThan10Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile("07776157");
        appellant.setAppointee(buildAppointeeWithMobileNumber("07776156"));
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", appellant, true), false);

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
        assertEquals("person2_mobile is invalid", response.getErrors().get(1));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantMobileNumberGreaterThan11Digits_thenAddAnError() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithMobileNumber("077761560000"), true), false);

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidRepresentativeMobileNumberGreaterThan11Digits_thenAddAnError() {
        Representative representative = buildRepresentative();
        representative.setContact(Contact.builder().build());
        representative.getContact().setMobile("0123456789000");

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenARepresentativeTitleIsInvalid_thenAddWarning() {
        Representative representative = buildRepresentative();
        representative.setContact(Contact.builder().build());
        representative.getName().setTitle("%54 3434 ^7*");

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_title is invalid", response.getWarnings().get(0));
    }

    @Test
    @Parameters({"", "null", " "})
    public void givenARepresentativeTitleIsEmpty_thenDoNotAddAnyWarnings(@Nullable String title) {
        Representative representative = buildRepresentative();
        representative.setContact(Contact.builder().build());
        representative.getName().setTitle(title);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsValidAppellantAnInvalidAppointeeMobileNumberGreaterThan11Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile(VALID_MOBILE);
        appellant.setAppointee(buildAppointeeWithMobileNumber("077761560000"));
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", appellant, true), false);

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantAnInvalidAppointeeMobileNumberGreaterThan11Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile("077761560000");
        appellant.setAppointee(buildAppointeeWithMobileNumber("077761560000"));
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", appellant, true), false);

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
        assertEquals("person2_mobile is invalid", response.getErrors().get(1));
    }

    @Test
    public void givenAnAppealContainsAValidAppellantMobileNumber_thenDoNotAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellantWithMobileNumber(VALID_MOBILE), true),
            false);

        assertEquals(VALID_MOBILE,
            ((Appeal) response.getTransformedCase().get("appeal")).getAppellant().getContact().getMobile());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAnInvalidPostcode_thenAddAnError() {
        given(postcodeValidator.isValidPostcodeFormat(anyString())).willReturn(false);
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithPostcode("Bla Bla"), true), false);

        assertEquals("person1_postcode is not a valid postcode", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidPostcode_thenDoNotAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellantWithPostcode(VALID_POSTCODE), true), false);

        assertEquals(VALID_POSTCODE,
            ((Appeal) response.getTransformedCase().get("appeal")).getAppellant().getAddress().getPostcode());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeDoesNotContainAFirstNameOrLastNameOrTitleOrCompany_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(
            "representative_company, representative_first_name and representative_last_name are empty. At least one must be populated",
            response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeContainsAFirstNameButDoesNotContainALastNameOrTitleOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsALastNameButDoesNotContainAFirstNameOrTitleOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsATitleButDoesNotContainAFirstNameOrLastNameOrCompany_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(
            "representative_company, representative_first_name and representative_last_name are empty. At least one must be populated",
            response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeContainsACompanyButDoesNotContainAFirstNameOrLastNameOrTitle_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeDoesNotContainAnAddressLine1_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine1(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainATown_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine2("101 Street");
        representative.getAddress().setTown(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainACountyAndContainAddressLine2_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine2("101 Street");
        representative.getAddress().setCounty(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainACountyAndAddressLine2_thenAddAWarning() {
        ocrCaseData.remove("representative_address_line4");
        Representative representative = buildRepresentative();
        representative.getAddress().setLine2(null);
        representative.getAddress().setCounty(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainAPostcode_thenAddAWarningAndDoNotAddRegionalProcessingCenter() {
        Representative representative = buildRepresentative();
        representative.getAddress().setPostcode(null);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals("representative_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenANullRepresentative_thenAddAnError() {
        Representative representative = null;
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(1, response.getErrors().size());
        assertEquals("The \"Has representative\" field is not selected, please select an option to proceed",
            response.getErrors().get(0));
    }

    @Test
    public void givenARepresentativeWithHasRepresentativeFieldNotSet_thenAddAnError() {
        Representative representative = buildRepresentative();
        representative.setHasRepresentative(null);
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true), false);

        assertEquals(1, response.getErrors().size());
        assertEquals(HAS_REPRESENTATIVE_FIELD_MISSING, response.getErrors().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainATitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setTitle(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_title is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setFirstName(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setLastName(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine1(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine3AndContainAddressLine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine2("101 Street");
        appellant.getAppointee().getAddress().setTown(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine3And2_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        appellant.getAppointee().getAddress().setLine2(null);
        appellant.getAppointee().getAddress().setTown(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line2 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine4AndContainAddressLine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine2("101 Street");
        appellant.getAppointee().getAddress().setCounty(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine4And2_thenAddAWarning() {
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine2(null);
        appellant.getAppointee().getAddress().setCounty(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLinePostcode_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setPostcode(null);

        CaseResponse response = validator
            .validateExceptionRecord(transformResponse, exceptionRecord, buildMinimumAppealData(appellant, true),
                false);

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealWithNoHearingType_thenAddAWarning() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithHearingType(null, buildAppellant(false), true), false);

        assertEquals("is_hearing_type_oral and/or is_hearing_type_paper is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAllMandatoryFieldsForAnAppellantExists_thenDoNotAddAWarning() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), true);

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord, pairs, false);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAllMandatoryFieldsAndValidDocumentDoNotAddAnError() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), false);

        pairs.put("sscsDocument", buildDocument("myfile.pdf"));

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAllMandatoryFieldsAndDocumentNameIsNullAddAnError() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), false);

        pairs.put("sscsDocument", buildDocument(null));

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals(
            "There is a file attached to the case that does not have a filename, add a filename, e.g. filename.pdf",
            response.getErrors().get(0));
    }

    @Test
    public void givenAllMandatoryFieldsAndDocumentNameNoExtensionAddAnError() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), false);

        pairs.put("sscsDocument", buildDocument("Waiver"));

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals(
            "There is a file attached to the case called Waiver, filenames must have extension, e.g. filename.pdf",
            response.getErrors().get(0));
    }

    @Test
    public void givenAValidationCallbackTypeWithIncompleteDetails_thenAddAWarningWithCorrectMessage() {

        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator.validateValidationRecord(buildMinimumAppealData(appellant, false), false);

        assertEquals("Appellant postcode is empty", response.getWarnings().get(0));
        verifyNoInteractions(regionalProcessingCenterService);
    }

    @Test
    public void givenAValidationCallbackEventIsAppealToProceedAndMrnDateIsEmpty_thenNoWarningOrErrorMessage() {

        Map<String, Object> pairs =
            buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("Sheffield DRT").build(),
                buildAppellant(false), false);

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAValidationCallbackEventIsOtherAndMrnDateIsEmpty_thenNoWarningOrErrorMessage() {

        Map<String, Object> pairs =
            buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("Sheffield DRT").build(),
                buildAppellant(false), false);

        CaseResponse response = validator.validateValidationRecord(pairs, false);

        assertEquals("Mrn date is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealWithAnErrorAndCombineWarningsTrue_thenMoveErrorsToWarnings() {
        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithMobileNumber("07776156"), true), true);

        assertEquals("person1_mobile is invalid", response.getWarnings().get(1));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    @Parameters({"07900123456", "01277323440", "01277323440 ext 123"})
    public void givenAnAppealWithValidHearingPhoneNumber_thenDoNotAddWarning(String number) {
        HearingSubtype hearingSubtype = HearingSubtype.builder().hearingTelephoneNumber(number).build();

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithHearingSubtype(hearingSubtype, buildAppellant(false), true), false);

        assertEquals(0, response.getErrors().size());
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAnAppealWithAnInvalidHearingPhoneNumber_thenAddWarning() {
        HearingSubtype hearingSubtype = HearingSubtype.builder().hearingTelephoneNumber("01222").build();

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithHearingSubtype(hearingSubtype, buildAppellant(false), true), false);

        assertEquals(
            "Telephone hearing selected but the number used is invalid. Please check either the hearing_telephone_number or person1_phone fields",
            response.getWarnings().get(0));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealWithAnInvalidHearingPhoneNumberForSscsCase_thenAddWarning() {
        Map<String, Object> pairs = buildMinimumAppealDataWithHearingSubtype(
            HearingSubtype.builder().wantsHearingTypeTelephone("Yes").hearingTelephoneNumber("01222").build(),
            buildAppellant(false), false);

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals(
            "Telephone hearing selected but the number used is invalid. Please check either the telephone or hearing telephone number fields",
            response.getWarnings().get(0));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealWithAHearingTypeTelephoneSelectedButNoTelephoneEntered_thenAddWarning() {
        HearingSubtype hearingSubtype =
            HearingSubtype.builder().wantsHearingTypeTelephone("Yes").hearingTelephoneNumber(null).build();

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithHearingSubtype(hearingSubtype, buildAppellant(false), true), false);

        assertEquals("hearing_telephone_number has not been provided but data indicates hearing telephone is required",
            response.getWarnings().get(0));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealWithAHearingTypeTelephoneSelectedButNoTelephoneEnteredForSscsCase_thenAddWarning() {
        Map<String, Object> pairs = buildMinimumAppealDataWithHearingSubtype(
            HearingSubtype.builder().wantsHearingTypeTelephone("Yes").hearingTelephoneNumber(null).build(),
            buildAppellant(false), false);

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals("Hearing telephone number has not been provided but data indicates hearing telephone is required",
            response.getWarnings().get(0));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealWithAHearingTypeVideoSelectedButNoVideoEmailEntered_thenAddWarning() {
        HearingSubtype hearingSubtype =
            HearingSubtype.builder().wantsHearingTypeVideo("Yes").hearingVideoEmail(null).build();

        CaseResponse response = validator.validateExceptionRecord(transformResponse, exceptionRecord,
            buildMinimumAppealDataWithHearingSubtype(hearingSubtype, buildAppellant(false), true), false);

        assertEquals("hearing_video_email has not been provided but data indicates hearing video is required",
            response.getWarnings().get(0));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealWithAHearingTypeVideoSelectedButNoVideoEmailEnteredForSscsCase_thenAddWarning() {
        Map<String, Object> pairs = buildMinimumAppealDataWithHearingSubtype(
            HearingSubtype.builder().wantsHearingTypeVideo("Yes").hearingVideoEmail(null).build(),
            buildAppellant(false), false);

        CaseResponse response = validator.validateValidationRecord(pairs, true);

        assertEquals("Hearing video email address has not been provided but data indicates hearing video is required",
            response.getWarnings().get(0));
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealWithAnHearingTypePaperAndEmptyHearingSubTypeForSscsCase_thenNoWarning() {
        Map<String, Object> pairs =
            buildMinimumAppealDataWithHearingType(HEARING_TYPE_PAPER, buildAppellant(false), false);
        CaseResponse response = validator.validateValidationRecord(pairs, true);
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAnAppealWithAnEmptyHearingSubTypeAndFormTypeIsSscs1peuForSscsCase_thenAddWarning() {
        Map<String, Object> pairs =
            buildMinimumAppealDataWithHearingSubtype(HearingSubtype.builder().build(), buildAppellant(false), false);
        pairs.put("formType", FormType.SSCS1PEU);
        CaseResponse response = validator.validateValidationRecord(pairs, true);
        assertEquals(1, response.getWarnings().size());
        assertEquals("Hearing option telephone, video and face to face are empty. At least one must be populated",
            response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealWithAnEmptyHearingSubTypeForSscsCase_thenNoWarning() {
        Map<String, Object> pairs = buildMinimumAppealDataWithHearingSubtype(HearingSubtype.builder().build(), buildAppellant(false), false);
        pairs.put("formType", FormType.SSCS1);
        CaseResponse response = validator.validateValidationRecord(pairs, true);
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAnAppealWithAnEmptyHearingSubTypeAndFormTypIsNullForSscsCase_thenNoWarning() {
        Map<String, Object> pairs =
            buildMinimumAppealDataWithHearingSubtype(HearingSubtype.builder().build(), buildAppellant(false), false);
        pairs.put("formType", null);
        CaseResponse response = validator.validateValidationRecord(pairs, true);
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAnAppealWithAnHearingSubTypeVideoForSscsCase_thenNoWarning() {
        Map<String, Object> pairs = buildMinimumAppealDataWithHearingSubtype(
            HearingSubtype.builder().wantsHearingTypeVideo("Yes").hearingVideoEmail("m@m.com").build(),
            buildAppellant(false), false);
        CaseResponse response = validator.validateValidationRecord(pairs, true);
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAnAppealWithAnHearingSubTypeFaceForSscsCase_thenNoWarning() {
        Map<String, Object> pairs =
            buildMinimumAppealDataWithHearingSubtype(HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build(),
                buildAppellant(false), false);
        CaseResponse response = validator.validateValidationRecord(pairs, true);
        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenSscs2FormWithoutChildMaintenance_thenAddAWarning() {

        ocrCaseData.put("person1_child_maintenance_number", "");
        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildMinimumAppealDataWithBenefitTypeAndFormType(UC.getShortName(), buildAppellant(false), true,
                FormType.SSCS2),
            false);

        assertEquals("'person1_child_maintenance_number' is blank", response.getWarnings().get(0));
    }

    @Test
    public void givenSscs2FormWithChildMaintenance_thenAppellantShouldReturnValue() {

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, buildOtherPartyName()), false);

        assertEquals(0, response.getWarnings().size());
        assertEquals(CHILD_MAINTENANCE_NUMBER, response.getTransformedCase().get("childMaintenanceNumber"));
    }

    @Test
    @Parameters({", test2, test3, TS1 1ST, other_party_address_line1 is empty, 1",
        "test1, , , TS1 1ST, other_party_address_line2 is empty, 1",
        "test1, test2, , , other_party_postcode is empty, 1",
        "test1, , , , other_party_address_line2 is empty, 2",
        ", , , , other_party_address_line1 is empty, 3",
    })
    public void givenSscs2FormWithoutOtherPartyAddressEntry_thenAddAWarning(String line1, String line2, String line3, String postcode, String warning, int size) {

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildCaseWithChildMaintenanceWithOtherPartyNameAddress(CHILD_MAINTENANCE_NUMBER,line1, line2,line3, postcode, buildOtherPartyName()),
            false);

        assertFalse(response.getWarnings().isEmpty());
        assertEquals(size, response.getWarnings().size());
        assertEquals(warning, response.getWarnings().get(0));
    }

    @Test
    public void givenSscs2FormWithOtherPartyAddressEntry_thenValueIsSet() {

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecord,
            buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, buildOtherPartyName()),
            false);

        assertTrue(response.getWarnings().isEmpty());
        assertTrue(response.getErrors().isEmpty());
    }


    @Test
    public void givenSscs2FormWithOtherPartyLastNameMissingAndIgnoreWarningsFalse_thenOtherPartyShouldReturnWarning() {
        Name otherPartyName = buildOtherPartyName();
        otherPartyName.setLastName(null);
        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecordSscs2, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, otherPartyName),
            false);

        assertEquals(1, response.getWarnings().size());
        assertEquals("other_party_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenSscs2FormWithOtherPartyFirstNameMissingAndIgnoreWarningsFalse_thenOtherPartyShouldReturnWarning() {
        Name otherPartyName = buildOtherPartyName();
        otherPartyName.setFirstName(null);
        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecordSscs2, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, otherPartyName),
            false);

        assertEquals(1, response.getWarnings().size());
        assertEquals("other_party_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenSscs2FormWithOtherPartyTitleInvalidAndIgnoreWarningsFalse_thenOtherPartyShouldReturnWarning() {
        Name otherPartyName = buildOtherPartyName();
        otherPartyName.setTitle("Random");
        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecordSscs2, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, otherPartyName),
            false);

        assertEquals(1, response.getWarnings().size());
        assertEquals("other_party_title is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenSscs2FormWithOtherPartyLastNameMissingAndIgnoreWarningsTrue_thenNoWarningsShown() {
        Name otherPartyName = buildOtherPartyName();
        otherPartyName.setLastName(null);

        exceptionRecordSscs2 =
            ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS2.getId()).ignoreWarnings(true).build();
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs2)).willReturn(scannedData);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecordSscs2, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, otherPartyName),
            false);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenSscs2FormWithOtherPartyFirstNameMissingAndIgnoreWarningsTrue_thenNoWarningsShown() {
        Name otherPartyName = buildOtherPartyName();
        otherPartyName.setFirstName(null);

        exceptionRecordSscs2 =
            ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS2.getId()).ignoreWarnings(true).build();
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs2)).willReturn(scannedData);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecordSscs2, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, otherPartyName),
            false);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenSscs2FormWithOtherPartyTitleInvalidAndIgnoreWarningsTrue_thenNoWarningsShown() {
        Name otherPartyName = buildOtherPartyName();
        otherPartyName.setTitle("Random");

        exceptionRecordSscs2 =
            ExceptionRecord.builder().ocrDataFields(ocrList).formType(FormType.SSCS2.getId()).ignoreWarnings(true).build();
        given(sscsJsonExtractor.extractJson(exceptionRecordSscs2)).willReturn(scannedData);

        CaseResponse response = validator.validateExceptionRecord(transformResponse,
            exceptionRecordSscs2, buildCaseWithChildMaintenanceWithOtherPartyNameAddress(
                CHILD_MAINTENANCE_NUMBER,OTHER_PARTY_ADDRESS_LINE1, OTHER_PARTY_ADDRESS_LINE2,OTHER_PARTY_ADDRESS_LINE3, OTHER_PARTY_POSTCODE, otherPartyName),
            false);

        assertEquals(0, response.getWarnings().size());
    }

    private Object buildDocument(String filename) {
        List<SscsDocument> documentDetails = new ArrayList<>();

        SscsDocumentDetails details = SscsDocumentDetails.builder()
            .documentFileName(filename).documentLink(DocumentLink.builder().documentFilename(filename).build()).build();
        documentDetails.add(SscsDocument.builder().value(details).build());

        return documentDetails;
    }

    private Map<String, Object> buildMinimumAppealData(Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant,
            buildMinimumRep(), null, exceptionCaseType, HEARING_TYPE_ORAL,
            HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build());
    }

    private Map<String, Object> buildMinimumAppealDataWithMrn(MrnDetails mrn, Appellant appellant,
                                                              Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(mrn, ESA.name(), appellant, buildMinimumRep(), null,
            exceptionCaseType, HEARING_TYPE_PAPER, null);
    }

    private Map<String, Object> buildMinimumAppealDataWithBenefitType(String benefitCode, Appellant appellant,
                                                                      Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, benefitCode, appellant,
            buildMinimumRep(), null, exceptionCaseType, HEARING_TYPE_ORAL,
            HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build());
    }

    private Map<String, Object> buildMinimumAppealDataWithRepresentative(Appellant appellant,
                                                                         Representative representative,
                                                                         Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant, representative,
            null, exceptionCaseType, HEARING_TYPE_ORAL,
            HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build());
    }

    private Map<String, Object> buildMinimumAppealDataWithExcludedDate(String excludedDate, Appellant appellant,
                                                                       Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant,
            buildMinimumRep(), excludedDate, exceptionCaseType, HEARING_TYPE_ORAL, null);
    }

    private Map<String, Object> buildMinimumAppealDataWithHearingType(String hearingType, Appellant appellant,
                                                                      Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant,
            buildMinimumRep(), null, exceptionCaseType, hearingType, null);
    }

    private Map<String, Object> buildMinimumAppealDataWithHearingSubtype(HearingSubtype hearingSubtype,
                                                                         Appellant appellant,
                                                                         Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant,
            buildMinimumRep(), null, exceptionCaseType, HEARING_TYPE_ORAL, hearingSubtype);
    }

    private Representative buildMinimumRep() {
        return Representative.builder().hasRepresentative(NO_LITERAL).build();
    }

    private Map<String, Object> buildMinimumAppealDataWithBenefitTypeAndFormType(String benefitCode,
                                                                                 Appellant appellant,
                                                                                 Boolean exceptionCaseType,
                                                                                 FormType formType) {
        return buildMinimumAppealDataWithMrnDateFormTypeAndBenefitType(defaultMrnDetails, benefitCode, appellant,
            buildMinimumRep(), null, exceptionCaseType, HEARING_TYPE_ORAL,
            HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build(), formType);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrnDateAndBenefitType(MrnDetails mrn, String benefitCode,
                                                                                Appellant appellant,
                                                                                Representative representative,
                                                                                String excludeDates,
                                                                                Boolean exceptionCaseType,
                                                                                String hearingType,
                                                                                HearingSubtype hearingSubtype) {
        return buildMinimumAppealDataWithMrnDateFormTypeAndBenefitType(mrn, benefitCode, appellant, representative,
            excludeDates,
            exceptionCaseType, hearingType, hearingSubtype, FormType.SSCS1PE);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrnDateFormTypeAndBenefitType(MrnDetails mrn,
                                                                                        String benefitCode,
                                                                                        Appellant appellant,
                                                                                        Representative representative,
                                                                                        String excludeDates,
                                                                                        Boolean exceptionCaseType,
                                                                                        String hearingType,
                                                                                        HearingSubtype hearingSubtype,
                                                                                        FormType formType) {
        Map<String, Object> dataMap = new HashMap<>();
        List<ExcludeDate> excludedDates = new ArrayList<>();
        excludedDates.add(ExcludeDate.builder().value(DateRange.builder().start(excludeDates).build()).build());

        dataMap.put("formType", formType);
        dataMap.put("appeal", Appeal.builder()
            .mrnDetails(
                MrnDetails.builder().mrnDate(mrn.getMrnDate()).dwpIssuingOffice(mrn.getDwpIssuingOffice()).build())
            .benefitType(BenefitType.builder().code(benefitCode).build())
            .appellant(appellant)
            .rep(representative)
            .hearingOptions(HearingOptions.builder().excludeDates(excludedDates).build())
            .hearingType(hearingType)
            .hearingSubtype(hearingSubtype)
            .build());

        if (exceptionCaseType) {
            dataMap.put("bulkScanCaseReference", 123);
        }
        return dataMap;
    }

    private Appellant buildAppellant(Boolean withAppointee) {
        return buildAppellantWithMobileNumberAndPostcode(withAppointee, VALID_MOBILE, VALID_POSTCODE);
    }

    private Appellant buildAppellantWithPostcode(String postcode) {
        return buildAppellantWithMobileNumberAndPostcode(false, VALID_MOBILE, postcode);
    }

    private Appellant buildAppellantWithMobileNumber(String mobileNumber) {
        return buildAppellantWithMobileNumberAndPostcode(false, mobileNumber, VALID_POSTCODE);
    }

    private Appellant buildAppellantWithMobileNumberAndPostcode(Boolean withAppointee, String mobileNumber,
                                                                String postcode) {
        Appointee appointee = withAppointee ? buildAppointee(VALID_MOBILE) : null;

        return Appellant.builder()
            .name(Name.builder().title("Mr").firstName("Bob").lastName("Smith").build())
            .address(
                Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode(postcode).build())
            .identity(Identity.builder().nino("BB000000B").build())
            .contact(Contact.builder().mobile(mobileNumber).build())
            .appointee(appointee)
            .role(Role.builder().name("Paying parent").build()).build();
    }

    private Map<String, Object> buildCaseWithChildMaintenanceWithOtherPartyNameAddress(String childMaintenanceNumber, String line1, String line2, String line3, String postcode, Name otherPartyName) {
        Map<String, Object> datamap = buildMinimumAppealDataWithMrnDateFormTypeAndBenefitType(
            defaultMrnDetails,
            UC.getShortName(),
            buildAppellant(true),
            buildMinimumRep(),
            null,
            true,
            HEARING_TYPE_ORAL,
            HearingSubtype.builder().wantsHearingTypeFaceToFace("Yes").build(),
            FormType.SSCS2);
        datamap.put("childMaintenanceNumber", childMaintenanceNumber);
        datamap.put("otherParties", Collections.singletonList(CcdValue.<OtherParty>builder().value(
            OtherParty.builder()
                .name(otherPartyName)
                .address(Address.builder()
                    .line1(line1)
                    .town(line2)
                    .county((line3 != null && !line3.equals("")) ? "." : line3)
                    .postcode(postcode)
                    .build())
                .build())
            .build()));
        return datamap;
    }

    private Name buildOtherPartyName() {
        return Name.builder().title("Mr").firstName("Jerry").lastName("Fisher").build();
    }

    private Appointee buildAppointeeWithMobileNumber(String mobileNumber) {
        return buildAppointee(mobileNumber);
    }

    private Appointee buildAppointee(String mobileNumber) {

        return Appointee.builder()
            .name(Name.builder().title("Mr").firstName("Tim").lastName("Garwood").build())
            .address(Address.builder().line1("101 My Road").town("Gidea Park").county("Essex").postcode(VALID_POSTCODE)
                .build())
            .identity(Identity.builder().build())
            .contact(Contact.builder().mobile(mobileNumber).build())
            .build();
    }

    private Representative buildRepresentative() {

        return Representative.builder()
            .hasRepresentative("Yes")
            .organisation("Bob the builders Ltd")
            .name(Name.builder().title("Mr").firstName("Bob").lastName("Smith").build())
            .address(
                Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode("CM13 1HG").build())
            .build();
    }

}
