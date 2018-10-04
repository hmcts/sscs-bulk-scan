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
import uk.gov.hmcts.reform.sscs.domain.CcdCallbackResponse;
import uk.gov.hmcts.reform.sscs.domain.CreateCaseEvent;
import uk.gov.hmcts.reform.sscs.service.CcdCallbackHandler;

import static org.slf4j.LoggerFactory.getLogger;

@RestController
public class BulkScanController {

    private static final org.slf4j.Logger LOG = getLogger(BulkScanController.class);

    private final CcdCallbackHandler ccdCallbackHandler;

    @Autowired
    public BulkScanController(CcdCallbackHandler ccdCallbackHandler) {
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
        @RequestHeader(value = "Authorization") String authorisationToken,
        @RequestBody @ApiParam("CaseData") CreateCaseEvent caseDetailsRequest) {

        return ResponseEntity.ok(ccdCallbackHandler.handle(caseDetailsRequest, authorisationToken));
    }

}
