package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;

public interface CaseDataHandler {

    CallbackResponse handle(CaseResponse caseValidationResponse,
                            boolean ignoreWarnings,
                            Token token,
                            String exceptionRecordId);
}
