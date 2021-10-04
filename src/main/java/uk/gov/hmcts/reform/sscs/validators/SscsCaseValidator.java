package uk.gov.hmcts.reform.sscs.validators;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.constants.WarningMessage.getMessageByCallbackType;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.EXCEPTION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.VALIDATION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ExceptionRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.validators.CaseValidator;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.constants.WarningMessage;
import uk.gov.hmcts.reform.sscs.domain.CallbackType;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.RegionalProcessingCenterService;

@Component
@Slf4j
public class SscsCaseValidator implements CaseValidator {

    @SuppressWarnings("squid:S5843")
    private static final String PHONE_REGEX =
        "^((?:(?:\\(?(?:0(?:0|11)\\)?[\\s-]?\\(?|\\+)\\d{1,4}\\)?[\\s-]?(?:\\(?0\\)?[\\s-]?)?)|(?:\\(?0))(?:"
            + "(?:\\d{5}\\)?[\\s-]?\\d{4,5})|(?:\\d{4}\\)?[\\s-]?(?:\\d{5}|\\d{3}[\\s-]?\\d{3}))|(?:\\d{3}\\)"
            + "?[\\s-]?\\d{3}[\\s-]?\\d{3,4})|(?:\\d{2}\\)?[\\s-]?\\d{4}[\\s-]?\\d{4}))(?:[\\s-]?(?:x|ext\\.?|\\#)"
            + "\\d{3,4})?)?$";

    @SuppressWarnings("squid:S5843")
    private static final String UK_NUMBER_REGEX =
        "^\\(?(?:(?:0(?:0|11)\\)?[\\s-]?\\(?|\\+)44\\)?[\\s-]?\\(?(?:0\\)?[\\s-]?\\(?)?|0)(?:\\d{2}\\)?[\\s-]?\\d{4}"
            + "[\\s-]?\\d{4}|\\d{3}\\)?[\\s-]?\\d{3}[\\s-]?\\d{3,4}|\\d{4}\\)?[\\s-]?(?:\\d{5}|\\d{3}[\\s-]?\\d{3})|"
            +
            "\\d{5}\\)?[\\s-]?\\d{4,5}|8(?:00[\\s-]?11[\\s-]?11|45[\\s-]?46[\\s-]?4\\d))(?:(?:[\\s-]?(?:x|ext\\.?\\s?|"
            + "\\#)\\d+)?)$";

    @SuppressWarnings("squid:S5843")
    private static final String ADDRESS_REGEX =
        "^[a-zA-ZÀ-ž0-9]{1}[a-zA-ZÀ-ž0-9 \\r\\n\\.“”\",’\\?\\!\\[\\]\\(\\)/£:\\\\_+\\-%&;]{1,}$";

    @SuppressWarnings("squid:S5843")
    private static final String COUNTY_REGEX =
        "^\\.$|^[a-zA-ZÀ-ž0-9]{1}[a-zA-ZÀ-ž0-9 \\r\\n\\.“”\",’\\?\\!\\[\\]\\(\\)/£:\\\\_+\\-%&;]{1,}$";
    private final RegionalProcessingCenterService regionalProcessingCenterService;
    private final DwpAddressLookupService dwpAddressLookupService;
    private final PostcodeValidator postcodeValidator;
    private final SscsJsonExtractor sscsJsonExtractor;
    List<String> warnings;
    List<String> errors;
    private CallbackType callbackType;
    @Value("#{'${validation.titles}'.split(',')}")
    private List<String> titles;

    //TODO: Remove when uc-office-feature switched on
    private boolean ucOfficeFeatureActive;

    public SscsCaseValidator(RegionalProcessingCenterService regionalProcessingCenterService,
                             DwpAddressLookupService dwpAddressLookupService,
                             PostcodeValidator postcodeValidator,
                             SscsJsonExtractor sscsJsonExtractor,
                             @Value("${feature.uc-office-feature.enabled}") boolean ucOfficeFeatureActive) {
        this.regionalProcessingCenterService = regionalProcessingCenterService;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.postcodeValidator = postcodeValidator;
        this.sscsJsonExtractor = sscsJsonExtractor;
        this.ucOfficeFeatureActive = ucOfficeFeatureActive;
    }

