package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;
import static uk.gov.hmcts.reform.sscs.ccd.domain.EventType.SEND_TO_DWP;

import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;
import uk.gov.hmcts.reform.sscs.domain.CaseEvent;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;

@Component
@Slf4j
public class SscsCaseDataHandler implements CaseDataHandler {

    private final SscsDataHelper sscsDataHelper;
    private final CaseDataHelper caseDataHelper;
    private final SscsRoboticsHandler roboticsHandler;
    private final CaseEvent caseEvent;

    @Value("${feature.send_to_dwp}")
    private Boolean sendToDwpFeature;

    public SscsCaseDataHandler(SscsDataHelper sscsDataHelper,
                               CaseDataHelper caseDataHelper,
                               SscsRoboticsHandler roboticsHandler,
                               CaseEvent caseEvent) {
        this.sscsDataHelper = sscsDataHelper;
        this.caseDataHelper = caseDataHelper;
        this.roboticsHandler = roboticsHandler;
        this.caseEvent = caseEvent;
    }

    public CallbackResponse handle(CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   Token token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings)) {
            String eventId = sscsDataHelper.findEventToCreateCase(caseValidationResponse);

            try {
                Long caseId = caseDataHelper.createCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), eventId);

                log.info("Case created with caseId {} from exception record id {}", caseId, exceptionRecordId);

                if (isCaseCreatedEvent(eventId)) {
                    roboticsHandler.handle(caseValidationResponse, caseId);

                    if (sendToDwpFeature) {
                        log.info("About to update case with sendToDwp event for id {}", caseId);
                        caseDataHelper.updateCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), SEND_TO_DWP.getCcdType(), caseId, "Send to DWP", "Send to DWP event has been triggered from Bulk Scan service");
                        log.info("Case updated with sendToDwp event for id {}", caseId);
                    }
                }

                return HandlerResponse.builder().state("ScannedRecordCaseCreated").caseId(String.valueOf(caseId)).build();
            } catch (FeignException e) {
                throw e;
            } catch (Exception e) {
                wrapAndThrowCaseDataHandlerException(exceptionRecordId, e);
            }
        }
        return null;
    }

    private boolean isCaseCreatedEvent(String eventId) {
        return eventId.equals(caseEvent.getCaseCreatedEventId());
    }

    private Boolean canCreateCase(CaseResponse caseValidationResponse, boolean ignoreWarnings) {
        return ((!isEmpty(caseValidationResponse.getWarnings()) && ignoreWarnings) || isEmpty(caseValidationResponse.getWarnings()));
    }

    private void wrapAndThrowCaseDataHandlerException(String exceptionId, Exception ex) {
        CaseDataHelperException exception = new CaseDataHelperException(exceptionId, ex);
        log.error("Error for exception id: " + exceptionId, exception);
        throw exception;
    }
}
