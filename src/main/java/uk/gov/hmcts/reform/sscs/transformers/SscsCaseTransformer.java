package uk.gov.hmcts.reform.sscs.transformers;

import static uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService.normaliseNino;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.helper.SscsDataHelper.getValidationStatus;
import static uk.gov.hmcts.reform.sscs.model.AllowedFileTypes.getContentTypeForFileName;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;
import static uk.gov.hmcts.reform.sscs.utility.AppealNumberGenerator.generateAppealNumber;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.*;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.ccd.service.CcdService;
import uk.gov.hmcts.reform.sscs.exception.UnknownFileTypeException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.idam.IdamService;
import uk.gov.hmcts.reform.sscs.idam.IdamTokens;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.service.DwpAddressLookupService;
import uk.gov.hmcts.reform.sscs.service.FuzzyMatcherService;
import uk.gov.hmcts.reform.sscs.validators.SscsKeyValuePairValidator;

@Component
@Slf4j
public class SscsCaseTransformer implements CaseTransformer {

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    @Autowired
    private SscsKeyValuePairValidator keyValuePairValidator;

    @Autowired
    private SscsDataHelper sscsDataHelper;

    @Autowired
    private FuzzyMatcherService fuzzyMatcherService;

    @Autowired
    private DwpAddressLookupService dwpAddressLookupService;

    @Autowired
    private final IdamService idamService;

    @Autowired
    private final CcdService ccdService;

    private Set<String> errors;
    private Set<String> warnings;

    public SscsCaseTransformer(SscsJsonExtractor sscsJsonExtractor,
                               SscsKeyValuePairValidator keyValuePairValidator,
                               SscsDataHelper sscsDataHelper,
                               FuzzyMatcherService fuzzyMatcherService,
                               DwpAddressLookupService dwpAddressLookupService,
                               IdamService idamService,
                               CcdService ccdService) {
        this.sscsJsonExtractor = sscsJsonExtractor;
        this.keyValuePairValidator = keyValuePairValidator;
        this.sscsDataHelper = sscsDataHelper;
        this.fuzzyMatcherService = fuzzyMatcherService;
        this.dwpAddressLookupService = dwpAddressLookupService;
        this.idamService = idamService;
        this.ccdService = ccdService;
    }

    @Override
    public CaseResponse transformExceptionRecord(ExceptionRecord exceptionRecord, boolean combineWarnings) {

        String caseId = exceptionRecord.getId() != null ? exceptionRecord.getId() : "N/A";
        log.info("Validating exception record against schema caseId {}", caseId);

        CaseResponse keyValuePairValidatorResponse = keyValuePairValidator.validate(exceptionRecord.getOcrDataFields());

        if (keyValuePairValidatorResponse.getErrors() != null) {
            log.info("Errors found while validating key value pairs while transforming exception record caseId {}", caseId);
            return keyValuePairValidatorResponse;
        }

        log.info("Extracting and transforming exception record caseId {}", caseId);

        errors = new HashSet<>();
        warnings = new HashSet<>();

        ScannedData scannedData = sscsJsonExtractor.extractJson(exceptionRecord);

        IdamTokens token = idamService.getIdamTokens();

        Map<String, Object> transformed = transformData(caseId, scannedData, token);

        duplicateCaseCheck(caseId, transformed, token);

        if (combineWarnings) {
            warnings = combineWarnings();
        }

        return CaseResponse.builder().transformedCase(transformed).errors(new ArrayList<>(errors)).warnings(new ArrayList<>(warnings))
            .status(getValidationStatus(new ArrayList<>(errors), new ArrayList<>(warnings))).build();
    }

    private Set<String> combineWarnings() {
        Set<String> mergedWarnings = new HashSet<>();

        mergedWarnings.addAll(warnings);
        mergedWarnings.addAll(errors);
        errors.clear();

        return mergedWarnings;
    }

    @Override
    public CaseResponse transformExceptionRecordToCaseOld(CaseDetails caseDetails, IdamTokens token) {
        String caseId = caseDetails.getCaseId();

        log.info("Transforming exception record {}", caseId);

        CaseResponse keyValuePairValidatorResponse = keyValuePairValidator.validateOld(caseDetails.getCaseData(), "scanOCRData");

        if (keyValuePairValidatorResponse.getErrors() != null) {
            log.info("Errors found while validating key value pairs while transforming exception record {}", caseId);
            return keyValuePairValidatorResponse;
        }

        log.info("Key value pairs validated while transforming exception record {}", caseId);

        errors = new HashSet<>();
        warnings = new HashSet<>();

        ScannedData scannedData = sscsJsonExtractor.extractJsonOld(caseDetails.getCaseData());

        Map<String, Object> transformed = transformData(caseId, scannedData, token);

        return CaseResponse.builder().transformedCase(transformed).errors(new ArrayList<>(errors)).warnings(new ArrayList<>(warnings)).build();
    }

