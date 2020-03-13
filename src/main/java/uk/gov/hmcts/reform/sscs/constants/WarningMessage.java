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
    APPELLANT_ADDRESS_LINE2(ADDRESS_LINE2, "Appellant address town"),
    APPOINTEE_ADDRESS_LINE2(ADDRESS_LINE2, "Appointee address town"),
    REPRESENTATIVE_ADDRESS_LINE2(ADDRESS_LINE2, "Representative address town"),
    APPELLANT_ADDRESS_LINE3(ADDRESS_LINE3, "Appellant address town"),
    APPOINTEE_ADDRESS_LINE3(ADDRESS_LINE3, "Appointee address town"),
    REPRESENTATIVE_ADDRESS_LINE3(ADDRESS_LINE3, "Representative address town"),
    APPELLANT_ADDRESS_LINE3_COUNTY(ADDRESS_LINE3, "Appellant address county"),
    APPOINTEE_ADDRESS_LINE3_COUNTY(ADDRESS_LINE3, "Appointee address county"),
    REPRESENTATIVE_ADDRESS_LINE3_COUNTY(ADDRESS_LINE3, "Representative address county"),
    APPELLANT_ADDRESS_LINE4(ADDRESS_LINE4, "Appellant address county"),
    APPOINTEE_ADDRESS_LINE4(ADDRESS_LINE4, "Appointee address county"),
    REPRESENTATIVE_ADDRESS_LINE4(ADDRESS_LINE4, "Representative address county"),
    APPELLANT_POSTCODE(ADDRESS_POSTCODE, "Appellant postcode"),
    APPOINTEE_POSTCODE(ADDRESS_POSTCODE, "Appointee postcode"),
    REPRESENTATIVE_POSTCODE(ADDRESS_POSTCODE, "Representative postcode"),
    BENEFIT_TYPE_DESCRIPTION(SscsConstants.BENEFIT_TYPE_DESCRIPTION, "Benefit type description"),
    MRN_DATE(SscsConstants.MRN_DATE, "Mrn date"),
    OFFICE(ISSUING_OFFICE, "DWP issuing office"),
    HEARING_OPTIONS_EXCLUDE_DATES(HEARING_OPTIONS_EXCLUDE_DATES_LITERAL, "Hearing options exclude dates"),
    APPELLANT_NINO(NINO, "Appellant nino"),
    APPELLANT_MOBILE(MOBILE, "Appellant mobile"),
    APPOINTEE_MOBILE(MOBILE, "Appointee mobile"),
    REPRESENTATIVE_MOBILE(MOBILE, "Representative mobile"),
    APPELLANT_DOB(DOB, "Appellant date of birth"),
    APPOINTEE_DOB(DOB, "Appointee date of birth"),
    HEARING_TYPE("is_hearing_type_oral and/or is_hearing_type_paper", "Hearing type"),
    REPRESENTATIVE_NAME_OR_ORGANISATION("representative_company, representative_first_name and representative_last_name", "Representative organisation, Representative first name and Representative last name");


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
