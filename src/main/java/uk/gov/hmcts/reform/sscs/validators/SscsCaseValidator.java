package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.constants.WarningMessage.getMessageByCallbackType;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.EXCEPTION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.VALIDATION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.findBooleanExists;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getField;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.ccd.client.model.AboutToStartOrSubmitCallbackResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.domain.CallbackType;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@Component
@Slf4j
public class SscsCaseValidator implements CaseValidator {

    private static final String PHONE_REGEX =
        "^((?:(?:\\(?(?:0(?:0|11)\\)?[\\s-]?\\(?|\\+)\\d{1,4}\\)?[\\s-]?(?:\\(?0\\)?[\\s-]?)?)|(?:\\(?0))(?:"
            + "(?:\\d{5}\\)?[\\s-]?\\d{4,5})|(?:\\d{4}\\)?[\\s-]?(?:\\d{5}|\\d{3}[\\s-]?\\d{3}))|(?:\\d{3}\\)"
            + "?[\\s-]?\\d{3}[\\s-]?\\d{3,4})|(?:\\d{2}\\)?[\\s-]?\\d{4}[\\s-]?\\d{4}))(?:[\\s-]?(?:x|ext\\.?|\\#)"
            + "\\d{3,4})?)?$";

    List<String> warnings;
    List<String> errors;

    private CallbackType callbackType;

    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final DwpAddressLookupService dwpAddressLookupService;
    private final SscsJsonExtractor sscsJsonExtractor;

    @Value("#{'${validation.titles}'.split(',')}")
    private List<String> titles;

    public SscsCaseValidator(RegionalProcessingCenterService regionalProcessingCenterService,
                             DwpAddressLookupService dwpAddressLookupService,
                             SscsJsonExtractor sscsJsonExtractor) {
        this.regionalProcessingCenterService = regionalProcessingCenterService;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.sscsJsonExtractor = sscsJsonExtractor;
    }

    @Override
    public CaseResponse validate(AboutToStartOrSubmitCallbackResponse transformErrorResponse, CaseDetails caseDetails, Map<String, Object> caseData) {
        warnings = transformErrorResponse != null && transformErrorResponse.getWarnings() != null ? transformErrorResponse.getWarnings() : new ArrayList<>();
        errors = new ArrayList<>();

        validateAppeal(caseDetails, caseData);

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .build();
    }

    private List<String> validateAppeal(CaseDetails caseDetails, Map<String, Object> caseData) {

        Map<String, Object> ocrCaseData = new HashMap<>();
        Appeal appeal = (Appeal) caseData.get("appeal");
        String appellantPersonType = getPerson1OrPerson2(appeal.getAppellant());

        callbackType = caseData.get("bulkScanCaseReference") != null ? EXCEPTION_CALLBACK : VALIDATION_CALLBACK;

        if (EXCEPTION_CALLBACK.equals(callbackType)) {
            ocrCaseData = sscsJsonExtractor.extractJson(caseDetails.getCaseData()).getOcrCaseData();
        }

        checkAppellant(appeal, ocrCaseData, caseData, appellantPersonType);
        checkRepresentative(appeal, ocrCaseData, caseData);
        checkMrnDetails(appeal);

        checkExcludedDates(appeal);

        isBenefitTypeValid(appeal);

        isHearingTypeValid(appeal);

        if (caseData.get("sscsDocument") != null) {
            @SuppressWarnings("unchecked")
            List<SscsDocument> lists = ((List<SscsDocument>) caseData.get("sscsDocument"));
            checkAdditionalEvidence(lists);
        }

        return warnings;
    }

    private void checkAdditionalEvidence(List<SscsDocument> sscsDocuments) {
        sscsDocuments.stream().filter(sscsDocument -> sscsDocument.getValue().getDocumentFileName() == null).forEach(sscsDocument -> {
            errors.add("There is a file attached to the case that does not have a filename, add a filename, e.g. filename.pdf");
        });

        sscsDocuments.stream().filter(sscsDocument -> sscsDocument.getValue().getDocumentLink() != null
            && sscsDocument.getValue().getDocumentLink().getDocumentFilename() != null
            && sscsDocument.getValue().getDocumentLink().getDocumentFilename().indexOf('.') == -1).forEach(sscsDocument -> {
                errors.add("There is a file attached to the case called " + sscsDocument.getValue().getDocumentLink().getDocumentFilename()
                    + ", filenames must have extension, e.g. filename.pdf");
            });
    }


