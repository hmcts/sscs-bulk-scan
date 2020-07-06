package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.PreSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.EventType;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.domain.transformation.CaseCreationDetails;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.sscs.exceptions.InvalidExceptionRecordException;
import uk.gov.hmcts.reform.sscs.handler.InterlocReferralReasonOptions;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@Component
@Slf4j
public class CcdCallbackHandler {

    private static final String LOGSTR_VALIDATION_ERRORS = "Errors found while validating exception record id {}";

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final CaseDataHandler caseDataHandler;

    private final SscsDataHelper sscsDataHelper;

    private final DwpAddressLookupService dwpAddressLookupService;

    public static final String CASE_TYPE_ID = "Benefit";

    @Value("${feature.debug_json}")
    private Boolean debugJson;

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator,
        CaseDataHandler caseDataHandler,
        SscsDataHelper sscsDataHelper,
        DwpAddressLookupService dwpAddressLookupService
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
        this.caseDataHandler = caseDataHandler;
        this.sscsDataHelper = sscsDataHelper;
        this.dwpAddressLookupService = dwpAddressLookupService;
    }

    public CaseResponse handleValidation(ExceptionRecord exceptionRecord) {

        log.info("Processing callback for SSCS exception record");

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecord(exceptionRecord, true);

        if (caseTransformationResponse.getErrors() != null && caseTransformationResponse.getErrors().size() > 0) {
            log.info("Errors found during validation");
            return caseTransformationResponse;
        }

        log.info("Exception record id {} transformed successfully ready for validation");

        return caseValidator.validateExceptionRecord(caseTransformationResponse, exceptionRecord, caseTransformationResponse.getTransformedCase(), true);
    }

    public SuccessfulTransformationResponse handle(ExceptionRecord exceptionRecord) {
        String exceptionRecordId = exceptionRecord.getId();

        log.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecord(exceptionRecord, false);

        if (caseTransformationResponse.getErrors() != null && caseTransformationResponse.getErrors().size() > 0) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            throw new InvalidExceptionRecordException(caseTransformationResponse.getErrors());
        }

        log.info("Exception record id {} transformed successfully. About to validate transformed case from exception");

        CaseResponse caseValidationResponse = caseValidator.validateExceptionRecord(caseTransformationResponse, exceptionRecord, caseTransformationResponse.getTransformedCase(), false);

        if (!ObjectUtils.isEmpty(caseValidationResponse.getErrors())) {
            log.info(LOGSTR_VALIDATION_ERRORS, exceptionRecordId);
            throw new InvalidExceptionRecordException(caseValidationResponse.getErrors());
        } else {
            String eventId = sscsDataHelper.findEventToCreateCase(caseValidationResponse);

            stampReferredCase(caseValidationResponse, eventId);

            return new SuccessfulTransformationResponse(
                new CaseCreationDetails(
                    CASE_TYPE_ID,
                    eventId,
                    caseValidationResponse.getTransformedCase()
                ),
            caseValidationResponse.getWarnings());
        }
    }

    //FIXME: Remove after bulk scan migration
    public CallbackResponse handleOld(ExceptionCaseData exceptionCaseData, IdamTokens token) {

        String exceptionRecordId = exceptionCaseData.getCaseDetails().getCaseId();

        log.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCaseOld(exceptionCaseData.getCaseDetails(), token);
        AboutToStartOrSubmitCallbackResponse transformErrorResponse = checkForErrorsAndWarningsOld(caseTransformationResponse, exceptionRecordId, exceptionCaseData.isIgnoreWarnings());

        if (transformErrorResponse != null && transformErrorResponse.getErrors() != null && transformErrorResponse.getErrors().size() > 0) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return transformErrorResponse;
        }

        log.info("Exception record id {} transformed successfully", exceptionRecordId);

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();

        log.info("About to validate transformed case from exception id {}", exceptionRecordId);

        CaseResponse caseValidationResponse = caseValidator.validateExceptionRecordOld(transformErrorResponse, exceptionCaseData.getCaseDetails(), transformedCase);

        AboutToStartOrSubmitCallbackResponse validationErrorResponse = checkForErrors(caseValidationResponse, exceptionRecordId);

        if (validationErrorResponse != null) {
            log.info(LOGSTR_VALIDATION_ERRORS, exceptionRecordId);
            return validationErrorResponse;
        } else {
            if (debugJson) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    String data = mapper.writeValueAsString(exceptionCaseData);
                    log.info("*** Json **" + data);
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    log.error("Cannot print Json.", e);
                }
            }
            log.info("Exception record id {} validated successfully", exceptionRecordId);

            return update(exceptionCaseData, caseValidationResponse, token, exceptionRecordId);
        }
    }

    public PreSubmitCallbackResponse<SscsCaseData> handleValidationAndUpdate(Callback<SscsCaseData> callback, IdamTokens token) {
        log.info("Processing validation and update request for SSCS exception record id {}", callback.getCaseDetails().getId());

        if (null != callback.getCaseDetails().getCaseData().getInterlocReviewState()) {
            callback.getCaseDetails().getCaseData().setInterlocReviewState("none");
        }

        setUnsavedFieldsOnCallback(callback);

        Map<String, Object> appealData = new HashMap<>();

        sscsDataHelper.addSscsDataToMap(appealData,
            callback.getCaseDetails().getCaseData().getAppeal(),
            callback.getCaseDetails().getCaseData().getSscsDocument(),
            callback.getCaseDetails().getCaseData().getSubscriptions());

        CaseResponse caseValidationResponse = caseValidator.validateValidationRecord(appealData);

        PreSubmitCallbackResponse<SscsCaseData> validationErrorResponse = convertWarningsToErrors(callback.getCaseDetails().getCaseData(), caseValidationResponse);

        if (validationErrorResponse != null) {
            log.info(LOGSTR_VALIDATION_ERRORS, callback.getCaseDetails().getId());
            return validationErrorResponse;
        } else {
            log.info("Exception record id {} validated successfully", callback.getCaseDetails().getId());

            PreSubmitCallbackResponse<SscsCaseData> preSubmitCallbackResponse = new PreSubmitCallbackResponse<>(callback.getCaseDetails().getCaseData());

            if (caseValidationResponse.getWarnings() != null) {
                preSubmitCallbackResponse.addWarnings(caseValidationResponse.getWarnings());
            }

            caseValidationResponse.setTransformedCase(caseTransformer.checkForMatches(caseValidationResponse.getTransformedCase(), token));

            return preSubmitCallbackResponse;
        }
    }

    private void setUnsavedFieldsOnCallback(Callback<SscsCaseData> callback) {
        Appeal appeal = callback.getCaseDetails().getCaseData().getAppeal();
        callback.getCaseDetails().getCaseData().setCreatedInGapsFrom(sscsDataHelper.getCreatedInGapsFromField(appeal));
        callback.getCaseDetails().getCaseData().setEvidencePresent(sscsDataHelper.hasEvidence(callback.getCaseDetails().getCaseData().getSscsDocument()));

        if (appeal != null) {
            if (callback.getCaseDetails().getCaseData().getAppeal().getBenefitType() != null) {
                String benefitCode = generateBenefitCode(callback.getCaseDetails().getCaseData().getAppeal().getBenefitType().getCode());
                String issueCode = generateIssueCode();

                callback.getCaseDetails().getCaseData().setBenefitCode(benefitCode);
                callback.getCaseDetails().getCaseData().setIssueCode(issueCode);
                callback.getCaseDetails().getCaseData().setCaseCode(generateCaseCode(benefitCode, issueCode));

                if (callback.getCaseDetails().getCaseData().getAppeal().getMrnDetails() != null
                    && callback.getCaseDetails().getCaseData().getAppeal().getMrnDetails().getDwpIssuingOffice() != null) {

                    String dwpRegionCentre = dwpAddressLookupService.getDwpRegionalCenterByBenefitTypeAndOffice(
                        appeal.getBenefitType().getCode(),
                        appeal.getMrnDetails().getDwpIssuingOffice());

                    callback.getCaseDetails().getCaseData().setDwpRegionalCentre(dwpRegionCentre);
                }
            }
        }
    }

    private CallbackResponse update(ExceptionCaseData exceptionCaseData, CaseResponse caseValidationResponse, IdamTokens token, String exceptionRecordId) {
        HandlerResponse handlerResponse = (HandlerResponse) caseDataHandler.handle(
            exceptionCaseData,
            caseValidationResponse,
            exceptionCaseData.isIgnoreWarnings(),
            token,
            exceptionRecordId);

        Map<String, Object> exceptionRecordData = exceptionCaseData.getCaseDetails().getCaseData();

        if (handlerResponse != null) {
            String state = handlerResponse.getState();
            String caseReference = String.valueOf(handlerResponse.getCaseId());

            log.info("Setting exception record state to {} - caseReference {}", state, caseReference);

            exceptionRecordData.put("state", state);
            exceptionRecordData.put("caseReference", caseReference);
        }

        return AboutToStartOrSubmitCallbackResponse.builder()
            .warnings(caseValidationResponse.getWarnings())
            .data(exceptionRecordData).build();
    }

    private AboutToStartOrSubmitCallbackResponse checkForErrors(CaseResponse caseResponse,
                                                                String exceptionRecordId) {

        if (!ObjectUtils.isEmpty(caseResponse.getErrors())) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return AboutToStartOrSubmitCallbackResponse.builder()
                .errors(caseResponse.getErrors())
                .build();
        }
        return null;
    }

    private AboutToStartOrSubmitCallbackResponse checkForErrorsAndWarningsOld(CaseResponse caseResponse,
                                                                              String exceptionRecordId, boolean ignoreWarnings) {

        if (!ObjectUtils.isEmpty(caseResponse.getErrors()) || (!ObjectUtils.isEmpty(caseResponse.getWarnings()) && !ignoreWarnings)) {
            log.info("Errors or warnings found while transforming exception record id {}", exceptionRecordId);
            return AboutToStartOrSubmitCallbackResponse.builder()
                .errors(caseResponse.getErrors())
                .warnings(caseResponse.getWarnings())
                .build();
        }
        return null;
    }

    private PreSubmitCallbackResponse<SscsCaseData> convertWarningsToErrors(SscsCaseData caseData, CaseResponse caseResponse) {

        List<String> appendedWarningsAndErrors = new ArrayList<>();

        if (!ObjectUtils.isEmpty(caseResponse.getWarnings())) {
            log.info("Warnings found while validating exception record id {}", caseData.getCcdCaseId());
            appendedWarningsAndErrors.addAll(caseResponse.getWarnings());
        }

        if (!ObjectUtils.isEmpty(caseResponse.getErrors())) {
            log.info(LOGSTR_VALIDATION_ERRORS, caseData.getCcdCaseId());
            appendedWarningsAndErrors.addAll(caseResponse.getErrors());
        }

        if (appendedWarningsAndErrors.size() > 0) {
            PreSubmitCallbackResponse<SscsCaseData> preSubmitCallbackResponse = new PreSubmitCallbackResponse<>(caseData);

            preSubmitCallbackResponse.addErrors(appendedWarningsAndErrors);
            return preSubmitCallbackResponse;
        }
        return null;
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
