package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;

//FIXME: Delete this after migration
public interface CaseDataHandler {

    CallbackResponse handle(ExceptionCaseData exceptionCaseData,
                            CaseResponse caseValidationResponse,
                            boolean ignoreWarnings,
                            Token token,
                            String exceptionRecordId);

}
