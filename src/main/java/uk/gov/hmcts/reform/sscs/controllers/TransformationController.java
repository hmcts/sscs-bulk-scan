package uk.gov.hmcts.reform.sscs.controllers;

import static org.slf4j.LoggerFactory.getLogger;

import javax.validation.Valid;
import org.slf4j.Logger;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.sscs.auth.AuthService;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.handlers.CcdCallbackHandler;
import uk.gov.hmcts.reform.sscs.domain.transformation.SuccessfulTransformationResponse;

@RestController
public class TransformationController {

    private static final Logger LOGGER = getLogger(TransformationController.class);

    private final AuthService authService;
    private final CcdCallbackHandler handler;

    public TransformationController(
        AuthService authService,
        CcdCallbackHandler handler
    ) {
        this.authService = authService;
        this.handler = handler;
    }

    @PostMapping("/transform-exception-record")
    public SuccessfulTransformationResponse transform(
        @RequestHeader(name = "ServiceAuthorization", required = false) String serviceAuthHeader,
        @Valid @RequestBody ExceptionRecord exceptionRecord
    ) {
        String serviceName = authService.authenticate(serviceAuthHeader);
        LOGGER.info("Request received to transform from service {}", serviceName);

        authService.assertIsAllowedToHandleCallback(serviceName);

        return handler.handle(exceptionRecord);
    }
}
