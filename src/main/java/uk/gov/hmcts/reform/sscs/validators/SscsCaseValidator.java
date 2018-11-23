package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.BENEFIT_TYPE_DESCRIPTION;
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
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;
import uk.gov.hmcts.reform.sscs.ccd.domain.BenefitType;

@Component
public class SscsCaseValidator implements CaseValidator {

    List<String> warnings;
    List<String> errors;

    @Override
    public CaseResponse validate(Map<String, Object> caseData) {
        warnings = new ArrayList<>();
        errors = new ArrayList<>();

        validateAppeal((Appeal) caseData.get("appeal"));

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .build();
    }

    private List<String> validateAppeal(Appeal appeal) {

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
        isBenefitTypeValid(appeal.getBenefitType());

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

    private void isBenefitTypeValid(BenefitType benefitType) {
        if (benefitType != null && benefitType.getCode() != null) {
            if (!validBenefitTypes(benefitType.getCode())) {
                List<String> benefitNameList = new ArrayList<>();
                for (Benefit be : Benefit.values()) {
                    benefitNameList.add(be.name());
                }

                errors.add(BENEFIT_TYPE_DESCRIPTION + " invalid. Should be one of: " + String.join(", ", benefitNameList));
            }
        } else {
            warnings.add(BENEFIT_TYPE_DESCRIPTION + " is empty");
        }
    }

    private Boolean validBenefitTypes(String field) {
        for (Benefit benefit : Benefit.values()) {
            if (benefit.toString().toLowerCase().equals(field.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
