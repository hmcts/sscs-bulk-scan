package uk.gov.hmcts.reform.sscs.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;
import uk.gov.hmcts.reform.sscs.domain.ExactMatchList;
import uk.gov.hmcts.reform.sscs.domain.FuzzyMatchList;

@Service
@Slf4j
public class FuzzyMatcherService {

    private static List<String> pipFuzzyList = new FuzzyMatchList() {{
            add("personal");
            add("independence");
            add("p.i.p");
        }};

    private static List<String> pipExactMatchList = new ExactMatchList() {{
            add("pip");
        }};

    private static List<String> esaFuzzyList = new FuzzyMatchList() {{
            add("employment");
            add("support");
            add("e.s.a");
        }};

    private static List<String> esaExactMatchList = new ExactMatchList() {{
            add("esa");
        }};

    private static List<String> ucFuzzyList = new FuzzyMatchList() {{
            add("universal");
            add("credit");
            add("u.c");
        }};

    private static List<String> ucExactMatchList = new ExactMatchList() {{
            add("uc");
        }};

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
