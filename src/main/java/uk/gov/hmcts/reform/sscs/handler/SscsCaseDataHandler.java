package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;

import feign.FeignException;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.EventType;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@Component
@Slf4j
//FIXME: Delete this after migration
public class SscsCaseDataHandler implements CaseDataHandler {

    private static final String INTERLOC_REFERRAL_REASON = "interlocReferralReason";
    private final SscsDataHelper sscsDataHelper;
    private final CaseDataHelper caseDataHelper;
    private final CcdService ccdService;

    public SscsCaseDataHandler(SscsDataHelper sscsDataHelper,
                               CaseDataHelper caseDataHelper,
                               CcdService ccdService) {
        this.sscsDataHelper = sscsDataHelper;
        this.caseDataHelper = caseDataHelper;
        this.ccdService = ccdService;
    }

    public CallbackResponse handle(ExceptionCaseData exceptionCaseData,
                                   CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   IdamTokens token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings)) {
            boolean isCaseAlreadyExists = false;
            String eventId = sscsDataHelper.findEventToCreateCase(caseValidationResponse);
            stampReferredCase(caseValidationResponse, eventId);

            Appeal appeal = (Appeal) caseValidationResponse.getTransformedCase().get("appeal");
            String mrnDate = "";
            String benefitType = "";
            String nino = "";

            if (appeal != null) {
                if (appeal.getMrnDetails() != null) {
                    mrnDate = appeal.getMrnDetails().getMrnDate();
                }
                if (appeal.getBenefitType() != null) {
                    benefitType = appeal.getBenefitType().getCode();
                }
                if (appeal.getAppellant() != null
                    && appeal.getAppellant().getIdentity() != null && appeal.getAppellant().getIdentity().getNino() != null) {
                    nino = appeal.getAppellant().getIdentity().getNino();
                }
            }

            String caseReference = String.valueOf(Optional.ofNullable(
                exceptionCaseData.getCaseDetails().getCaseData().get("caseReference")).orElse(""));

            if (!StringUtils.isEmpty(caseReference)) {
                log.info("Case {} already exists for exception record id {}", caseReference, exceptionRecordId);
                isCaseAlreadyExists = true;
            } else if (!StringUtils.isEmpty(nino) && !StringUtils.isEmpty(benefitType)
                && !StringUtils.isEmpty(mrnDate)) {

                SscsCaseDetails caseDetails = ccdService.findCcdCaseByNinoAndBenefitTypeAndMrnDate(
                    nino, benefitType, mrnDate, token);

                if (null != caseDetails) {
                    log.info("Duplicate case found for Nino {} , benefit type {} and mrnDate {}. "
                            + "No need to continue with post create case processing.",
                        nino, benefitType, mrnDate);
                    isCaseAlreadyExists = true;
                    caseReference = String.valueOf(caseDetails.getId());
                }
            }

            try {
                if (!isCaseAlreadyExists) {
                    Map<String, Object> sscsCaseData = caseValidationResponse.getTransformedCase();

                    Long caseId = caseDataHelper.createCase(sscsCaseData,
                        token.getIdamOauth2Token(), token.getServiceAuthorization(), token.getUserId(), eventId);

                    log.info("Case created with caseId {} from exception record id {}, setting isSaveAndReturn to No", caseId, exceptionRecordId);



                    caseReference = String.valueOf(caseId);
                }

                return HandlerResponse.builder().state("ScannedRecordCaseCreated").caseId(caseReference).build();
            } catch (FeignException e) {
                throw e;
            } catch (Exception e) {
                wrapAndThrowCaseDataHandlerException(exceptionRecordId, e);
            }
        }
        return null;
    }

    private void stampReferredCase(CaseResponse caseValidationResponse, String eventId) {
        Map<String, Object> transformedCase = caseValidationResponse.getTransformedCase();
        Appeal appeal = (Appeal) transformedCase.get("appeal");
        if (EventType.NON_COMPLIANT.getCcdType().equals(eventId)) {
            if (appealReasonIsNotBlank(appeal)) {
                transformedCase.put(INTERLOC_REFERRAL_REASON,
                    InterlocReferralReasonOptions.OVER_13_MONTHS.getValue());
            } else {
                transformedCase.put(INTERLOC_REFERRAL_REASON,
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

    private boolean canCreateCase(CaseResponse caseValidationResponse, boolean ignoreWarnings) {
        return ((!isEmpty(caseValidationResponse.getWarnings()) && ignoreWarnings)
            || isEmpty(caseValidationResponse.getWarnings()));
    }

    private void wrapAndThrowCaseDataHandlerException(String exceptionId, Exception ex) {
        CaseDataHelperException exception = new CaseDataHelperException(exceptionId, ex);
        log.error("Error for exception id: " + exceptionId, exception);
        throw exception;
    }
}
