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

        checkAppellant(appeal, caseData);
        checkRepresentative(appeal, caseData);

        if (!doesMrnDateExist(appeal)) {
            warnings.add("mrn_date is empty");
        }

        isBenefitTypeValid(appeal);

        return warnings;
    }

    private void checkAppellant(Appeal appeal, Map<String, Object> caseData) {
        Appellant appellant = appeal.getAppellant();

        String personType = getPerson1OrPerson2(appellant);

        Name appellantName = appellant != null ? appellant.getName() : null;
        Address appellantAddress = appellant != null ? appellant.getAddress() : null;

        checkPerson(appellantName, appellantAddress, personType, caseData);

        if (!doesAppellantNinoExist(appellant)) {
            warnings.add(personType + "_nino is empty");
        }

        if (!isPhoneNumberValid(appellant)) {
            warnings.add(personType + "_phone is invalid");
        }
    }

    private void checkRepresentative(Appeal appeal, Map<String, Object> caseData) {
        if (appeal.getRep() != null && appeal.getRep().getHasRepresentative().equals("Yes")) {
            checkPerson(appeal.getRep().getName(), appeal.getRep().getAddress(), "representative", caseData);
        }
    }

    private void checkPerson(Name name, Address address, String personType, Map<String, Object> caseData) {
        if (!doesFirstNameExist(name)) {
            warnings.add(personType + "_first_name is empty");
        }
        if (!doesLastNameExist(name)) {
            warnings.add(personType + "_last_name is empty");
        }
        if (!doesAddressLine1Exist(address)) {
            warnings.add(personType + "_address_line1 is empty");
        }
        if (!doesAddressTownExist(address)) {
            warnings.add(personType + "_address_line3 is empty");
        }
        if (!doesAddressCountyExist(address)) {
            warnings.add(personType + "_address_line4 is empty");
        }
        if (isAddressPostcodeValid(address, personType) && address != null) {
            RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(address.getPostcode());

            if (!personType.equals("representative")) {
                caseData.put("region", rpc.getName());
                caseData.put("regionalProcessingCenter", rpc);
            }
        }
    }

    private Boolean doesFirstNameExist(Name name) {
        if (name != null) {
            return name.getFirstName() != null;
        }
        return false;
    }

    private Boolean doesLastNameExist(Name name) {
        if (name != null) {
            return name.getLastName() != null;
        }
        return false;
    }

    private Boolean doesAddressLine1Exist(Address address) {
        if (address != null) {
            return address.getLine1() != null;
        }
        return false;
    }

    private Boolean doesAddressTownExist(Address address) {
        if (address != null) {
            return address.getTown() != null;
        }
        return false;
    }

    private Boolean doesAddressCountyExist(Address address) {
        if (address != null) {
            return address.getCounty() != null;
        }
        return false;
    }

    private Boolean isAddressPostcodeValid(Address address, String personType) {
        if (address != null && address.getPostcode() != null) {
            if (address.getPostcode().matches("^((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z])|([Gg][Ii][Rr]))))\\s?([0-9][A-Za-z]{2})|(0[Aa]{2}))$")) {
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
