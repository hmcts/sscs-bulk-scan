package uk.gov.hmcts.reform.sscs.transformers;

import static uk.gov.hmcts.reform.sscs.ccd.service.SscsCcdConvertService.normaliseNino;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.AGREE_LESS_HEARING_NOTICE_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.APPEAL_GROUNDS;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.BENEFIT_TYPE_DESCRIPTION;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.DEFAULT_SIGN_LANGUAGE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_DIALECT_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_EXCLUDE_DATES_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_HEARING_LOOP_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_SUPPORT_ARRANGEMENTS_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_TYPE_ORAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.HEARING_TYPE_PAPER;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.IS_BENEFIT_TYPE_ESA;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.IS_BENEFIT_TYPE_PIP;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.IS_HEARING_TYPE_ORAL_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.IS_HEARING_TYPE_PAPER_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.NO_LITERAL;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.PERSON1_VALUE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.PERSON2_VALUE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.REPRESENTATIVE_VALUE;
import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.YES_LITERAL;
import static uk.gov.hmcts.reform.sscs.model.AllowedFileTypes.getContentTypeForFileName;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.generateBenefitCode;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.generateCaseCode;
import static uk.gov.hmcts.reform.sscs.service.CaseCodeService.generateIssueCode;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.areBooleansValid;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.checkBooleanValue;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.convertBooleanToYesNoString;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.doValuesContradict;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.findBooleanExists;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.generateDateForCcd;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getBoolean;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getDateForCcd;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.getField;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.hasPerson;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;
import static uk.gov.hmcts.reform.sscs.utility.AppealNumberGenerator.generateAppealNumber;

import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseDetails;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.Address;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appeal;
import uk.gov.hmcts.reform.sscs.ccd.domain.AppealReason;
import uk.gov.hmcts.reform.sscs.ccd.domain.AppealReasonDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.AppealReasons;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appellant;
import uk.gov.hmcts.reform.sscs.ccd.domain.Appointee;
import uk.gov.hmcts.reform.sscs.ccd.domain.Benefit;
import uk.gov.hmcts.reform.sscs.ccd.domain.BenefitType;
import uk.gov.hmcts.reform.sscs.ccd.domain.Contact;
import uk.gov.hmcts.reform.sscs.ccd.domain.DateRange;
import uk.gov.hmcts.reform.sscs.ccd.domain.ExcludeDate;
import uk.gov.hmcts.reform.sscs.ccd.domain.HearingOptions;
import uk.gov.hmcts.reform.sscs.ccd.domain.Identity;
import uk.gov.hmcts.reform.sscs.ccd.domain.MrnDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.Name;
import uk.gov.hmcts.reform.sscs.ccd.domain.Representative;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocument;
import uk.gov.hmcts.reform.sscs.ccd.domain.SscsDocumentDetails;
import uk.gov.hmcts.reform.sscs.ccd.domain.Subscription;
import uk.gov.hmcts.reform.sscs.ccd.domain.Subscriptions;
import uk.gov.hmcts.reform.sscs.exception.UnknownFileTypeException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
import uk.gov.hmcts.reform.sscs.validators.SscsKeyValuePairValidator;

@Component
@Slf4j
public class SscsCaseTransformer implements CaseTransformer {
    private static DateTimeFormatter DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd");

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    @Autowired
    private SscsKeyValuePairValidator keyValuePairValidator;

    @Autowired
    private SscsDataHelper sscsDataHelper;

    private Set<String> errors;

    public SscsCaseTransformer(SscsJsonExtractor sscsJsonExtractor,
                               SscsKeyValuePairValidator keyValuePairValidator,
                               SscsDataHelper sscsDataHelper) {
        this.sscsJsonExtractor = sscsJsonExtractor;
        this.keyValuePairValidator = keyValuePairValidator;
        this.sscsDataHelper = sscsDataHelper;
    }

