package uk.gov.hmcts.reform.sscs.service;

import uk.gov.hmcts.reform.sscs.domain.CaseValidationResponse;

import java.util.Map;

public interface CaseValidator {
    CaseValidationResponse validate(Map<String, Object> caseData);
}
