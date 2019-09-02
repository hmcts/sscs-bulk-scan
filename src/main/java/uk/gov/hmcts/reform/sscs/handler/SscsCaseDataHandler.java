package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.SEND_TO_DWP;

import feign.FeignException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseDetails;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;


@Component
@Slf4j
public class SscsCaseDataHandler implements CaseDataHandler {

    private final SscsDataHelper sscsDataHelper;
    private final CaseDataHelper caseDataHelper;
    private final CaseEvent caseEvent;
    private final CcdService ccdService;
    private final IdamService idamService;

    public SscsCaseDataHandler(SscsDataHelper sscsDataHelper,
                               CaseDataHelper caseDataHelper,
                               CaseEvent caseEvent,
                               CcdService ccdService,
                               IdamService idamService) {
        this.sscsDataHelper = sscsDataHelper;
        this.caseDataHelper = caseDataHelper;
        this.caseEvent = caseEvent;
        this.ccdService = ccdService;
        this.idamService = idamService;
    }

    public CallbackResponse handle(CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   Token token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings, exceptionRecordId)) {
            boolean isCaseAlreadyExists = false;
            String eventId = sscsDataHelper.findEventToCreateCase(caseValidationResponse);

            String caseReference = String.valueOf(Optional.ofNullable(caseValidationResponse.getTransformedCase().get("caseReference")).orElse(""));
            String generatedNino = String.valueOf(Optional.ofNullable(caseValidationResponse.getTransformedCase().get("generatedNino")).orElse(""));
            String benefitType = String.valueOf(Optional.ofNullable(caseValidationResponse.getTransformedCase().get("benefitCode")).orElse(""));
            Appeal appeal = (Appeal) caseValidationResponse.getTransformedCase().get("appeal");
            String mrnDate = appeal.getMrnDetails().getMrnDate();
            Long caseId = null;

            if (!StringUtils.isEmpty(caseReference)) {
                log.info("Case already exists for exception record id {}", caseReference, exceptionRecordId);
                isCaseAlreadyExists = true;
            } else if (!StringUtils.isEmpty(generatedNino) && !StringUtils.isEmpty(benefitType) && !StringUtils.isEmpty(mrnDate)) {
                IdamTokens idamTokens = idamService.getIdamTokens();
                Map<String,String> searchCriteria = new HashMap<>();
                searchCriteria.put("case.generatedNino", generatedNino);
                searchCriteria.put("case.appeal.benefitType.code", benefitType);
                searchCriteria.put("case.appeal.mrnDetails.mrnDate", mrnDate);

                List<SscsCaseDetails> caseDetails = ccdService.findCaseBy(searchCriteria, idamTokens);

                if (!CollectionUtils.isEmpty(caseDetails)) {
                    log.info("Duplicate case found for Nino {} , benefit type {} and mrnDate {}. "
                                    + "No need to continue with post create case processing.",
                            generatedNino, benefitType, mrnDate);
                    isCaseAlreadyExists = true;
                }
            }

            try {

                if (!isCaseAlreadyExists) {
                    caseId = caseDataHelper.createCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), eventId);
                    log.info("Case created with caseId {} from exception record id {}", caseId, exceptionRecordId);

                    if (isCaseCreatedEvent(eventId)) {
                        log.info("About to update case with sendToDwp event for id {}", caseId);
                        caseDataHelper.updateCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), SEND_TO_DWP.getCcdType(), caseId, "Send to DWP", "Send to DWP event has been triggered from Bulk Scan service");
                        log.info("Case updated with sendToDwp event for id {}", caseId);
                    }

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

    private boolean isCaseCreatedEvent(String eventId) {
        return eventId.equals(caseEvent.getCaseCreatedEventId()) || eventId.equals(caseEvent.getValidAppealCreatedEventId());
    }

    private Boolean canCreateCase(CaseResponse caseValidationResponse, boolean ignoreWarnings, String exceptionRecordId) {
        return ((!isEmpty(caseValidationResponse.getWarnings()) && ignoreWarnings) || isEmpty(caseValidationResponse.getWarnings()));
    }

    private void wrapAndThrowCaseDataHandlerException(String exceptionId, Exception ex) {
        CaseDataHelperException exception = new CaseDataHelperException(exceptionId, ex);
        log.error("Error for exception id: " + exceptionId, exception);
        throw exception;
    }
}
