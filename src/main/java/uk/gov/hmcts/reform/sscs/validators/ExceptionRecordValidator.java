package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.ccd.callback.ValidationType.EXCEPTION_RECORD;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.ccd.validation.sscscasedata.AddressValidator;
import uk.gov.hmcts.reform.sscs.ccd.validation.sscscasedata.AppealValidator;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@Component
@Slf4j
public class ExceptionRecordValidator extends AppealValidator {
    private final SscsJsonExtractor sscsJsonExtractor;

    public ExceptionRecordValidator(SscsJsonExtractor sscsJsonExtractor,
                                    DwpAddressLookupService dwpAddressLookupService,
                                    AddressValidator addressValidator,
                                    List<String> titles) {
        super(dwpAddressLookupService, addressValidator, EXCEPTION_RECORD, titles);
        this.sscsJsonExtractor = sscsJsonExtractor;
    }

    public CaseResponse validateExceptionRecord(CaseResponse transformResponse, ExceptionRecord exceptionRecord,
                                                Map<String, Object> caseData, boolean combineWarnings) {

        List<String> warnings =
            transformResponse != null && transformResponse.getWarnings() != null
                ? transformResponse.getWarnings() : new ArrayList<>();
        List<String> errors  = new ArrayList<>();

        ScannedData ocrCaseData = sscsJsonExtractor.extractJson(exceptionRecord);

        boolean ignoreWarningsValue = exceptionRecord.getIgnoreWarnings() != null ? exceptionRecord.getIgnoreWarnings() : false;
        var errsWarns = validateAppeal(ocrCaseData.getOcrCaseData(), caseData, false, ignoreWarningsValue, true);

        if (combineWarnings) {
            warnings = combineWarnings(errors, warnings);
        }

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .status(getValidationStatus(errors, warnings))
            .build();
    }

    private List<String> combineWarnings(List<String> errors, List<String> warnings) {
        List<String> mergedWarnings = new ArrayList<>();

        mergedWarnings.addAll(warnings);
        mergedWarnings.addAll(errors);
        errors.clear();

        return mergedWarnings;
    }
}
