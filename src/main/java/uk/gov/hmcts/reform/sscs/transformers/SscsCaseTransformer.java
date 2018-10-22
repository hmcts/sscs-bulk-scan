package uk.gov.hmcts.reform.sscs.transformers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.sscs.bulkscancore.domain.CaseTransformationResponse;
import uk.gov.hmcts.reform.sscs.bulkscancore.transformers.CaseTransformer;
import uk.gov.hmcts.reform.sscs.ccd.domain.*;

@Component
public class SscsCaseTransformer implements CaseTransformer {

    @Override
    public CaseTransformationResponse transformExceptionRecordToCase(Map<String, Object> exceptionCaseData) {

        Map<String, Object> transformed = new HashMap<>();
        List<String> errors = new ArrayList<>();

        try {
            HashMap<String, Object> pairs = extractJson(exceptionCaseData);

            Appeal appeal = buildAppealFromData(pairs);

            transformed.put("appeal", appeal);
        } catch (JSONException e) {
            errors.add(e.getMessage());
            e.printStackTrace();
        }

        return CaseTransformationResponse.builder().transformedCase(transformed).errors(errors).build();
    }

    protected Appeal buildAppealFromData(Map<String, Object> pairs) {
        return Appeal.builder()
            .benefitType(BenefitType.builder().code(pairs.get("benefit_type_description").toString()).build())
            .appellant(buildAppellant(pairs))
            .rep(buildRepresentative(pairs))
            .mrnDetails(buildMrnDetails(pairs))
            .hearingType(findHearingType(pairs))
            .hearingOptions(buildHearingOptions(pairs))
            .signer(pairs.get("signature_appellant_name").toString())
        .build();
    }

    private Appellant buildAppellant(Map<String, Object> pairs) {
        return Appellant.builder()
            .name(buildAppellantName(pairs))
            .address(buildAppellantAddress(pairs))
            .identity(buildAppellantIdentity(pairs))
            .contact(buildAppellantContact(pairs))
            .isAppointee(convertBooleanToYesNoString(checkValidBooleans((Boolean) pairs.get("is_appointee"), (Boolean) pairs.get("is_not_appointee"))))
        .build();
    }

    private Representative buildRepresentative(Map<String, Object> pairs) {
        Boolean doesRepExist = findBooleanExists(pairs.get("representative_person_title").toString(), pairs.get("representative_person_first_name").toString(),
            pairs.get("representative_person_last_name").toString(), pairs.get("representative_address_line1").toString(), pairs.get("representative_address_line2").toString(),
            pairs.get("representative_address_line3").toString(), pairs.get("representative_address_line4").toString(), pairs.get("representative_postcode").toString(),
            pairs.get("representative_phone_number").toString(), pairs.get("representative_name").toString());

        return Representative.builder()
            .hasRepresentative(convertBooleanToYesNoString(doesRepExist))
            .name(buildRepresentativePersonName(pairs))
            .address(buildRepresentativeAddress(pairs))
            .organisation(pairs.get("representative_name").toString())
            .contact(buildRepresentativeContact(pairs))
        .build();
    }

    private MrnDetails buildMrnDetails(Map<String, Object> pairs) {

        return MrnDetails.builder()
            .mrnLateReason(pairs.get("appeal_late_reason").toString())
        .build();
    }

    private Name buildAppellantName(Map<String, Object> pairs) {
        return Name.builder()
            .title(pairs.get("appellant_title").toString())
            .firstName(pairs.get("appellant_first_name").toString())
            .lastName(pairs.get("appellant_last_name").toString())
        .build();
    }

    private Address buildAppellantAddress(Map<String, Object> pairs) {
        return Address.builder()
            .line1(pairs.get("appellant_address_line1").toString())
            .line2(pairs.get("appellant_address_line2").toString())
            .town(pairs.get("appellant_address_line3").toString())
            .county(pairs.get("appellant_address_line4").toString())
            .postcode(pairs.get("appellant_postcode").toString())
        .build();
    }

