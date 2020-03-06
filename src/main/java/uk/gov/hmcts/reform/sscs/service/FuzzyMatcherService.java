package uk.gov.hmcts.reform.sscs.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;
import uk.gov.hmcts.reform.sscs.domain.ExactMatchList;
import uk.gov.hmcts.reform.sscs.domain.FuzzyMatchList;

@Service
@Slf4j
public class FuzzyMatcherService {

    private static List<String> pipFuzzyList = new FuzzyMatchList();
    private static List<String> pipExactMatchList = new ExactMatchList();
    private static List<String> esaFuzzyList = new FuzzyMatchList();
    private static List<String> esaExactMatchList = new ExactMatchList();
    private static List<String> ucFuzzyList = new FuzzyMatchList();
    private static List<String> ucExactMatchList = new ExactMatchList();

    public FuzzyMatcherService() {
        pipFuzzyList.add("personal");
        pipFuzzyList.add("independence");
        pipFuzzyList.add("p.i.p");

        pipExactMatchList.add("pip");

        esaFuzzyList.add("employment");
        esaFuzzyList.add("support");
        esaFuzzyList.add("e.s.a");

        esaExactMatchList.add("esa");

        ucFuzzyList.add("universal");
        ucFuzzyList.add("credit");
        ucFuzzyList.add("u.c");

        ucExactMatchList.add("uc");
    }

    public String matchBenefitType(String ocrBenefitValue) {
        if (pipFuzzyList.contains(ocrBenefitValue) || pipExactMatchList.contains(ocrBenefitValue)) {
            return Benefit.PIP.name();
        } else if (esaFuzzyList.contains(ocrBenefitValue) || esaExactMatchList.contains(ocrBenefitValue)) {
            return Benefit.ESA.name();
        } else if (ucFuzzyList.contains(ocrBenefitValue) || ucExactMatchList.contains(ocrBenefitValue)) {
            return Benefit.UC.name();
        }
        return ocrBenefitValue;
    }
}