    @Override
    public CaseResponse transformExceptionRecordToCase(CaseDetails caseDetails) {

        String caseId = caseDetails.getCaseId();
        log.info("Transforming exception record {}", caseId);

        CaseResponse keyValuePairValidatorResponse = keyValuePairValidator.validate(caseDetails.getCaseData());

        if (keyValuePairValidatorResponse.getErrors() != null) {
            log.info("Errors found while validating key value pairs while transforming exception record {}", caseId);
            return CaseResponse.builder().errors(keyValuePairValidatorResponse.getErrors()).build();
        }

        log.info("Key value pairs validated while transforming exception record {}", caseId);

        errors = new HashSet<>();

        ScannedData scannedData = sscsJsonExtractor.extractJson(caseDetails.getCaseData());

        Appeal appeal = buildAppealFromData(scannedData.getOcrCaseData(), caseDetails.getCaseId());
        List<SscsDocument> sscsDocuments = buildDocumentsFromData(scannedData.getRecords());
        Subscriptions subscriptions = populateSubscriptions(appeal, scannedData.getOcrCaseData());

        Map<String, Object> transformed = new HashMap<>();

        sscsDataHelper.addSscsDataToMap(transformed, appeal, sscsDocuments, subscriptions);

        transformed.put("bulkScanCaseReference", caseId);

        String caseCreated = extractOpeningDate(caseDetails);
        transformed.put("caseCreated", caseCreated);

        log.info("Transformation complete for exception record id {}, caseCreated field set to {}", caseId, caseCreated);

        return CaseResponse.builder().transformedCase(transformed).errors(errors.stream().collect(Collectors.toList())).build();
    }

    private String extractOpeningDate(CaseDetails caseDetails) {
        String openingDate = (String) caseDetails.getCaseData().get("openingDate");
        return (StringUtils.isEmpty(openingDate) || openingDate.length() < 10)
            ? DATE_FORMAT.print(new DateTime())
            : ((String) caseDetails.getCaseData().get("openingDate")).substring(0, 10);
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
                .mrnDetails(buildMrnDetails(pairs))
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

    private MrnDetails buildMrnDetails(Map<String, Object> pairs) {

        return MrnDetails.builder()
            .mrnDate(generateDateForCcd(pairs, errors, "mrn_date"))
            .mrnLateReason(getField(pairs, "appeal_late_reason"))
            .dwpIssuingOffice(getField(pairs, "office"))
            .build();
    }

    private Name buildPersonName(Map<String, Object> pairs, String personType) {
        return Name.builder()
            .title(getField(pairs, personType + "_title"))
            .firstName(getField(pairs, personType + "_first_name"))
            .lastName(getField(pairs, personType + "_last_name"))
            .build();
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

        List<ExcludeDate> excludedDates = buildExcludedDates(pairs);

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

    private List<ExcludeDate> buildExcludedDates(Map<String, Object> pairs) {

        if (pairs.containsKey(HEARING_OPTIONS_EXCLUDE_DATES_LITERAL)) {
            return extractExcludedDates(getField(pairs, HEARING_OPTIONS_EXCLUDE_DATES_LITERAL));
        } else {
            return null;
        }
    }

    private List<ExcludeDate> extractExcludedDates(String excludedDatesList) {
        List<ExcludeDate> excludeDates = new ArrayList<>();

        if (excludedDatesList != null && !excludedDatesList.isEmpty()) {
            String[] items = excludedDatesList.split(",\\s*");

            for (String item : items) {
                List<String> range = Arrays.asList(item.split("\\s*-\\s*"));
                String errorMessage = "hearing_options_exclude_dates contains an invalid date range. "
                    + "Should be single dates separated by commas and/or a date range "
                    + "e.g. 01/01/2019, 07/01/2019, 12/01/2019 - 15/01/2019";

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

    private List<SscsDocument> buildDocumentsFromData(List<ScannedRecord> records) {
        List<SscsDocument> documentDetails = new ArrayList<>();
        if (records != null) {
            for (ScannedRecord record : records) {

                String documentType = record.getSubtype() != null && record.getSubtype().equalsIgnoreCase("sscs1") ? "sscs1" : "appellantEvidence";

                checkFileExtensionValid(record.getFileName());

                SscsDocumentDetails details = SscsDocumentDetails.builder()
                    .documentLink(record.getUrl())
                    .documentDateAdded(stripTimeFromDocumentDate(record.getScannedDate()))
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


    private String stripTimeFromDocumentDate(String documentDate) {
        return documentDate == null
            ? null
            : LocalDateTime.parse(documentDate).toLocalDate().toString();
    }
}
