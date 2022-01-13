package uk.gov.hmcts.reform.sscs.transformers;

import static uk.gov.hmcts.reform.sscs.ccd.domain.AppellantRole.OTHER;
import static uk.gov.hmcts.reform.sscs.ccd.domain.Benefit.CHILD_SUPPORT;
import static uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService.normaliseNino;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.constants.WarningMessage.getMessageByCallbackType;
import static uk.gov.hmcts.reform.sscs.domain.CallbackType.EXCEPTION_CALLBACK;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;
import static uk.gov.hmcts.reform.sscs.model.AllowedFileTypes.getContentTypeForFileName;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.convertBooleanToYesNoString;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.doValuesContradict;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.extractBooleanValue;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.extractValuesWhereBooleansValid;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.findBooleanExists;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.generateDateForCcd;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getBoolean;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getDateForCcd;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getField;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.hasPerson;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.isExactlyOneBooleanTrue;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.isExactlyZeroBooleanTrue;
import static uk.gov.hmcts.reform.sscs.utility.AppealNumberGenerator.generateAppealNumber;

import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.*;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.domain.Role;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.constants.*;
import uk.gov.hmcts.reform.sscs.exception.UnknownFileTypeException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.model.dwp.OfficeMapping;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.FuzzyMatcherService;
import uk.gov.hmcts.reform.sscs.validators.SscsKeyValuePairValidator;

@Component
@Slf4j
public class SscsCaseTransformer implements CaseTransformer {

    private static final String OTHER_PARTY_ID_ONE = "1";
    private final IdamService idamService;
    private final CcdService ccdService;
    private SscsJsonExtractor sscsJsonExtractor;
    private SscsKeyValuePairValidator keyValuePairValidator;
    private SscsDataHelper sscsDataHelper;
    private FuzzyMatcherService fuzzyMatcherService;
    private DwpAddressLookupService dwpAddressLookupService;
    private Set<String> errors;
    private Set<String> warnings;

    //TODO: Remove when uc-office-feature switched on
    private boolean ucOfficeFeatureActive;

    @Autowired
    public SscsCaseTransformer(SscsJsonExtractor sscsJsonExtractor,
                               SscsKeyValuePairValidator keyValuePairValidator,
                               SscsDataHelper sscsDataHelper,
                               FuzzyMatcherService fuzzyMatcherService,
                               DwpAddressLookupService dwpAddressLookupService,
                               IdamService idamService,
                               CcdService ccdService,
                               @Value("${feature.uc-office-feature.enabled}") boolean ucOfficeFeatureActive) {
        this.sscsJsonExtractor = sscsJsonExtractor;
        this.keyValuePairValidator = keyValuePairValidator;
        this.sscsDataHelper = sscsDataHelper;
        this.fuzzyMatcherService = fuzzyMatcherService;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.idamService = idamService;
        this.ccdService = ccdService;
        this.ucOfficeFeatureActive = ucOfficeFeatureActive;
    }

    public void setUcOfficeFeatureActive(boolean ucOfficeFeatureActive) {
        this.ucOfficeFeatureActive = ucOfficeFeatureActive;
    }

    @Override
    public CaseResponse transformExceptionRecord(ExceptionRecord exceptionRecord, boolean combineWarnings) {
        // New transformation request contains exceptionRecordId
        // Old transformation request contains id field, which is the exception record id
        String caseId = "N/A";
        if (StringUtils.isNotEmpty(exceptionRecord.getExceptionRecordId())) {
            caseId = exceptionRecord.getExceptionRecordId();
        } else if (StringUtils.isNotEmpty(exceptionRecord.getId())) {
            caseId = exceptionRecord.getId();
        }
        log.info("Validating exception record against schema caseId {}", caseId);

        String formType = exceptionRecord.getFormType();
        CaseResponse keyValuePairValidatorResponse = keyValuePairValidator.validate(exceptionRecord.getOcrDataFields(),
            FormType.getById(formType));

        if (keyValuePairValidatorResponse.getErrors() != null) {
            log.info("Errors found while validating key value pairs while transforming exception record caseId {}",
                caseId);
            return keyValuePairValidatorResponse;
        }

        log.info("Extracting and transforming exception record caseId {}", caseId);

        errors = new HashSet<>();
        warnings = new HashSet<>();

        boolean ignoreWarningsValue = exceptionRecord.getIgnoreWarnings() != null ? exceptionRecord.getIgnoreWarnings() : false;

        ScannedData scannedData = sscsJsonExtractor.extractJson(exceptionRecord);

        IdamTokens token = idamService.getIdamTokens();

        Map<String, Object> transformed = transformData(caseId, scannedData, token, formType, errors, ignoreWarningsValue);

        duplicateCaseCheck(caseId, transformed, token);

        if (combineWarnings) {
            warnings = combineWarnings();
        }

        return CaseResponse.builder().transformedCase(transformed).errors(new ArrayList<>(errors))
            .warnings(new ArrayList<>(warnings))
            .status(getValidationStatus(new ArrayList<>(errors), new ArrayList<>(warnings))).build();
    }

    private Set<String> combineWarnings() {
        Set<String> mergedWarnings = new HashSet<>();

        mergedWarnings.addAll(warnings);
        mergedWarnings.addAll(errors);
        errors.clear();

        return mergedWarnings;
    }

