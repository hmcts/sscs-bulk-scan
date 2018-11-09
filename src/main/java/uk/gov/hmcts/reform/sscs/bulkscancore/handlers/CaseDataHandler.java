package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import java.util.Map;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.IdamToken;

public interface CaseDataHandler {

    CallbackResponse handle(CaseResponse caseValidationResponse,
                            boolean ignoreWarnings,
                            Map<String, Object> transformedCase,
                            IdamToken idamToken,
                            String exceptionRecordId);
}
