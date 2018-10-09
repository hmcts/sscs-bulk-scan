package uk.gov.hmcts.reform.sscs.service;

import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.domain.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;

import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.ObjectUtils.isEmpty;

@Component
public class CcdCallbackHandler {
    private static final Logger logger = getLogger(CcdCallbackHandler.class);

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final CaseDataHelper caseDataHelper;

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator,
        CaseDataHelper caseDataHelper
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
        this.caseDataHelper = caseDataHelper;
    }


    public CcdCallbackResponse handle(
        CreateCaseEvent createCaseEvent,
        String userAuthorisationToken,
        String serviceAuthorisationToken,
        String userId
    ) {
        // check user and service auth or use new token ???

        // Transform into sscs case
        Map<String, Object> exceptionRecordData = createCaseEvent.getCaseDetails().getCaseData();

        logger.info("SSCS Exception record data  {}", exceptionRecordData);

        Map<String, Object> sscsCaseCaseData = caseTransformer.transformExceptionRecordToCase(exceptionRecordData);

        logger.info("Transformed SSCS Case data  {}", sscsCaseCaseData);

        // Validate with robotics schema
        CaseValidationResponse caseValidationResponse = caseValidator.validate(sscsCaseCaseData);

        logger.info("Validation response  {}", caseValidationResponse);

        if (isEmpty(caseValidationResponse.getErrors()) && isEmpty(caseValidationResponse.getWarnings())) {
            // Start event -create case
            // Submit event - create case
            Long caseId = caseDataHelper.createCase(sscsCaseCaseData, userAuthorisationToken, serviceAuthorisationToken, userId);

            logger.info("SSCS Case Reference number {}", caseId);

            if (caseId != null) {
                exceptionRecordData.put("state", "ScannedRecordCaseCreated");
                exceptionRecordData.put("caseReference", String.valueOf(caseId));
            }

            return CcdCallbackResponse.builder()
                .data(exceptionRecordData)
                .build();
        }

        return CcdCallbackResponse.builder()
            .data(exceptionRecordData)
            .errors(caseValidationResponse.getErrors())
            .warnings(caseValidationResponse.getWarnings())
            .build();
    }
}
