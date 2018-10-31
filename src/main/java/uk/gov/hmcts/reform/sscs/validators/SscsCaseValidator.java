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

<<<<<<< HEAD
    private List<String> populateErrors(Appeal appeal) {
        List<String> errors = new ArrayList<>();
        if (!checkAppellantExists(appeal.getAppellant())) {
            errors.add("person1 address and name mandatory fields are empty");
        }
=======
    private List<String> populateErrors(Map<String, Object> caseData) {
        /**
         * <pre>
         * TODO : Currently transformation is not populating data which validator is expecting(keys with person1 and person2).
         * </pre>
         */
        /*List<String> errors = new ArrayList<>();

        //if (!hasPerson(caseData, "person1") && !hasPerson(caseData, "person2")) {
            errors.add("person1 and person2 are both empty");
        }*/
>>>>>>> Ignored validator tests and fixed checkstyle issue

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
