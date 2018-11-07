package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import java.util.Map;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

public interface CaseDataHandler {

    CallbackResponse handle(CaseResponse caseValidationResponse,
                            Map<String, Object> transformedCase,
                            String userAuthToken,
                            String serviceAuthToken,
                            String userId,
                            Map<String, Object> exceptionRecordData,
                            String exceptionRecordId);
}