    public void setUcOfficeFeatureActive(boolean ucOfficeFeatureActive) {
        this.ucOfficeFeatureActive = ucOfficeFeatureActive;
    }

    @Override
    public CaseResponse validateExceptionRecord(CaseResponse transformResponse, ExceptionRecord exceptionRecord,
                                                Map<String, Object> caseData, boolean combineWarnings) {
        warnings =
            transformResponse != null && transformResponse.getWarnings() != null ? transformResponse.getWarnings() :
                new ArrayList<>();
        errors = new ArrayList<>();
        callbackType = EXCEPTION_CALLBACK;

        ScannedData ocrCaseData = sscsJsonExtractor.extractJson(exceptionRecord);

        boolean ignoreWarningsValue = exceptionRecord.getIgnoreWarnings() != null ? exceptionRecord.getIgnoreWarnings() : false;
        validateAppeal(ocrCaseData.getOcrCaseData(), caseData, false, ignoreWarningsValue);

        if (combineWarnings) {
            warnings = combineWarnings();
        }

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .status(getValidationStatus(errors, warnings))
            .build();
    }

    private List<String> combineWarnings() {
        List<String> mergedWarnings = new ArrayList<>();

        mergedWarnings.addAll(warnings);
        mergedWarnings.addAll(errors);
        errors.clear();

        return mergedWarnings;
    }

    @Override
    public CaseResponse validateValidationRecord(Map<String, Object> caseData, boolean ignoreMrnValidation) {
        warnings = new ArrayList<>();
        errors = new ArrayList<>();
        callbackType = VALIDATION_CALLBACK;

        Map<String, Object> ocrCaseData = new HashMap<>();

        validateAppeal(ocrCaseData, caseData, ignoreMrnValidation, false);

        return CaseResponse.builder()
            .errors(errors)
            .warnings(warnings)
            .transformedCase(caseData)
            .build();
    }

    private List<String> validateAppeal(Map<String, Object> ocrCaseData, Map<String, Object> caseData,
                                        boolean ignoreMrnValidation, boolean ignoreWarnings) {

        FormType formType = (FormType) caseData.get("formType");
        Appeal appeal = (Appeal) caseData.get("appeal");
        String appellantPersonType = getPerson1OrPerson2(appeal.getAppellant());

        checkAppellant(appeal, ocrCaseData, caseData, appellantPersonType, formType);
        checkRepresentative(appeal, ocrCaseData, caseData);
        checkMrnDetails(appeal, ocrCaseData, ignoreMrnValidation);

        if (formType != null && formType.equals(FormType.SSCS2)) {
            checkChildMaintenance((String) caseData.get("childMaintenanceNumber"));

            checkOtherParty(caseData, ignoreWarnings);
        }

        checkExcludedDates(appeal);

        isBenefitTypeValid(appeal, formType);

        isHearingTypeValid(appeal);

        checkHearingSubTypeIfHearingIsOral(appeal, caseData);

        if (caseData.get("sscsDocument") != null) {
            @SuppressWarnings("unchecked")
            List<SscsDocument> lists = ((List<SscsDocument>) caseData.get("sscsDocument"));
            checkAdditionalEvidence(lists);
        }

        return warnings;
    }

    private void checkChildMaintenance(String childMaintenanceNumber) {
        if (childMaintenanceNumber == null || childMaintenanceNumber.equals("")) {
            warnings.add(getMessageByCallbackType(callbackType, "", PERSON_1_CHILD_MAINTENANCE_NUMBER, IS_BLANK));
        }
    }

    private void checkAdditionalEvidence(List<SscsDocument> sscsDocuments) {
        sscsDocuments.stream().filter(sscsDocument -> sscsDocument.getValue().getDocumentFileName() == null)
            .forEach(sscsDocument -> {
                errors.add(
                    "There is a file attached to the case that does not have a filename, add a filename, e.g. filename.pdf");
            });

        sscsDocuments.stream().filter(sscsDocument -> sscsDocument.getValue().getDocumentLink() != null
            && sscsDocument.getValue().getDocumentLink().getDocumentFilename() != null
            && sscsDocument.getValue().getDocumentLink().getDocumentFilename().indexOf('.') == -1)
            .forEach(sscsDocument -> {
                errors.add("There is a file attached to the case called "
                    + sscsDocument.getValue().getDocumentLink().getDocumentFilename()
                    + ", filenames must have extension, e.g. filename.pdf");
            });
    }