    private Map<String, Object> transformData(String caseId, ScannedData scannedData, IdamTokens token,
                                              String formType, Set<String> errors, boolean ignoreWarnings) {
        Appeal appeal = buildAppealFromData(scannedData.getOcrCaseData(), caseId, formType, errors, ignoreWarnings);
        List<SscsDocument> sscsDocuments = buildDocumentsFromData(scannedData.getRecords());
        Subscriptions subscriptions = populateSubscriptions(appeal, scannedData.getOcrCaseData());

        Map<String, Object> transformed = new HashMap<>();

        List<CcdValue<OtherParty>> otherParties = buildOtherParty(scannedData.getOcrCaseData());

        sscsDataHelper.addSscsDataToMap(transformed, appeal, sscsDocuments, subscriptions, FormType.getById(formType),
            getField(scannedData.getOcrCaseData(), PERSON_1_CHILD_MAINTENANCE_NUMBER), otherParties);

        transformed.put("bulkScanCaseReference", caseId);
        transformed.put("caseCreated", scannedData.getOpeningDate());

        String processingVenue = sscsDataHelper.findProcessingVenue(appeal.getAppellant(), appeal.getBenefitType());

        if (StringUtils.isNotEmpty(processingVenue)) {
            log.info("{} - setting venue name to {}", caseId, processingVenue);
            transformed.put("processingVenue", processingVenue);
        }

        log.info("Transformation complete for exception record id {}, caseCreated field set to {}", caseId,
            scannedData.getOpeningDate());

        transformed = checkForMatches(transformed, token);

        return transformed;
    }

    private Subscriptions populateSubscriptions(Appeal appeal, Map<String, Object> ocrCaseData) {

        return Subscriptions.builder()
            .appellantSubscription(appeal.getAppellant() != null
                && appeal.getAppellant().getAppointee() == null
                ? generateSubscriptionWithAppealNumber(ocrCaseData, PERSON1_VALUE) : null)
            .appointeeSubscription(appeal.getAppellant() != null
                && appeal.getAppellant().getAppointee() != null
                ? generateSubscriptionWithAppealNumber(ocrCaseData, PERSON1_VALUE) : null)
            .representativeSubscription(appeal.getRep() != null
                && appeal.getRep().getHasRepresentative().equals("Yes")
                ? generateSubscriptionWithAppealNumber(ocrCaseData, REPRESENTATIVE_VALUE) : null)
            .build();
    }

    private Subscription generateSubscriptionWithAppealNumber(Map<String, Object> pairs, String personType) {
        boolean wantsSms = getBoolean(pairs, errors, personType + "_want_sms_notifications");
        String email = getField(pairs, personType + "_email");
        String mobile = getField(pairs, personType + "_mobile");

        boolean wantEmailNotifications = false;
        if ((PERSON1_VALUE.equals(personType) || REPRESENTATIVE_VALUE.equals(personType))
            && StringUtils.isNotBlank(email)) {
            wantEmailNotifications = true;
        }

        return Subscription.builder().email(email).mobile(mobile).subscribeSms(convertBooleanToYesNoString(wantsSms))
            .subscribeEmail(convertBooleanToYesNoString(wantEmailNotifications))
            .wantSmsNotifications(convertBooleanToYesNoString(wantsSms)).tya(generateAppealNumber()).build();
    }

    private Appeal buildAppealFromData(Map<String, Object> pairs, String caseId, String formType, Set<String> errors, boolean ignoreWarnings) {
        Appellant appellant = null;

        if (pairs != null && pairs.size() != 0) {
            if (hasPerson(pairs, PERSON2_VALUE)) {
                Appointee appointee = null;
                if (hasPerson(pairs, PERSON1_VALUE)) {
                    appointee = Appointee.builder()
                        .name(buildPersonName(pairs, PERSON1_VALUE))
                        .address(buildPersonAddress(pairs, PERSON1_VALUE))
                        .contact(buildPersonContact(pairs, PERSON1_VALUE))
                        .identity(buildPersonIdentity(pairs, PERSON1_VALUE))
                        .build();
                }
                appellant = buildAppellant(pairs, PERSON2_VALUE, appointee, buildPersonContact(pairs, PERSON2_VALUE), formType, ignoreWarnings);

            } else if (hasPerson(pairs, PERSON1_VALUE)) {
                appellant = buildAppellant(pairs, PERSON1_VALUE, null, buildPersonContact(pairs, PERSON1_VALUE), formType, ignoreWarnings);
            }

            String hearingType = findHearingType(pairs);
            AppealReasons appealReasons = findAppealReasons(pairs);

            BenefitType benefitType;

            if (FormType.SSCS1.toString().equalsIgnoreCase(formType)) {
                benefitType = getBenefitTypeForSscs1(caseId, pairs);
            } else if (FormType.SSCS1U.toString().equalsIgnoreCase(formType)) {
                benefitType = getBenefitTypeForSscs1U(caseId, pairs);
            } else if (FormType.SSCS2.toString().equalsIgnoreCase(formType)) {
                benefitType = BenefitType.builder()
                    .code(CHILD_SUPPORT.getShortName())
                    .description(CHILD_SUPPORT.getDescription()).build();
            } else {
                benefitType = getBenefitType(caseId, pairs, FormType.getById(formType));
            }


            return Appeal.builder()
                .benefitType(benefitType)
                .appellant(appellant)
                .appealReasons(appealReasons)
                .rep(buildRepresentative(pairs))
                .mrnDetails(buildMrnDetails(pairs, benefitType))
                .hearingType(hearingType)
                .hearingOptions(buildHearingOptions(pairs, hearingType))
                .hearingSubtype(buildHearingSubtype(pairs))
                .signer(getField(pairs, "signature_name"))
                .receivedVia("Paper")
                .build();
        } else {
            String errorMessage = "No OCR data, case cannot be created";
            log.info("{} for exception record id {}", errorMessage, caseId);
            errors.add(errorMessage);
            return Appeal.builder().build();
        }
    }

