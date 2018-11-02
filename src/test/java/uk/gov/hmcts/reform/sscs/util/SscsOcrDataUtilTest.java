package uk.gov.hmcts.reform.sscs.util;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.APPELLANT_TITLE;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class SscsOcrDataUtilTest {

    Map<String, Object> pairs = new HashMap<>();

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
    public void givenBooleanExists_theReturnTrue() {
        assertTrue(findBooleanExists("test"));
    }

    @Test
    public void givenAMap_thenFindField() {
        pairs.put("person1_title", APPELLANT_TITLE);

        assertEquals(APPELLANT_TITLE, SscsOcrDataUtil.getField(pairs, "person1_title"));
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
    public void givenABooleanValue_thenReturnTrue() {
        pairs.put("hearing_type_oral", true);

        assertTrue(areBooleansValid(pairs, new ArrayList<>(), "hearing_type_oral"));
    }

    @Test
    public void givenABooleanValueWithText_thenReturnFalseAndAddError() {
        pairs.put("hearing_type_oral", "blue");

        List<String> errors = new ArrayList<>();

        assertFalse(areBooleansValid(pairs, errors, "hearing_type_oral"));
        assertEquals("hearing_type_oral does not contain a valid boolean value. Needs to be true or false", errors.get(0));
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
    public void givenAnOcrDateWithInvalidFormat_thenAddError() {
        pairs.put("hearingDate", "01/30/2018");

        List<String> errors = new ArrayList<>();

        generateDateForCcd(pairs, errors, "hearingDate");

        assertEquals("hearingDate is an invalid date field. Needs to be in the format dd/mm/yyyy", errors.get(0));
    }
}
