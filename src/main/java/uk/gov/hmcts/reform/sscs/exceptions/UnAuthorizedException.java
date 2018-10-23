package uk.gov.hmcts.reform.sscs.exceptions;

public class UnAuthorizedException extends RuntimeException {
    private static final long serialVersionUID = -948721106877408028L;

    public UnAuthorizedException(String message) {
        super(message);
    }
}
