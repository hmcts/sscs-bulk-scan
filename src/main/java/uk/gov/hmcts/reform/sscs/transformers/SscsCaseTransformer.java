package uk.gov.hmcts.reform.sscs.transformers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;
import uk.gov.hmcts.reform.sscs.json.SscsJsonExtractor;

@Component
public class SscsCaseTransformer implements CaseTransformer {

    private static final String YES = "Yes";
    private static final String NO = "No";

    @Autowired
    private SscsJsonExtractor sscsJsonExtractor;

    private List<String> errors;

    @Override
    public CaseTransformationResponse transformExceptionRecordToCase(Map<String, Object> caseData) {

        Map<String, Object> transformed = new HashMap<>();
        errors = new ArrayList<>();

        Map<String, Object> pairs = sscsJsonExtractor.extractJson(caseData);
        Appeal appeal = buildAppealFromData(pairs);
        transformed.put("appeal", appeal);

        return CaseTransformationResponse.builder().transformedCase(transformed).errors(errors).build();
    }

    private Appeal buildAppealFromData(Map<String, Object> pairs) {
        return Appeal.builder()
            .benefitType(BenefitType.builder().code(getField(pairs, "benefit_type_description")).build())
            .appellant(buildAppellant(pairs))
            .rep(buildRepresentative(pairs))
            .mrnDetails(buildMrnDetails(pairs))
            .hearingType(findHearingType(pairs))
            .hearingOptions(buildHearingOptions(pairs))
            .signer(getField(pairs,"signature_appellant_name"))
        .build();
    }

    private Appellant buildAppellant(Map<String, Object> pairs) {
        return Appellant.builder()
            .name(buildAppellantName(pairs))
            .address(buildAppellantAddress(pairs))
            .identity(buildAppellantIdentity(pairs))
            .contact(buildAppellantContact(pairs))
            .isAppointee(buildIsAppointeeField(pairs))
        .build();
    }

    private String buildIsAppointeeField(Map<String, Object> pairs) {
        if (areBooleansValid(pairs, "is_appointee", "is_not_appointee") && !doBooleansContradict(pairs, "is_appointee", "is_not_appointee")) {
            return convertBooleanToYesNoString((boolean) pairs.get("is_appointee"));
        }
        return null;
    }

    private Representative buildRepresentative(Map<String, Object> pairs) {
        boolean doesRepExist = findBooleanExists(getField(pairs,"representative_person_title"), getField(pairs,"representative_person_first_name"),
            getField(pairs,"representative_person_last_name"), getField(pairs,"representative_address_line1"), getField(pairs,"representative_address_line2"),
            getField(pairs,"representative_address_line3"), getField(pairs,"representative_address_line4"), getField(pairs,"representative_postcode"),
            getField(pairs,"representative_phone_number"), getField(pairs,"representative_name"));

        if (doesRepExist) {
            return Representative.builder()
                .hasRepresentative(convertBooleanToYesNoString(doesRepExist))
                .name(buildRepresentativePersonName(pairs))
                .address(buildRepresentativeAddress(pairs))
                .organisation(getField(pairs,"representative_name"))
                .contact(buildRepresentativeContact(pairs))
                .build();
        } else {
            return Representative.builder().hasRepresentative(NO).build();
        }
    }

    private MrnDetails buildMrnDetails(Map<String, Object> pairs) {

        return MrnDetails.builder()
            .mrnLateReason(getField(pairs,"appeal_late_reason"))
        .build();
    }

    private Name buildAppellantName(Map<String, Object> pairs) {
        return Name.builder()
            .title(getField(pairs,"appellant_title"))
            .firstName(getField(pairs,"appellant_first_name"))
            .lastName(getField(pairs,"appellant_last_name"))
        .build();
    }

