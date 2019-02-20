package uk.gov.hmcts.reform.sscs.transformers;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.domain.email.EmailAttachment.*;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;
import static uk.gov.hmcts.reform.sscs.utility.AppealNumberGenerator.generateAppealNumber;

import java.time.LocalDateTime;
import java.util.*;

import lombok.extern.slf4j.Slf4j;
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
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.exception.UnknownFileTypeException;
import uk.gov.hmcts.reform.sscs.helper.SscsDataHelper;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;
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

    private List<String> errors;

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

        errors = new ArrayList<>();

        ScannedData scannedData = sscsJsonExtractor.extractJson(caseDetails.getCaseData());

        Appeal appeal = buildAppealFromData(scannedData.getOcrCaseData(), caseDetails.getCaseId());
        List<SscsDocument> sscsDocuments = buildDocumentsFromData(scannedData.getRecords());
        Subscriptions subscriptions = populateSubscriptions(appeal);

        Map<String, Object> transformed = new HashMap<>();

        sscsDataHelper.addSscsDataToMap(transformed, appeal, sscsDocuments, subscriptions);

        transformed.put("bulkScanCaseReference", caseId);

        DateTimeFormatter dtfOut = DateTimeFormat.forPattern("yyyy-MM-dd");

        String caseCreated = dtfOut.print(new DateTime());
        transformed.put("caseCreated", caseCreated);

        log.info("Transformation complete for exception record id {}, caseCreated field set to {}", caseId, caseCreated);

        return CaseResponse.builder().transformedCase(transformed).errors(errors).build();
    }

    private static Subscriptions populateSubscriptions(Appeal appeal) {

        return Subscriptions.builder()
            .appellantSubscription(appeal.getAppellant() != null && appeal.getAppellant().getAppointee() == null ? generateSubscriptionWithAppealNumber() : null)
            .appointeeSubscription(appeal.getAppellant() != null && appeal.getAppellant().getAppointee() != null ? generateSubscriptionWithAppealNumber() : null)
            .representativeSubscription(appeal.getRep() != null && appeal.getRep().getHasRepresentative().equals("Yes") ? generateSubscriptionWithAppealNumber() : null)
            .build();
    }

    private static Subscription generateSubscriptionWithAppealNumber() {
        return Subscription.builder().tya(generateAppealNumber()).build();
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

            BenefitType benefitType = getField(pairs, BENEFIT_TYPE_DESCRIPTION) != null ? BenefitType.builder().code(getField(pairs, BENEFIT_TYPE_DESCRIPTION).toUpperCase()).build() : null;

            return Appeal.builder()
                .benefitType(benefitType)
                .appellant(appellant)
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

    private Appellant buildAppellant(Map<String, Object> pairs, String personType, Appointee appointee, Contact contact) {
        return Appellant.builder()
            .name(buildPersonName(pairs, personType))
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
                .hasRepresentative(convertBooleanToYesNoString(doesRepExist))
                .name(buildPersonName(pairs, REPRESENTATIVE_VALUE))
                .address(buildPersonAddress(pairs, REPRESENTATIVE_VALUE))
                .organisation(getField(pairs,"representative_company"))
                .contact(buildPersonContact(pairs, REPRESENTATIVE_VALUE))
                .build();
        } else {
            return Representative.builder().hasRepresentative(convertBooleanToYesNoString(doesRepExist)).build();
        }
    }

    private MrnDetails buildMrnDetails(Map<String, Object> pairs) {

        return MrnDetails.builder()
            .mrnDate(generateDateForCcd(pairs, errors,"mrn_date"))
            .mrnLateReason(getField(pairs,"appeal_late_reason"))
            .dwpIssuingOffice(getField(pairs,"office"))
        .build();
    }

    private Name buildPersonName(Map<String, Object> pairs, String personType) {
        return Name.builder()
            .title(getField(pairs,personType + "_title"))
            .firstName(getField(pairs,personType + "_first_name"))
            .lastName(getField(pairs,personType + "_last_name"))
        .build();
    }

    private Address buildPersonAddress(Map<String, Object> pairs, String personType) {
        return Address.builder()
            .line1(getField(pairs,personType + "_address_line1"))
            .line2(getField(pairs,personType + "_address_line2"))
            .town(getField(pairs,personType + "_address_line3"))
            .county(getField(pairs,personType + "_address_line4"))
            .postcode(getField(pairs,personType + "_postcode"))
        .build();
    }

    private Identity buildPersonIdentity(Map<String, Object> pairs, String personType) {
        return Identity.builder()
            .dob(generateDateForCcd(pairs, errors,personType + "_dob"))
            .nino(getField(pairs,personType + "_nino"))
        .build();
    }

    private Contact buildPersonContact(Map<String, Object> pairs, String personType) {
        return Contact.builder()
            .phone(getField(pairs,personType + "_phone"))
            .mobile(getField(pairs,personType + "_mobile"))
        .build();
    }

    private String findHearingType(Map<String, Object> pairs) {

        if (checkBooleanValue(pairs, IS_HEARING_TYPE_ORAL_LITERAL) && (pairs.get(IS_HEARING_TYPE_PAPER_LITERAL) == null || pairs.get(IS_HEARING_TYPE_PAPER_LITERAL).equals("null"))) {
            pairs.put(IS_HEARING_TYPE_PAPER_LITERAL, !Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).toString()));
        } else if (checkBooleanValue(pairs, IS_HEARING_TYPE_PAPER_LITERAL) && (pairs.get(IS_HEARING_TYPE_ORAL_LITERAL) == null || pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).equals("null"))) {
            pairs.put(IS_HEARING_TYPE_ORAL_LITERAL,!Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_PAPER_LITERAL).toString()));
        }

        if (areBooleansValid(pairs, IS_HEARING_TYPE_ORAL_LITERAL, IS_HEARING_TYPE_PAPER_LITERAL) && !doValuesContradict(pairs, errors, IS_HEARING_TYPE_ORAL_LITERAL, IS_HEARING_TYPE_PAPER_LITERAL)) {
            return Boolean.parseBoolean(pairs.get(IS_HEARING_TYPE_ORAL_LITERAL).toString()) ? HEARING_TYPE_ORAL : HEARING_TYPE_PAPER;
        }
        return null;
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs, String hearingType) {

        boolean isSignLanguageInterpreterRequired = findSignLanguageInterpreterRequired(pairs);

        String signLanguageType = findSignLanguageType(pairs, isSignLanguageInterpreterRequired);

        boolean isLanguageInterpreterRequired = (findBooleanExists(getField(pairs, HEARING_OPTIONS_LANGUAGE_TYPE_LITERAL)) || findBooleanExists(getField(pairs, HEARING_OPTIONS_DIALECT_LITERAL))) && !isSignLanguageInterpreterRequired;

        String languageType = isLanguageInterpreterRequired ? findLanguageTypeString(pairs) : null;

        String wantsToAttend = hearingType != null && hearingType.equals(HEARING_TYPE_ORAL) ? YES_LITERAL : NO_LITERAL;

        List<String> arrangements = buildArrangements(pairs);

        String wantsSupport = !arrangements.isEmpty() ? YES_LITERAL : NO_LITERAL;

        List<ExcludeDate> excludedDates = buildExcludedDates(pairs);

        String scheduleHearing = excludedDates != null && !excludedDates.isEmpty() && wantsToAttend.equals(YES_LITERAL) ? YES_LITERAL : NO_LITERAL;

        return HearingOptions.builder()
            .wantsToAttend(wantsToAttend)
            .wantsSupport(wantsSupport)
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

    private boolean findSignLanguageInterpreterRequired(Map<String, Object> pairs) {
        if (areBooleansValid(pairs, HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL)) {
            return Boolean.parseBoolean(pairs.get(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL).toString());
        } else {
            return false;
        }
    }

    private String findSignLanguageType(Map<String, Object> pairs, boolean isSignLanguageInterpreterRequired) {
        if (isSignLanguageInterpreterRequired) {
            return getField(pairs, HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL) != null ? getField(pairs, HEARING_OPTIONS_SIGN_LANGUAGE_TYPE_LITERAL) : DEFAULT_SIGN_LANGUAGE;
        }
        return null;
    }

    private List<ExcludeDate> buildExcludedDates(Map<String, Object> pairs) {

        if (pairs.containsKey("hearing_options_exclude_dates")) {
            return extractExcludedDates(getField(pairs,"hearing_options_exclude_dates"));
        } else {
            return null;
        }
    }

    private List<ExcludeDate> extractExcludedDates(String excludedDatesList) {
        List<ExcludeDate> excludeDates = new ArrayList<>();

        if (excludedDatesList != null && !excludedDatesList.isEmpty()) {
            List<String> items = Arrays.asList(excludedDatesList.split(",\\s*"));

            for (String item : items) {
                List<String> range = Arrays.asList(item.split("\\s*-\\s*"));
                String errorMessage = "hearing_options_exclude_dates contains an invalid date range. Should be single dates separated by commas and/or a date range e.g. 01/01/2019, 07/01/2019, 12/01/2019 - 15/01/2019";

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

    private List<String> buildArrangements(Map<String, Object> pairs) {

        List<String> arrangements = new ArrayList<>();

        if (areBooleansValid(pairs, HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL) &&  Boolean.parseBoolean(pairs.get(HEARING_OPTIONS_ACCESSIBLE_HEARING_ROOMS_LITERAL).toString())) {
            arrangements.add("disabledAccess");
        }
        if (areBooleansValid(pairs, HEARING_OPTIONS_HEARING_LOOP_LITERAL) && Boolean.parseBoolean(pairs.get(HEARING_OPTIONS_HEARING_LOOP_LITERAL).toString())) {
            arrangements.add("hearingLoop");
        }
        if (areBooleansValid(pairs, HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL) && Boolean.parseBoolean(pairs.get(HEARING_OPTIONS_SIGN_LANGUAGE_INTERPRETER_LITERAL).toString())) {
            arrangements.add("signLanguageInterpreter");
        }
        return arrangements;

    }

    private List<SscsDocument> buildDocumentsFromData(List<ScannedRecord> records) {
        List<SscsDocument> documentDetails = new ArrayList<>();
        if (records != null) {
            for (ScannedRecord record : records) {
                checkFileExtensionValid(record.getFileName());

                SscsDocumentDetails details = SscsDocumentDetails.builder()
                    .documentLink(record.getUrl())
                    .documentDateAdded(stripTimeFromDocumentDate(record.getScannedDate()))
                    .documentFileName(record.getFileName())
                    .documentType("Other document").build();
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