    private AppealReasons findAppealReasons(Map<String, Object> pairs) {

        String appealReason = getField(pairs, APPEAL_GROUNDS) != null
            ? getField(pairs, APPEAL_GROUNDS) : getField(pairs, APPEAL_GROUNDS_2);

        if (appealReason != null) {
            List<AppealReason> reasons = Collections.singletonList(AppealReason.builder()
                .value(AppealReasonDetails.builder()
                    .description(appealReason)
                    .build())
                .build());
            return AppealReasons.builder().reasons(reasons).build();
        }
        return null;
    }

    private BenefitType getBenefitTypeForSscs1(String caseId, Map<String, Object> pairs) {
        String code = getCodeFromField(caseId, pairs, BENEFIT_TYPE_DESCRIPTION);

        return (code != null) ? BenefitType.builder().code(code.toUpperCase()).build() : null;
    }

    private BenefitType getBenefitType(String caseId, Map<String, Object> pairs, FormType formType) {
        String code = getCodeFromField(caseId, pairs, BENEFIT_TYPE_DESCRIPTION);
        String description = null;

        List<String> benefitList = findBenefitListFromFormType(formType);

        // Extract all the provided benefit type booleans, outputting errors for any that are invalid
        List<String> validProvidedBooleanValues =
            extractValuesWhereBooleansValid(pairs, errors, benefitList);

        // Of the provided benefit type booleans (if any), check that exactly one is set to true, outputting errors
        // for conflicting values.
        if (validProvidedBooleanValues.isEmpty()) {
            allBenefitFieldsEmptyError(benefitList);
        } else {
            // If one is set to true, extract the string indicator value (eg. IS_BENEFIT_TYPE_PIP) and lookup the Benefit type.
            if (isExactlyOneBooleanTrue(pairs, errors,
                validProvidedBooleanValues.toArray(new String[validProvidedBooleanValues.size()]))) {

                Optional<Benefit> benefit = findBenefitFromFormType(formType, pairs, validProvidedBooleanValues);
                if (benefit.isPresent()) {
                    code = benefit.get().getShortName();
                    description = benefit.get().getDescription();
                }
            } else if (isExactlyZeroBooleanTrue(pairs, errors, validProvidedBooleanValues.toArray(new String[validProvidedBooleanValues.size()]))) {
                allBenefitFieldsEmptyError(benefitList);
            } else {
                errors.add(contradictingValuesError(validProvidedBooleanValues, pairs));
            }
        }
        return (code != null) ? BenefitType.builder().code(code).description(description).build() : null;
    }

    private void allBenefitFieldsEmptyError(List<String> benefitList) {
        errors.add((uk.gov.hmcts.reform.sscs.utility.StringUtils
            .getGramaticallyJoinedStrings(benefitList)
            + " fields are empty or false"));
    }

    private String contradictingValuesError(List<String> validProvidedBooleanValues, Map<String, Object> pairs) {
        return uk.gov.hmcts.reform.sscs.utility.StringUtils
            .getGramaticallyJoinedStrings(validProvidedBooleanValues.stream()
                .filter(value -> extractBooleanValue(pairs, errors, value)).collect(Collectors.toList()))
            + " have contradicting values";
    }

    private List<String> findBenefitListFromFormType(FormType formType) {
        return formType.equals(FormType.SSCS5) ? BenefitTypeIndicatorSscs5.getAllIndicatorStrings() : BenefitTypeIndicator.getAllIndicatorStrings();
    }

    private Optional<Benefit> findBenefitFromFormType(FormType formType, Map<String, Object> pairs, List<String> validProvidedBooleanValues) {
        return formType.equals(FormType.SSCS5)
            ? BenefitTypeIndicatorSscs5.findByIndicatorString(valueIndicatorWithTrueValue(pairs, validProvidedBooleanValues))
            : BenefitTypeIndicator.findByIndicatorString(valueIndicatorWithTrueValue(pairs, validProvidedBooleanValues));
    }

    private String valueIndicatorWithTrueValue(Map<String, Object> pairs, List<String> validProvidedBooleanValues) {
        return validProvidedBooleanValues.stream()
            .filter(value -> extractBooleanValue(pairs, errors, value))
            .findFirst()
            .orElse(null);
    }

    private BenefitType getBenefitTypeForSscs1U(String caseId, Map<String, Object> pairs) {
        String benefitTypeOther = getCodeFromField(caseId, pairs, BENEFIT_TYPE_OTHER);
        String code = getBenefitTypeOther(benefitTypeOther);

        // Extract all the provided benefit type booleans, outputting errors for any that are invalid
        List<String> validProvidedBooleanValues =
            extractValuesWhereBooleansValid(pairs, errors, BenefitTypeIndicatorSscs1U.getAllIndicatorStrings());


        if (!validProvidedBooleanValues.isEmpty()
            && !isExactlyZeroBooleanTrue(pairs, errors,
            validProvidedBooleanValues.toArray(new String[validProvidedBooleanValues.size()]))) {
            // Of the provided benefit type booleans (if any), check that exactly one is set to true, outputting errors
            // for conflicting values.
            // If one is set to true, extract the string indicator value (eg. IS_BENEFIT_TYPE_PIP) and lookup the Benefit type.
            if (isExactlyOneBooleanTrue(pairs, errors,
                validProvidedBooleanValues.toArray(new String[validProvidedBooleanValues.size()]))) {
                String valueIndicatorWithTrueValue = valueIndicatorWithTrueValue(pairs, validProvidedBooleanValues);

                if (!IS_BENEFIT_TYPE_OTHER.equals(valueIndicatorWithTrueValue)) {
                    code = getBenefitCodeFromIndicators(pairs, benefitTypeOther, valueIndicatorWithTrueValue,
                        validProvidedBooleanValues);
                }
            } else {
                String error = contradictingValuesError(validProvidedBooleanValues, pairs);
                if (!StringUtils.isEmpty(benefitTypeOther)) {
                    error = error.replace(IS_BENEFIT_TYPE_OTHER, BENEFIT_TYPE_OTHER);
                }
                errors.add(error);
            }
        } else {
            if (StringUtils.isEmpty(benefitTypeOther)) {
                errors.add((uk.gov.hmcts.reform.sscs.utility.StringUtils
                    .getGramaticallyJoinedStrings(BenefitTypeIndicatorSscs1U.getAllIndicatorStrings())
                    + " fields are empty")
                    .replace(IS_BENEFIT_TYPE_OTHER, BENEFIT_TYPE_OTHER));
            }
        }

        Optional<Benefit> benefit = Benefit.findBenefitByShortName(code);

        if (benefit.isEmpty() && errors.isEmpty()) {
            // only add when no other errors, otherwise similar errors get added to the list
            errors.add(BENEFIT_TYPE_OTHER + " " + IS_INVALID);
        }
        return (benefit.isPresent() && errors.size() == 0)
            ? BenefitType.builder().code(code).description(benefit.get().getDescription()).build() : null;
    }

