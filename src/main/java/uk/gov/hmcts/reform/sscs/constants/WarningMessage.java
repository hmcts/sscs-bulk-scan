package uk.gov.hmcts.reform.sscs.constants;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.EXCEPTION_CALLBACK;

import uk.gov.hmcts.reform.sscs.domain.CallbackType;

public enum WarningMessage {

    APPELLANT_TITLE(TITLE, "Appellant title"),
    APPOINTEE_TITLE(TITLE, "Appointee title"),
    REPRESENTATIVE_TITLE(TITLE, "Representative title"),
    APPELLANT_FIRST_NAME(FIRST_NAME, "Appellant first name"),
    APPOINTEE_FIRST_NAME(FIRST_NAME, "Appointee first name"),
    REPRESENTATIVE_FIRST_NAME(FIRST_NAME, "Representative first name"),
    APPELLANT_LAST_NAME(LAST_NAME, "Appellant last name"),
    APPOINTEE_LAST_NAME(LAST_NAME, "Appointee last name"),
    REPRESENTATIVE_LAST_NAME(LAST_NAME, "Representative last name"),
    APPELLANT_ADDRESS_LINE1(ADDRESS_LINE1, "Appellant address line 1"),
    APPOINTEE_ADDRESS_LINE1(ADDRESS_LINE1, "Appointee address line 1"),
    REPRESENTATIVE_ADDRESS_LINE1(ADDRESS_LINE1, "Representative address line 1"),
    APPELLANT_ADDRESS_LINE3(ADDRESS_LINE3, "Appellant address town"),
    APPOINTEE_ADDRESS_LINE3(ADDRESS_LINE3, "Appointee address town"),
    REPRESENTATIVE_ADDRESS_LINE3(ADDRESS_LINE3, "Representative address town"),
    APPELLANT_ADDRESS_LINE4(ADDRESS_LINE4, "Appellant address county"),
    APPOINTEE_ADDRESS_LINE4(ADDRESS_LINE4, "Appointee address county"),
    REPRESENTATIVE_ADDRESS_LINE4(ADDRESS_LINE4, "Representative address county"),
    APPELLANT_POSTCODE(ADDRESS_POSTCODE, "Appellant postcode"),
    APPOINTEE_POSTCODE(ADDRESS_POSTCODE, "Appointee postcode"),
    REPRESENTATIVE_POSTCODE(ADDRESS_POSTCODE, "Representative postcode"),
    BENEFIT_TYPE_DESCRIPTION(SscsConstants.BENEFIT_TYPE_DESCRIPTION, "Benefit type description"),
    MRN_DATE(SscsConstants.MRN_DATE, "Mrn date"),
    HEARING_OPTIONS_EXCLUDE_DATES(SscsConstants.HEARING_OPTIONS_EXCLUDE_DATES, "Hearing options exclude dates"),
    APPELLANT_NINO(NINO, "Appellant nino"),
    APPELLANT_PHONE(PHONE, "Appellant phone"),
    APPELLANT_DOB(DOB, "Appellant date of birth"),
    APPOINTEE_DOB(DOB, "Appointee date of birth");

    private String exceptionRecordMessage;
    private String validationRecordMessage;

    WarningMessage(String exceptionRecordMessage, String validationRecordMessage) {
        this.exceptionRecordMessage = exceptionRecordMessage;
        this.validationRecordMessage = validationRecordMessage;
    }

    public static String getMessageByCallbackType(CallbackType callbackType, String personType, String name, String endMessage) {
        String startMessage =  callbackType == EXCEPTION_CALLBACK
            ? personType + valueOf(name.toUpperCase()).exceptionRecordMessage
            : valueOf(name.toUpperCase()).validationRecordMessage;
        return startMessage + " " + endMessage;
    }

}
