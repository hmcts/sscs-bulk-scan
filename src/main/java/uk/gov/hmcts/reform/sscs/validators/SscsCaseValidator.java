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
            .transformedCase(caseData)
            .build();
    }

    private List<String> populateWarnings(Appeal appeal) {
        List<String> warnings = new ArrayList<>();

        Appellant appellant = appeal.getAppellant();

        String personType = getPerson1OrPerson2(appellant);
        if (!doesAppellantFirstNameExist(appellant)) {
            warnings.add(personType + "_first_name is empty");
        }
        if (!doesAppellantLastNameExist(appellant)) {
            warnings.add(personType + "_last_name is empty");
        }
        if (!doesAppellantAddressLine1Exist(appellant)) {
            warnings.add(personType + "_address_line1 is empty");
        }
        if (!doesAppellantAddressTownExist(appellant)) {
            warnings.add(personType + "_address_line3 is empty");
        }
        if (!doesAppellantAddressCountyExist(appellant)) {
            warnings.add(personType + "_address_line4 is empty");
        }
        if (!doesAppellantAddressPostcodeExist(appellant)) {
            warnings.add(personType + "_postcode is empty");
        }
        if (!doesAppellantNinoExist(appellant)) {
            warnings.add(personType + "_nino is empty");
        }
        if (!doesMrnDateExist(appeal)) {
            warnings.add("mrn_date is empty");
        }

        return warnings;
    }

    private Boolean doesAppellantFirstNameExist(Appellant appellant) {
        if (appellant != null && appellant.getName() != null) {
            return appellant.getName().getFirstName() != null;
        }
        return false;
    }

    private Boolean doesAppellantLastNameExist(Appellant appellant) {
        if (appellant != null && appellant.getName() != null) {
            return appellant.getName().getLastName() != null;
        }
        return false;
    }

    private Boolean doesAppellantAddressLine1Exist(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getLine1() != null;
        }
        return false;
    }

    private Boolean doesAppellantAddressTownExist(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getTown() != null;
        }
        return false;
    }

    private Boolean doesAppellantAddressCountyExist(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getCounty() != null;
        }
        return false;
    }

    private Boolean doesAppellantAddressPostcodeExist(Appellant appellant) {
        if (appellant != null && appellant.getAddress() != null) {
            return appellant.getAddress().getPostcode() != null;
        }
        return false;
    }

    private Boolean doesAppellantNinoExist(Appellant appellant) {
        if (appellant != null && appellant.getIdentity() != null) {
            return appellant.getIdentity().getNino() != null;
        }
        return false;
    }

    private Boolean doesMrnDateExist(Appeal appeal) {
        if (appeal.getMrnDetails() != null) {
            return appeal.getMrnDetails().getMrnDate() != null;
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
