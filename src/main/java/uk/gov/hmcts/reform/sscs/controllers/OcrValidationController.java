package uk.gov.hmcts.reform.sscs.controllers;

import static org.slf4j.LoggerFactory.getLogger;
import static org.springframework.http.ResponseEntity.ok;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import javax.validation.Valid;
import org.apache.commons.lang3.EnumUtils;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.domain.FormType;
import uk.gov.hmcts.reform.sscs.domain.validation.OcrValidationResponse;
import uk.gov.hmcts.reform.sscs.domain.validation.ValidationStatus;
import uk.gov.hmcts.reform.sscs.validators.SscsKeyValuePairValidator;

@RestController
public class OcrValidationController {
    private static final Logger logger = getLogger(OcrValidationController.class);

    @Autowired
    private SscsKeyValuePairValidator keyValuePairValidator;
    private final AuthService authService;

    public OcrValidationController(
        SscsKeyValuePairValidator keyValuePairValidator,
        AuthService authService
    ) {
        this.keyValuePairValidator = keyValuePairValidator;
        this.authService = authService;
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
        @Valid @RequestBody Map<String, Object> request
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

        //FIXME: Put form type in
        CaseResponse result = keyValuePairValidator.validate(request, "ocr_data_fields");

        return ok().body(new OcrValidationResponse(result.getWarnings(), result.getErrors(), result.getStatus()));
    }

}
