package uk.gov.hmcts.reform.sscs.bulkscancore.transformers;

import java.util.Map;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

/**
 * Implementation of this interface will need to return CaseTransformationResponse.
 * If case transformation fails then errors field needs to be populated with appropriate message and field which failed transformation.
 */
public interface CaseTransformer {
    CaseResponse transformExceptionRecord(ExceptionRecord exceptionRecord, boolean combineWarnings);

    CaseResponse transformExceptionRecordToCaseOld(CaseDetails caseDetails, IdamTokens token);

    Map<String, Object> checkForMatches(Map<String, Object> sscsCaseData, IdamTokens token);

}
