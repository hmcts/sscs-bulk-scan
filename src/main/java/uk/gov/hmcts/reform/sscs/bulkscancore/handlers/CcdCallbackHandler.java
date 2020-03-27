package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.PreSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;

@Component
@Slf4j
public class CcdCallbackHandler {

    private static final String LOGSTR_VALIDATION_ERRORS = "\"Errors found while validating exception record id {}\"";

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final CaseDataHandler caseDataHandler;

    private final SscsDataHelper sscsDataHelper;

    private final DwpAddressLookupService dwpAddressLookupService;

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

    public CallbackResponse handle(ExceptionCaseData exceptionCaseData, Token token) {

        String exceptionRecordId = exceptionCaseData.getCaseDetails().getCaseId();

        log.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCase(exceptionCaseData.getCaseDetails());
        AboutToStartOrSubmitCallbackResponse transformErrorResponse = checkForErrorsAndWarnings(caseTransformationResponse, exceptionRecordId, exceptionCaseData.isIgnoreWarnings());

        if (transformErrorResponse != null && transformErrorResponse.getErrors() != null && transformErrorResponse.getErrors().size() > 0) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return transformErrorResponse;
        }

        log.info("Exception record id {} transformed successfully", exceptionRecordId);

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();

        log.info("About to validate transformed case from exception id {}", exceptionRecordId);

        CaseResponse caseValidationResponse = caseValidator.validate(transformErrorResponse, exceptionCaseData.getCaseDetails(), transformedCase);

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
            return update(exceptionCaseData, caseValidationResponse, exceptionCaseData.isIgnoreWarnings(), token,
                exceptionRecordId, exceptionCaseData.getCaseDetails().getCaseData());
        }
    }

    public PreSubmitCallbackResponse<SscsCaseData> handleValidationAndUpdate(Callback<SscsCaseData> callback) {
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

        CaseResponse caseValidationResponse = caseValidator.validate(null, null, appealData);

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

            return preSubmitCallbackResponse;
        }
    }

    private void setUnsavedFieldsOnCallback(Callback<SscsCaseData> callback) {
        Appeal appeal = callback.getCaseDetails().getCaseData().getAppeal();
        callback.getCaseDetails().getCaseData().setCreatedInGapsFrom(sscsDataHelper.getCreatedInGapsFromField(appeal));
        callback.getCaseDetails().getCaseData().setEvidencePresent(sscsDataHelper.hasEvidence(callback.getCaseDetails().getCaseData().getSscsDocument()));

        if (appeal != null) {
            if (appeal.getAppellant() != null) {
                if (appeal.getAppellant().getName() != null) {
                    callback.getCaseDetails().getCaseData().setGeneratedSurname(callback.getCaseDetails().getCaseData().getAppeal().getAppellant().getName().getLastName());
                }
                if (appeal.getAppellant().getIdentity() != null) {
                    callback.getCaseDetails().getCaseData().setGeneratedNino(callback.getCaseDetails().getCaseData().getAppeal().getAppellant().getIdentity().getNino());
                    callback.getCaseDetails().getCaseData().setGeneratedDob(callback.getCaseDetails().getCaseData().getAppeal().getAppellant().getIdentity().getDob());
                }
            }

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

    private CallbackResponse update(ExceptionCaseData exceptionCaseData, CaseResponse caseValidationResponse, Boolean isIgnoreWarnings, Token token, String exceptionRecordId, Map<String, Object> exceptionRecordData) {
        HandlerResponse handlerResponse = (HandlerResponse) caseDataHandler.handle(
            exceptionCaseData,
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

    private AboutToStartOrSubmitCallbackResponse checkForErrorsAndWarnings(CaseResponse caseResponse,
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
}
