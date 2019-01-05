package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.domain.ValidateCaseData;
import uk.gov.hmcts.reform.sscs.handler.SscsRoboticsHandler;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;

@Component
@Slf4j
public class CcdCallbackHandler {

    private static final String LOGSTR_VALIDATION_ERRORS = "\"Errors found while validating exception record id {}\"";

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final CaseDataHandler caseDataHandler;

    private final SscsRoboticsHandler roboticsHandler;

    private final SscsDataHelper sscsDataHelper;

    private final CaseEvent caseEvent;

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator,
        CaseDataHandler caseDataHandler,
        SscsRoboticsHandler roboticsHandler,
        SscsDataHelper sscsDataHelper,
        CaseEvent caseEvent
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
        this.caseDataHandler = caseDataHandler;
        this.roboticsHandler = roboticsHandler;
        this.sscsDataHelper = sscsDataHelper;
        this.caseEvent = caseEvent;
    }

    public CallbackResponse handle(
        ExceptionCaseData exceptionCaseData,
        Token token) {

        String exceptionRecordId = exceptionCaseData.getCaseDetails().getCaseId();

        log.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCase(exceptionCaseData.getCaseDetails());
        AboutToStartOrSubmitCallbackResponse transformErrorResponse = checkForErrors(caseTransformationResponse, exceptionRecordId);

        if (transformErrorResponse != null) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return transformErrorResponse;
        }

        log.info("Exception record id {} transformed successfully", exceptionRecordId);

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();

        log.info("About to validate transformed case from exception id {}", exceptionRecordId);

        CaseResponse caseValidationResponse = caseValidator.validate(transformedCase);

        AboutToStartOrSubmitCallbackResponse validationErrorResponse = checkForErrors(caseValidationResponse, exceptionRecordId);

        if (validationErrorResponse != null) {
            log.info(LOGSTR_VALIDATION_ERRORS, exceptionRecordId);
            return validationErrorResponse;
        } else {
            log.info("Exception record id {} validated successfully", exceptionRecordId);
            return update(caseValidationResponse, exceptionCaseData.isIgnoreWarnings(), token, exceptionRecordId, exceptionCaseData.getCaseDetails().getCaseData());
        }
    }

    public CallbackResponse handleValidationAndUpdate(ValidateCaseData validateCaseData) {
        Map<String, Object> appealData = new HashMap<>();

        String exceptionRecordId = validateCaseData.getCaseDetails().getCaseId();

        log.info("Processing validation and update request for SSCS exception record id {}", exceptionRecordId);

        sscsDataHelper.addSscsDataToMap(appealData, validateCaseData.getCaseDetails().getCaseData().getAppeal(), validateCaseData.getCaseDetails().getCaseData().getSscsDocument());

        CaseResponse caseValidationResponse = caseValidator.validate(appealData);

        AboutToStartOrSubmitCallbackResponse validationErrorResponse = convertWarningsToErrors(caseValidationResponse, validateCaseData.getCaseDetails().getCaseId());

        if (validationErrorResponse != null) {
            log.info(LOGSTR_VALIDATION_ERRORS, exceptionRecordId);
            return validationErrorResponse;
        } else {
            log.info("Exception record id {} validated successfully", exceptionRecordId);
            roboticsHandler.handle(caseValidationResponse, Long.valueOf(exceptionRecordId), caseEvent.getCaseCreatedEventId());

            return AboutToStartOrSubmitCallbackResponse.builder()
                .warnings(caseValidationResponse.getWarnings())
                .build();
        }
    }

    private CallbackResponse update(CaseResponse caseValidationResponse, Boolean isIgnoreWarnings, Token token, String exceptionRecordId, Map<String, Object> exceptionRecordData) {
        HandlerResponse handlerResponse = (HandlerResponse) caseDataHandler.handle(
            caseValidationResponse,
            isIgnoreWarnings,
            token,
            exceptionRecordId);

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

    private AboutToStartOrSubmitCallbackResponse convertWarningsToErrors(CaseResponse caseResponse,
                                                                String exceptionRecordId) {

        List<String> appendedWarningsAndErrors = new ArrayList<>();

        if (!ObjectUtils.isEmpty(caseResponse.getWarnings())) {
            log.info("Warnings found while validating exception record id {}", exceptionRecordId);
            appendedWarningsAndErrors.addAll(caseResponse.getWarnings());
        }

        if (!ObjectUtils.isEmpty(caseResponse.getErrors())) {
            log.info(LOGSTR_VALIDATION_ERRORS, exceptionRecordId);
            appendedWarningsAndErrors.addAll(caseResponse.getErrors());
        }

        if (appendedWarningsAndErrors.size() > 0) {
            return AboutToStartOrSubmitCallbackResponse.builder()
                .errors(appendedWarningsAndErrors)
                .build();
        }
        return null;
    }
}