    private String getBenefitCodeFromIndicators(Map<String, Object> pairs, String benefitTypeOther,
                                                String valueIndicatorWithTrueValue,
                                                List<String> validProvidedBooleanValues) {
        if (StringUtils.isEmpty(benefitTypeOther)) {
            Optional<Benefit> benefit = BenefitTypeIndicatorSscs1U.findByIndicatorString(valueIndicatorWithTrueValue);
            if (benefit.isPresent()) {
                return benefit.get().getShortName();
            }
        } else {
            errors.add(uk.gov.hmcts.reform.sscs.utility.StringUtils
                .getGramaticallyJoinedStrings(validProvidedBooleanValues.stream()
                    .filter(value -> extractBooleanValue(pairs, errors, value)).collect(Collectors.toList()))
                + " and " + BENEFIT_TYPE_OTHER + " have contradicting values");
        }
        return null;
    }

    private String getBenefitTypeOther(String benefitTypeOther) {
        if (!StringUtils.isEmpty(benefitTypeOther)) {
            Optional<Benefit> benefit = Benefit.findBenefitByShortName(benefitTypeOther);
            if (benefit.isPresent()) {
                return benefit.get().getShortName();
            }
        }
        return null;
    }

    private String getCodeFromField(String caseId, Map<String, Object> pairs, String fieldName) {
        String code = getField(pairs, fieldName);
        if (code != null) {
            code = fuzzyMatcherService.matchBenefitType(caseId, code);
        }
        return code;
    }

    private Appellant buildAppellant(Map<String, Object> pairs, String personType, Appointee appointee,
                                     Contact contact, String formType, boolean ignoreWarnings) {
        return Appellant.builder()
            .name(buildPersonName(pairs, personType))
            .isAppointee(convertBooleanToYesNoString(appointee != null))
            .address(buildPersonAddress(pairs, personType))
            .identity(buildPersonIdentity(pairs, personType))
            .contact(contact)
            .confidentialityRequired(getConfidentialityRequired(pairs, errors))
            .appointee(appointee)
            .role(buildAppellantRole(pairs, formType, ignoreWarnings))
            .build();
    }

    private YesNo getConfidentialityRequired(Map<String, Object> pairs, Set<String> errors) {
        String keepHomeAddressConfidential = (String) pairs.get(KEEP_HOME_ADDRESS_CONFIDENTIAL);
        return keepHomeAddressConfidential != null && StringUtils.isNotBlank(keepHomeAddressConfidential)
            ? convertBooleanToYesNo(getBoolean(pairs, errors, KEEP_HOME_ADDRESS_CONFIDENTIAL)) : null;
    }

    private Role buildAppellantRole(Map<String, Object> pairs, String formType, boolean ignoreWarnings) {
        if (FormType.SSCS2.toString().equalsIgnoreCase(formType)) {
            List<String> validProvidedBooleanValues =
                extractValuesWhereBooleansValid(pairs, errors, AppellantRoleIndicator.getAllIndicatorStrings());
            List<String> valueIndicatorsWithTrueValue =
                validProvidedBooleanValues.stream().filter(value -> extractBooleanValue(pairs, errors, value))
                    .collect(Collectors.toList());
            String otherPartyDetails = getField(pairs, OTHER_PARTY_DETAILS);

            if (validateValues(valueIndicatorsWithTrueValue, otherPartyDetails, ignoreWarnings)) {
                if (!valueIndicatorsWithTrueValue.isEmpty()) {
                    String selectedValue = valueIndicatorsWithTrueValue.get(0);
                    AppellantRole appellantRole = AppellantRoleIndicator.findByIndicatorString(selectedValue).orElse(null);

                    if (OTHER.equals(appellantRole)) {
                        return Role.builder().name(OTHER.getName()).description(otherPartyDetails).build();
                    } else if (appellantRole != null) {
                        return Role.builder().name(appellantRole.getName()).build();
                    }
                } else if (StringUtils.isNotEmpty(otherPartyDetails)) {
                    return Role.builder().name(OTHER.getName()).description(otherPartyDetails).build();
                }
            }
        }
        return null;
    }

