package uk.gov.hmcts.reform.sscs.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.ESA;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.PIP;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.BENEFIT_TYPE_DESCRIPTION;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_TYPE_ORAL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

public class SscsCaseValidatorTest {

    private static final String VALID_MOBILE = "07832882849";
    private static final String VALID_PHONE_NUMBER = "01234567898";
    private static final String VALID_POSTCODE = "CM13 0GD";

    @Mock
    RegionalProcessingCenterService regionalProcessingCenterService;

    @Mock
    SscsJsonExtractor sscsJsonExtractor;

    DwpAddressLookupService dwpAddressLookupService;

    private SscsCaseValidator validator;

    private MrnDetails defaultMrnDetails;

    private List<String> titles = new ArrayList<>();

    private Map<String, Object> ocrCaseData = new HashMap<>();

    private AboutToStartOrSubmitCallbackResponse transformErrorResponse;

    private CaseDetails caseDetails;

    private ScannedData scannedData;

    @Before
    public void setup() {
        initMocks(this);
        dwpAddressLookupService = new DwpAddressLookupService();
        scannedData = mock(ScannedData.class);
        caseDetails = mock(CaseDetails.class);
        validator = new SscsCaseValidator(regionalProcessingCenterService, dwpAddressLookupService, sscsJsonExtractor);
        transformErrorResponse = AboutToStartOrSubmitCallbackResponse.builder().build();

        defaultMrnDetails = MrnDetails.builder().dwpIssuingOffice("2").mrnDate("2018-12-09").build();

        titles.add("Mr");
        titles.add("Mrs");
        ReflectionTestUtils.setField(validator, "titles", titles);
        ocrCaseData.put("person1_address_line4", "county");
        ocrCaseData.put("person2_address_line4", "county");
        ocrCaseData.put("representative_address_line4", "county");

        given(regionalProcessingCenterService.getByPostcode(VALID_POSTCODE)).willReturn(RegionalProcessingCenter.builder().address1("Address 1").name("Liverpool").build());
        given(sscsJsonExtractor.extractJson(anyMap())).willReturn(scannedData);
        given(scannedData.getOcrCaseData()).willReturn(ocrCaseData);
    }

