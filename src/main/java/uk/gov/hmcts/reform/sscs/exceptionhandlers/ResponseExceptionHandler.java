package uk.gov.hmcts.reform.sscs.exceptionhandlers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.hmcts.reform.sscs.exceptions.ForbiddenException;
import uk.gov.hmcts.reform.sscs.exceptions.UnauthorizedException;

@ControllerAdvice
public class ResponseExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ResponseExceptionHandler.class);

    @ExceptionHandler(UnauthorizedException.class)
    protected ResponseEntity<Void> handleUnAuthorizedException(Exception exception) {
        log.error(exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler(ForbiddenException.class)
    protected ResponseEntity<Void> handleForbiddenException(Exception exception) {
        log.error(exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<Void> handleInternalException(Exception exception) {
        log.error(exception.getMessage(), exception);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
