package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.util.ObjectUtils.isEmpty;

import java.util.Map;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.ccd.CaseDataHelper;
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

    public CallbackResponse handle(
        ExceptionCaseData exceptionCaseData,
        String userAuthToken,
        String serviceAuthToken,
        String userId
    ) {
        // TODO : Transformation and Validation interface implementation

        Map<String, Object> exceptionRecordData = exceptionCaseData.getCaseDetails().getCaseData();

        String exceptionRecordId = (String) exceptionRecordData.get("id");

        logger.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        // Transform into SSCS case
        CaseTransformationResponse caseTransformationResponse =
            caseTransformer.transformExceptionRecordToCase(exceptionRecordData);

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();

        if (!ObjectUtils.isEmpty(caseTransformationResponse.getErrors())) {
            logger.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return AboutToStartOrSubmitCallbackResponse.builder()
                .data(exceptionRecordData)
                .errors(caseTransformationResponse.getErrors())
                .build();
        }

        // Validate the transformed case
        CaseValidationResponse caseValidationResponse = caseValidator.validate(transformedCase);

        if (isEmpty(caseValidationResponse.getErrors()) && isEmpty(caseValidationResponse.getWarnings())) {
            Long caseId = caseDataHelper.createCase(transformedCase, userAuthToken, serviceAuthToken, userId);

            logger.info(
                "Case created with exceptionRecordId {} from exception record id {}",
                caseId,
                exceptionRecordId
            );

            exceptionRecordData.put("state", "ScannedRecordCaseCreated");
            exceptionRecordData.put("caseReference", String.valueOf(caseId));

            return AboutToStartOrSubmitCallbackResponse.builder()
                .data(exceptionRecordData)
                .build();
        }

        logger.info("Validations/Warnings found while processing exception record id {}", exceptionRecordId);

        return AboutToStartOrSubmitCallbackResponse.builder()
            .data(exceptionRecordData)
            .errors(caseValidationResponse.getErrors())
            .warnings(caseValidationResponse.getWarnings())
            .build();
    }
}