    private boolean validateValues(List<String> validValues, String otherPartyDetails, boolean ignoreWarnings) {
        if (validValues.isEmpty() && StringUtils.isEmpty(otherPartyDetails)) {
            if (!ignoreWarnings) {
                warnings.add(getMessageByCallbackType(EXCEPTION_CALLBACK, "", WarningMessage.APPELLANT_PARTY_NAME.toString(),
                    FIELDS_EMPTY));
            }
            return false;
        } else if (!validValues.isEmpty()) {
            if (validValues.size() > 1) {
                if (StringUtils.isNotEmpty(otherPartyDetails)) {
                    validValues.add(OTHER_PARTY_DETAILS);
                }
                if (!ignoreWarnings) {
                    warnings.add(uk.gov.hmcts.reform.sscs.utility.StringUtils
                        .getGramaticallyJoinedStrings(validValues) + " have conflicting values");
                }
                return false;
            }

            AppellantRole appellantRole = AppellantRoleIndicator.findByIndicatorString(validValues.get(0)).orElse(null);

            if (OTHER.equals(appellantRole) && StringUtils.isEmpty(otherPartyDetails)) {
                if (!ignoreWarnings) {
                    warnings.add(getMessageByCallbackType(EXCEPTION_CALLBACK, "", WarningMessage.APPELLANT_PARTY_DESCRIPTION.toString(),
                        FIELDS_EMPTY));
                }
                return false;
            } else if (StringUtils.isNotEmpty(otherPartyDetails) && !OTHER.equals(appellantRole)) {
                if (!ignoreWarnings) {
                    warnings.add(uk.gov.hmcts.reform.sscs.utility.StringUtils
                        .getGramaticallyJoinedStrings(List.of(validValues.get(0), OTHER_PARTY_DETAILS))
                        + " have conflicting values");
                }
                return false;
            }
        }
        return true;
    }

    private Representative buildRepresentative(Map<String, Object> pairs) {
        boolean doesRepExist = hasPerson(pairs, REPRESENTATIVE_VALUE);

        if (doesRepExist) {
            return Representative.builder()
                .hasRepresentative(YES_LITERAL)
                .name(buildPersonName(pairs, REPRESENTATIVE_VALUE))
                .address(buildPersonAddress(pairs, REPRESENTATIVE_VALUE))
                .organisation(getField(pairs, "representative_company"))
                .contact(buildPersonContact(pairs, REPRESENTATIVE_VALUE))
                .build();
        } else {
            return Representative.builder().hasRepresentative(NO_LITERAL).build();
        }
    }

    private List<CcdValue<OtherParty>> buildOtherParty(Map<String, Object> pairs) {
        if (pairs != null && pairs.size() != 0) {

            boolean doesOtherPartyExist = hasPerson(pairs, OTHER_PARTY_VALUE);

            if (doesOtherPartyExist) {
                if (isOtherPartyAddressValid(pairs)) {
                    return Collections.singletonList(CcdValue.<OtherParty>builder().value(
                        OtherParty.builder()
                            .id(OTHER_PARTY_ID_ONE)
                            .name(buildPersonName(pairs, OTHER_PARTY_VALUE))
                            .address(buildPersonAddress(pairs, OTHER_PARTY_VALUE))
                            .build())
                        .build());
                }

                return Collections.singletonList(CcdValue.<OtherParty>builder().value(
                    OtherParty.builder()
                        .id(OTHER_PARTY_ID_ONE)
                        .name(buildPersonName(pairs, OTHER_PARTY_VALUE)).build())
                    .build());
            }
        }
        return null;
    }

    private boolean isOtherPartyAddressValid(Map<String, Object> pairs) {
        // yes+dont check address, no
        if (extractBooleanValue(pairs, errors, IS_OTHER_PARTY_ADDRESS_KNOWN)
            || (hasAddress(pairs, OTHER_PARTY_VALUE))) {
            return true;
        }
        return false;
    }

    private MrnDetails buildMrnDetails(Map<String, Object> pairs, BenefitType benefitType) {

        String office = getDwpIssuingOffice(pairs, benefitType);

        return MrnDetails.builder()
            .mrnDate(generateDateForCcd(pairs, errors, "mrn_date"))
            .mrnLateReason(getField(pairs, "appeal_late_reason"))
            .dwpIssuingOffice(office)
            .build();
    }

    private String getDwpIssuingOffice(Map<String, Object> pairs, BenefitType benefitType) {
        String dwpIssuingOffice = getField(pairs, "office");

        if (benefitType != null && benefitType.getCode() != null) {
            if (Benefit.getBenefitOptionalByCode(benefitType.getCode())
                .filter(benefit -> isBenefitWithAutoFilledOffice(benefit, dwpIssuingOffice)).isPresent()) {
                return dwpAddressLookupService.getDefaultDwpMappingByBenefitType(benefitType.getCode())
                    .map(OfficeMapping::getCode)
                    .orElse(null);
            } else if (dwpIssuingOffice != null) {
                return dwpAddressLookupService.getDwpMappingByOffice(benefitType.getCode(), dwpIssuingOffice)
                    .map(office -> office.getMapping().getCcd())
                    .orElse(null);
            }
        }

        return dwpIssuingOffice;
    }

    //TODO: After ucOfficeFeatureActive fully enabled this method should go into Benefit enum in sscs-common
    private boolean isBenefitWithAutoFilledOffice(Benefit benefit, String office) {
        switch (benefit) {
            case UC:
                if (ucOfficeFeatureActive && office != null && !office.isBlank()) {
                    return false;
                } //else fallthrough
            case CARERS_ALLOWANCE:
            case BEREAVEMENT_BENEFIT:
            case MATERNITY_ALLOWANCE:
            case BEREAVEMENT_SUPPORT_PAYMENT_SCHEME:
            case CHILD_SUPPORT:
            case TAX_CREDIT:
            case GUARDIANS_ALLOWANCE:
            case TAX_FREE_CHILDCARE:
            case HOME_RESPONSIBILITIES_PROTECTION:
            case CHILD_BENEFIT:
            case THIRTY_HOURS_FREE_CHILDCARE:
            case GUARANTEED_MINIMUM_PENSION:
            case NATIONAL_INSURANCE_CREDITS:
                return true;
            default:
                return false;
        }
    }

