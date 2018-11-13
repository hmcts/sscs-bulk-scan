package uk.gov.hmcts.reform.sscs.bulkscancore.handlers;

import static org.slf4j.LoggerFactory.getLogger;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.HandlerResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;

@Component
@Slf4j
public class CcdCallbackHandler {

    private static final Logger logger = getLogger(CcdCallbackHandler.class);

    private final CaseTransformer caseTransformer;

    private final CaseValidator caseValidator;

    private final CaseDataHandler caseDataHandler;

    public CcdCallbackHandler(
        CaseTransformer caseTransformer,
        CaseValidator caseValidator,
        CaseDataHandler caseDataHandler
    ) {
        this.caseTransformer = caseTransformer;
        this.caseValidator = caseValidator;
        this.caseDataHandler = caseDataHandler;
    }

    public CallbackResponse handle(
        ExceptionCaseData exceptionCaseData,
        Token token) {

        Map<String, Object> exceptionRecordData = exceptionCaseData.getCaseDetails().getCaseData();

        String exceptionRecordId = (String) exceptionRecordData.get("id");

        logger.info("Processing callback for SSCS exception record id {}", exceptionRecordId);

        CaseResponse caseTransformationResponse = caseTransformer.transformExceptionRecordToCase(exceptionRecordData);
        AboutToStartOrSubmitCallbackResponse transformErrorResponse = checkForErrors(caseTransformationResponse, exceptionRecordData, exceptionRecordId);

        if (transformErrorResponse != null) {
            return transformErrorResponse;
        }

        Map<String, Object> transformedCase = caseTransformationResponse.getTransformedCase();
        CaseResponse caseValidationResponse = caseValidator.validate(transformedCase);

        AboutToStartOrSubmitCallbackResponse validationErrorResponse = checkForErrors(caseValidationResponse, exceptionRecordData, exceptionRecordId);

        if (validationErrorResponse != null) {
            return validationErrorResponse;
        } else {
            HandlerResponse handlerResponse = (HandlerResponse) caseDataHandler.handle(
                caseValidationResponse,
                exceptionCaseData.isIgnoreWarnings(),
                token,
                exceptionRecordId);

            if (handlerResponse != null) {
                exceptionRecordData.put("state", (handlerResponse.getState()));
                exceptionRecordData.put("caseReference", String.valueOf((handlerResponse.getCaseId())));
            }

            return AboutToStartOrSubmitCallbackResponse.builder()
                .warnings(caseValidationResponse.getWarnings())
                .data(exceptionRecordData).build();
        }
    }

    private AboutToStartOrSubmitCallbackResponse checkForErrors(CaseResponse caseResponse,
                                                                Map<String, Object> exceptionRecordData,
                                                                String exceptionRecordId) {

        if (!ObjectUtils.isEmpty(caseResponse.getErrors())) {
            log.info("Errors found while transforming exception record id {}", exceptionRecordId);
            return AboutToStartOrSubmitCallbackResponse.builder()
                .data(exceptionRecordData)
                .errors(caseResponse.getErrors())
                .build();
        }
        return null;
    }
}
