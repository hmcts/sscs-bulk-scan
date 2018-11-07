package uk.gov.hmcts.reform.sscs.exceptions;

public class CcdFindCaseException extends RuntimeException {
    private static final long serialVersionUID = 1641102126427991237L;

    public CcdFindCaseException(Throwable throwable) {
        super(throwable);
    }
}
