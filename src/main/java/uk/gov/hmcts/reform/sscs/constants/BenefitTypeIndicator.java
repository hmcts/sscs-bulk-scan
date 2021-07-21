package uk.gov.hmcts.reform.sscs.constants;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;

public enum BenefitTypeIndicator {
    PIP("is_benefit_type_pip", Benefit.PIP),
    ESA("is_benefit_type_esa", Benefit.ESA),
    UC("is_benefit_type_uc", Benefit.UC);
    
    private final String indicatorString;
    private final Benefit benefit;

    BenefitTypeIndicator(String indicatorString, Benefit benefit) {
        this.indicatorString = indicatorString;
        this.benefit = benefit;
    }

    public static List<String> getAllIndicatorStrings() {
        return Arrays.stream(values()).map(BenefitTypeIndicator::getIndicatorString).collect(Collectors.toList());
    }

    public static Optional<Benefit> findByIndicatorString(String indicatorString) {
        return  Arrays.stream(values()).filter(v -> v.getIndicatorString().equals(indicatorString)).map(BenefitTypeIndicator::getBenefit).findFirst();
    }

    public String getIndicatorString() {
        return indicatorString;
    }

    public Benefit getBenefit() {
        return benefit;
    }
}
