package uk.gov.hmcts.reform.sscs.util;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.*;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SscsOcrDataUtilTest {

    Map<String, Object> pairs = new HashMap<>();
    List<String> errors = new ArrayList<>();

    @Test
    public void givenAPersonExists_thenReturnTrue() {
        pairs.put("person1_title", APPELLANT_TITLE);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonDoesNotExist_thenReturnTrue() {
        pairs.put("person1_title", APPELLANT_TITLE);
        assertFalse(hasPerson(pairs, "person2"));
    }

    @Test
    public void givenAPersonFirstNameExists_thenReturnTrue() {
        pairs.put("person1_first_name", APPELLANT_FIRST_NAME);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonLastNameExists_thenReturnTrue() {
        pairs.put("person1_last_name", APPELLANT_LAST_NAME);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonAddressLine1Exists_thenReturnTrue() {
        pairs.put("person1_address_line1", APPELLANT_ADDRESS_LINE1);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonAddressLine2Exists_thenReturnTrue() {
        pairs.put("person1_address_line2", APPELLANT_ADDRESS_LINE2);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonAddressLine3Exists_thenReturnTrue() {
        pairs.put("person1_address_line3", APPELLANT_ADDRESS_LINE3);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonAddressLine4Exists_thenReturnTrue() {
        pairs.put("person1_address_line4", APPELLANT_ADDRESS_LINE4);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonAddressPostcodeExists_thenReturnTrue() {
        pairs.put("person1_postcode", APPELLANT_POSTCODE);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonDateOfBirthExists_thenReturnTrue() {
        pairs.put("person1_dob", APPELLANT_DATE_OF_BIRTH);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonNinoExists_thenReturnTrue() {
        pairs.put("person1_nino", APPELLANT_NINO);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonCompanyExists_thenReturnTrue() {
        pairs.put("person1_company", APPOINTEE_COMPANY);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenAPersonPhoneExists_thenReturnTrue() {
        pairs.put("person1_phone", APPELLANT_PHONE);
        assertTrue(hasPerson(pairs, "person1"));
    }

    @Test
    public void givenBooleanExists_theReturnTrue() {
        assertTrue(findBooleanExists("test"));
    }

    @Test
    public void givenAMap_thenFindField() {
        pairs.put("person1_title", APPELLANT_TITLE);

        assertEquals(APPELLANT_TITLE, SscsOcrDataUtil.getField(pairs, "person1_title"));
    }

    @Test
    public void givenAFieldWithEmptyValue_thenReturnNull() {
        pairs.put("person1_title", null);

        assertNull(SscsOcrDataUtil.getField(pairs, "person1_title"));
    }

    @Test
    public void givenAMapWhichDoesNotContainField_thenReturnNull() {
        assertNull(SscsOcrDataUtil.getField(pairs, "test"));
    }

    @Test
    public void givenTwoBooleansContradict_thenReturnTrue() {
        pairs.put("hearing_type_oral", true);
        pairs.put("hearing_type_paper", true);

        assertTrue(doValuesContradict(pairs, new ArrayList<>(), "hearing_type_oral", "hearing_type_paper"));
    }

    @Test
    public void givenTwoBooleansDoNotContradict_thenReturnFalse() {
        pairs.put("hearing_type_oral", true);
        pairs.put("hearing_type_paper", false);

        assertFalse(doValuesContradict(pairs, new ArrayList<>(), "hearing_type_oral", "hearing_type_paper"));
    }

    @Test
    public void givenAValidBooleanValue_thenReturnTrue() {
        pairs.put("hearing_type_oral", true);

        assertTrue(areBooleansValid(pairs, errors, "hearing_type_oral"));
    }

    @Test
    public void givenMultipleBooleanValues_thenReturnTrue() {
        pairs.put("hearing_type_oral", true);
        pairs.put("hearing_type_paper", false);

        assertTrue(areBooleansValid(pairs, errors, "hearing_type_oral", "hearing_type_paper"));
    }

    @Test
    public void givenABooleanValueWithText_thenReturnFalse() {
        pairs.put("hearing_type_oral", "blue");

        assertFalse(areBooleansValid(pairs, errors, "hearing_type_oral"));
    }

    @Test
    public void givenTrue_thenReturnYes() {
        assertEquals("Yes", convertBooleanToYesNoString(true));
    }

    @Test
    public void givenFalse_thenReturnNo() {
        assertEquals("No", convertBooleanToYesNoString(false));
    }

    @Test
    public void givenAnOcrDate_thenConvertToCcdDateFormat() {
        pairs.put("hearingDate", "01/01/2018");

        assertEquals("2018-01-01", generateDateForCcd(pairs, new ArrayList<>(), "hearingDate"));
    }

    @Test
    public void givenAnOcrDateWithNoLeadingZero_thenConvertToCcdDateFormat() {
        pairs.put("hearingDate", "1/1/2018");

        assertEquals("2018-01-01", generateDateForCcd(pairs, new ArrayList<>(), "hearingDate"));
    }

    @Test
    public void givenAnOcrDateWithInvalidFormat_thenAddError() {
        pairs.put("hearingDate", "01/30/2018");

        List<String> errors = new ArrayList<>();

        generateDateForCcd(pairs, errors, "hearingDate");

        assertEquals("hearingDate is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy", errors.get(0));
    }

    @Test
    public void givenAnOcrInvalidDate_thenAddError() {
        pairs.put("hearingDate", "29/02/2018");

        List<String> errors = new ArrayList<>();

        generateDateForCcd(pairs, errors, "hearingDate");

        assertEquals("hearingDate is an invalid date field. Needs to be a valid date and in the format dd/mm/yyyy", errors.get(0));
    }

    @Test
    public void givenAnInvalidBooleanValue_thenAddError() {
        pairs.put("hearing_options_hearing_loop", "Yrs");

        List<String> errors = new ArrayList<>();

        areBooleansValid(pairs, errors, "hearing_options_hearing_loop");

        assertEquals("hearing_options_hearing_loop has an invalid value. Should be Yes/No or True/False", errors.get(0));
    }
}
