package uk.gov.hmcts.reform.sscs.bulkscancore.validators;

import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;

/**
 * Each jurisdiction service needs to provide implementation of this interface
 * In case validation is not required then return original case data in the transformation.
 */
public interface CaseValidator {
    CaseValidationResponse validate(Map<String, Object> caseData);
}
