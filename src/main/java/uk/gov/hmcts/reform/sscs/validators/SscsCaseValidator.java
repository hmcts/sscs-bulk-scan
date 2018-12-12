package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.constants.WarningMessage.getMessageByCallbackType;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.EXCEPTION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.VALIDATION_CALLBACK;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.CallbackType;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@Component
@Slf4j
public class SscsCaseValidator implements CaseValidator {

    List<String> warnings;
    List<String> errors;

    private CallbackType callbackType;

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
        String appellantPersonType = getPerson1OrPerson2(appeal.getAppellant());

        callbackType = caseData.get("bulkScanCaseReference") != null ? EXCEPTION_CALLBACK : VALIDATION_CALLBACK;

        checkAppellant(appeal, caseData, appellantPersonType);
        checkRepresentative(appeal, caseData);

        if (!doesMrnDateExist(appeal)) {
            warnings.add(getMessageByCallbackType(callbackType, "", MRN_DATE, IS_EMPTY));
        }

        isBenefitTypeValid(appeal);

        return warnings;
    }

    private void checkAppellant(Appeal appeal, Map<String, Object> caseData, String personType) {
        Appellant appellant = appeal.getAppellant();

        Name appellantName = appellant != null ? appellant.getName() : null;
        Address appellantAddress = appellant != null ? appellant.getAddress() : null;

        checkPerson(appellantName, appellantAddress, personType, caseData, appellant);

        if (!doesAppellantNinoExist(appellant)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + NINO, IS_EMPTY));
        }

        if (!isPhoneNumberValid(appellant)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + PHONE, IS_INVALID));
        }

        checkAppointee(appellant, caseData);
    }

    private void checkAppointee(Appellant appellant, Map<String, Object> caseData) {

        if (appellant != null && !isAppointeeDetailsEmpty(appellant.getAppointee())) {
            checkPerson(appellant.getAppointee().getName(), appellant.getAppointee().getAddress(), PERSON1_VALUE, caseData, appellant);
        }
    }

    private void checkRepresentative(Appeal appeal, Map<String, Object> caseData) {
        if (appeal.getRep() != null && appeal.getRep().getHasRepresentative().equals("Yes")) {
            checkPerson(appeal.getRep().getName(), appeal.getRep().getAddress(), REPRESENTATIVE_VALUE, caseData, appeal.getAppellant());
        }
    }

    private void checkPerson(Name name, Address address, String personType, Map<String, Object> caseData, Appellant appellant) {

        if (!doesFirstNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + FIRST_NAME, IS_EMPTY));
        }
        if (!doesLastNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + LAST_NAME, IS_EMPTY));
        }
        if (!doesAddressLine1Exist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + ADDRESS_LINE1, IS_EMPTY));
        }
        if (!doesAddressTownExist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + ADDRESS_LINE3, IS_EMPTY));

        }
        if (!doesAddressCountyExist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + ADDRESS_LINE4, IS_EMPTY));
        }
        if (isAddressPostcodeValid(address, personType, appellant) && address != null && personType.equals(PERSON1_VALUE)) {
            RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(address.getPostcode());
            caseData.put("region", rpc.getName());
            caseData.put("regionalProcessingCenter", rpc);
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

    private Boolean isAddressPostcodeValid(Address address, String personType, Appellant appellant) {
        if (address != null && address.getPostcode() != null) {
            if (address.getPostcode().matches("^((([A-Za-z][0-9]{1,2})|(([A-Za-z][A-Ha-hJ-Yj-y][0-9]{1,2})|(([A-Za-z][0-9][A-Za-z])|([A-Za-z][A-Ha-hJ-Yj-y][0-9]?[A-Za-z])|([Gg][Ii][Rr]))))\\s?([0-9][A-Za-z]{2})|(0[Aa]{2}))$")) {
                return true;
            } else {
                warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + ADDRESS_POSTCODE, "is not a valid postcode"));
                return false;
            }
        }
        warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + ADDRESS_POSTCODE, IS_EMPTY));
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
        if (appellant == null || isAppointeeDetailsEmpty(appellant.getAppointee())) {
            return PERSON1_VALUE;
        } else {
            return PERSON2_VALUE;
        }
    }

    private Boolean isAppointeeDetailsEmpty(Appointee appointee) {
        return appointee == null
            || (isAddressEmpty(appointee.getAddress())
            && isContactEmpty(appointee.getContact())
            && isIdentityEmpty(appointee.getIdentity())
            && isNameEmpty(appointee.getName()));
    }

    private Boolean isAddressEmpty(Address address) {
        return address == null
            || (address.getLine1() == null
            && address.getLine2() == null
            && address.getTown() == null
            && address.getCounty() == null
            && address.getPostcode() == null);
    }

    private Boolean isContactEmpty(Contact contact) {
        return contact == null
            || (contact.getEmail() == null
            && contact.getPhone() == null
            && contact.getMobile() == null);
    }

    private Boolean isIdentityEmpty(Identity identity) {
        return identity == null
            || (identity.getDob() == null
            && identity.getNino() == null);
    }

    private Boolean isNameEmpty(Name name) {
        return name == null
            || (name.getFirstName() == null
            && name.getLastName() == null
            && name.getTitle() == null);
    }

    private void isBenefitTypeValid(Appeal appeal) {
        BenefitType benefitType = appeal.getBenefitType();
        if (benefitType != null && benefitType.getCode() != null) {
            if (!Benefit.isBenefitTypeValid(benefitType.getCode())) {
                List<String> benefitNameList = new ArrayList<>();
                for (Benefit be : Benefit.values()) {
                    benefitNameList.add(be.name());
                }
                errors.add(getMessageByCallbackType(callbackType, "", BENEFIT_TYPE_DESCRIPTION, "invalid. Should be one of: " + String.join(", ", benefitNameList)));
            } else {
                appeal.setBenefitType(BenefitType.builder()
                    .code(benefitType.getCode().toUpperCase())
                    .description(Benefit.getBenefitByCode(benefitType.getCode().toUpperCase()).getDescription())
                    .build());
            }
        } else {
            warnings.add(getMessageByCallbackType(callbackType, "", BENEFIT_TYPE_DESCRIPTION, IS_EMPTY));
        }
    }

    private boolean isPhoneNumberValid(Appellant appellant) {
        if (appellant != null && appellant.getContact() != null && appellant.getContact().getPhone() != null) {
            return appellant.getContact().getPhone().matches("^[0-9\\-+ ]{10,17}$");
        }
        return true;
    }

    private String getWarningMessageName(String personType, Appellant appellant) {
        if (personType == REPRESENTATIVE_VALUE) {
            return "REPRESENTATIVE";
        } else if (personType == PERSON2_VALUE || appellant == null || isAppointeeDetailsEmpty(appellant.getAppointee())) {
            return "APPELLANT";
        } else {
            return "APPOINTEE";
        }
    }
}