    private Identity buildAppellantIdentity(Map<String, Object> pairs) {
        return Identity.builder()
            .dob(pairs.get("appellant_date_of_birth").toString())
            .nino(pairs.get("appellant_ni_number").toString())
        .build();
    }

    private Contact buildAppellantContact(Map<String, Object> pairs) {
        return Contact.builder()
            .phone(pairs.get("appellant_phone").toString())
            .mobile(pairs.get("appellant_mobile").toString())
        .build();
    }

    private Name buildRepresentativePersonName(Map<String, Object> pairs) {
        return Name.builder()
            .title(pairs.get("representative_person_title").toString())
            .firstName(pairs.get("representative_person_first_name").toString())
            .lastName(pairs.get("representative_person_last_name").toString())
        .build();
    }

    private Address buildRepresentativeAddress(Map<String, Object> pairs) {
        return Address.builder()
            .line1(pairs.get("representative_address_line1").toString())
            .line2(pairs.get("representative_address_line2").toString())
            .town(pairs.get("representative_address_line3").toString())
            .county(pairs.get("representative_address_line4").toString())
            .postcode(pairs.get("representative_postcode").toString())
        .build();
    }

    private Contact buildRepresentativeContact(Map<String, Object> pairs) {
        return Contact.builder()
            .phone(pairs.get("representative_phone_number").toString())
        .build();
    }

    private String findHearingType(Map<String, Object> pairs) {
        if (checkValidBooleans((Boolean) pairs.get("is_hearing_type_oral"), (Boolean) pairs.get("is_hearing_type_paper"))) {
            return (Boolean) pairs.get("is_hearing_type_oral") ? "Oral" : "Paper";
        } else {
            // TODO: handle thrown exception
            return "";
        }
    }

    private HearingOptions buildHearingOptions(Map<String, Object> pairs) {

        String isLanguagueInterpreterRequired = convertBooleanToYesNoString(findBooleanExists(pairs.get("hearing_options_language").toString()));

        //TODO: Handle sign languages here - discuss with Josh
        return HearingOptions.builder()
            .excludeDates(buildExcludedDates(pairs))
            .arrangements(buildArrangements(pairs))
            .languageInterpreter(isLanguagueInterpreterRequired)
            .languages(pairs.get("hearing_options_language").toString())
        .build();
    }

    private List<ExcludeDate> buildExcludedDates(Map<String, Object> pairs) {
        //TODO: Create story to properly implement this
        List<ExcludeDate> excludeDates = new ArrayList<>();

        excludeDates.add(ExcludeDate.builder().value(DateRange.builder().start(pairs.get("hearing_options_exclude_dates").toString()).build()).build());
        return excludeDates;
    }

    private List<String> buildArrangements(Map<String, Object> pairs) {
        // TODO: Create story to properly handle arrangements
        List<String> arrangements = new ArrayList<>();
        arrangements.add(pairs.get("hearing_support_arrangements").toString());

        return arrangements;
    }

    private Boolean checkValidBooleans(Boolean positiveValue, Boolean negativeValue) {
        if (positiveValue != negativeValue) {
            return positiveValue;
        } else {
            // TODO: throw validation exception!
            return false;
        }
    }

    private String convertBooleanToYesNoString(Boolean value) {
        return value ? "Yes" : "No";
    }

    private Boolean findBooleanExists(String... values) {
        for (String v : values) {
            if (v != null) {
                return true;
            }
        }
        return false;
    }

    private HashMap<String, Object> extractJson(Map<String, Object> exceptionCaseData) throws JSONException {
        HashMap<String, Object> pairs = new HashMap<>();

        JSONArray jsonArray = new JSONArray(exceptionCaseData.get("scanOCRData").toString());
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject j = jsonArray.optJSONObject(i);

            JSONObject jsonObject = (JSONObject) j.get("value");

            pairs.put(jsonObject.get("key").toString(), jsonObject.get("value").toString());
        }

        return pairs;
    }
}
