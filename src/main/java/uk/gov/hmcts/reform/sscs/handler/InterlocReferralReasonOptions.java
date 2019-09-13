package uk.gov.hmcts.reform.sscs.handler;

public enum InterlocReferralReasonOptions {
    OVER_13_MONTHS("over13months"), GROUNDS_MISSING("groundsMissing");
    private String value;

    public String getValue() {
        return value;
    }

    InterlocReferralReasonOptions(String value) {
        this.value = value;
    }
}
