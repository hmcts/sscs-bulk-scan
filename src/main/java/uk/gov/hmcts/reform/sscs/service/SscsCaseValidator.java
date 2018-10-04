package uk.gov.hmcts.reform.sscs.service;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.CaseValidationResponse;

import java.util.Map;

@Component
public class SscsCaseValidator implements CaseValidator {
    @Override
    public CaseValidationResponse validate(Map<String, Object> caseData) {
        // validate and populate error/warning lists here
        return CaseValidationResponse.builder().build();
    }
}
