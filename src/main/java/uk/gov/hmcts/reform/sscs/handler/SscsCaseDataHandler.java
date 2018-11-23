package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.time.LocalDate;

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
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.exceptions.CaseDataHelperException;

@Component
@Slf4j
public class SscsCaseDataHandler implements CaseDataHandler {

    private final CaseDataHelper caseDataHelper;

    private final String caseCreatedEventId;

    private final String incompleteApplicationEventId;

    private final String nonCompliantEventId;

    public SscsCaseDataHandler(CaseDataHelper caseDataHelper,
                               @Value("${ccd.case.caseCreatedEventId}") String caseCreatedEventId,
                               @Value("${ccd.case.incompleteApplicationEventId}") String incompleteApplicationEventId,
                               @Value("${ccd.case.nonCompliantEventId}") String nonCompliantEventId) {
        this.caseDataHelper = caseDataHelper;
        this.caseCreatedEventId = caseCreatedEventId;
        this.incompleteApplicationEventId = incompleteApplicationEventId;
        this.nonCompliantEventId = nonCompliantEventId;
    }

    public CallbackResponse handle(CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   Token token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings)) {
            String eventId = findEventToCreateCase(caseValidationResponse);

            try {
                Long caseId = caseDataHelper.createCase(caseValidationResponse.getTransformedCase(), token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), eventId);

                log.info("Case created with caseId {} from exception record id {}", caseId, exceptionRecordId);

                return HandlerResponse.builder().state("ScannedRecordCaseCreated").caseId(String.valueOf(caseId)).build();
            } catch (FeignException e) {
                throw e;
            } catch (Exception e) {
                wrapAndThrowCaseDataHandlerException(exceptionRecordId, e);
            }
        }
        return null;
    }

    private Boolean canCreateCase(CaseResponse caseValidationResponse, boolean ignoreWarnings) {
        return ((!isEmpty(caseValidationResponse.getWarnings()) && ignoreWarnings) || isEmpty(caseValidationResponse.getWarnings()));
    }

    private String findEventToCreateCase(CaseResponse caseValidationResponse) {
        LocalDate mrnDate = findMrnDateTime(((Appeal) caseValidationResponse.getTransformedCase().get("appeal")).getMrnDetails());

        if (!isEmpty(caseValidationResponse.getWarnings())) {
            return incompleteApplicationEventId;
        } else if (mrnDate != null && mrnDate.plusMonths(13L).isBefore(LocalDate.now())) {
            return nonCompliantEventId;
        } else {
            return caseCreatedEventId;
        }
    }

    private LocalDate findMrnDateTime(MrnDetails mrnDetails) {
        if (mrnDetails != null && mrnDetails.getMrnDate() != null) {
            return LocalDate.parse(mrnDetails.getMrnDate());
        }
        return null;
    }

    private void wrapAndThrowCaseDataHandlerException(String exceptionId, Exception ex) {
        CaseDataHelperException exception = new CaseDataHelperException(exceptionId, ex);
        log.error("Error for exception id: " + exceptionId, exception);
        throw exception;
    }
}
