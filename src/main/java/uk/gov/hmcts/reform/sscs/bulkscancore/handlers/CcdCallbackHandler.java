package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;

@Component
public class CcdCallbackHandler {

    private static final Logger logger = getLogger(CcdCallbackHandler.class);

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
    }

    public AboutToStartOrSubmitCallbackResponse handle(
        ExceptionCaseData exceptionCaseData,
        String userAuthToken,
        String serviceAuthToken,
        String userId
    ) {
        // TODO : Transform implementation, Validate implementation, Create Case, Case transition

        Map<String, Object> exceptionRecordData = exceptionCaseData.getCaseDetails().getCaseData();

        logger.info("Processing callback for SSCS exception record id  {}", exceptionRecordData.get("id"));

        // Transform into SSCS case
        CaseTransformationResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCase(exceptionRecordData);

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();

        if (!ObjectUtils.isEmpty(caseTransformationResponse.getErrors())) {
            return AboutToStartOrSubmitCallbackResponse.builder()
                .data(exceptionRecordData)
                .errors(caseTransformationResponse.getErrors())
                .build();
        }

        // Validate the transformed case
        CaseValidationResponse caseValidationResponse = caseValidator.validate(transformedCase);

        if (isEmpty(caseValidationResponse.getErrors()) && isEmpty(caseValidationResponse.getWarnings())) {
            // TODO : Create case and populate case reference in exception record. Also transition state of exception record

            return AboutToStartOrSubmitCallbackResponse.builder()
                .data(transformedCase) // Return transformed case for now. Replace later with exception record
                .build();
        }

        return AboutToStartOrSubmitCallbackResponse.builder()
            .data(exceptionRecordData)
            .errors(caseValidationResponse.getErrors())
            .warnings(caseValidationResponse.getWarnings())
            .build();
    }
}