    private void checkAppellant(Appeal appeal, Map<String, Object> ocrCaseData, Map<String, Object> caseData, String personType) {
        Appellant appellant = appeal.getAppellant();

        checkAppointee(appellant, ocrCaseData, caseData);

        Name appellantName = appellant != null ? appellant.getName() : null;
        Address appellantAddress = appellant != null ? appellant.getAddress() : null;
        Identity appellantIdentity = appellant != null ? appellant.getIdentity() : null;
        final Contact appellantContact = appellant != null ? appellant.getContact() : null;

        checkPersonName(appellantName, personType, appellant);
        checkPersonAddressAndDob(appellantAddress, appellantIdentity, personType, ocrCaseData, caseData, appellant);
        checkAppellantNino(appellant, personType);
        checkMobileNumber(appellantContact, personType);

    }

    private void checkAppointee(Appellant appellant, Map<String, Object> ocrCaseData, Map<String, Object> caseData) {
        if (appellant != null && !isAppointeeDetailsEmpty(appellant.getAppointee())) {
            checkPersonName(appellant.getAppointee().getName(), PERSON1_VALUE, appellant);
            checkPersonAddressAndDob(appellant.getAppointee().getAddress(), appellant.getAppointee().getIdentity(), PERSON1_VALUE, ocrCaseData, caseData, appellant);
            checkMobileNumber(appellant.getAppointee().getContact(), PERSON1_VALUE);
        }
    }

    private void checkRepresentative(Appeal appeal, Map<String, Object> ocrCaseData, Map<String, Object> caseData) {
        if (appeal.getRep() != null && appeal.getRep().getHasRepresentative().equals("Yes")) {
            final Contact repsContact = appeal.getRep().getContact();
            checkPersonAddressAndDob(appeal.getRep().getAddress(), null, REPRESENTATIVE_VALUE, ocrCaseData, caseData, appeal.getAppellant());

            Name name = appeal.getRep().getName();

            if (!isTitleValid(name.getTitle())) {
                warnings.add(getMessageByCallbackType(callbackType, REPRESENTATIVE_VALUE, getWarningMessageName(REPRESENTATIVE_VALUE, null) + TITLE, IS_INVALID));
            }

            if (!doesFirstNameExist(name) && !doesLastNameExist(name) && appeal.getRep().getOrganisation() == null) {
                warnings.add(getMessageByCallbackType(callbackType, "", REPRESENTATIVE_NAME_OR_ORGANISATION_DESCRIPTION, ARE_EMPTY));
            }

            checkPhoneNumber(repsContact, REPRESENTATIVE_VALUE);
            checkMobileNumber(repsContact, REPRESENTATIVE_VALUE);
        }
    }

    private void checkMrnDetails(Appeal appeal) {
        if (!doesMrnDateExist(appeal)) {
            warnings.add(getMessageByCallbackType(callbackType, "", MRN_DATE, IS_EMPTY));
        } else {
            checkDateValidDate(appeal.getMrnDetails().getMrnDate(), MRN_DATE, "", true);
        }

        if (!doesIssuingOfficeExist(appeal)) {
            warnings.add(getMessageByCallbackType(callbackType, "", ISSUING_OFFICE, IS_EMPTY));

        } else if (appeal.getBenefitType() != null && appeal.getBenefitType().getCode() != null) {

            Optional<OfficeMapping> officeMapping = dwpAddressLookupService.getDwpMappingByOffice(appeal.getBenefitType().getCode(), appeal.getMrnDetails().getDwpIssuingOffice());

            if (!officeMapping.isPresent()) {
                warnings.add(getMessageByCallbackType(callbackType, "", ISSUING_OFFICE, IS_INVALID));
            }
        }
    }

