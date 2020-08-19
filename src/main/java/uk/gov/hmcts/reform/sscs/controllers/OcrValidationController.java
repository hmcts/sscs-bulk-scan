package uk.gov.hmcts.reform.sscs.controllers;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.ResponseEntity.ok;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import javax.validation.Valid;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.ccd.callback.Callback;
import uk.gov.hmcts.reform.sscs.ccd.callback.PreSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.ccd.deserialisation.SscsCaseCallbackDeserializer;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsCaseData;
import uk.gov.hmcts.reform.sscs.domain.FormType;
import uk.gov.hmcts.reform.sscs.domain.validation.OcrDataValidationRequest;
import uk.gov.hmcts.reform.sscs.domain.validation.OcrValidationResponse;
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;

@RestController
public class OcrValidationController {
    private static final Logger logger = getLogger(OcrValidationController.class);

    @Autowired
    private CcdCallbackHandler handler;
    private final AuthService authService;
    private final SscsCaseCallbackDeserializer deserializer;

    public OcrValidationController(
        CcdCallbackHandler handler,
        AuthService authService,
        SscsCaseCallbackDeserializer deserializer
    ) {
        this.handler = handler;
        this.authService = authService;
        this.deserializer = deserializer;
    }

    @PostMapping(
        path = "/forms/{form-type}/validate-ocr",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ApiOperation("Validates OCR form data based on form type")
    @ApiResponses({
        @ApiResponse(
            code = 200, response = OcrValidationResponse.class, message = "Validation executed successfully"
        ),
        @ApiResponse(code = 401, message = "Provided S2S token is missing or invalid"),
        @ApiResponse(code = 403, message = "S2S token is not authorized to use the service")
    })
    public ResponseEntity<OcrValidationResponse> validateOcrData(
        @RequestHeader(name = "ServiceAuthorization", required = false) String serviceAuthHeader,
        @PathVariable(name = "form-type", required = false) String formType,
        @Valid @RequestBody OcrDataValidationRequest ocrDataValidationRequest
    ) {
        String encodedFormType = UriUtils.encode(formType, StandardCharsets.UTF_8);
        if (!EnumUtils.isValidEnum(FormType.class, encodedFormType)) {
            logger.error("Invalid form type {} received when validating bulk scan", encodedFormType);

            return ok().body(new OcrValidationResponse(
                Collections.emptyList(),
                Collections.singletonList("Form type '" + encodedFormType + "' not found"),
                ValidationStatus.ERRORS
            ));
        }

        String serviceName = authService.authenticate(serviceAuthHeader);
        logger.info("Request received to validate ocr data from service {}", serviceName);

        authService.assertIsAllowedToHandleCallback(serviceName);

        CaseResponse result = handler.handleValidation(ExceptionRecord.builder().ocrDataFields(ocrDataValidationRequest.getOcrDataFields()).build());

        return ok().body(new OcrValidationResponse(result.getWarnings(), result.getErrors(), result.getStatus()));
    }

    @PostMapping(path = "/validate",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Handles callback from SSCS to check case meets validation to change state to appeal created")
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Callback was processed successfully or in case of an error message is attached to the case",
            response = CallbackResponse.class),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    public ResponseEntity<PreSubmitCallbackResponse<SscsCaseData>> handleValidationCallback(
        @RequestHeader(value = "Authorization") String userAuthToken,
        @RequestHeader(value = "serviceauthorization", required = false) String serviceAuthToken,
        @RequestHeader(value = "user-id") String userId,
        @RequestBody String message) {

        final Callback<SscsCaseData> callback = deserializer.deserialize(message);

        logger.info("Request received for to validate SSCS exception record id {}", callback.getCaseDetails().getId());

        String serviceName = authService.authenticate(serviceAuthToken);

        logger.info("Asserting that service {} is allowed to request validation of exception record {}", serviceName, callback.getCaseDetails().getId());

        authService.assertIsAllowedToHandleCallback(serviceName);

        IdamTokens token = IdamTokens.builder().serviceAuthorization(serviceAuthToken).idamOauth2Token(userAuthToken).userId(userId).build();

        PreSubmitCallbackResponse<SscsCaseData> ccdCallbackResponse = handler.handleValidationAndUpdate(callback, token);

        return ResponseEntity.ok(ccdCallbackResponse);
    }

}
