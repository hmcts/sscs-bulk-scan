package uk.gov.hmcts.reform.sscs.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.domain.CaseValidationResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SscsCaseValidator implements CaseValidator {

    @Value("${display.case.error}")
    private boolean displayCaseError; // Only required to demo error scenario

    @Value("${display.case.warning}")
    private boolean displayCaseWarning; // Only required to demo warning scenario

    @Override
    public CaseValidationResponse validate(Map<String, Object> caseData) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        // validate and populate error/warning lists here
        if (displayCaseError) {
            errors.add("Invalid NINO");
            errors.add("Invalid Apppeal details");
        }

        if (displayCaseWarning) {
            warnings.add("GAPS2 Evidence missing");
            warnings.add("Documents required");
        }

        return CaseValidationResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .build();
    }
}