    private void checkPersonName(Name name, String personType, Appellant appellant) {

        if (!doesTitleExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + TITLE, IS_EMPTY));
        } else if (name != null && !isTitleValid(name.getTitle())) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + TITLE, IS_INVALID));
        }

        if (!doesFirstNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + FIRST_NAME, IS_EMPTY));
        }
        if (!doesLastNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + LAST_NAME, IS_EMPTY));
        }
    }

    private void checkPersonAddressAndDob(Address address, Identity identity, String personType, Map<String, Object> ocrCaseData, Map<String, Object> caseData, Appellant appellant) {

        boolean isAddressLine4Present = findBooleanExists(getField(ocrCaseData, personType + "_address_line4"));

        if (!doesAddressLine1Exist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + ADDRESS_LINE1, IS_EMPTY));
        }
        if (!doesAddressTownExist(address)) {
            String addressLine = (isAddressLine4Present) ? ADDRESS_LINE3 : ADDRESS_LINE2;
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + addressLine, IS_EMPTY));

        }
        if (!doesAddressCountyExist(address)) {
            String addressLine = (isAddressLine4Present) ? ADDRESS_LINE4 : "_ADDRESS_LINE3_COUNTY";
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + addressLine, IS_EMPTY));
        }
        if (isAddressPostcodeValid(address, personType, appellant) && address != null && personType.equals(PERSON1_VALUE)) {
            RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(address.getPostcode());

            if (rpc != null) {
                caseData.put("region", rpc.getName());
                caseData.put("regionalProcessingCenter", rpc);
            }
        }
        if (identity != null) {
            checkDateValidDate(identity.getDob(), getWarningMessageName(personType, appellant) + DOB, personType, true);
        }
    }

    private Boolean doesTitleExist(Name name) {
        if (name != null) {
            return name.getTitle() != null;
        }
        return false;
    }

    private boolean isTitleValid(String title) {
        if (title != null) {
            String strippedTitle = title.replaceAll("[-+.^:,'_]", "");
            return titles.stream().anyMatch(strippedTitle::equalsIgnoreCase);
        }
        return true;
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

    private void checkAppellantNino(Appellant appellant, String personType) {
        if (appellant != null && appellant.getIdentity() != null && appellant.getIdentity().getNino() != null) {
            if (!appellant.getIdentity().getNino().matches("^(?!BG)(?!GB)(?!NK)(?!KN)(?!TN)(?!NT)(?!ZZ)\\s?(?:[A-CEGHJ-PR-TW-Z]\\s?[A-CEGHJ-NPR-TW-Z])\\s?(?:\\d\\s?){6}([A-D]|\\s)\\s?$")) {
                warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + NINO, IS_INVALID));
            }
        } else {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + NINO, IS_EMPTY));
        }
    }

    private void checkDateValidDate(String dateField, String fieldName, String personType, Boolean isInFutureCheck) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (!StringUtils.isEmpty(dateField)) {
            try {
                LocalDate date = LocalDate.parse(dateField, formatter);

                if (isInFutureCheck && date.isAfter(LocalDate.now())) {
                    warnings.add(getMessageByCallbackType(callbackType, personType, fieldName, IS_IN_FUTURE));
                } else if (!isInFutureCheck && date.isBefore(LocalDate.now())) {
                    warnings.add(getMessageByCallbackType(callbackType, personType, fieldName, IS_IN_PAST));

                }
            } catch (DateTimeParseException ex) {
                log.error("Date time error", ex);
            }
        }
    }

    private Boolean doesMrnDateExist(Appeal appeal) {
        if (appeal.getMrnDetails() != null) {
            return appeal.getMrnDetails().getMrnDate() != null;
        }
        return false;
    }

    private Boolean doesIssuingOfficeExist(Appeal appeal) {
        if (appeal.getMrnDetails() != null) {
            return appeal.getMrnDetails().getDwpIssuingOffice() != null;
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

    private void isHearingTypeValid(Appeal appeal) {
        String hearingType = appeal.getHearingType();

        if (hearingType == null || (!hearingType.equals(HEARING_TYPE_ORAL) && !hearingType.equals(HEARING_TYPE_PAPER))) {
            warnings.add(getMessageByCallbackType(callbackType, "", HEARING_TYPE_DESCRIPTION, IS_INVALID));
        }
    }

    private void checkExcludedDates(Appeal appeal) {
        if (appeal.getHearingOptions() != null && appeal.getHearingOptions().getExcludeDates() != null) {
            for (ExcludeDate excludeDate : appeal.getHearingOptions().getExcludeDates()) {
                checkDateValidDate(excludeDate.getValue().getStart(), HEARING_OPTIONS_EXCLUDE_DATES_LITERAL, "", false);
            }
        }
    }

    private void checkMobileNumber(Contact contact, String personType) {
        if (contact != null && contact.getMobile() != null && !isPhoneOrMobileNumberValid(contact.getMobile())) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, null) + MOBILE, IS_INVALID));
        }
    }

    private void checkPhoneNumber(Contact contact, String personType) {
        if (contact != null && contact.getPhone() != null && !isPhoneOrMobileNumberValid(contact.getPhone())) {
            warnings.add(getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, null) + PHONE, IS_INVALID));
        }
    }

    private boolean isPhoneOrMobileNumberValid(String number) {
        if (number != null) {
            return number.matches(PHONE_REGEX);
        }
        return true;
    }

    private String getWarningMessageName(String personType, Appellant appellant) {
        if (personType.equals(REPRESENTATIVE_VALUE)) {
            return "REPRESENTATIVE";
        } else if (personType.equals(PERSON2_VALUE) || appellant == null || isAppointeeDetailsEmpty(appellant.getAppointee())) {
            return "APPELLANT";
        } else {
            return "APPOINTEE";
        }
    }
}