    private Map<String, Object> transformData(String caseId, ScannedData scannedData, IdamTokens token) {
        Appeal appeal = buildAppealFromData(scannedData.getOcrCaseData(), caseId);
        List<SscsDocument> sscsDocuments = buildDocumentsFromData(scannedData.getRecords());
        Subscriptions subscriptions = populateSubscriptions(appeal, scannedData.getOcrCaseData());

        Map<String, Object> transformed = new HashMap<>();

        sscsDataHelper.addSscsDataToMap(transformed, appeal, sscsDocuments, subscriptions);

        transformed.put("bulkScanCaseReference", caseId);

        transformed.put("caseCreated", scannedData.getOpeningDate());

        log.info("Transformation complete for exception record id {}, caseCreated field set to {}", caseId, scannedData.getOpeningDate());

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
        String email = getField(pairs,personType + "_email");
        String mobile = getField(pairs,personType + "_mobile");

        return Subscription.builder().email(email).mobile(mobile).subscribeSms(convertBooleanToYesNoString(wantsSms))
            .wantSmsNotifications(convertBooleanToYesNoString(wantsSms)).tya(generateAppealNumber()).build();
    }

    private Appeal buildAppealFromData(Map<String, Object> pairs, String caseId) {
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
                appellant = buildAppellant(pairs, PERSON2_VALUE, appointee, buildPersonContact(pairs, PERSON2_VALUE));

            } else if (hasPerson(pairs, PERSON1_VALUE)) {
                appellant = buildAppellant(pairs, PERSON1_VALUE, null, buildPersonContact(pairs, PERSON1_VALUE));
            }

            String hearingType = findHearingType(pairs);
            AppealReasons appealReasons = findAppealReasons(pairs);

            BenefitType benefitType = getBenefitType(pairs);

            return Appeal.builder()
                .benefitType(benefitType)
                .appellant(appellant)
                .appealReasons(appealReasons)
                .rep(buildRepresentative(pairs))
                .mrnDetails(buildMrnDetails(pairs, benefitType))
                .hearingType(hearingType)
                .hearingOptions(buildHearingOptions(pairs, hearingType))
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
        String appealReason = getField(pairs, APPEAL_GROUNDS);
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

    private BenefitType getBenefitType(Map<String, Object> pairs) {
        String code = getField(pairs, BENEFIT_TYPE_DESCRIPTION);

        if (code != null) {
            code = fuzzyMatcherService.matchBenefitType(code);
        }

        if (areBooleansValid(pairs, errors, IS_BENEFIT_TYPE_ESA, IS_BENEFIT_TYPE_PIP)) {
            doValuesContradict(pairs, errors, IS_BENEFIT_TYPE_ESA, IS_BENEFIT_TYPE_PIP);
        }
        if (checkBooleanValue(pairs, errors, IS_BENEFIT_TYPE_PIP) && BooleanUtils.toBoolean(pairs.get(IS_BENEFIT_TYPE_PIP).toString())) {
            code = Benefit.PIP.name();
        } else if (checkBooleanValue(pairs, errors, IS_BENEFIT_TYPE_ESA) && BooleanUtils.toBoolean(pairs.get(IS_BENEFIT_TYPE_ESA).toString())) {
            code = Benefit.ESA.name();
        }
        return (code != null) ? BenefitType.builder().code(code.toUpperCase()).build() : null;
    }

