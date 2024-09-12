package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.domain.CallbackType.EXCEPTION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.ccd.validation.sscscasedata.AppealValidator;
import uk.gov.hmcts.reform.sscs.domain.CallbackType;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@Component
@Slf4j
public class SscsCaseValidator {

    public static final String IS_NOT_A_VALID_POSTCODE = "is not a valid postcode";

    @SuppressWarnings("squid:S5843")
    private static final String PHONE_REGEX =
        "^((?:(?:\\(?(?:0(?:0|11)\\)?[\\s-]?\\(?|\\+)\\d{1,4}\\)?[\\s-]?(?:\\(?0\\)?[\\s-]?)?)|(?:\\(?0))(?:"
            + "(?:\\d{5}\\)?[\\s-]?\\d{4,5})|(?:\\d{4}\\)?[\\s-]?(?:\\d{5}|\\d{3}[\\s-]?\\d{3}))|(?:\\d{3}\\)"
            + "?[\\s-]?\\d{3}[\\s-]?\\d{3,4})|(?:\\d{2}\\)?[\\s-]?\\d{4}[\\s-]?\\d{4}))(?:[\\s-]?(?:x|ext\\.?|\\#)"
            + "\\d{3,4})?)?$";

    @SuppressWarnings("squid:S5843")
    private static final String UK_NUMBER_REGEX =
        "^\\(?(?:(?:0(?:0|11)\\)?[\\s-]?\\(?|\\+)44\\)?[\\s-]?\\(?(?:0\\)?[\\s-]?\\(?)?|0)(?:\\d{2}\\)?[\\s-]?\\d{4}"
            + "[\\s-]?\\d{4}|\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{3,4}|\\d{4}\\)?[\\s-]?(?:\\d{5}|\\d{3}[\\s-]?\\d{3})|"
            +
            "\\d{5}\\)?[\\s-]?\\d{4,5}|8(?:00[\\s-]?11[\\s-]?11|45[\\s-]?46[\\s-]?4\\d))(?:(?:[\\s-]?(?:x|ext\\.?\\s?|"
            + "\\#)\\d+)?)$";

    @SuppressWarnings("squid:S5843")
    private static final String ADDRESS_REGEX =
        "^[a-zA-ZÀ-ž0-9]{1}[a-zA-ZÀ-ž0-9 \\r\\n\\.“”\",’\\?\\!\\[\\]\\(\\)/£:\\\\_+\\-%&;]{1,}$";

    @SuppressWarnings("squid:S5843")
    private static final String COUNTY_REGEX =
        "^\\.$|^[a-zA-ZÀ-ž0-9]{1}[a-zA-ZÀ-ž0-9 \\r\\n\\.“”\",’\\?\\!\\[\\]\\(\\)/£:\\\\_+\\-%&;]{1,}$";

    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final DwpAddressLookupService dwpAddressLookupService;
    private final PostcodeValidator postcodeValidator;
    private final SscsJsonExtractor sscsJsonExtractor;
    private final AppealValidator appealValidator;
    List<String> warnings;
    List<String> errors;
    private CallbackType callbackType;
    @Value("#{'${validation.titles}'.split(',')}")
    private List<String> titles;

    //TODO: Remove when uc-office-feature switched on
    private boolean ucOfficeFeatureActive;

    public SscsCaseValidator(RegionalProcessingCenterService regionalProcessingCenterService,
                             DwpAddressLookupService dwpAddressLookupService,
                             PostcodeValidator postcodeValidator,
                             SscsJsonExtractor sscsJsonExtractor,
                             AppealValidator appealValidator,
                             @Value("${feature.uc-office-feature.enabled}") boolean ucOfficeFeatureActive) {
        this.regionalProcessingCenterService = regionalProcessingCenterService;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.postcodeValidator = postcodeValidator;
        this.sscsJsonExtractor = sscsJsonExtractor;
        this.appealValidator = appealValidator;
        this.ucOfficeFeatureActive = ucOfficeFeatureActive;
    }

    public void setUcOfficeFeatureActive(boolean ucOfficeFeatureActive) {
        this.ucOfficeFeatureActive = ucOfficeFeatureActive;
    }

    private List<String> combineWarnings() {
        List<String> mergedWarnings = new ArrayList<>();

        mergedWarnings.addAll(warnings);
        mergedWarnings.addAll(errors);
        errors.clear();

        return mergedWarnings;
    }

    public CaseResponse validateExceptionRecord(CaseResponse transformResponse, ExceptionRecord exceptionRecord,
                                                Map<String, Object> caseData, boolean combineWarnings) {
        warnings =
            transformResponse != null && transformResponse.getWarnings() != null ? transformResponse.getWarnings() :
                new ArrayList<>();
        errors = new ArrayList<>();
        callbackType = EXCEPTION_CALLBACK;

        ScannedData ocrCaseData = sscsJsonExtractor.extractJson(exceptionRecord);

        boolean ignoreWarningsValue = exceptionRecord.getIgnoreWarnings() != null ? exceptionRecord.getIgnoreWarnings() : false;
        warnings = (List<String>) appealValidator.validateAppeal(ocrCaseData.getOcrCaseData(), caseData, false, ignoreWarningsValue, true); //need to add warnings since its from another class

        if (combineWarnings) {
            warnings = combineWarnings();
        }

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .status(getValidationStatus(errors, warnings))
            .build();
    }
}
