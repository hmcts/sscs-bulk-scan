package uk.gov.hmcts.reform.sscs.bulkscancore.transformers;

import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;

/**
 * Implementation of this interface will need to return CaseTransformationResponse.
 * In case transformation fails then errors field needs to be populated with appropriate message and field which failed transformation.
 */
public interface CaseTransformer {
    CaseResponse transformExceptionRecordToCase(Map<String, Object> exceptionCaseData);
}