    private Name buildPersonName(Map<String, Object> pairs, String personType) {
        String title = transformTitle(getField(pairs, personType + "_title"));

        return Name.builder()
            .title(title)
            .firstName(getField(pairs, personType + "_first_name"))
            .lastName(getField(pairs, personType + "_last_name"))
            .build();
    }

    private String transformTitle(String title) {
        if ("doctor".equalsIgnoreCase(title)) {
            return "Dr";
        } else if ("reverend".equalsIgnoreCase(title)) {
            return "Rev";
        }
        return title;
    }

    private Address buildPersonAddress(Map<String, Object> pairs, String personType) {
        if (findBooleanExists(getField(pairs, personType + ADDRESS_LINE4))) {
            return Address.builder()
                .line1(getField(pairs, personType + ADDRESS_LINE1))
                .line2(getField(pairs, personType + ADDRESS_LINE2))
                .town(getField(pairs, personType + ADDRESS_LINE3))
                .county(getField(pairs, personType + ADDRESS_LINE4))
                .postcode(getField(pairs, personType + ADDRESS_POSTCODE))
                .build();
        }
        boolean line3IsBlank = false;
        if (findBooleanExists(getField(pairs, personType + ADDRESS_LINE2))
            && !findBooleanExists(getField(pairs, personType + ADDRESS_LINE3))) {
            line3IsBlank = true;
        }
        return Address.builder()
            .line1(getField(pairs, personType + ADDRESS_LINE1))
            .town(getField(pairs, personType + ADDRESS_LINE2))
            .county(line3IsBlank ? "." : getField(pairs, personType + ADDRESS_LINE3))
            .postcode(getField(pairs, personType + ADDRESS_POSTCODE))
            .build();
    }

    private Identity buildPersonIdentity(Map<String, Object> pairs, String personType) {
        return Identity.builder()
            .dob(generateDateForCcd(pairs, errors, personType + "_dob"))
            .nino(normaliseNino(getField(pairs, personType + "_nino")))
            .build();
    }

    private Contact buildPersonContact(Map<String, Object> pairs, String personType) {
        return Contact.builder()
            .phone(getField(pairs, personType + "_phone"))
            .mobile(getField(pairs, personType + "_mobile"))
            .email(getField(pairs, personType + "_email"))
            .build();
    }

