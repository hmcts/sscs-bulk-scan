package uk.gov.hmcts.reform.sscs.bulkscancore.validators;

import java.util.Map;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;

/**
 * Each jurisdiction service needs to provide implementation of this interface
 * If case validation is not required then return original case data in the transformation.
 */
public interface CaseValidator {
    CaseResponse validateExceptionRecordOld(AboutToStartOrSubmitCallbackResponse transformErrorResponse, CaseDetails caseDetails, Map<String, Object> caseData);

    CaseResponse validateValidationRecord(Map<String, Object> caseData, boolean ignoreMrnValidation);

    CaseResponse validateExceptionRecord(CaseResponse transformResponse, ExceptionRecord exceptionRecord, Map<String, Object> caseData, boolean combineWarnings);
}
