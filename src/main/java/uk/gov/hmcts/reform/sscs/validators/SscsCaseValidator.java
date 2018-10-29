package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.hasPerson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;

@Component
public class SscsCaseValidator implements CaseValidator {

    @Override
    public CaseValidationResponse validate(Map<String, Object> caseData) {
        populateErrors(caseData);

        return CaseValidationResponse.builder()
            .errors(populateErrors(caseData))
            .build();
    }

    private List<String> populateErrors(Map<String, Object> caseData) {
        List<String> errors = new ArrayList<>();

        if (!hasPerson(caseData, "person1") && !hasPerson(caseData, "person2")) {
            errors.add("person1 and person2 are both empty");
        }

        return errors;
    }

}
