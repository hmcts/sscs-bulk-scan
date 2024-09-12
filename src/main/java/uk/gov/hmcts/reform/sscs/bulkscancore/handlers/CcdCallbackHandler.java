package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.util.CollectionUtils.isEmpty;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.EventType;
import uk.gov.hmcts.reform.sscs.domain.transformation.CaseCreationDetails;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.sscs.exceptions.InvalidExceptionRecordException;
import uk.gov.hmcts.reform.sscs.handler.InterlocReferralReasonOptions;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.validators.SscsCaseValidator;

@Slf4j
@Component
public class CcdCallbackHandler {

    private static final String LOGSTR_VALIDATION_ERRORS = "Errors found while validating exception record id {} - {}";
    private static final String LOGSTR_VALIDATION_WARNING = "Warnings found while validating exception record id {} - {}";
    private static final String CASE_TYPE_ID = "Benefit";

    private final SscsDataHelper sscsDataHelper;
    private final SscsCaseValidator caseValidator;
    private final CaseTransformer caseTransformer;

    public CcdCallbackHandler(SscsDataHelper sscsDataHelper,
                              SscsCaseValidator caseValidator,
                              CaseTransformer caseTransformer) {
        this.sscsDataHelper = sscsDataHelper;
        this.caseValidator = caseValidator;
        this.caseTransformer = caseTransformer;
    }

    public CaseResponse handleValidation(ExceptionRecord exceptionRecord) {

        log.info("Processing callback for SSCS exception record");

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecord(exceptionRecord, true);

        if (caseTransformationResponse.getErrors() != null && !caseTransformationResponse.getErrors().isEmpty()) {
            log.info("Errors found during validation");
            return caseTransformationResponse;
        }

        log.info("Exception record id {} transformed successfully ready for validation", exceptionRecord.getId());

        return caseValidator.validateExceptionRecord(caseTransformationResponse, exceptionRecord, caseTransformationResponse.getTransformedCase(), true);
    }

    public SuccessfulTransformationResponse handle(ExceptionRecord exceptionRecord) {
        // New transformation request contains exceptionRecordId
        // Old transformation request contains id field, which is the exception record id
        String exceptionRecordId = isNotBlank(exceptionRecord.getExceptionRecordId()) ? exceptionRecord.getExceptionRecordId() : exceptionRecord.getId();

        log.info("Processing callback for SSCS exception record id {}", exceptionRecordId);
        log.info("IsAutomatedProcess: {}", exceptionRecord.getIsAutomatedProcess());

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecord(exceptionRecord, false);

        if (caseTransformationResponse.getErrors() != null && !caseTransformationResponse.getErrors().isEmpty()) {
            log.info("Errors found while transforming exception record id {} - {}", exceptionRecordId, stringJoin(caseTransformationResponse.getErrors()));
            throw new InvalidExceptionRecordException(caseTransformationResponse.getErrors());
        }

        if (BooleanUtils.isTrue(exceptionRecord.getIsAutomatedProcess()) && !isEmpty(caseTransformationResponse.getWarnings())) {
            log.info("Warning found while transforming exception record id {}", exceptionRecordId);
            throw new InvalidExceptionRecordException(caseTransformationResponse.getWarnings());
        }

        log.info("Exception record id {} transformed successfully. About to validate transformed case from exception", exceptionRecordId);

        CaseResponse caseValidationResponse = caseValidator.validateExceptionRecord(caseTransformationResponse, exceptionRecord, caseTransformationResponse.getTransformedCase(), false);

        if (!isEmpty(caseValidationResponse.getErrors())) {
            log.info(LOGSTR_VALIDATION_ERRORS, exceptionRecordId, stringJoin(caseValidationResponse.getErrors()));
            throw new InvalidExceptionRecordException(caseValidationResponse.getErrors());
        } else if (BooleanUtils.isTrue(exceptionRecord.getIsAutomatedProcess()) && !isEmpty(caseValidationResponse.getWarnings())) {
            log.info(LOGSTR_VALIDATION_WARNING, exceptionRecordId, stringJoin(caseValidationResponse.getWarnings()));
            throw new InvalidExceptionRecordException(caseValidationResponse.getWarnings());
        } else {
            String eventId = sscsDataHelper.findEventToCreateCase(caseValidationResponse);

            stampReferredCase(caseValidationResponse, eventId);

            return new SuccessfulTransformationResponse(
                new CaseCreationDetails(
                    CASE_TYPE_ID,
                    eventId,
                    caseValidationResponse.getTransformedCase()
                ),
            caseValidationResponse.getWarnings(), Map.of("$set", Map.of("HMCTSServiceId", "BBA3")));
        }
    }

    private String stringJoin(List<String> messages) {
        return String.join(". ", messages);
    }

    private void stampReferredCase(CaseResponse caseValidationResponse, String eventId) {
        if (EventType.NON_COMPLIANT.getCcdType().equals(eventId)) {
            Map<String, Object> transformedCase = caseValidationResponse.getTransformedCase();
            Appeal appeal = (Appeal) transformedCase.get("appeal");
            if (appealReasonIsNotBlank(appeal)) {
                transformedCase.put("interlocReferralReason",
                    InterlocReferralReasonOptions.OVER_13_MONTHS.getValue());
            } else {
                transformedCase.put("interlocReferralReason",
                    InterlocReferralReasonOptions.OVER_13_MONTHS_AND_GROUNDS_MISSING.getValue());
            }
        }
    }

    private boolean appealReasonIsNotBlank(Appeal appeal) {
        return appeal.getAppealReasons() != null && (StringUtils.isNotBlank(appeal.getAppealReasons().getOtherReasons())
            || reasonsIsNotBlank(appeal));
    }

    private boolean reasonsIsNotBlank(Appeal appeal) {
        return !isEmpty(appeal.getAppealReasons().getReasons())
            && appeal.getAppealReasons().getReasons().get(0) != null
            && appeal.getAppealReasons().getReasons().get(0).getValue() != null
            && (StringUtils.isNotBlank(appeal.getAppealReasons().getReasons().get(0).getValue().getReason())
            || StringUtils.isNotBlank(appeal.getAppealReasons().getReasons().get(0).getValue().getDescription()));
    }

}
