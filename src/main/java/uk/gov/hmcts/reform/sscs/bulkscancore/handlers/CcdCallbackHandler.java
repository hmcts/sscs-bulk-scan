package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
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

        logger.info("Processing callback for SSCS exception record id  {}", exceptionRecordData.get("id"));

        // Transform into SSCS case
        CaseTransformationResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCase(exceptionRecordData);

        if (!ObjectUtils.isEmpty(caseTransformationResponse.getErrors())) {
            return CcdCallbackResponse.builder()
                .errors(caseTransformationResponse.getErrors())
                .data(exceptionRecordData)
                .build();
        }

        return CcdCallbackResponse.builder()
            .data(caseTransformationResponse.getTransformedCase())
            .build();
    }
}