    private Appellant buildAppellant(Map<String, Object> pairs, String personType, Appointee appointee, Contact contact) {
        return Appellant.builder()
            .name(buildPersonName(pairs, personType))
            .isAppointee(convertBooleanToYesNoString(appointee != null))
            .address(buildPersonAddress(pairs, personType))
            .identity(buildPersonIdentity(pairs, personType))
            .contact(contact)
            .appointee(appointee)
            .build();
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

        if (dwpIssuingOffice != null) {

            if (benefitType != null) {
                return dwpAddressLookupService.getDwpMappingByOffice(benefitType.getCode(), dwpIssuingOffice)
                    .map(office -> office.getMapping().getCcd())
                    .orElse(null);
            } else {
                return dwpIssuingOffice;
            }
        }
        return null;
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
        if (findBooleanExists(getField(pairs, personType + "_address_line4"))) {
            return Address.builder()
                .line1(getField(pairs, personType + "_address_line1"))
                .line2(getField(pairs, personType + "_address_line2"))
                .town(getField(pairs, personType + "_address_line3"))
                .county(getField(pairs, personType + "_address_line4"))
                .postcode(getField(pairs, personType + "_postcode"))
                .build();
        }
        return Address.builder()
            .line1(getField(pairs, personType + "_address_line1"))
            .town(getField(pairs, personType + "_address_line2"))
            .county(getField(pairs, personType + "_address_line3"))
            .postcode(getField(pairs, personType + "_postcode"))
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
            && (pairs.get(IS_HEARING_TYPE_PAPER_LITERAL) == null
            || pairs.get(IS_HEARING_TYPE_PAPER_LITERAL).equals("null"))) {
            pairs.put(IS_HEARING_TYPE_PAPER_LITERAL, !Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).toString()));
        } else if (checkBooleanValue(pairs, errors, IS_HEARING_TYPE_PAPER_LITERAL)
            && (pairs.get(IS_HEARING_TYPE_ORAL_LITERAL) == null
            || pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).equals("null"))) {
            pairs.put(IS_HEARING_TYPE_ORAL_LITERAL,!Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_PAPER_LITERAL).toString()));
        }

        if (areBooleansValid(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL, IS_HEARING_TYPE_PAPER_LITERAL)
            && !doValuesContradict(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL, IS_HEARING_TYPE_PAPER_LITERAL)) {
            return BooleanUtils.toBoolean(pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).toString()) ? HEARING_TYPE_ORAL : HEARING_TYPE_PAPER;
        }
        return null;
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs, String hearingType) {

        boolean isSignLanguageInterpreterRequired = findSignLanguageInterpreterRequired(pairs);

        String signLanguageType = findSignLanguageType(pairs, isSignLanguageInterpreterRequired);

        boolean isLanguageInterpreterRequired = findBooleanExists(getField(pairs, HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL))
            || findBooleanExists(getField(pairs, HEARING_OPTIONS_DIALECT_LITERAL));

        String languageType = isLanguageInterpreterRequired ? findLanguageTypeString(pairs) : null;

        String wantsToAttend = hearingType != null && hearingType.equals(HEARING_TYPE_ORAL) ? YES_LITERAL : NO_LITERAL;

        List<String> arrangements = buildArrangements(pairs, isSignLanguageInterpreterRequired);

        String wantsSupport = !arrangements.isEmpty() ? YES_LITERAL : NO_LITERAL;

        List<ExcludeDate> excludedDates = extractExcludedDates(pairs, getField(pairs, HEARING_OPTIONS_EXCLUDE_DATES_LITERAL));;

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
            return Optional.of(BooleanUtils.toBoolean(pairs.get(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL).toString()));
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
                excludeDates.add(ExcludeDate.builder().value(DateRange.builder().start(startDate).end(endDate).build()).build());
            }
        }
        if (excludeDates.size() == 0) {
            String tellTribunalAboutDates = checkBooleanValue(pairs, errors, TELL_TRIBUNAL_ABOUT_DATES)
                ? convertBooleanToYesNoString(getBoolean(pairs, errors, TELL_TRIBUNAL_ABOUT_DATES)) : null;

            if (("Yes").equals(tellTribunalAboutDates)) {
                warnings.add("No excluded dates provided but data indicates that there are dates customer cannot attend hearing as " + TELL_TRIBUNAL_ABOUT_DATES + " is true. Is this correct?");
            }
            return null;
        }
        return excludeDates;
    }

    private List<String> buildArrangements(Map<String, Object> pairs, boolean isSignLanguageInterpreterRequired) {

        List<String> arrangements = new ArrayList<>();

        if (areBooleansValid(pairs, errors, HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL)
            &&  BooleanUtils.toBoolean(pairs.get(HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL).toString())) {
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

                String documentType = StringUtils.startsWithIgnoreCase(record.getSubtype(), "sscs1") ? "sscs1" : "appellantEvidence";

                checkFileExtensionValid(record.getFileName());

                String scannedDate = record.getScannedDate() != null ? record.getScannedDate().toLocalDate().toString() : null;

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
            Map<String, String> linkCasesCriteria = new HashMap<>();
            linkCasesCriteria.put("case.appeal.appellant.identity.nino", nino);
            matchedByNinoCases = ccdService.findCaseBy(linkCasesCriteria, token);
        }

        sscsCaseData = addAssociatedCases(sscsCaseData, matchedByNinoCases);
        return sscsCaseData;
    }

    private Map<String, Object> addAssociatedCases(Map<String, Object> sscsCaseData, List<SscsCaseDetails> matchedByNinoCases) {
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

            List<SscsCaseDetails> duplicateCases = ccdService.findCaseBy(searchCriteria, token);

            if (!CollectionUtils.isEmpty(duplicateCases)) {
                log.info("Duplicate case already exists for exception record id {}", caseId);
                errors.add("Duplicate case already exists - please reject this exception record");
            }
        }
    }
}
