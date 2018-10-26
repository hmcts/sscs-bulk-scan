package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.hasPerson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;

@Component
public class SscsCaseValidator implements CaseValidator {

    @Value("${display.case.error}")
    private boolean displayCaseError; // Only required to demo error scenario

    @Value("${display.case.warning}")
    private boolean displayCaseWarning; // Only required to demo warning scenario

    List<String> errors;
    List<String> warnings;

    @Override
    public CaseValidationResponse validate(Map<String, Object> caseData) {
        errors = new ArrayList<>();
        warnings = new ArrayList<>();

        populateErrors(caseData);

        return CaseValidationResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .build();
    }

    private void populateErrors(Map<String, Object> caseData) {
        if (displayCaseError) {
            if (!hasPerson(caseData, "person1") && !hasPerson(caseData, "person2")) {
                errors.add("person1 and person2 are both empty");
            }
        }
    }

}
