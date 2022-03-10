package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.springframework.util.CollectionUtils.*;
import static uk.gov.hmcts.reform.sscs.ccd.domain.State.READY_TO_LIST;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.*;
import static uk.gov.hmcts.reform.sscs.validators.SscsCaseValidator.IS_NOT_A_VALID_POSTCODE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.PreSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.transformation.CaseCreationDetails;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;
import uk.gov.hmcts.reform.sscs.exceptions.InvalidExceptionRecordException;
import uk.gov.hmcts.reform.sscs.handler.InterlocReferralReasonOptions;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.model.CourtVenue;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RefDataService;

@Component
@Slf4j
public class CcdCallbackHandler {

    private static final String LOGSTR_VALIDATION_ERRORS = "Errors found while validating exception record id {} - {}";
    private static final String LOGSTR_VALIDATION_WARNING = "Warnings found while validating exception record id {} - {}";

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final SscsDataHelper sscsDataHelper;

    private final DwpAddressLookupService dwpAddressLookupService;

    public static final String CASE_TYPE_ID = "Benefit";

    private final RefDataService refDataService;

    private final boolean workAllocationFeature;
    private Map<String, Object> hmctsServiceIdMap = new HashMap<>();
    private Map<String, Map<String, Object>> supplementaryDataRequestMap = new HashMap<>();

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator,
        SscsDataHelper sscsDataHelper,
        DwpAddressLookupService dwpAddressLookupService,
        RefDataService refDataService,
        @Value("${feature.work-allocation.enabled}")  boolean workAllocationFeature
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
        this.sscsDataHelper = sscsDataHelper;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.refDataService = refDataService;
        this.workAllocationFeature = workAllocationFeature;
        hmctsServiceIdMap.put("HMCTSServiceId", "BBA3");
        supplementaryDataRequestMap.put("$set", hmctsServiceIdMap);
    }

    public CaseResponse handleValidation(ExceptionRecord exceptionRecord) {

        log.info("Processing callback for SSCS exception record");

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecord(exceptionRecord, true);

        if (caseTransformationResponse.getErrors() != null && caseTransformationResponse.getErrors().size() > 0) {
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

        if (caseTransformationResponse.getErrors() != null && caseTransformationResponse.getErrors().size() > 0) {
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
            caseValidationResponse.getWarnings(),
            supplementaryDataRequestMap);
        }
    }

    private String stringJoin(List<String> messages) {
        return String.join(". ", messages);
    }

    public PreSubmitCallbackResponse<SscsCaseData> handleValidationAndUpdate(Callback<SscsCaseData> callback, IdamTokens token) {
        log.info("Processing validation and update request for SSCS exception record id {}", callback.getCaseDetails().getId());

        if (null != callback.getCaseDetails().getCaseData().getInterlocReviewState()) {
            callback.getCaseDetails().getCaseData().setInterlocReviewState("none");
        }

        setUnsavedFieldsOnCallback(callback);

        FormType formType = callback.getCaseDetails().getCaseData().getFormType();
        Map<String, Object> appealData = new HashMap<>();
        sscsDataHelper.addSscsDataToMap(appealData,
            callback.getCaseDetails().getCaseData().getAppeal(),
            callback.getCaseDetails().getCaseData().getSscsDocument(),
            callback.getCaseDetails().getCaseData().getSubscriptions(),
            formType,
            callback.getCaseDetails().getCaseData().getChildMaintenanceNumber(),
            callback.getCaseDetails().getCaseData().getOtherParties()
        );

        boolean ignoreMrnValidation = false;
        if (callback.getEvent() != null && (EventType.DIRECTION_ISSUED.equals(callback.getEvent())
            || EventType.DIRECTION_ISSUED_WELSH.equals(callback.getEvent()))
            && callback.getCaseDetails().getCaseData().getDirectionTypeDl() != null) {
            ignoreMrnValidation = StringUtils.equals(DirectionType.APPEAL_TO_PROCEED.toString(), callback.getCaseDetails().getCaseData().getDirectionTypeDl().getValue().getCode());
        }
        CaseResponse caseValidationResponse = caseValidator.validateValidationRecord(appealData, ignoreMrnValidation);

        PreSubmitCallbackResponse<SscsCaseData> validationErrorResponse = convertWarningsToErrors(callback.getCaseDetails().getCaseData(), caseValidationResponse);

        if (validationErrorResponse != null) {
            log.info(LOGSTR_VALIDATION_ERRORS, callback.getCaseDetails().getId(), ".");
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
        callback.getCaseDetails().getCaseData().setCreatedInGapsFrom(READY_TO_LIST.getId());
        callback.getCaseDetails().getCaseData().setEvidencePresent(sscsDataHelper.hasEvidence(callback.getCaseDetails().getCaseData().getSscsDocument()));

        if (appeal != null && callback.getCaseDetails().getCaseData().getAppeal().getBenefitType() != null && isNotBlank(callback.getCaseDetails().getCaseData().getAppeal().getBenefitType().getCode())) {
            String benefitCode = null;
            String addressName = null;
            if (appeal.getMrnDetails() != null) {
                addressName = appeal.getMrnDetails().getDwpIssuingOffice();
            }
            benefitCode = generateBenefitCode(callback.getCaseDetails().getCaseData().getAppeal().getBenefitType().getCode(), addressName).orElse(EMPTY);

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

            String processingVenue = sscsDataHelper.findProcessingVenue(appeal.getAppellant(), appeal.getBenefitType());
            if (isNotBlank(processingVenue)) {
                callback.getCaseDetails().getCaseData().setProcessingVenue(processingVenue);
                if (workAllocationFeature) {
                    CourtVenue courtVenue = refDataService.getVenueRefData(processingVenue);

                    if (courtVenue != null) {
                        callback.getCaseDetails().getCaseData().setCaseManagementLocation(CaseManagementLocation.builder()
                            .baseLocation(courtVenue.getEpimsId())
                            .region(courtVenue.getRegionId()).build());
                    }
                }
            }
            setWorkAllocationCategories(appeal, callback);
        } else {
            setUnknownCategory(callback);
        }

        setWorkallocationFields(appeal, callback);
    }

    private void setWorkAllocationCategories(Appeal appeal, Callback<SscsCaseData> callback) {
        if (workAllocationFeature) {
            Optional<Benefit> benefit = Benefit.getBenefitOptionalByCode(appeal.getBenefitType().getCode());
            if (benefit.isPresent()) {
                callback.getCaseDetails().getCaseData().getWorkAllocationFields().setCategories(benefit.get());
            }
        }
    }

    private void setUnknownCategory(Callback<SscsCaseData> callback) {
        FormType formType = callback.getCaseDetails().getCaseData().getFormType();
        if (formType != null) {
            if (formType.equals(FormType.SSCS5)) {
                DynamicListItem caseManagementCategoryItem = new DynamicListItem("sscs5Unknown", "SSCS5 Unknown");
                List<DynamicListItem> listItems = Arrays.asList(caseManagementCategoryItem);
                callback.getCaseDetails().getCaseData().getWorkAllocationFields().setCaseManagementCategory(new DynamicList(caseManagementCategoryItem, listItems));
            } else {
                DynamicListItem caseManagementCategoryItem = new DynamicListItem("sscs12Unknown", "SSCS1/2 Unknown");
                List<DynamicListItem> listItems = Arrays.asList(caseManagementCategoryItem);
                callback.getCaseDetails().getCaseData().getWorkAllocationFields().setCaseManagementCategory(new DynamicList(caseManagementCategoryItem, listItems));
            }
        }
    }

    private void setWorkallocationFields(Appeal appeal, Callback<SscsCaseData> callback) {
        if (workAllocationFeature) {
            if (appeal != null && appeal.getAppellant() != null && appeal.getAppellant().getName() != null
                && appeal.getAppellant().getName().getFirstName() != null && appeal.getAppellant().getName().getLastName() != null) {
                callback.getCaseDetails().getCaseData().getWorkAllocationFields().setCaseNames(appeal.getAppellant().getName().getFullNameNoTitle());
            }
            if (appeal != null && appeal.getBenefitType() != null) {
                FormType formType = callback.getCaseDetails().getCaseData().getFormType();
                Optional<Benefit> benefit = Benefit.getBenefitOptionalByCode(appeal.getBenefitType().getCode());

                String ogdType = isHmrcBenefit(benefit, formType) ? "HMRC" : "DWP";
                callback.getCaseDetails().getCaseData().getWorkAllocationFields().setOgdType(ogdType);
            }
        }
    }

    private boolean isHmrcBenefit(Optional<Benefit> benefit, FormType formType) {
        if (benefit.isEmpty()) {
            return FormType.SSCS5.equals(formType);
        }
        return SscsType.SSCS5.equals(benefit.get().getSscsType());
    }

    private PreSubmitCallbackResponse<SscsCaseData> convertWarningsToErrors(SscsCaseData caseData, CaseResponse caseResponse) {

        List<String> appendedWarningsAndErrors = new ArrayList<>();

        List<String> allWarnings = caseResponse.getWarnings();
        List<String> warningsThatAreNotErrors = getWarningsThatShouldNotBeErrors(caseResponse);
        List<String> filteredWarnings = emptyIfNull(allWarnings).stream()
            .filter(w -> !warningsThatAreNotErrors.contains(w))
            .collect(Collectors.toList());

        if (!isEmpty(filteredWarnings)) {
            log.info(LOGSTR_VALIDATION_WARNING, caseData.getCcdCaseId(), stringJoin(filteredWarnings));
            appendedWarningsAndErrors.addAll(filteredWarnings);
        }

        if (!isEmpty(caseResponse.getErrors())) {
            log.info(LOGSTR_VALIDATION_ERRORS, caseData.getCcdCaseId(), stringJoin(caseResponse.getErrors()));
            appendedWarningsAndErrors.addAll(caseResponse.getErrors());
        }

        if (!appendedWarningsAndErrors.isEmpty() || !warningsThatAreNotErrors.isEmpty()) {
            PreSubmitCallbackResponse<SscsCaseData> preSubmitCallbackResponse = new PreSubmitCallbackResponse<>(caseData);

            preSubmitCallbackResponse.addErrors(appendedWarningsAndErrors);
            preSubmitCallbackResponse.addWarnings(warningsThatAreNotErrors);
            return preSubmitCallbackResponse;
        }
        return null;
    }

    private List<String> getWarningsThatShouldNotBeErrors(CaseResponse caseResponse) {
        return emptyIfNull(caseResponse.getWarnings()).stream()
            .filter(warning -> warning.endsWith(IS_NOT_A_VALID_POSTCODE))
            .collect(Collectors.toList());
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
