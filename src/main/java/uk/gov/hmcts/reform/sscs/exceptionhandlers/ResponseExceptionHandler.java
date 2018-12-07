package uk.gov.hmcts.reform.sscs.exceptionhandlers;

import feign.FeignException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.exceptions.ForbiddenException;
import uk.gov.hmcts.reform.sscs.exceptions.UnauthorizedException;

@ControllerAdvice
public class ResponseExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    protected ResponseEntity<Void> handleUnAuthorizedException(UnauthorizedException exception) {
        log.error(exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(ForbiddenException.class)
    protected ResponseEntity<Void> handleForbiddenException(ForbiddenException exception) {
        log.error(exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(FeignException.class)
    protected ResponseEntity<String> handleFeignException(FeignException exception) {
        log.error(exception.getMessage(), exception);

        return ResponseEntity.status(exception.status()).body(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<CaseResponse> handleInternalException(Exception exception) {
        log.error(exception.getMessage(), exception);

        List<String> errors = new ArrayList<>();

        errors.add("There was an unknown error when processing the case. If the error persists, please contact the Bulk Scan development team");

        return ResponseEntity.ok(CaseResponse.builder().errors(errors).build());
    }
}
