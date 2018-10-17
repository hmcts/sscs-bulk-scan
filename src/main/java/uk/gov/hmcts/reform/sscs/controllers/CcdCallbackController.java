package uk.gov.hmcts.reform.sscs.controllers;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionCaseData;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;

@RestController
public class CcdCallbackController {

    private final CcdCallbackHandler ccdCallbackHandler;

    @Autowired
    public CcdCallbackController(CcdCallbackHandler ccdCallbackHandler) {
        this.ccdCallbackHandler = ccdCallbackHandler;
    }

    @PostMapping(path = "/exception-record",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Handles about to submit callback from CCD")
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Callback was processed successfully or in case of an error message is attached to the case",
            response = AboutToStartOrSubmitCallbackResponse.class),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    public ResponseEntity<AboutToStartOrSubmitCallbackResponse> handleExceptionRecordCallback(
        @RequestHeader(value = "Authorization") String userAuthToken,
        @RequestHeader(value = "serviceauthorization") String serviceAuthToken,
        @RequestHeader(value = "user-id") String userId,
        @RequestBody @ApiParam("CaseData") ExceptionCaseData caseData) {

        AboutToStartOrSubmitCallbackResponse ccdCallbackResponse = ccdCallbackHandler.handle(caseData, userAuthToken, serviceAuthToken, userId);

        return ResponseEntity.ok(ccdCallbackResponse);
    }

}
