package uk.gov.hmcts.reform.sscs.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
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
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

public class SscsCaseValidatorTest {

    @Mock
    RegionalProcessingCenterService regionalProcessingCenterService;

    private SscsCaseValidator validator;

    private MrnDetails defaultMrnDetails;

    private List<String> titles = new ArrayList<>();
    private List<String> offices = new ArrayList<>();

    @Before
    public void setup() {
        initMocks(this);
        validator = new SscsCaseValidator(regionalProcessingCenterService);

        defaultMrnDetails = MrnDetails.builder().dwpIssuingOffice("2").mrnDate("2018-12-09").build();

        titles.add("Mr");
        titles.add("Mrs");
        ReflectionTestUtils.setField(validator, "titles", titles);

        offices.add("1");
        offices.add("2");
        ReflectionTestUtils.setField(validator, "offices", offices);

        given(regionalProcessingCenterService.getByPostcode("CM13 0GD")).willReturn(RegionalProcessingCenter.builder().address1("Address 1").name("Liverpool").build());
    }

    @Test
    public void givenAnAppellantIsEmpty_thenAddAWarning() {

        Map<String, Object> pairs = new HashMap<>();
        pairs.put("appeal", Appeal.builder().hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validate(pairs);

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
                Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode("CM13 0GD").build())
                .identity(Identity.builder().nino("BB000000B").build()).build())
                .benefitType(BenefitType.builder().code(PIP.name()).build())
                .mrnDetails(defaultMrnDetails)
                .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validate(pairs);

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
            .address(Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode("CM13 0GD").build())
            .identity(Identity.builder().nino("BB000000B").build()).build())
            .benefitType(BenefitType.builder().code(PIP.name()).build())
            .mrnDetails(defaultMrnDetails)
            .hearingType(HEARING_TYPE_ORAL).build());
        pairs.put("bulkScanCaseReference", 123);

        CaseResponse response = validator.validate(pairs);

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

        CaseResponse response = validator.validate(pairs);

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

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_title is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidTitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Bla");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_title is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitleWithFullStop_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Mr.");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitleLowercase_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("mr");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesContainAValidTitle_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setTitle("Mr");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppellantDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setFirstName(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setLastName(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine1(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainATown_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setTown(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainACounty_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setCounty(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAPostcode_thenAddAWarningAndDoNotAddRegionalProcessingCenter() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
        verifyZeroInteractions(regionalProcessingCenterService);
    }

    @Test
    public void givenAnAppellantContainsPostcodeWithNoRegionalProcessingCenter_thenDoNotAddRegionalProcessingCenter() {
        Appellant appellant = buildAppellant(false);
        given(regionalProcessingCenterService.getByPostcode("CM13 0GD")).willReturn(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertNull(response.getTransformedCase().get("regionalProcessingCenter"));
        assertNull(response.getTransformedCase().get("region"));
    }

    @Test
    public void givenAnAppellantDoesNotContainANino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAValidNino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino("Bla");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_nino is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesContainANino_thenDoNotAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino("BB000000B");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppointeeExistsAndAnAppellantDoesNotContainANino_thenAddAWarningAboutPerson2() {
        Appellant appellant = buildAppellant(true);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

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

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainAnMrnDate_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("2").build(), buildAppellant(false), true));

        assertEquals("mrn_date is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnMrnDateInFuture_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithMrn(MrnDetails.builder().dwpIssuingOffice("2").mrnDate("2148-10-10").build(), buildAppellant(false), true));

        assertEquals("mrn_date is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnDoesNotContainADwpIssuingOffice_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice(null).build(), buildAppellant(false), true));

        assertEquals("office is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnDoesNotContainAValidDwpIssuingOffice_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("Bla").build(), buildAppellant(false), true));

        assertEquals("office is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnMrnContainsAValidDwpIssuingOffice_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithMrn(MrnDetails.builder().mrnDate("2019-01-01").dwpIssuingOffice("1").build(), buildAppellant(false), true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAnAppellantDateOfBirthInFuture_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setDob("2148-10-10");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_dob is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnAppointeeDateOfBirthInFuture_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getIdentity().setDob("2148-10-10");

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_dob is in future", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAHearingExcludedDateInPast_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithExcludedDate("2018-10-10", appellant, true));

        assertEquals("hearing_options_exclude_dates is in past", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainABenefitTypeDescription_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(null, buildAppellant(false), true));

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidBenefitTypeDescription_thenAddAnError() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType("Bla", buildAppellant(false), true));

        List<String> benefitNameList = new ArrayList<>();
        for (Benefit be : Benefit.values()) {
            benefitNameList.add(be.name());
        }

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " invalid. Should be one of: " + String.join(", ", benefitNameList), response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidLowercaseBenefitTypeDescription_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name().toLowerCase(), buildAppellant(false), true));

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
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false), true));

        assertEquals("PIP", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getCode());
        assertEquals("Personal Independence Payment", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getDescription());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAPostcode_thenAddRegionalProcessingCenterToCase() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false), true));

        assertEquals("Address 1", ((RegionalProcessingCenter) response.getTransformedCase().get("regionalProcessingCenter")).getAddress1());
        assertEquals("Liverpool", (response.getTransformedCase().get("region")));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantPhoneNumberLessThan10Digits_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithPhoneNumber("012345678"), true));

        assertEquals("person1_phone is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidAppellantPhoneNumberMoreThan17Digits_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name().toLowerCase(), buildAppellantWithPhoneNumber("012345678987654322"), true));

        assertEquals("person1_phone is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidAppellantPhoneNumber_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellantWithPhoneNumber("01234567891"), true));

        assertEquals("01234567891", ((Appeal) response.getTransformedCase().get("appeal")).getAppellant().getContact().getPhone());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAnAppealContainsAnInvalidPostcode_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType("Bla", buildAppellantWithPostcode("Bla Bla"), true));

        assertEquals("person1_postcode is not a valid postcode", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidPostcode_thenDoNotAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellantWithPostcode("CM13 0GD"), true));

        assertEquals("CM13 0GD", ((Appeal) response.getTransformedCase().get("appeal")).getAppellant().getAddress().getPostcode());
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

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_company, representative_title, representative_first_name and representative_last_name are empty. At least one must be populated", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeContainsAFirstNameButDoesNotContainALastNameOrTitleOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsALastNameButDoesNotContainAFirstNameOrTitleOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setTitle(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsATitleButDoesNotContainAFirstNameOrLastNameOrCompany_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.setOrganisation(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeContainsACompanyButDoesNotContainAFirstNameOrLastNameOrTitle_thenDoNotAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getName().setFirstName(null);
        representative.getName().setLastName(null);
        representative.getName().setTitle(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenARepresentativeDoesNotContainAnAddressLine1_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setLine1(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainATown_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setTown(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainACounty_thenAddAWarning() {
        Representative representative = buildRepresentative();
        representative.getAddress().setCounty(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenARepresentativeDoesNotContainAPostcode_thenAddAWarningAndDoNotAddRegionalProcessingCenter() {
        Representative representative = buildRepresentative();
        representative.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(buildMinimumAppealDataWithRepresentative(buildAppellant(false), representative, true));

        assertEquals("representative_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainATitle_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setTitle(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_title is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setFirstName(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getName().setLastName(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setLine1(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine3_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setTown(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLine4_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setCounty(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeDoesNotContainAnAddressLinePostcode_thenAddAWarning() {
        Appellant appellant = buildAppellant(true);
        appellant.getAppointee().getAddress().setPostcode(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, true));

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealWithNoHearingType_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithHearingType(null, buildAppellant(false), true));

        assertEquals("is_hearing_type_oral and/or is_hearing_type_paper is invalid", response.getWarnings().get(0));
    }

    @Test
    public void givenAllMandatoryFieldsForAnAppellantExists_thenDoNotAddAWarning() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false), true);

        CaseResponse response = validator.validate(pairs);

        assertEquals(0, response.getWarnings().size());
    }

    @Test
    public void givenAValidationCallbackTypeWithIncompleteDetails_thenAddAWarningWithCorrectMessage() {

        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant, false));

        assertEquals("Appellant postcode is empty", response.getWarnings().get(0));
        verifyZeroInteractions(regionalProcessingCenterService);
    }

    private Map<String, Object> buildMinimumAppealData(Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(defaultMrnDetails, PIP.name(), appellant, null, null, exceptionCaseType, HEARING_TYPE_ORAL);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrn(MrnDetails mrn, Appellant appellant, Boolean exceptionCaseType) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(mrn, PIP.name(), appellant, null, null, exceptionCaseType, HEARING_TYPE_ORAL);
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
        return buildAppellantWithPhoneNumberAndPostcode(withAppointee, "01234567898", "CM13 0GD");
    }

    private Appellant buildAppellantWithPostcode(String postcode) {
        return buildAppellantWithPhoneNumberAndPostcode(false, "01234567898", postcode);
    }

    private Appellant buildAppellantWithPhoneNumber(String phoneNumber) {
        return buildAppellantWithPhoneNumberAndPostcode(false, phoneNumber, "CM13 0GD");
    }

    private Appellant buildAppellantWithPhoneNumberAndPostcode(Boolean withAppointee, String phoneNumber, String postcode) {
        Appointee appointee = withAppointee ? buildAppointee() : null;

        return Appellant.builder()
            .name(Name.builder().title("Mr").firstName("Bob").lastName("Smith").build())
            .address(Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode(postcode).build())
            .identity(Identity.builder().nino("BB000000B").build())
            .contact(Contact.builder().phone(phoneNumber).build())
            .appointee(appointee).build();
    }

    private Appointee buildAppointee() {

        return Appointee.builder()
            .name(Name.builder().title("Mr").firstName("Tim").lastName("Garwood").build())
            .address(Address.builder().line1("101 My Road").town("Gidea Park").county("Essex").postcode("CM13 0GD").build())
            .identity(Identity.builder().build())
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
