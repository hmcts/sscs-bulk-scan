package uk.gov.hmcts.reform.sscs.bulkscancore.validators;

import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

/**
 * Each jurisdiction service needs to provide implementation of this interface
 * If case validation is not required then return original case data in the transformation.
 */
public interface CaseValidator {
    CaseResponse validate(Map<String, Object> caseData);
}
