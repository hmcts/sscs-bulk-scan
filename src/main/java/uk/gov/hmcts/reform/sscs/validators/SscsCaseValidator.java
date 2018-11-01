package uk.gov.hmcts.reform.sscs.validators;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseValidationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;

@Component
public class SscsCaseValidator implements CaseValidator {

    @Override
    public CaseValidationResponse validate(Map<String, Object> caseData) {

        return CaseValidationResponse.builder()
            .errors(populateErrors((Appeal) caseData.get("appeal")))
            .build();
    }

    private List<String> populateErrors(Appeal appeal) {
        List<String> errors = new ArrayList<>();
        if (!checkAppellantExists(appeal.getAppellant())) {
            errors.add("person1 address and name mandatory fields are empty");
        }

        return errors;
    }

    private Boolean checkAppellantExists(Appellant appellant) {
        if (appellant != null && appellant.getName() != null && appellant.getAddress() != null) {
            return Stream.of(appellant,
                appellant.getName().getFirstName(),
                appellant.getName().getLastName(),
                appellant.getAddress().getLine1(),
                appellant.getAddress().getTown(),
                appellant.getAddress().getCounty(),
                appellant.getAddress().getPostcode()
            )
                .noneMatch(Objects::isNull);
        } else {
            return false;
        }
    }
}
