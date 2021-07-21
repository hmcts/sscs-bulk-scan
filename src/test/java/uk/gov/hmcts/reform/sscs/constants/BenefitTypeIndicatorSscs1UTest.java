package uk.gov.hmcts.reform.sscs.constants;

import junitparams.JUnitParamsRunner;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;

import java.util.Optional;

import static org.junit.Assert.*;

@RunWith(JUnitParamsRunner.class)
public class BenefitTypeIndicatorSscs1UTest {

    @Test
    public void testAllBenefitTypesIndicatorsAreConfiguredCorrectly() {
        for (BenefitTypeIndicatorSscs1U benefitTypeIndicator : BenefitTypeIndicatorSscs1U.values()) {
            // Assert that each BenefitTypeIndicator enum has both an indicator string and a benefit
            assertNotNull(benefitTypeIndicator.getIndicatorString());
            if (!benefitTypeIndicator.getIndicatorString().equals("is_benefit_type_other")) {
                assertNotNull(benefitTypeIndicator.getBenefit());
                // Assert that if we lookup the benefit type indicator via it's indicator string, we
                // find the same benefit as has been configured.
                Optional<Benefit> benefitTypeLookup = BenefitTypeIndicator.findByIndicatorString(benefitTypeIndicator.getIndicatorString());
                assertTrue(benefitTypeLookup.isPresent());
                assertEquals(benefitTypeLookup.get(), benefitTypeIndicator.getBenefit());
            }
        }
    }

    @Test
    public void testLookupByInvalidBenefitTypeIndicatorReturnsEmptyOptional() {
        Optional<Benefit> optional = BenefitTypeIndicatorSscs1U.findByIndicatorString("something");
        assertTrue(optional.isEmpty());
    }

    @Test
    public void testGetAllIndicatorStrings() {
        assertEquals(BenefitTypeIndicatorSscs1U.values().length, BenefitTypeIndicatorSscs1U.getAllIndicatorStrings().size());
    }
}
