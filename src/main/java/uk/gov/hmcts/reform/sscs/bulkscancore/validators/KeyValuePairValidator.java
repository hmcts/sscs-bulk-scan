package uk.gov.hmcts.reform.sscs.bulkscancore.validators;

import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

/**
 * Each jurisdiction service needs to provide implementation of this interface
 * If key value pair validation is not required then return empty CaseResponse object.
 */
public interface KeyValuePairValidator {
    CaseResponse validate(Map<String, Object> keyValuePairs);
}