    private Address buildAppellantAddress(Map<String, Object> pairs) {
        return Address.builder()
            .line1(getField(pairs,"appellant_address_line1"))
            .line2(getField(pairs,"appellant_address_line2"))
            .town(getField(pairs,"appellant_address_line3"))
            .county(getField(pairs,"appellant_address_line4"))
            .postcode(getField(pairs,"appellant_postcode"))
        .build();
    }

    private Identity buildAppellantIdentity(Map<String, Object> pairs) {
        return Identity.builder()
            .dob(getField(pairs,"appellant_date_of_birth"))
            .nino(getField(pairs,"appellant_ni_number"))
        .build();
    }

    private Contact buildAppellantContact(Map<String, Object> pairs) {
        return Contact.builder()
            .phone(getField(pairs,"appellant_phone"))
            .mobile(getField(pairs,"appellant_mobile"))
        .build();
    }

    private Name buildRepresentativePersonName(Map<String, Object> pairs) {
        return Name.builder()
            .title(getField(pairs,"representative_person_title"))
            .firstName(getField(pairs,"representative_person_first_name"))
            .lastName(getField(pairs,"representative_person_last_name"))
        .build();
    }

    private Address buildRepresentativeAddress(Map<String, Object> pairs) {
        return Address.builder()
            .line1(getField(pairs,"representative_address_line1"))
            .line2(getField(pairs,"representative_address_line2"))
            .town(getField(pairs,"representative_address_line3"))
            .county(getField(pairs,"representative_address_line4"))
            .postcode(getField(pairs,"representative_postcode"))
        .build();
    }

    private Contact buildRepresentativeContact(Map<String, Object> pairs) {
        return Contact.builder()
            .phone(getField(pairs,"representative_phone_number"))
        .build();
    }

    private String findHearingType(Map<String, Object> pairs) {
        if (areBooleansValid(pairs, "is_hearing_type_oral", "is_hearing_type_paper") && !doBooleansContradict(pairs, "is_hearing_type_oral", "is_hearing_type_paper")) {
            return (boolean) pairs.get("is_hearing_type_oral") ? "Oral" : "Paper";
        }
        return null;
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs) {

        String isLanguageInterpreterRequired = convertBooleanToYesNoString(findBooleanExists(getField(pairs,"hearing_options_language")));

        //TODO: Handle sign languages here - discuss with Josh
        return HearingOptions.builder()
            .excludeDates(buildExcludedDates(pairs))
            .arrangements(buildArrangements(pairs))
            .languageInterpreter(isLanguageInterpreterRequired)
            .languages(getField(pairs,"hearing_options_language"))
        .build();
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
        // TODO: Create story to properly handle arrangements

        if (pairs.containsKey("hearing_support_arrangements")) {
            List<String> arrangements = new ArrayList<>();

            arrangements.add(getField(pairs,"hearing_support_arrangements"));
            return arrangements;
        } else {
            return null;
        }
    }

    private boolean doBooleansContradict(Map<String, Object> pairs, String value1, String value2) {
        if (pairs.containsKey(value1) && pairs.containsKey(value2)) {
            if ((boolean) pairs.get(value1) == (boolean) pairs.get(value2)) {
                errors.add(value1 + " and " + value2 + " have contradicting values");
                return true;
            }
        }
        return false;
    }

    private boolean areBooleansValid(Map<String, Object> pairs, String... values) {
        for (String value : values) {
            if (pairs.get(value) == null) {
                return false;
            } else if (!(pairs.get(value) instanceof Boolean)) {
                errors.add(value + " does not contain a valid boolean value. Needs to be true or false");
                return false;
            }
        }
        return true;
    }

    private String convertBooleanToYesNoString(boolean value) {
        return value ? YES : NO;
    }

    private boolean findBooleanExists(String... values) {
        for (String v : values) {
            if (v != null) {
                return true;
            }
        }
        return false;
    }

    private String getField(Map<String, Object> pairs, String field) {
        return pairs.get(field) != null ? pairs.get(field).toString() : null;
    }

}
