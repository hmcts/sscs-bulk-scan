package uk.gov.hmcts.reform.sscs.exceptions;

import static java.lang.String.format;

import uk.gov.hmcts.reform.logging.exception.AlertLevel;
import uk.gov.hmcts.reform.logging.exception.UnknownErrorCodeException;

@SuppressWarnings("squid:MaximumInheritanceDepth")
public class CaseDataHelperException extends UnknownErrorCodeException {

    public CaseDataHelperException(String exceptionRecordId, Exception ex) {
        super(AlertLevel.P3, format("Exception thrown for exception record [%s]", exceptionRecordId), ex);
    }
}