    private String findHearingType(Map<String, Object> pairs) {

        checkBooleanValue(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL);
        checkBooleanValue(pairs, errors, IS_HEARING_TYPE_PAPER_LITERAL);
        if (checkBooleanValue(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL)
            && (!pairs.containsKey(IS_HEARING_TYPE_PAPER_LITERAL) || pairs.get(IS_HEARING_TYPE_PAPER_LITERAL).equals("null"))) {
            pairs.put(IS_HEARING_TYPE_PAPER_LITERAL,
                !Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).toString()));
        } else if (checkBooleanValue(pairs, errors, IS_HEARING_TYPE_PAPER_LITERAL)
            && (!pairs.containsKey(IS_HEARING_TYPE_ORAL_LITERAL) || pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).equals("null"))) {
            pairs.put(IS_HEARING_TYPE_ORAL_LITERAL,
                !Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_PAPER_LITERAL).toString()));
        }

        if (areBooleansValid(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL, IS_HEARING_TYPE_PAPER_LITERAL)
            && !doValuesContradict(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL, IS_HEARING_TYPE_PAPER_LITERAL)) {
            return BooleanUtils.toBoolean(pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).toString()) ? HEARING_TYPE_ORAL :
                HEARING_TYPE_PAPER;
        }
        return null;
    }

    private HearingSubtype buildHearingSubtype(Map<String, Object> pairs) {
        if (getField(pairs, HEARING_TYPE_TELEPHONE_LITERAL) != null
            || getField(pairs, HEARING_TELEPHONE_LITERAL) != null
            || getField(pairs, HEARING_TYPE_VIDEO_LITERAL) != null
            || getField(pairs, HEARING_VIDEO_EMAIL_LITERAL) != null
            || getField(pairs, HEARING_TYPE_FACE_TO_FACE_LITERAL) != null) {

            String hearingTypeTelephone = checkBooleanValue(pairs, errors, HEARING_TYPE_TELEPHONE_LITERAL)
                ? convertBooleanToYesNoString(getBoolean(pairs, errors, HEARING_TYPE_TELEPHONE_LITERAL)) : null;

            String hearingTelephoneNumber = findHearingTelephoneNumber(pairs);

            String hearingTypeVideo = checkBooleanValue(pairs, errors, HEARING_TYPE_VIDEO_LITERAL)
                ? convertBooleanToYesNoString(getBoolean(pairs, errors, HEARING_TYPE_VIDEO_LITERAL)) : null;

            String hearingVideoEmail = findHearingVideoEmail(pairs);

            String hearingTypeFaceToFace = checkBooleanValue(pairs, errors, HEARING_TYPE_FACE_TO_FACE_LITERAL)
                ? convertBooleanToYesNoString(getBoolean(pairs, errors, HEARING_TYPE_FACE_TO_FACE_LITERAL)) : null;

            return HearingSubtype.builder()
                .wantsHearingTypeTelephone(hearingTypeTelephone)
                .hearingTelephoneNumber(hearingTelephoneNumber)
                .wantsHearingTypeVideo(hearingTypeVideo)
                .hearingVideoEmail(hearingVideoEmail)
                .wantsHearingTypeFaceToFace(hearingTypeFaceToFace)
                .build();
        }
        return HearingSubtype.builder().build();
    }

    private String findHearingTelephoneNumber(Map<String, Object> pairs) {
        if (getField(pairs, HEARING_TELEPHONE_LITERAL) != null) {
            return getField(pairs, HEARING_TELEPHONE_LITERAL);
        } else if (getField(pairs, PERSON1_VALUE + MOBILE) != null) {
            return getField(pairs, PERSON1_VALUE + MOBILE);
        } else if (getField(pairs, PERSON1_VALUE + PHONE) != null) {
            return getField(pairs, PERSON1_VALUE + PHONE);
        }
        return null;
    }

    private String findHearingVideoEmail(Map<String, Object> pairs) {
        if (getField(pairs, HEARING_VIDEO_EMAIL_LITERAL) != null) {
            return getField(pairs, HEARING_VIDEO_EMAIL_LITERAL);
        } else if (getField(pairs, PERSON1_VALUE + EMAIL) != null) {
            return getField(pairs, PERSON1_VALUE + EMAIL);
        }
        return null;
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs, String hearingType) {

        boolean isSignLanguageInterpreterRequired = findSignLanguageInterpreterRequired(pairs);

        String signLanguageType = findSignLanguageType(pairs, isSignLanguageInterpreterRequired);

        boolean isLanguageInterpreterRequired =
            findBooleanExists(getField(pairs, HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL))
                || findBooleanExists(getField(pairs, HEARING_OPTIONS_DIALECT_LITERAL));

        String languageType = isLanguageInterpreterRequired ? findLanguageTypeString(pairs) : null;

        String wantsToAttend = (hearingType.equals(HEARING_TYPE_ORAL)) ? YES_LITERAL : NO_LITERAL;

        List<String> arrangements = buildArrangements(pairs, isSignLanguageInterpreterRequired);

        String wantsSupport = !arrangements.isEmpty() ? YES_LITERAL : NO_LITERAL;

        List<ExcludeDate> excludedDates =
            extractExcludedDates(pairs, getField(pairs, HEARING_OPTIONS_EXCLUDE_DATES_LITERAL));

        String agreeLessNotice = checkBooleanValue(pairs, errors, AGREE_LESS_HEARING_NOTICE_LITERAL)
            ? convertBooleanToYesNoString(getBoolean(pairs, errors, AGREE_LESS_HEARING_NOTICE_LITERAL)) : null;

        String scheduleHearing = excludedDates != null && !excludedDates.isEmpty()
            && wantsToAttend.equals(YES_LITERAL) ? YES_LITERAL : NO_LITERAL;

        return HearingOptions.builder()
            .wantsToAttend(wantsToAttend)
            .wantsSupport(wantsSupport)
            .agreeLessNotice(agreeLessNotice)
            .scheduleHearing(scheduleHearing)
            .excludeDates(excludedDates)
            .arrangements(arrangements)
            .other(getField(pairs, HEARING_SUPPORT_ARRANGEMENTS_LITERAL))
            .languageInterpreter(convertBooleanToYesNoString(isLanguageInterpreterRequired))
            .languages(languageType)
            .signLanguageType(signLanguageType)
            .build();
    }

    private String findLanguageTypeString(Map<String, Object> pairs) {
        String languageType = getField(pairs, HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL);
        String dialectType = getField(pairs, HEARING_OPTIONS_DIALECT_LITERAL);

        StringJoiner buildLanguageType = new StringJoiner(" ");
        if (languageType != null) {
            buildLanguageType.add(languageType);
        }
        if (dialectType != null) {
            buildLanguageType.add(dialectType);
        }
        return buildLanguageType.toString();
    }

    private Optional<Boolean> findSignLanguageInterpreterRequiredInOldForm(Map<String, Object> pairs) {
        if (areBooleansValid(pairs, errors, HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL)) {
            return Optional
                .of(BooleanUtils.toBoolean(pairs.get(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL).toString()));
        }
        return Optional.empty();
    }

    private boolean findSignLanguageInterpreterRequired(Map<String, Object> pairs) {
        Optional<Boolean> fromOldVersionForm = findSignLanguageInterpreterRequiredInOldForm(pairs);
        if (fromOldVersionForm.isPresent()) {
            return fromOldVersionForm.get();
        }
        return StringUtils.isNotBlank(getField(pairs, HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL));
    }

    private String findSignLanguageType(Map<String, Object> pairs, boolean isSignLanguageInterpreterRequired) {
        if (isSignLanguageInterpreterRequired) {
            String signLanguageType = getField(pairs, HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL);
            return signLanguageType != null ? signLanguageType : DEFAULT_SIGN_LANGUAGE;
        }
        return null;
    }

    private List<ExcludeDate> extractExcludedDates(Map<String, Object> pairs, String excludedDatesList) {
        List<ExcludeDate> excludeDates = new ArrayList<>();

        if (excludedDatesList != null && !excludedDatesList.isEmpty()) {
            String[] items = excludedDatesList.split(",\\s*");

            for (String item : items) {
                List<String> range = Arrays.asList(item.split("\\s*-\\s*"));
                String errorMessage = "hearing_options_exclude_dates contains an invalid date range. "
                    + "Should be single dates separated by commas and/or a date range "
                    + "e.g. 01/01/2020, 07/01/2020, 12/01/2020 - 15/01/2020";

                if (range.size() > 2) {
                    errors.add(errorMessage);
                    return excludeDates;
                }

                String startDate = getDateForCcd(range.get(0), errors, errorMessage);
                String endDate = null;

                if (2 == range.size()) {
                    endDate = getDateForCcd(range.get(1), errors, errorMessage);
                }
                excludeDates.add(
                    ExcludeDate.builder().value(DateRange.builder().start(startDate).end(endDate).build()).build());
            }
        }
        if (excludeDates.size() == 0) {
            String tellTribunalAboutDates = checkBooleanValue(pairs, errors, TELL_TRIBUNAL_ABOUT_DATES)
                ? convertBooleanToYesNoString(getBoolean(pairs, errors, TELL_TRIBUNAL_ABOUT_DATES)) : null;

            if (("Yes").equals(tellTribunalAboutDates)) {
                warnings.add(HEARING_EXCLUDE_DATES_MISSING);
            }
            return null;
        }
        return excludeDates;
    }

    private List<String> buildArrangements(Map<String, Object> pairs, boolean isSignLanguageInterpreterRequired) {

        List<String> arrangements = new ArrayList<>();

        if (areBooleansValid(pairs, errors, HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL)
            && BooleanUtils.toBoolean(pairs.get(HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL).toString())) {
            arrangements.add("disabledAccess");
        }
        if (areBooleansValid(pairs, errors, HEARING_OPTIONS_HEARING_LOOP_LITERAL)
            && BooleanUtils.toBoolean(pairs.get(HEARING_OPTIONS_HEARING_LOOP_LITERAL).toString())) {
            arrangements.add("hearingLoop");
        }
        if (isSignLanguageInterpreterRequired) {
            arrangements.add("signLanguageInterpreter");
        }
        return arrangements;

    }

    private List<SscsDocument> buildDocumentsFromData(List<InputScannedDoc> records) {
        List<SscsDocument> documentDetails = new ArrayList<>();
        if (records != null) {
            for (InputScannedDoc record : records) {

                String documentType =
                    StringUtils.startsWithIgnoreCase(record.getSubtype(), "sscs1") ? "sscs1" : "appellantEvidence";

                checkFileExtensionValid(record.getFileName());

                String scannedDate =
                    record.getScannedDate() != null ? record.getScannedDate().toLocalDate().toString() : null;

                SscsDocumentDetails details = SscsDocumentDetails.builder()
                    .documentLink(record.getUrl())
                    .documentDateAdded(scannedDate)
                    .documentFileName(record.getFileName())
                    .documentType(documentType).build();
                documentDetails.add(SscsDocument.builder().value(details).build());
            }
        }
        return documentDetails;
    }

    private void checkFileExtensionValid(String fileName) {
        if (fileName != null) {
            try {
                getContentTypeForFileName(fileName);
            } catch (UnknownFileTypeException ex) {
                errors.add(ex.getCause().getMessage());
            }
        } else {
            errors.add("File name field must not be empty");
        }
    }

    public Map<String, Object> checkForMatches(Map<String, Object> sscsCaseData, IdamTokens token) {
        Appeal appeal = (Appeal) sscsCaseData.get("appeal");
        String nino = "";
        if (appeal != null && appeal.getAppellant() != null
            && appeal.getAppellant().getIdentity() != null && appeal.getAppellant().getIdentity().getNino() != null) {
            nino = appeal.getAppellant().getIdentity().getNino();
        }

        List<SscsCaseDetails> matchedByNinoCases = new ArrayList<>();

        if (!StringUtils.isEmpty(nino)) {
            matchedByNinoCases = ccdService.findCaseBy("data.appeal.appellant.identity.nino", nino, token);
        }

        sscsCaseData = addAssociatedCases(sscsCaseData, matchedByNinoCases);
        return sscsCaseData;
    }

    private Map<String, Object> addAssociatedCases(Map<String, Object> sscsCaseData,
                                                   List<SscsCaseDetails> matchedByNinoCases) {
        List<CaseLink> associatedCases = new ArrayList<>();

        for (SscsCaseDetails sscsCaseDetails : matchedByNinoCases) {
            CaseLink caseLink = CaseLink.builder().value(
                CaseLinkDetails.builder().caseReference(sscsCaseDetails.getId().toString()).build()).build();
            associatedCases.add(caseLink);

            String caseId = null != sscsCaseDetails.getId() ? sscsCaseDetails.getId().toString() : "N/A";
            log.info("Added associated case {}" + caseId);
        }
        if (associatedCases.size() > 0) {
            sscsCaseData.put("associatedCase", associatedCases);
            sscsCaseData.put("linkedCasesBoolean", "Yes");
        } else {
            sscsCaseData.put("linkedCasesBoolean", "No");
        }

        return sscsCaseData;
    }

    private void duplicateCaseCheck(String caseId, Map<String, Object> sscsCaseData, IdamTokens token) {
        Appeal appeal = (Appeal) sscsCaseData.get("appeal");
        String nino = "";
        String mrnDate = "";
        String benefitType = "";

        if (appeal != null && appeal.getAppellant() != null
            && appeal.getAppellant().getIdentity() != null && appeal.getAppellant().getIdentity().getNino() != null) {
            nino = appeal.getAppellant().getIdentity().getNino();
        }
        if (appeal != null && appeal.getMrnDetails() != null) {
            mrnDate = appeal.getMrnDetails().getMrnDate();
        }
        if (appeal != null && appeal.getBenefitType() != null) {
            benefitType = appeal.getBenefitType().getCode();
        }

        if (!StringUtils.isEmpty(nino) && !StringUtils.isEmpty(benefitType)
            && !StringUtils.isEmpty(mrnDate)) {
            Map<String, String> searchCriteria = new HashMap<>();
            searchCriteria.put("case.appeal.appellant.identity.nino", nino);
            searchCriteria.put("case.appeal.benefitType.code", benefitType);
            searchCriteria.put("case.appeal.mrnDetails.mrnDate", mrnDate);

            SscsCaseDetails duplicateCase =
                ccdService.findCcdCaseByNinoAndBenefitTypeAndMrnDate(nino, benefitType, mrnDate, token);

            if (duplicateCase != null) {
                log.info("Duplicate case already exists for exception record id {}", caseId);
                errors.add("Duplicate case already exists - please reject this exception record");
            }
        }
    }
}