    private void checkAppellant(Appeal appeal, Map<String, Object> ocrCaseData, Map<String, Object> caseData,
                                String personType, FormType formType) {
        Appellant appellant = appeal.getAppellant();

        if (appellant == null) {
            warnings.add(
                getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + TITLE,
                    IS_EMPTY));
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + FIRST_NAME, IS_EMPTY));
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + LAST_NAME, IS_EMPTY));
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + ADDRESS_LINE1, IS_EMPTY));
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + ADDRESS_LINE3, IS_EMPTY));
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + ADDRESS_LINE4, IS_EMPTY));
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + ADDRESS_POSTCODE, IS_EMPTY));
            warnings.add(
                getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + NINO,
                    IS_EMPTY));
        } else {

            checkAppointee(appellant, ocrCaseData, caseData);

            checkPersonName(appellant.getName(), personType, appellant);
            checkPersonAddressAndDob(appellant.getAddress(), appellant.getIdentity(), personType, ocrCaseData, caseData,
                appellant);
            checkAppellantNino(appellant, personType);
            checkMobileNumber(appellant.getContact(), personType);

            checkHearingSubtypeDetails(appeal.getHearingSubtype());
            if (formType != null && formType.equals(FormType.SSCS2)) {
                checkAppellantRole(appellant.getRole());
            }
        }

    }

    private void checkAppellantRole(Role role) {
        if (role == null) {
            warnings.add(getMessageByCallbackType(callbackType, "", WarningMessage.APPELLANT_PARTY_NAME.toString(),
                EXCEPTION_CALLBACK == callbackType ? FIELDS_EMPTY : IS_MISSING));
        } else {
            String name = role.getName();
            String description = role.getDescription();
            if (StringUtils.isEmpty(name)) {
                warnings.add(getMessageByCallbackType(callbackType, "", WarningMessage.APPELLANT_PARTY_NAME.toString(),
                    EXCEPTION_CALLBACK == callbackType ? FIELDS_EMPTY : IS_MISSING));
            } else if (AppellantRole.OTHER.getName().equalsIgnoreCase(name) && StringUtils.isEmpty(description)) {
                warnings.add(getMessageByCallbackType(callbackType, "", WarningMessage.APPELLANT_PARTY_DESCRIPTION.toString(),
                    EXCEPTION_CALLBACK == callbackType ? IS_EMPTY : IS_MISSING));
            }
        }
    }

    private void checkHearingSubtypeDetails(HearingSubtype hearingSubtype) {
        if (hearingSubtype != null) {
            if (YES_LITERAL.equals(hearingSubtype.getWantsHearingTypeTelephone())
                && hearingSubtype.getHearingTelephoneNumber() == null) {

                warnings.add(getMessageByCallbackType(callbackType, "", HEARING_TYPE_TELEPHONE_LITERAL,
                    PHONE_SELECTED_NOT_PROVIDED));

            } else if (hearingSubtype.getHearingTelephoneNumber() != null
                && !isUkNumberValid(hearingSubtype.getHearingTelephoneNumber())) {

                warnings
                    .add(getMessageByCallbackType(callbackType, "", HEARING_TELEPHONE_NUMBER_MULTIPLE_LITERAL, null));
            }

            if (YES_LITERAL.equals(hearingSubtype.getWantsHearingTypeVideo())
                && hearingSubtype.getHearingVideoEmail() == null) {

                warnings.add(getMessageByCallbackType(callbackType, "", HEARING_TYPE_VIDEO_LITERAL,
                    EMAIL_SELECTED_NOT_PROVIDED));
            }
        }
    }

    private void checkAppointee(Appellant appellant, Map<String, Object> ocrCaseData, Map<String, Object> caseData) {
        if (appellant != null && !isAppointeeDetailsEmpty(appellant.getAppointee())) {
            checkPersonName(appellant.getAppointee().getName(), PERSON1_VALUE, appellant);
            checkPersonAddressAndDob(appellant.getAppointee().getAddress(), appellant.getAppointee().getIdentity(),
                PERSON1_VALUE, ocrCaseData, caseData, appellant);
            checkMobileNumber(appellant.getAppointee().getContact(), PERSON1_VALUE);
        }
    }

    private void checkRepresentative(Appeal appeal, Map<String, Object> ocrCaseData, Map<String, Object> caseData) {
        if (appeal.getRep() == null || StringUtils.isBlank(appeal.getRep().getHasRepresentative())) {
            errors.add(HAS_REPRESENTATIVE_FIELD_MISSING);
        }
        if (appeal.getRep() != null && StringUtils.equals(appeal.getRep().getHasRepresentative(), YES_LITERAL)) {
            final Contact repsContact = appeal.getRep().getContact();
            checkPersonAddressAndDob(appeal.getRep().getAddress(), null, REPRESENTATIVE_VALUE, ocrCaseData, caseData,
                appeal.getAppellant());

            Name name = appeal.getRep().getName();

            if (!isTitleValid(name.getTitle())) {
                warnings.add(getMessageByCallbackType(callbackType, REPRESENTATIVE_VALUE,
                    getWarningMessageName(REPRESENTATIVE_VALUE, null) + TITLE, IS_INVALID));
            }

            if (!doesFirstNameExist(name) && !doesLastNameExist(name) && appeal.getRep().getOrganisation() == null) {
                warnings.add(getMessageByCallbackType(callbackType, "", REPRESENTATIVE_NAME_OR_ORGANISATION_DESCRIPTION,
                    ARE_EMPTY));
            }

            checkMobileNumber(repsContact, REPRESENTATIVE_VALUE);
        }
    }

    private void checkOtherParty(Map<String, Object> caseData, boolean ignoreWarnings) {
        @SuppressWarnings("unchecked")
        List<CcdValue<OtherParty>> otherParties = ((List<CcdValue<OtherParty>>) caseData.get("otherParties"));

        OtherParty otherParty;
        if (otherParties != null && !otherParties.isEmpty()) {
            otherParty = otherParties.get(0).getValue();

            Name name = otherParty.getName();
            Address address = otherParty.getAddress();

            if (!ignoreWarnings) {
                checkOtherPartyDataValid(name, address);
            } else {
                if (!doesFirstNameExist(name) || !doesLastNameExist(name)) {
                    caseData.remove("otherParties");
                } else if (!doesAddressLine1Exist(address) || !doesAddressTownExist(address)
                    || !doesAddressPostcodeExist(address)) {
                    otherParty.setAddress(null);
                }
            }
        }
    }

    private void checkOtherPartyDataValid(Name name, Address address) {
        if (name != null && !isTitleValid(name.getTitle())) {
            warnings.add(
                getMessageByCallbackType(callbackType, OTHER_PARTY_VALUE,
                    getWarningMessageName(OTHER_PARTY_VALUE, null) + TITLE,
                    IS_INVALID));
        }

        if (!doesFirstNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, OTHER_PARTY_VALUE,
                getWarningMessageName(OTHER_PARTY_VALUE, null) + FIRST_NAME, IS_EMPTY));
        }

        if (!doesLastNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, OTHER_PARTY_VALUE,
                getWarningMessageName(OTHER_PARTY_VALUE, null) + LAST_NAME, IS_EMPTY));
        }

        if (!doesAddressLine1Exist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, OTHER_PARTY_VALUE,
                getWarningMessageName(OTHER_PARTY_VALUE, null) + ADDRESS_LINE1, IS_EMPTY));
        }

        if (!doesAddressTownExist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, OTHER_PARTY_VALUE,
                getWarningMessageName(OTHER_PARTY_VALUE, null) + ADDRESS_LINE2, IS_EMPTY));
        }

        if (!doesAddressPostcodeExist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, OTHER_PARTY_VALUE,
                getWarningMessageName(OTHER_PARTY_VALUE, null) + ADDRESS_POSTCODE, IS_EMPTY));
        }
    }

    private void checkMrnDetails(Appeal appeal, Map<String, Object> ocrCaseData, boolean ignoreMrnValidation) {

        String dwpIssuingOffice = getDwpIssuingOffice(appeal, ocrCaseData);

        // if Appeal to Proceed direction type for direction Issue event and mrn date is blank then ignore mrn date validation
        if (!ignoreMrnValidation && !doesMrnDateExist(appeal)) {
            warnings.add(getMessageByCallbackType(callbackType, "", MRN_DATE, IS_EMPTY));
        } else if (!ignoreMrnValidation) {
            checkDateValidDate(appeal.getMrnDetails().getMrnDate(), MRN_DATE, "", true);
        }

        if (dwpIssuingOffice != null && appeal.getBenefitType() != null && appeal.getBenefitType().getCode() != null) {

            Optional<OfficeMapping> officeMapping = Optional.empty();
            //TODO: remove when ucOfficeFeatureActive fully enabled.
            if (!ucOfficeFeatureActive && Benefit.UC.getShortName().equals(appeal.getBenefitType().getCode())) {
                officeMapping = dwpAddressLookupService.getDefaultDwpMappingByBenefitType(Benefit.UC.getShortName());
            } else {
                officeMapping =
                    dwpAddressLookupService.getDwpMappingByOffice(appeal.getBenefitType().getCode(), dwpIssuingOffice);
            }

            if (!officeMapping.isPresent()) {
                log.info("DwpHandling handling office is not valid");
                warnings.add(getMessageByCallbackType(callbackType, "", ISSUING_OFFICE, IS_INVALID));
            }
        } else if (dwpIssuingOffice == null) {
            warnings.add(getMessageByCallbackType(callbackType, "", ISSUING_OFFICE, IS_EMPTY));
        }
    }

    private String getDwpIssuingOffice(Appeal appeal, Map<String, Object> ocrCaseData) {
        if (Boolean.TRUE.equals(doesIssuingOfficeExist(appeal))) {
            return appeal.getMrnDetails().getDwpIssuingOffice();
        } else if (!ocrCaseData.isEmpty()) {
            return getField(ocrCaseData, "office");
        } else {
            return null;
        }
    }

    private void checkPersonName(Name name, String personType, Appellant appellant) {

        if (!doesTitleExist(name)) {
            warnings.add(
                getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + TITLE,
                    IS_EMPTY));
        } else if (name != null && !isTitleValid(name.getTitle())) {
            warnings.add(
                getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + TITLE,
                    IS_INVALID));
        }

        if (!doesFirstNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + FIRST_NAME, IS_EMPTY));
        }
        if (!doesLastNameExist(name)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + LAST_NAME, IS_EMPTY));
        }
    }

    private void checkPersonAddressAndDob(Address address, Identity identity, String personType,
                                          Map<String, Object> ocrCaseData, Map<String, Object> caseData,
                                          Appellant appellant) {

        boolean isAddressLine4Present = findBooleanExists(getField(ocrCaseData, personType + "_address_line4"));

        if (!doesAddressLine1Exist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + ADDRESS_LINE1, IS_EMPTY));
        } else if (!address.getLine1().matches(ADDRESS_REGEX)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + ADDRESS_LINE1, HAS_INVALID_ADDRESS));
        }

        String townLine = (isAddressLine4Present) ? ADDRESS_LINE3 : ADDRESS_LINE2;
        if (!doesAddressTownExist(address)) {

            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + townLine, IS_EMPTY));
        } else if (!address.getTown().matches(ADDRESS_REGEX)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + townLine, HAS_INVALID_ADDRESS));
        }

        String countyLine = (isAddressLine4Present) ? ADDRESS_LINE4 : "_ADDRESS_LINE3_COUNTY";
        if (!doesAddressCountyExist(address)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + countyLine, IS_EMPTY));
        } else if (!address.getCounty().matches(COUNTY_REGEX)) {
            warnings.add(getMessageByCallbackType(callbackType, personType,
                getWarningMessageName(personType, appellant) + countyLine, HAS_INVALID_ADDRESS));
        }

        if (isAddressPostcodeValid(address, personType, appellant) && address != null) {
            if (personType.equals(getPerson1OrPerson2(appellant))) {
                RegionalProcessingCenter rpc = regionalProcessingCenterService.getByPostcode(address.getPostcode());

                if (rpc != null) {
                    caseData.put("region", rpc.getName());
                    caseData.put("regionalProcessingCenter", rpc);
                } else {
                    warnings.add(getMessageByCallbackType(callbackType, personType,
                        getWarningMessageName(personType, appellant) + ADDRESS_POSTCODE,
                        "is not a postcode that maps to a regional processing center"));
                }
            }
        }
        if (identity != null) {
            checkDateValidDate(identity.getDob(), getWarningMessageName(personType, appellant) + DOB, personType, true);
        }
    }

    private Boolean doesTitleExist(Name name) {
        if (name != null) {
            return StringUtils.isNotEmpty(name.getTitle());
        }
        return false;
    }

    private boolean isTitleValid(String title) {
        if (StringUtils.isNotBlank(title)) {
            String strippedTitle = title.replaceAll("[-+.^:,'_]", "");
            return titles.stream().anyMatch(strippedTitle::equalsIgnoreCase);
        }
        return true;
    }

    private Boolean doesFirstNameExist(Name name) {
        if (name != null) {
            return StringUtils.isNotEmpty(name.getFirstName());
        }
        return false;
    }

    private Boolean doesLastNameExist(Name name) {
        if (name != null) {
            return StringUtils.isNotEmpty(name.getLastName());
        }
        return false;
    }

    private Boolean doesAddressLine1Exist(Address address) {
        if (address != null) {
            return StringUtils.isNotEmpty(address.getLine1());
        }
        return false;
    }

    private Boolean doesAddressPostcodeExist(Address address) {
        if (address != null) {
            return StringUtils.isNotEmpty(address.getPostcode());
        }
        return false;
    }

    private Boolean doesAddressTownExist(Address address) {
        if (address != null) {
            return StringUtils.isNotEmpty(address.getTown());
        }
        return false;
    }

    private Boolean doesAddressCountyExist(Address address) {
        if (address != null) {
            return StringUtils.isNotEmpty(address.getCounty());
        }
        return false;
    }

    private Boolean isAddressPostcodeValid(Address address, String personType, Appellant appellant) {
        if (address != null && address.getPostcode() != null) {
            if (postcodeValidator.isValidPostcodeFormat(address.getPostcode())
                && postcodeValidator.isValid(address.getPostcode())) {
                return true;
            } else {
                errors.add(getMessageByCallbackType(callbackType, personType,
                    getWarningMessageName(personType, appellant) + ADDRESS_POSTCODE, "is not a valid postcode"));
                return false;
            }
        }
        warnings.add(getMessageByCallbackType(callbackType, personType,
            getWarningMessageName(personType, appellant) + ADDRESS_POSTCODE, IS_EMPTY));
        return false;
    }

    private void checkAppellantNino(Appellant appellant, String personType) {
        if (appellant != null && appellant.getIdentity() != null && appellant.getIdentity().getNino() != null) {
            if (!appellant.getIdentity().getNino().matches(
                "^(?!BG)(?!GB)(?!NK)(?!KN)(?!TN)(?!NT)(?!ZZ)\\s?(?:[A-CEGHJ-PR-TW-Z]\\s?[A-CEGHJ-NPR-TW-Z])\\s?(?:\\d\\s?){6}([A-D]|\\s)\\s?$")) {
                warnings.add(getMessageByCallbackType(callbackType, personType,
                    getWarningMessageName(personType, appellant) + NINO, IS_INVALID));
            }
        } else {
            warnings.add(
                getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, appellant) + NINO,
                    IS_EMPTY));
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
            return StringUtils.isNotEmpty(appeal.getMrnDetails().getDwpIssuingOffice());
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

    private void isBenefitTypeValid(Appeal appeal, FormType formType) {
        BenefitType benefitType = appeal.getBenefitType();
        if (benefitType != null && benefitType.getCode() != null) {
            final Optional<Benefit> benefitOptional = Benefit.findBenefitByShortName(benefitType.getCode());
            if (benefitOptional.isEmpty()) {
                List<String> benefitNameList = new ArrayList<>();
                for (Benefit be : Benefit.values()) {
                    benefitNameList.add(be.getShortName());
                }
                errors.add(getMessageByCallbackType(callbackType, "", BENEFIT_TYPE_DESCRIPTION,
                    "invalid. Should be one of: " + String.join(", ", benefitNameList)));
            } else {
                Benefit benefit = benefitOptional.get();
                appeal.setBenefitType(BenefitType.builder()
                    .code(benefit.getShortName())
                    .description(benefit.getDescription())
                    .build());
            }
        } else {
            if (formType == null || !formType.equals(FormType.SSCS1U)) {
                warnings.add(getMessageByCallbackType(callbackType, "", BENEFIT_TYPE_DESCRIPTION, IS_EMPTY));
            }
        }
    }

    private void isHearingTypeValid(Appeal appeal) {
        String hearingType = appeal.getHearingType();

        if (hearingType == null
            || (!hearingType.equals(HEARING_TYPE_ORAL) && !hearingType.equals(HEARING_TYPE_PAPER))) {
            warnings.add(getMessageByCallbackType(callbackType, "", HEARING_TYPE_DESCRIPTION, IS_INVALID));
        }
    }

    private void checkHearingSubTypeIfHearingIsOral(Appeal appeal, Map<String, Object> caseData) {
        String hearingType = appeal.getHearingType();
        FormType formType = (FormType) caseData.get("formType");
        log.info("Bulk-scan form type: {}", formType != null ? formType.toString() : null);
        if (FormType.SSCS1PEU.equals(formType) && hearingType != null && hearingType.equals(HEARING_TYPE_ORAL)
            && !isValidHearingSubType(appeal)) {
            warnings.add(
                getMessageByCallbackType(callbackType, "", HEARING_SUB_TYPE_TELEPHONE_OR_VIDEO_FACE_TO_FACE_DESCRIPTION,
                    ARE_EMPTY));
        }
    }

    private boolean isValidHearingSubType(Appeal appeal) {
        boolean isValid = true;
        HearingSubtype hearingSubType = appeal.getHearingSubtype();
        if (hearingSubType == null
            || !(hearingSubType.isWantsHearingTypeTelephone() || hearingSubType.isWantsHearingTypeVideo()
                || hearingSubType.isWantsHearingTypeFaceToFace())) {
            isValid = false;
        }
        return isValid;
    }

    private void checkExcludedDates(Appeal appeal) {
        if (appeal.getHearingOptions() != null && appeal.getHearingOptions().getExcludeDates() != null) {
            for (ExcludeDate excludeDate : appeal.getHearingOptions().getExcludeDates()) {
                checkDateValidDate(excludeDate.getValue().getStart(), HEARING_OPTIONS_EXCLUDE_DATES_LITERAL, "", false);
            }
        }
    }

    private void checkMobileNumber(Contact contact, String personType) {
        if (contact != null && contact.getMobile() != null && !isMobileNumberValid(contact.getMobile())) {
            errors.add(
                getMessageByCallbackType(callbackType, personType, getWarningMessageName(personType, null) + MOBILE,
                    IS_INVALID));
        }
    }

    private boolean isMobileNumberValid(String number) {
        if (number != null) {
            return number.matches(PHONE_REGEX);
        }
        return true;
    }

    private boolean isUkNumberValid(String number) {
        if (number != null) {
            return number.matches(UK_NUMBER_REGEX);
        }
        return true;
    }

    private String getWarningMessageName(String personType, Appellant appellant) {
        if (personType.equals(REPRESENTATIVE_VALUE)) {
            return "REPRESENTATIVE";
        } else if (personType.equals(OTHER_PARTY_VALUE)) {
            return "OTHER_PARTY";
        } else if (personType.equals(PERSON2_VALUE) || appellant == null
            || isAppointeeDetailsEmpty(appellant.getAppointee())) {
            return "APPELLANT";
        } else {
            return "APPOINTEE";
        }
    }
}
