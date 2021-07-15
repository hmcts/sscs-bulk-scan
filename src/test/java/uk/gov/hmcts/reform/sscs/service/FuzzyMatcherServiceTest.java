package uk.gov.hmcts.reform.sscs.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;

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

    @Test
    @Parameters({"unknown", "sdfhkjsdh", "fisls", "livewitre", "dlsb", "iida", "personel"})
    public void unknownCodeWillBeReturned(String unknownCode) {
        assertThat(fuzzyMatcherService.matchBenefitType(unknownCode), is(unknownCode));
    }

    @Test
    @Parameters({
        "pip, PIP",
        "esa, ESA",
        "Attendance, ATTENDANCE_ALLOWANCE",
        "att, ATTENDANCE_ALLOWANCE",
        "A.A, ATTENDANCE_ALLOWANCE",
        "AA, ATTENDANCE_ALLOWANCE",
        "Disability, DLA",
        "Living, DLA",
        "liv, DLA",
        "livi, DLA",
        "livin, DLA",
        "D.L.A, DLA",
        "D.L.A, DLA",
        "DLA, DLA",
        "DLA test, DLA",
        "Disability Living Allowance, DLA",
        "Income, INCOME_SUPPORT",
        "inc, INCOME_SUPPORT",
        "inco, INCOME_SUPPORT",
        "incom, INCOME_SUPPORT",
        "Income Support, INCOME_SUPPORT",
        "incomesupport, INCOME_SUPPORT",
        "incomesupport test, INCOME_SUPPORT",
        "I.S, INCOME_SUPPORT",
        "Injuries, IIDB",
        "Disablement, IIDB",
        "IIDB, IIDB",
        "I.I.D.B, IIDB",
        "Industrial Injuries Disablement Benefit, IIDB",
        "Job, JSA",
        "JSA, JSA",
        "J.S.A, JSA",
        "Job Seekers Allowance, JSA",
        "jobseekersallowance, JSA",
        "Social, SOCIAL_FUND",
        "Fund, SOCIAL_FUND",
        "SF, SOCIAL_FUND",
        "Social Fund', SOCIAL_FUND",
        "socialfund', SOCIAL_FUND",
        "PC, PENSION_CREDITS",
        "P.C, PENSION_CREDITS",
        "Pension Credits', PENSION_CREDITS",
        "pensioncredits', PENSION_CREDITS",
        "Retirement, RETIREMENT_PENSION",
        "Ret, RETIREMENT_PENSION",
        "Reti, RETIREMENT_PENSION",
        "Retir, RETIREMENT_PENSION",
        "Retire, RETIREMENT_PENSION",
        "Retirem, RETIREMENT_PENSION",
        "Retireme, RETIREMENT_PENSION",
        "Retiremen, RETIREMENT_PENSION",
        "RP, RETIREMENT_PENSION",
        "R.P, RETIREMENT_PENSION",
        "Retirement Pension, RETIREMENT_PENSION",
        "retirementpension, RETIREMENT_PENSION",
        "BB, BEREAVEMENT_BENEFIT",
        "B.B, BEREAVEMENT_BENEFIT",
        "Bereavement Benefit, BEREAVEMENT_BENEFIT",
        "bereavementbenefit, BEREAVEMENT_BENEFIT",
        "Carers, CARERS_ALLOWANCE",
        "Carers, CARERS_ALLOWANCE",
        "Care, CARERS_ALLOWANCE",
        "Car, CARERS_ALLOWANCE",
        "C.A, CARERS_ALLOWANCE",
        "Carer's Allowance, CARERS_ALLOWANCE",
        "carersallowance, CARERS_ALLOWANCE",
        "Maternity, MATERNITY_ALLOWANCE",
        "Mat, MATERNITY_ALLOWANCE",
        "Mate, MATERNITY_ALLOWANCE",
        "Mater, MATERNITY_ALLOWANCE",
        "Matern, MATERNITY_ALLOWANCE",
        "Materni, MATERNITY_ALLOWANCE",
        "Maternit, MATERNITY_ALLOWANCE",
        "M.A, MATERNITY_ALLOWANCE",
        "Maternity Allowance', MATERNITY_ALLOWANCE",
        "maternityallowance', MATERNITY_ALLOWANCE",
        "BSPS, BEREAVEMENT_SUPPORT_PAYMENT_SCHEME",
        "B.S.P.A, BEREAVEMENT_SUPPORT_PAYMENT_SCHEME",
        "Bereavement Support Payment Scheme', BEREAVEMENT_SUPPORT_PAYMENT_SCHEME",
        "bereavementsupportpaymentscheme', BEREAVEMENT_SUPPORT_PAYMENT_SCHEME",
    })
    public void matchBenefitType(String code, Benefit expectedBenefit) {
        final String result = fuzzyMatcherService.matchBenefitType(code);
        assertThat(result, is(expectedBenefit.getShortName()));
    }

}
