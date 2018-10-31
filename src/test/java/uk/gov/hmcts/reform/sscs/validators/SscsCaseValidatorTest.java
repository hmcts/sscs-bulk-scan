package uk.gov.hmcts.reform.sscs.validators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.junit.Test;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.Address;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.Name;

public class SscsCaseValidatorTest {

    private SscsCaseValidator validator = new SscsCaseValidator();

    @Test
    public void givenAnAppellantIsEmpty_thenAddAnError() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder().build()).build();

        CaseValidationResponse response = validator.validate(pairs);

        assertEquals("person1 address and name mandatory fields are empty", response.getErrors().get(0));
    }

    @Test
    public void givenAnAppellantDoesNotContainRequiredFields_thenAddAnError() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder().appellant(Appellant.builder().name(Name.builder().firstName("Bob").build()).build()).build()).build();

        CaseValidationResponse response = validator.validate(pairs);

        assertEquals("person1 address and name mandatory fields are empty", response.getErrors().get(0));
    }

    @Test
    public void givenAPerson1IsNotEmpty_thenDoNotAddAnError() {
        Map<String, Object> pairs = ImmutableMap.<String, Object>builder()
            .put("appeal", Appeal.builder().appellant(
                Appellant.builder().name(
                    Name.builder().firstName("Bob").lastName("Smith").build())
                    .address(Address.builder().line1("101 My Road").town("Brentwood").county("Essex").postcode("CM13 0GD")
                        .build()).build()).build()).build();

        CaseValidationResponse response = validator.validate(pairs);

        assertTrue(response.getErrors().isEmpty());
    }

}
