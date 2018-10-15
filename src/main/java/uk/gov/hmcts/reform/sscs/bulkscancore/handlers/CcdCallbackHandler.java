package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;

@Component
public class CcdCallbackHandler {

    private static final Logger logger = getLogger(CcdCallbackHandler.class);

    private final CaseTransformer caseTransformer;

    public CcdCallbackHandler(CaseTransformer caseTransformer
    ) {
        this.caseTransformer = caseTransformer;
    }

    public CcdCallbackResponse handle(
        ExceptionCaseData exceptionCaseData,
        String userAuthToken,
        String serviceAuthToken,
        String userId
    ) {
        // TODO : Transform implementation, Validate, Create Case, Case transition

        Map<String, Object> exceptionRecordData = exceptionCaseData.getCaseDetails().getCaseData();

        logger.info("SSCS exception record case id  {}", exceptionRecordData.get("id"));

        // Transform into SSCS case
        Map<String, Object> sscsCaseCaseData = caseTransformer.transformExceptionRecordToCase(exceptionRecordData);

        return CcdCallbackResponse.builder()
            .data(sscsCaseCaseData)
            .build();
    }
}
