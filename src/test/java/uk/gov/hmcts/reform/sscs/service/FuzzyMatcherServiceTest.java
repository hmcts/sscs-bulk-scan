package uk.gov.hmcts.reform.sscs.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class FuzzyMatcherServiceTest {

    FuzzyMatcherService fuzzyMatcherService;

    @Before
    public void setup() {
        initMocks(this);

        fuzzyMatcherService = new FuzzyMatcherService();
    }

    @Test
    @Parameters({"personal", "personal test", "PERSONAL", "independence", "personal independence", "personal independence payment", "independence test", "PIP", "p.i.p."})
    public void givenAWord_thenFuzzyMatchToPip(String ocrValue) {
        String result = fuzzyMatcherService.matchBenefitType(ocrValue);

        assertEquals("PIP", result);
    }

    @Test
    @Parameters({"employment", "employment test", "EMPLOYMENT", "support", "ESA", "E.S.A", "employmentsupportallowance", "Employment Support Allowance"})
    public void givenAWord_thenFuzzyMatchToEsa(String ocrValue) {
        String result = fuzzyMatcherService.matchBenefitType(ocrValue);

        assertEquals("ESA", result);
    }

    @Test
    @Parameters({"universal", "universal test", "UNIVERSAL", "credit", "UC", "U.C", "UniversalCredit", "Universal Credit"})
    public void givenAWord_thenFuzzyMatchToUc(String ocrValue) {
        String result = fuzzyMatcherService.matchBenefitType(ocrValue);

        assertEquals("UC", result);
    }

}
