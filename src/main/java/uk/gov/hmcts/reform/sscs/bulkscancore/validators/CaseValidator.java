package uk.gov.hmcts.reform.sscs.bulkscancore.validators;

import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.ccd.domain.EventType;

/**
 * Each jurisdiction service needs to provide implementation of this interface
 * If case validation is not required then return original case data in the transformation.
 */
public interface CaseValidator {
    CaseResponse  validateValidationRecord(Map<String, Object> caseData, boolean ignoreMrnValidation);

    CaseResponse validateValidationRecord(Map<String, Object> caseData, boolean ignoreMrnValidation, EventType eventType);

    CaseResponse validateExceptionRecord(CaseResponse transformResponse, ExceptionRecord exceptionRecord, Map<String, Object> caseData, boolean combineWarnings);
}
