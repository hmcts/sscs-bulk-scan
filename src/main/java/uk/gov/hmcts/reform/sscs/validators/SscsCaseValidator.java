package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@Component
public class SscsCaseValidator implements CaseValidator {

    List<String> warnings;
    List<String> errors;

    private final RegionalProcessingCenterService regionalProcessingCenterService;

    public SscsCaseValidator(RegionalProcessingCenterService regionalProcessingCenterService) {
        this.regionalProcessingCenterService = regionalProcessingCenterService;
    }

    @Override
    public CaseResponse validate(Map<String, Object> caseData) {
        warnings = new ArrayList<>();
        errors = new ArrayList<>();

        validateAppeal(caseData);

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .build();
    }

    private List<String> validateAppeal(Map<String, Object> caseData) {

        Appeal appeal = (Appeal) caseData.get("appeal");

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
        if (!doesAppellantNinoExist(appellant)) {
            warnings.add(personType + "_nino is empty");
        }
        if (!doesMrnDateExist(appeal)) {
            warnings.add("mrn_date is empty");
        }

        if (isAppellantAddressPostcodeValid(appellant, personType)) {
            RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(appellant.getAddress().getPostcode());

            caseData.put("region", rpc.getName());
            caseData.put("regionalProcessingCenter", rpc);
        }

        if (!isPhoneNumberValid(appellant)) {
            warnings.add(personType + "_phone is invalid");
        }

        isBenefitTypeValid(appeal);

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

    private Boolean isAppellantAddressPostcodeValid(Appellant appellant, String personType) {
        if (appellant != null && appellant.getAddress() != null && appellant.getAddress().getPostcode() != null) {
            if (appellant.getAddress().getPostcode().matches("^((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z])|([Gg][Ii][Rr]))))\\s?([0-9][A-Za-z]{2})|(0[Aa]{2}))$")) {
                return true;
            } else {
                warnings.add(personType + "_postcode is not a valid postcode");
                return false;
            }
        }
        warnings.add(personType + "_postcode is empty");
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
        if (appellant == null || appellant.getAppointee() == null || isAppointeeDetailsEmpty(appellant.getAppointee())) {
            return PERSON1_VALUE;
        } else {
            return PERSON2_VALUE;
        }
    }

    private Boolean isAppointeeDetailsEmpty(Appointee appointee) {
        return (appointee.getAddress() == null
            && appointee.getContact() == null
            && appointee.getIdentity() == null
            && appointee.getName() == null);
    }

    private void isBenefitTypeValid(Appeal appeal) {
        BenefitType benefitType = appeal.getBenefitType();
        if (benefitType != null && benefitType.getCode() != null) {
            if (!Benefit.isBenefitTypeValid(benefitType.getCode())) {
                List<String> benefitNameList = new ArrayList<>();
                for (Benefit be : Benefit.values()) {
                    benefitNameList.add(be.name());
                }
                errors.add(BENEFIT_TYPE_DESCRIPTION + " invalid. Should be one of: " + String.join(", ", benefitNameList));
            } else {
                appeal.setBenefitType(BenefitType.builder()
                    .code(benefitType.getCode().toUpperCase())
                    .description(Benefit.getBenefitByCode(benefitType.getCode().toUpperCase()).getDescription())
                    .build());
            }
        } else {
            warnings.add(BENEFIT_TYPE_DESCRIPTION + " is empty");
        }
    }

    private boolean isPhoneNumberValid(Appellant appellant) {
        if (appellant != null && appellant.getContact() != null && appellant.getContact().getPhone() != null) {
            return appellant.getContact().getPhone().matches("^[0-9\\-+ ]{10,17}$");
        }
        return true;
    }
}
