package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.hmcts.reform.sscs.TestDataConstants.APPELLANT_TITLE;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;

public class SscsCaseValidatorTest {

    SscsCaseValidator validator = new SscsCaseValidator();

    @Before
    public void setup() {
        ReflectionTestUtils.setField(validator, "displayCaseError", true);
    }

    @Test
    public void givenAPerson1AndPerson2AreBothEmpty_thenAddAnError() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder().build();

        CaseValidationResponse response = validator.validate(pairs);

        assertEquals("person1 and person2 are both empty", response.getErrors().get(0));
    }

    @Test
    public void givenAPerson1IsNotEmpty_thenDoNotAddAnError() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("person1_title", APPELLANT_TITLE).build();

        CaseValidationResponse response = validator.validate(pairs);

        assertTrue(response.getErrors().isEmpty());
    }

    @Test
    public void givenAPerson2IsNotEmpty_thenDoNotAddAnError() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("person2_title", APPELLANT_TITLE).build();

        CaseValidationResponse response = validator.validate(pairs);

        assertTrue(response.getErrors().isEmpty());
    }

}
