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

    public static final List<String> ALL_INDICATOR_STRINGS = Arrays.stream(values()).map(v -> v.getIndicatorString()).collect(Collectors.toList());

    private final String indicatorString;
    private final Benefit benefit;

    BenefitTypeIndicator(String indicatorString, Benefit benefit) {
        this.indicatorString = indicatorString;
        this.benefit = benefit;
    }

    public static Optional<Benefit> findByIndicatorString(String indicatorString) {
        return  Arrays.stream(values()).filter(v -> v.getIndicatorString().equals(indicatorString)).map(v -> v.getBenefit()).findFirst();
    }

    public String getIndicatorString() {
        return indicatorString;
    }

    public Benefit getBenefit() {
        return benefit;
    }
}
