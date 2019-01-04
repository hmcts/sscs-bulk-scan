package uk.gov.hmcts.reform.sscs.controllers;

import static org.slf4j.LoggerFactory.getLogger;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.ccd.client.model.CallbackResponse;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.Token;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.domain.ValidateCaseData;

@RestController
public class CcdCallbackController {

    private static final Logger logger = getLogger(CcdCallbackHandler.class);

    private final CcdCallbackHandler ccdCallbackHandler;

    private final AuthService authService;

    @Autowired
    public CcdCallbackController(
        CcdCallbackHandler ccdCallbackHandler,
        AuthService authService
    ) {
        this.ccdCallbackHandler = ccdCallbackHandler;
        this.authService = authService;
    }

    @PostMapping(path = "/exception-record",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Handles about to submit callback from CCD")
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Callback was processed successfully or in case of an error message is attached to the case",
            response = CallbackResponse.class),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    public ResponseEntity<CallbackResponse> handleExceptionRecordCallback(
        @RequestHeader(value = "Authorization") String userAuthToken,
        @RequestHeader(value = "serviceauthorization", required = false) String serviceAuthToken,
        @RequestHeader(value = "user-id") String userId,
        @RequestBody @ApiParam("CaseData") ExceptionCaseData caseData) {

        String exceptionRecordId = caseData.getCaseDetails().getCaseId();

        logger.info("Request received for to create CCD case for SSCS exception record id {}", exceptionRecordId);

        String serviceName = authService.authenticate(serviceAuthToken);

        logger.info("Asserting that service {} is allowed to create CCD case from exception record id {}", serviceName, exceptionRecordId);

        authService.assertIsAllowedToHandleCallback(serviceName);

        logger.info("Service {} allowed for exception record id {}, proceeding to create access token", serviceName, exceptionRecordId);

        Token token = Token.builder().serviceAuthToken(serviceAuthToken).userAuthToken(userAuthToken).userId(userId).build();

        CallbackResponse ccdCallbackResponse =
            ccdCallbackHandler.handle(caseData, token);

        return ResponseEntity.ok(ccdCallbackResponse);
    }

    @PostMapping(path = "/validate-record",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Handles callback from SSCS to check case meets validation to change state to appeal created")
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Callback was processed successfully or in case of an error message is attached to the case",
            response = CallbackResponse.class),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    public ResponseEntity<CallbackResponse> handleValidationCallback(
        @RequestHeader(value = "Authorization") String userAuthToken,
        @RequestHeader(value = "serviceauthorization", required = false) String serviceAuthToken,
        @RequestHeader(value = "user-id") String userId,
        @RequestBody @ApiParam("CaseData") ValidateCaseData caseData) {

        String exceptionRecordId = caseData.getCaseDetails().getCaseId();

        logger.info("Request received for to validate SSCS exception record id {}", exceptionRecordId);

        String serviceName = authService.authenticate(serviceAuthToken);

        logger.info("Asserting that service {} is allowed to request validation of exception record {}", serviceName, exceptionRecordId);

        authService.assertIsAllowedToHandleCallback(serviceName);

        CallbackResponse ccdCallbackResponse =
            ccdCallbackHandler.handleValidationAndUpdate(caseData);

        return ResponseEntity.ok(ccdCallbackResponse);
    }

}
