package uk.gov.hmcts.reform.sscs.handler;

import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CaseDataHandler;

@Component
@Slf4j
public class SscsCaseDataHandler implements CaseDataHandler {

    private final CaseDataHelper caseDataHelper;

    private final String caseCreatedEventId;

    private final String incompleteApplicationEventId;

    public SscsCaseDataHandler(CaseDataHelper caseDataHelper,
                               @Value("${ccd.case.caseCreatedEventId}") String caseCreatedEventId,
                               @Value("${ccd.case.incompleteApplicationEventId}") String incompleteApplicationEventId) {
        this.caseDataHelper = caseDataHelper;
        this.caseCreatedEventId = caseCreatedEventId;
        this.incompleteApplicationEventId = incompleteApplicationEventId;
    }

    public CallbackResponse handle(CaseResponse caseValidationResponse,
                                   boolean ignoreWarnings,
                                   Map<String, Object> transformedCase,
                                   Token token,
                                   String exceptionRecordId) {

        if (canCreateCase(caseValidationResponse, ignoreWarnings)) {
            String eventId = isEmpty(caseValidationResponse.getWarnings()) ? caseCreatedEventId : incompleteApplicationEventId;

            Long caseId = caseDataHelper.createCase(transformedCase, token.getUserAuthToken(), token.getServiceAuthToken(), token.getUserId(), eventId);

            log.info("Case created with caseId {} from exception record id {}", caseId, exceptionRecordId);

            return HandlerResponse.builder().state("ScannedRecordCaseCreated").caseId(String.valueOf(caseId)).build();
        }
        return null;
    }

    private Boolean canCreateCase(CaseResponse caseValidationResponse, boolean ignoreWarnings) {
        return ((!isEmpty(caseValidationResponse.getWarnings()) && ignoreWarnings) || isEmpty(caseValidationResponse.getWarnings()));
    }
}
