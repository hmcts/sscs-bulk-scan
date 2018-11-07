package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.PERSON1_VALUE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.PERSON2_VALUE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;

@Component
public class SscsCaseValidator implements CaseValidator {

    @Override
    public CaseResponse validate(Map<String, Object> caseData) {

        return CaseResponse.builder()
            .warnings(populateWarnings((Appeal) caseData.get("appeal")))
            .build();
    }

    private List<String> populateWarnings(Appeal appeal) {
        List<String> warnings = new ArrayList<>();

        Appellant appellant = appeal.getAppellant();

        String personType = getPerson1OrPerson2(appellant);
        if (!isAppellantFirstNameExists(appellant)) {
            warnings.add(personType + "_first_name is empty");
        }
        if (!isAppellantLastNameExists(appellant)) {
            warnings.add(personType + "_last_name is empty");
        }
        if (!isAppellantAddressLine1Exists(appellant)) {
            warnings.add(personType + "_address_line1 is empty");
        }
        if (!isAppellantAddressTownExists(appellant)) {
            warnings.add(personType + "_address_line3 is empty");
        }
        if (!isAppellantAddressCountyExists(appellant)) {
            warnings.add(personType + "_address_line4 is empty");
        }
        if (!isAppellantAddressPostcodeExists(appellant)) {
            warnings.add(personType + "_postcode is empty");
        }
        if (!isAppellantNinoExists(appellant)) {
            warnings.add(personType + "_nino is empty");
        }

        return warnings;
    }

    private Boolean isAppellantFirstNameExists(Appellant appellant) {
        if (appellant != null && appellant.getName() != null) {
            return appellant.getName().getFirstName() != null;
        }
        return false;
    }

    private Boolean isAppellantLastNameExists(Appellant appellant) {
        if (appellant != null && appellant.getName() != null) {
            return appellant.getName().getLastName() != null;
        }
        return false;
    }

    private Boolean isAppellantAddressLine1Exists(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getLine1() != null;
        }
        return false;
    }

    private Boolean isAppellantAddressTownExists(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getTown() != null;
        }
        return false;
    }

    private Boolean isAppellantAddressCountyExists(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getCounty() != null;
        }
        return false;
    }

    private Boolean isAppellantAddressPostcodeExists(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getPostcode() != null;
        }
        return false;
    }

    private Boolean isAppellantNinoExists(Appellant appellant) {
        if (appellant != null && appellant.getIdentity() != null) {
            return appellant.getIdentity().getNino() != null;
        }
        return false;
    }

    private String getPerson1OrPerson2(Appellant appellant) {
        if (appellant == null || appellant.getAppointee() == null) {
            return PERSON1_VALUE;
        } else {
            return PERSON2_VALUE;
        }
    }
}
