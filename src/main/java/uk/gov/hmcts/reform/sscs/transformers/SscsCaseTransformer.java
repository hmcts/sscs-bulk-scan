package uk.gov.hmcts.reform.sscs.transformers;

import static uk.gov.hmcts.reform.sscs.constants.SscsConstants.*;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.*;
import static uk.gov.hmcts.reform.sscs.util.SscsOcrDataUtil.convertBooleanToYesNoString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedData;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.ScannedRecord;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;

@Component
public class SscsCaseTransformer implements CaseTransformer {

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    private List<String> errors;

    @Override
    public CaseTransformationResponse transformExceptionRecordToCase(Map<String, Object> caseData) {

        Map<String, Object> transformed = new HashMap<>();
        errors = new ArrayList<>();

        ScannedData sscsData = sscsJsonExtractor.extractJson(caseData);
        Appeal appeal = buildAppealFromData(sscsData.getOcrCaseData());
        List<SscsDocument> sscsDocuments = buildDocumentsFromData(sscsData.getRecords());

        transformed.put("appeal", appeal);
        transformed.put("sscsDocument", sscsDocuments);

        return CaseTransformationResponse.builder().transformedCase(transformed).errors(errors).build();
    }

    private Appeal buildAppealFromData(Map<String, Object> pairs) {
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
                appellant = buildAppelant(pairs, PERSON2_VALUE, appointee, null);

            } else if (hasPerson(pairs, PERSON1_VALUE)) {
                appellant = buildAppelant(pairs, PERSON1_VALUE, null, buildPersonContact(pairs, PERSON1_VALUE));
            }

            return Appeal.builder()
                .benefitType(BenefitType.builder().code(getField(pairs, "benefit_type_description")).build())
                .appellant(appellant)
                .rep(buildRepresentative(pairs))
                .mrnDetails(buildMrnDetails(pairs))
                .hearingType(findHearingType(pairs))
                .hearingOptions(buildHearingOptions(pairs))
                .signer(getField(pairs, "signature_name"))
                .build();
        } else {
            errors.add("No OCR data, case cannot be created");
            return Appeal.builder().build();
        }
    }

    private Appellant buildAppelant(Map<String, Object> pairs, String personType, Appointee appointee, Contact contact) {
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
            .mrnLateReason(getField(pairs,"appeal_late_reason"))
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
        if (areBooleansValid(pairs, errors, "is_hearing_type_oral", "is_hearing_type_paper") && !doValuesContradict(pairs, errors, "is_hearing_type_oral", "is_hearing_type_paper")) {
            return Boolean.parseBoolean(pairs.get("is_hearing_type_oral").toString()) ? "oral" : "paper";
        }
        return null;
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs) {

        boolean isSignLanguageInterpreterRequired = areBooleansValid(pairs, errors, "hearing_options_sign_language_interpreter") ? (boolean) pairs.get("hearing_options_sign_language_interpreter") : false;

        String signLanguageType = findSignLanguageType(pairs, isSignLanguageInterpreterRequired);

        boolean isLanguageInterpreterRequired = findBooleanExists(getField(pairs,"hearing_options_language_type")) && !isSignLanguageInterpreterRequired;

        String languageType = isLanguageInterpreterRequired ? getField(pairs,"hearing_options_language_type") : null;

        return HearingOptions.builder()
            .excludeDates(buildExcludedDates(pairs))
            .arrangements(buildArrangements(pairs))
            .other(getField(pairs,"hearing_support_arrangements"))
            .languageInterpreter(convertBooleanToYesNoString(isLanguageInterpreterRequired))
            .languages(languageType)
            .signLanguageType(signLanguageType)
        .build();
    }

    private String findSignLanguageType(Map<String, Object> pairs, boolean isSignLanguageInterpreterRequired) {
        if (isSignLanguageInterpreterRequired) {
            return getField(pairs, "hearing_options_sign_language_type") != null ? getField(pairs, "hearing_options_sign_language_type") : DEFAULT_SIGN_LANGUAGE;
        }
        return null;
    }

    private List<ExcludeDate> buildExcludedDates(Map<String, Object> pairs) {
        //TODO: Create story to properly implement this

        if (pairs.containsKey("hearing_options_exclude_dates")) {
            List<ExcludeDate> excludeDates = new ArrayList<>();

            excludeDates.add(ExcludeDate.builder().value(DateRange.builder().start(getField(pairs,"hearing_options_exclude_dates")).build()).build());
            return excludeDates;
        } else {
            return null;
        }
    }

    private List<String> buildArrangements(Map<String, Object> pairs) {

        List<String> arrangements = new ArrayList<>();

        if (areBooleansValid(pairs, errors, "hearing_options_accessible_hearing_rooms") && (boolean) pairs.get("hearing_options_accessible_hearing_rooms")) {
            arrangements.add("disabledAccess");
        }
        if (areBooleansValid(pairs, errors, "hearing_options_hearing_loop") && (boolean) pairs.get("hearing_options_hearing_loop")) {
            arrangements.add("hearingLoop");
        }
        if (areBooleansValid(pairs, errors, "hearing_options_sign_language_interpreter") && (boolean) pairs.get("hearing_options_sign_language_interpreter")) {
            arrangements.add("signLanguageInterpreter");
        }
        return arrangements;

    }

    private List<SscsDocument> buildDocumentsFromData(List<ScannedRecord> records) {
        List<SscsDocument> documentDetails = new ArrayList<>();
        if (records != null) {
            for (ScannedRecord record : records) {
                SscsDocumentDetails details = SscsDocumentDetails.builder()
                    .documentLink(DocumentLink.builder().documentUrl(record.getDocumentLink()).build())
                    .documentDateAdded(record.getDocScanDate())
                    .documentFileName(record.getFilename())
                    .documentType("Other document").build();
                documentDetails.add(SscsDocument.builder().value(details).build());
            }
        }
        return documentDetails;
    }
}