    @Test
    public void givenAnAppellantIsEmpty_thenAddAWarning() {

        Map<String, Object> pairs = new HashMap<>();
        pairs.put("appeal", Appeal.builder().hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

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

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

        assertThat(response.getWarnings())
            .containsOnly("person1_title is empty",
                "person1_first_name is empty",
                "person1_last_name is empty");
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
            .address(Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode(VALID_POSTCODE).build())
            .identity(Identity.builder().nino("BB000000B").build()).build())
            .benefitType(BenefitType.builder().code(PIP.name()).build())
            .mrnDetails(defaultMrnDetails)
            .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

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
            .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

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

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_title is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidTitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Bla");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_title is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitleWithFullStop_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Mr.");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitleLowercase_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("mr");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitle_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Mr");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setFirstName(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setLastName(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine1(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainATownAndContainALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2("101 Street");
        appellant.getAddress().setTown(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainATownAndALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setTown(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line2 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainACountyAndContainALine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2("101 Street");
        appellant.getAddress().setCounty(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainACountyAndALine2_thenAddAWarning() {
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine2(null);
        appellant.getAddress().setCounty(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAPostcode_thenAddAWarningAndDoNotAddRegionalProcessingCenter() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
        verifyZeroInteractions(regionalProcessingCenterService);
    }

    @Test
    public void givenAnAppellantContainsPostcodeWithNoRegionalProcessingCenter_thenDoNotAddRegionalProcessingCenter() {
        Appellant appellant = buildAppellant(false);
        given(regionalProcessingCenterService.getByPostcode(VALID_POSTCODE)).willReturn(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertNull(response.getTransformedCase().get("regionalProcessingCenter"));
        assertNull(response.getTransformedCase().get("region"));
        assertEquals("person1_postcode is not a postcode that maps to a regional processing center", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainANino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidNino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino("Bla");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_nino is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesContainANino_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino("BB000000B");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppointeeExistsAndAnAppellantDoesNotContainANino_thenAddAWarningAboutPerson2() {
        Appellant appellant = buildAppellant(true);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

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

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainAnMrnDate_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("2").build(), buildAppellant(false), true));

        assertEquals("mrn_date is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnMrnDateInFuture_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("2").mrnDate("2148-10-10").build(), buildAppellant(false), true));

        assertEquals("mrn_date is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnDoesNotContainADwpIssuingOffice_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice(null).build(), buildAppellant(false), true));

        assertEquals("office is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnDoesNotContainAValidDwpIssuingOffice_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("Bla").build(), buildAppellant(false), true));

        assertEquals("office is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnDoesContainValidUpperCaseDwpIssuingOffice_thenNoWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("BALHAM DRT").build(), buildAppellant(false), true));

        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void givenAnMrnDoesContainValidCapitaliseDwpIssuingOffice_thenNoWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("Balham DRT").build(), buildAppellant(false), true));

        assertTrue(response.getWarnings().isEmpty());
    }

    @Test
    public void givenAnAppealContainsAnAppellantDateOfBirthInFuture_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setDob("2148-10-10");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_dob is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnAppointeeDateOfBirthInFuture_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getIdentity().setDob("2148-10-10");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_dob is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAHearingExcludedDateInPast_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithExcludedDate("2018-10-10", appellant, true));

        assertEquals("hearing_options_exclude_dates is in past", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainABenefitTypeDescription_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType(null, buildAppellant(false), true));

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidBenefitTypeDescription_thenAddAnError() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", buildAppellant(false), true));

        List<String> benefitNameList = new ArrayList<>();
        for (Benefit be : Benefit.values()) {
            benefitNameList.add(be.name());
        }

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " invalid. Should be one of: " + String.join(", ", benefitNameList), response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidLowercaseBenefitTypeDescription_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType(PIP.name().toLowerCase(), buildAppellant(false), true));

        List<String> benefitNameList = new ArrayList<>();
        for (Benefit be : Benefit.values()) {
            benefitNameList.add(be.name());
        }

        assertEquals("PIP", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getCode());
        assertEquals("Personal Independence Payment", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getDescription());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAValidBenefitTypeDescription_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false), true));

        assertEquals("PIP", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getCode());
        assertEquals("Personal Independence Payment", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getDescription());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAPostcode_thenAddRegionalProcessingCenterToCase() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false), true));

        assertEquals("Address 1", ((RegionalProcessingCenter) response.getTransformedCase().get("regionalProcessingCenter")).getAddress1());
        assertEquals("Liverpool", (response.getTransformedCase().get("region")));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantMobileNumberLessThan10Digits_thenAddAnError() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithMobileNumber("07776156"), true));

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidRepresentativeMobileNumberLessThan10Digits_thenAddAnError() {
        Representative representative = buildRepresentative();
        representative.setContact(Contact.builder().build());
        representative.getContact().setMobile("0123456");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsValidAppellantAnInvalidAppointeeMobileNumberLessThan10Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile(VALID_MOBILE);
        appellant.setAppointee(buildAppointeeWithMobileNumber("07776156"));
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", appellant, true));

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInValidAppellantAnInvalidAppointeeMobileNumberLessThan10Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile("07776157");
        appellant.setAppointee(buildAppointeeWithMobileNumber("07776156"));
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", appellant, true));

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
        assertEquals("person2_mobile is invalid", response.getErrors().get(1));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantMobileNumberGreaterThan11Digits_thenAddAnError() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithMobileNumber("077761560000"), true));

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidRepresentativeMobileNumberGreaterThan11Digits_thenAddAnError() {
        Representative representative = buildRepresentative();
        representative.setContact(Contact.builder().build());
        representative.getContact().setMobile("0123456789000");

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsValidAppellantAnInvalidAppointeeMobileNumberGreaterThan11Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile(VALID_MOBILE);
        appellant.setAppointee(buildAppointeeWithMobileNumber("077761560000"));
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", appellant, true));

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantAnInvalidAppointeeMobileNumberGreaterThan11Digits_thenAddAnError() {
        Appellant appellant = buildAppellant(true);
        appellant.getContact().setMobile("077761560000");
        appellant.setAppointee(buildAppointeeWithMobileNumber("077761560000"));
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", appellant, true));

        assertEquals("person1_mobile is invalid", response.getErrors().get(0));
        assertEquals("person2_mobile is invalid", response.getErrors().get(1));
    }

    @Test
    public void givenAnAppealContainsAValidAppellantMobileNumber_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellantWithMobileNumber(VALID_MOBILE), true));

        assertEquals(VALID_MOBILE, ((Appeal) response.getTransformedCase().get("appeal")).getAppellant().getContact().getMobile());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAnInvalidPostcode_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithPostcode("Bla Bla"), true));

        assertEquals("person1_postcode is not a valid postcode", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidPostcode_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellantWithPostcode(VALID_POSTCODE), true));

        assertEquals(VALID_POSTCODE, ((Appeal) response.getTransformedCase().get("appeal")).getAppellant().getAddress().getPostcode());
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

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_company, representative_first_name and representative_last_name are empty. At least one must be populated", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeContainsAFirstNameButDoesNotContainALastNameOrTitleOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsALastNameButDoesNotContainAFirstNameOrTitleOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsATitleButDoesNotContainAFirstNameOrLastNameOrCompany_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_company, representative_first_name and representative_last_name are empty. At least one must be populated", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeContainsACompanyButDoesNotContainAFirstNameOrLastNameOrTitle_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeDoesNotContainAnAddressLine1_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine1(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainATown_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine2("101 Street");
        representative.getAddress().setTown(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainACountyAndContainAddressLine2_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine2("101 Street");
        representative.getAddress().setCounty(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainACountyAndAddressLine2_thenAddAWarning() {
        ocrCaseData.remove("representative_address_line4");
        Representative representative = buildRepresentative();
        representative.getAddress().setLine2(null);
        representative.getAddress().setCounty(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainAPostcode_thenAddAWarningAndDoNotAddRegionalProcessingCenter() {
        Representative representative = buildRepresentative();
        representative.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainATitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setTitle(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_title is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setFirstName(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setLastName(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine1(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine3AndContainAddressLine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine2("101 Street");
        appellant.getAppointee().getAddress().setTown(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine3And2_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        appellant.getAppointee().getAddress().setLine2(null);
        appellant.getAppointee().getAddress().setTown(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line2 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine4AndContainAddressLine2_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine2("101 Street");
        appellant.getAppointee().getAddress().setCounty(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine4And2_thenAddAWarning() {
        ocrCaseData.remove("person1_address_line4");
        ocrCaseData.remove("person2_address_line4");
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine2(null);
        appellant.getAppointee().getAddress().setCounty(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLinePostcode_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setPostcode(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, true));

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealWithNoHearingType_thenAddAWarning() {
        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealDataWithHearingType(null, buildAppellant(false), true));

        assertEquals("is_hearing_type_oral and/or is_hearing_type_paper is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAllMandatoryFieldsForAnAppellantExists_thenDoNotAddAWarning() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), true);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAllMandatoryFieldsAndValidDocumentDoNotAddAnError() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), false);

        pairs.put("sscsDocument", buildDocument("myfile.pdf"));

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAllMandatoryFieldsAndDocumentNameIsNullAddAnError() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), false);

        pairs.put("sscsDocument", buildDocument(null));

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

        assertEquals("There is a file attached to the case that does not have a filename, add a filename, e.g. filename.pdf", response.getErrors().get(0));
    }

    @Test
    public void givenAllMandatoryFieldsAndDocumentNameNoExtensionAddAnError() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), false);

        pairs.put("sscsDocument", buildDocument("Waiver"));

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, pairs);

        assertEquals("There is a file attached to the case called Waiver, filenames must have extension, e.g. filename.pdf", response.getErrors().get(0));
    }

    @Test
    public void givenAValidationCallbackTypeWithIncompleteDetails_thenAddAWarningWithCorrectMessage() {

        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(transformErrorResponse, caseDetails, buildMinimumAppealData(appellant, false));

        assertEquals("Appellant postcode is empty", response.getWarnings().get(0));
        verifyZeroInteractions(regionalProcessingCenterService);
    }

    private Object buildDocument(String filename) {
        List<SscsDocument> documentDetails = new ArrayList<>();

        SscsDocumentDetails details = SscsDocumentDetails.builder()
            .documentFileName(filename).documentLink(DocumentLink.builder().documentFilename(filename).build()).build();
        documentDetails.add(SscsDocument.builder().value(details).build());

        return documentDetails;
    }

    private Map<String, Object> buildMinimumAppealData(Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant, null, null, exceptionCaseType, HEARING_TYPE_ORAL);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrn(MrnDetails mrn, Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(mrn, ESA.name(), appellant, null, null, exceptionCaseType, HEARING_TYPE_ORAL);
    }

    private Map<String, Object> buildMinimumAppealDataWithBenefitType(String benefitCode, Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, benefitCode, appellant, null, null, exceptionCaseType, HEARING_TYPE_ORAL);
    }

    private Map<String, Object> buildMinimumAppealDataWithRepresentative(Appellant appellant, Representative representative, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant, representative, null, exceptionCaseType, HEARING_TYPE_ORAL);
    }

    private Map<String, Object> buildMinimumAppealDataWithExcludedDate(String excludedDate, Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant, null, excludedDate, exceptionCaseType, HEARING_TYPE_ORAL);
    }

    private Map<String, Object> buildMinimumAppealDataWithHearingType(String hearingType, Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant, null, null, exceptionCaseType, hearingType);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrnDateAndBenefitType(MrnDetails mrn, String benefitCode, Appellant appellant, Representative representative, String excludeDates, Boolean exceptionCaseType, String hearingType) {
        Map<String, Object> dataMap = new HashMap<>();
        List<ExcludeDate> excludedDates = new ArrayList<>();
        excludedDates.add(ExcludeDate.builder().value(DateRange.builder().start(excludeDates).build()).build());

        dataMap.put("appeal", Appeal.builder()
            .mrnDetails(MrnDetails.builder().mrnDate(mrn.getMrnDate()).dwpIssuingOffice(mrn.getDwpIssuingOffice()).build())
            .benefitType(BenefitType.builder().code(benefitCode).build())
            .appellant(appellant)
            .rep(representative)
            .hearingOptions(HearingOptions.builder().excludeDates(excludedDates).build())
            .hearingType(hearingType)
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

    private Appellant buildAppellantWithMobileNumberAndPostcode(Boolean withAppointee, String mobileNumber, String postcode) {
        Appointee appointee = withAppointee ? buildAppointee(VALID_MOBILE) : null;

        return Appellant.builder()
            .name(Name.builder().title("Mr").firstName("Bob").lastName("Smith").build())
            .address(Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode(postcode).build())
            .identity(Identity.builder().nino("BB000000B").build())
            .contact(Contact.builder().mobile(mobileNumber).build())
            .appointee(appointee).build();
    }

    private Appointee buildAppointeeWithMobileNumber(String mobileNumber) {
        return buildAppointee(mobileNumber);
    }

    private Appointee buildAppointee(String mobileNumber) {

        return Appointee.builder()
            .name(Name.builder().title("Mr").firstName("Tim").lastName("Garwood").build())
            .address(Address.builder().line1("101 My Road").town("Gidea Park").county("Essex").postcode(VALID_POSTCODE).build())
            .identity(Identity.builder().build())
            .contact(Contact.builder().mobile(mobileNumber).build())
            .build();
    }

    private Representative buildRepresentative() {

        return Representative.builder()
            .hasRepresentative("Yes")
            .organisation("Bob the builders Ltd")
            .name(Name.builder().title("Mr").firstName("Bob").lastName("Smith").build())
            .address(Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode("CM13 1HG").build())
            .build();
    }

}
