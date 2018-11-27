package uk.gov.hmcts.reform.sscs.validators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.PIP;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.BENEFIT_TYPE_DESCRIPTION;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;

public class SscsCaseValidatorTest {

    private SscsCaseValidator validator = new SscsCaseValidator();

    @Test
    public void givenAnAppellantIsEmpty_thenAddAWarning() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder().build()).build();

        CaseResponse response = validator.validate(pairs);

        assertThat(response.getWarnings())
            .containsOnly("person1_first_name is empty",
                "person1_last_name is empty",
                "person1_address_line1 is empty",
                "person1_address_line3 is empty",
                "person1_address_line4 is empty",
                "person1_postcode is empty",
                "person1_nino is empty",
                "mrn_date is empty",
                "benefit_type_description is empty");
    }

    @Test
    public void givenAnAppellantWithNoName_thenAddWarnings() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder().appellant(Appellant.builder().address(
                Address.builder().line1("123 The Road").town("Harlow").county("Essex").postcode("CM13FG").build())
                .identity(Identity.builder().nino("JT1234567B").build()).build())
                .benefitType(BenefitType.builder().code(PIP.name()).build())
                .mrnDetails(MrnDetails.builder().mrnDate("12/12/2018").build()).build()).build();

        CaseResponse response = validator.validate(pairs);

        assertThat(response.getWarnings())
            .containsOnly("person1_first_name is empty",
                "person1_last_name is empty");
    }

    @Test
    public void givenAnAppellantWithNoAddress_thenAddWarnings() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder().appellant(Appellant.builder().name(
                Name.builder().firstName("Harry").lastName("Kane").build())
                .identity(Identity.builder().nino("JT1234567B").build()).build())
                .benefitType(BenefitType.builder().code(PIP.name()).build())
                .mrnDetails(MrnDetails.builder().mrnDate("12/12/2018").build()).build()).build();

        CaseResponse response = validator.validate(pairs);

        assertThat(response.getWarnings())
            .containsOnly("person1_address_line1 is empty",
                "person1_address_line3 is empty",
                "person1_address_line4 is empty",
                "person1_postcode is empty");
    }

    @Test
    public void givenAnAppellantDoesNotContainAFirstName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setFirstName(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_first_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainALastName_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getName().setLastName(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_last_name is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAnAddressLine1_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setLine1(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_address_line1 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainATown_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setTown(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_address_line3 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainACounty_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setCounty(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_address_line4 is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainAPostcode_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getAddress().setPostcode(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_postcode is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainANino_thenAddAWarning() {
        Appellant appellant = buildAppellant(false);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person1_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppointeeExistsAndAnAppellantDoesNotContainANino_thenAddAWarningAboutPerson2() {
        Appellant appellant = buildAppellant(true);
        appellant.getIdentity().setNino(null);

        CaseResponse response = validator.validate(buildMinimumAppealData(appellant));

        assertEquals("person2_nino is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainAnMrnDate_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithMrnDate(null, buildAppellant(false)));

        assertEquals("mrn_date is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealDoesNotContainABenefitTypeDescription_thenAddAWarning() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(null, buildAppellant(false)));

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " is empty", response.getWarnings().get(0));
    }

    @Test
    public void givenAnAppealContainsAnInvalidBenefitTypeDescription_thenAddAnError() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType("Bla", buildAppellant(false)));

        List<String> benefitNameList = new ArrayList<>();
        for (Benefit be : Benefit.values()) {
            benefitNameList.add(be.name());
        }

        assertEquals(BENEFIT_TYPE_DESCRIPTION + " invalid. Should be one of: " + String.join(", ", benefitNameList), response.getErrors().get(0));
    }

    @Test
    public void givenAnAppealContainsAValidLowercaseBenefitTypeDescription_thenAddAnError() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name().toLowerCase(), buildAppellant(false)));

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
    public void givenAnAppealContainsAValidBenefitTypeDescription_thenAddAnError() {
        CaseResponse response = validator.validate(buildMinimumAppealDataWithBenefitType(PIP.name(), buildAppellant(false)));

        assertEquals("PIP", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getCode());
        assertEquals("Personal Independence Payment", ((Appeal) response.getTransformedCase().get("appeal")).getBenefitType().getDescription());
        assertEquals(0, response.getWarnings().size());
        assertEquals(0, response.getErrors().size());
    }

    @Test
    public void givenAllMandatoryFieldsForAnAppellantExists_thenDoNotAddAWarning() {
        Map<String, Object> pairs = buildMinimumAppealData(buildAppellant(false));

        CaseResponse response = validator.validate(pairs);

        assertEquals(0, response.getWarnings().size());
    }

    private Map<String, Object> buildMinimumAppealData(Appellant appellant) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType("12/12/2018", PIP.name(), appellant);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrnDate(String mrnDate, Appellant appellant) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType(mrnDate, PIP.name(), appellant);
    }

    private Map<String, Object> buildMinimumAppealDataWithBenefitType(String benefitCode, Appellant appellant) {
        return buildMinimumAppealDataWithMrnDateAndBenefitType("12/12/2018", benefitCode, appellant);
    }

    private Map<String, Object> buildMinimumAppealDataWithMrnDateAndBenefitType(String mrnDate, String benefitCode, Appellant appellant) {
        return ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder()
                .mrnDetails(MrnDetails.builder().mrnDate(mrnDate).build())
                .benefitType(BenefitType.builder().code(benefitCode).build())
                .appellant(appellant).build()).build();
    }

    private Appellant buildAppellant(Boolean withAppointee) {
        Appointee appointee = withAppointee ? Appointee.builder().build() : null;

        return Appellant.builder()
            .name(Name.builder().firstName("Bob").lastName("Smith").build())
            .address(Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode("CM13 0GD").build())
            .identity(Identity.builder().nino("JT01234567B").build())
            .appointee(appointee).build();
    }

}
