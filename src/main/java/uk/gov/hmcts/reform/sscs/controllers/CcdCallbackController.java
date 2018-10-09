package uk.gov.hmcts.reform.sscs.controllers;

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
import uk.gov.hmcts.reform.sscs.domain.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;
import uk.gov.hmcts.reform.sscs.service.CcdCallbackHandler;

import static org.slf4j.LoggerFactory.getLogger;

@RestController
public class CcdCallbackController {

    private static final Logger logger = getLogger(CcdCallbackController.class);

    private final CcdCallbackHandler ccdCallbackHandler;

    @Autowired
    public CcdCallbackController(CcdCallbackHandler ccdCallbackHandler) {
        this.ccdCallbackHandler = ccdCallbackHandler;
    }

    @PostMapping(path = "/create-case",
        consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @ApiOperation(value = "Handles about to submit callback from CCD")
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Callback was processed successfully or in case of an error message is attached to the case",
            response = CcdCallbackResponse.class),
        @ApiResponse(code = 400, message = "Bad Request"),
        @ApiResponse(code = 500, message = "Internal Server Error")
    })
    public ResponseEntity<CcdCallbackResponse> createCaseCallbackHandler(
        @RequestHeader(value = "Authorization") String userAuthorisationToken,
        @RequestHeader(value = "serviceauthorization") String serviceAuthorisationToken,
        @RequestHeader(value = "user-id") String userId,
        @RequestBody @ApiParam("CaseData") CreateCaseEvent caseDetailsRequest) {

        logger.info("CCD Call back case details  {}", caseDetailsRequest);

        return ResponseEntity.ok(ccdCallbackHandler.handle(caseDetailsRequest, userAuthorisationToken, serviceAuthorisationToken, userId));
    }

}
